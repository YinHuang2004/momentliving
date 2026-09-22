"""
Hybrid 会话存储：Redis 滑动窗口（热）+ PostgreSQL 全量持久化（冷）。

- 写：先 Redis（同步），再 PG（失败仅记日志，不影响对话）—— PG 挂了 Redis 还能撑；
- 读：Redis 命中即返回；miss 则查 PG 最近 N 条回填 Redis；
- 清空 Redis 即降级到 PG 全量读（可回滚，方案硬约束第 5 条）。

Redis key: ai:conv:{conversation_id}（String，存最近 window_size 条 JSON，7 天过期）
PG table:  ai_message
"""
from datetime import datetime
from typing import List

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage

from app.config import Settings
from app.db import pg
from app.db.redis_client import get_redis
from app.logging_config import get_logger
from app.memory.message_codec import deserialize_messages, serialize_messages

logger = get_logger(__name__)

_REDIS_TTL = 7 * 24 * 3600


class HybridChatMessageHistory:
    def __init__(self, conversation_id: int, window_size: int = 20):
        self.conversation_id = conversation_id
        self.window_size = window_size
        self._loaded: List[BaseMessage] = []  # aget_messages 后的本地快照，供 __iter__ 使用

    @property
    def _redis_key(self) -> str:
        return f"ai:conv:{self.conversation_id}"

    def __iter__(self):
        """让 history 对象可直接 *history 展开（不含 aadd_message 新追加的当前消息）。"""
        return iter(self._loaded)

    async def aget_messages(self) -> List[BaseMessage]:
        """Redis 命中即返回；miss 查 PG 最近 N 条 user/assistant 消息并回填。"""
        r = get_redis()
        try:
            cached = await r.get(self._redis_key)
        except Exception as e:
            logger.warning("Redis 读取失败，降级 PG", error=str(e))
            cached = None

        if cached:
            # ⚠️ 必须同步写 self._loaded：*history 展开走 __iter__（读 _loaded），
            # 只 return 不赋值会导致 Redis 热路径下多轮上下文丢失（已实测复现）
            msgs = deserialize_messages(cached)
            self._loaded = list(msgs)
            return msgs

        rows = await pg.fetch_all(
            """
            SELECT role, content FROM (
                SELECT role, content, id
                FROM ai_message
                WHERE conversation_id = %s AND role IN ('user', 'assistant')
                  AND content IS NOT NULL
                ORDER BY id DESC
                LIMIT %s
            ) t ORDER BY id ASC
            """,
            (self.conversation_id, self.window_size),
        )
        msgs: List[BaseMessage] = []
        for role, content in rows:
            if role == "user":
                msgs.append(HumanMessage(content=content))
            elif role == "assistant":
                msgs.append(AIMessage(content=content))
        self._loaded = list(msgs)

        try:
            await r.set(self._redis_key, serialize_messages(msgs), ex=_REDIS_TTL)
        except Exception as e:
            logger.warning("Redis 回填失败", error=str(e))
        return msgs

    async def aadd_message(self, message: BaseMessage) -> tuple[int, datetime] | None:
        """追加一条：Redis 窗口 + PG 全量。返回 (id, created_at) 供响应 VO 用。"""
        r = get_redis()
        role = self._role_of(message)

        # 1. Redis 滑动窗口
        try:
            cached = await r.get(self._redis_key)
            msgs = deserialize_messages(cached) if cached else []
            msgs.append(message)
            if len(msgs) > self.window_size:
                msgs = msgs[-self.window_size:]
            await r.set(self._redis_key, serialize_messages(msgs), ex=_REDIS_TTL)
        except Exception as e:
            logger.warning("Redis 写入失败", error=str(e))

        # 2. PG 全量持久化（RETURNING id, created_at 供响应 VO 用）
        row = await pg.fetch_one(
            """
            INSERT INTO ai_message (conversation_id, role, content, created_at)
            VALUES (%s, %s, %s, %s)
            RETURNING id, created_at
            """,
            (self.conversation_id, role, message.content, datetime.now()),
        )
        return (row[0], row[1]) if row else None

    async def aclear(self) -> None:
        """清空会话热缓存（PG 由 delete_conversation 清）。"""
        try:
            await get_redis().delete(self._redis_key)
        except Exception as e:
            logger.warning("Redis 清理失败", error=str(e))

    @staticmethod
    def _role_of(msg: BaseMessage) -> str:
        return {
            "HumanMessage": "user",
            "AIMessage": "assistant",
            "SystemMessage": "system",
            "ToolMessage": "tool",
        }.get(type(msg).__name__, "user")
