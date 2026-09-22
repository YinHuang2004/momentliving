"""会话管理服务（替代 AiConversationServiceImpl，表 ai_conversation / ai_message / ai_feedback）。"""
import asyncio
from datetime import datetime

from app.db import pg
from app.db.redis_client import get_redis
from app.exception_handlers import BusinessException
from app.logging_config import get_logger

logger = get_logger(__name__)

ISO = "seconds"


def conv_vo(row) -> dict:
    """(id, title, last_message, updated_at) → AiConversationVO（camelCase）。"""
    return {
        "id": row[0],
        "title": row[1],
        "lastMessage": row[2],
        "updatedAt": row[3].isoformat(timespec=ISO) if row[3] else None,
    }


def msg_vo(row) -> dict:
    """(id, conversation_id, role, content, tool_calls, created_at) → AiMessage（camelCase）。"""
    return {
        "id": row[0],
        "conversationId": row[1],
        "role": row[2],
        "content": row[3],
        "toolCalls": row[4],
        "createdAt": row[5].isoformat(timespec=ISO) if row[5] else None,
    }


async def list_conversations(owner_id: int, user_type: int) -> list[dict]:
    rows = await pg.fetch_all(
        """
        SELECT id, title, last_message, updated_at
        FROM ai_conversation
        WHERE user_id = %s AND user_type = %s
        ORDER BY updated_at DESC
        LIMIT 50
        """,
        (owner_id, user_type),
    )
    return [conv_vo(r) for r in rows]


async def create_conversation(owner_id: int, user_type: int) -> dict:
    now = datetime.now()
    conv_id = await pg.execute_returning_id(
        """
        INSERT INTO ai_conversation (user_id, user_type, title, created_at, updated_at)
        VALUES (%s, %s, '新对话', %s, %s)
        RETURNING id
        """,
        (owner_id, user_type, now, now),
    )
    return {
        "id": conv_id,
        "userId": owner_id,
        "userType": user_type,
        "title": "新对话",
        "lastMessage": None,
        "createdAt": now.isoformat(timespec=ISO),
        "updatedAt": now.isoformat(timespec=ISO),
    }


async def must_get_owned(conversation_id: int, owner_id: int, user_type: int) -> tuple:
    """所有权校验：会话必须属于当前身份，防横向越权（对应 Java mustGetOwned）。"""
    row = await pg.fetch_one(
        "SELECT id, user_id, user_type FROM ai_conversation WHERE id = %s",
        (conversation_id,),
    )
    if row is None:
        raise BusinessException("会话不存在")
    if row[1] != owner_id or row[2] != user_type:
        raise BusinessException("无权访问该会话")
    return row


async def delete_conversation(conversation_id: int, owner_id: int, user_type: int) -> None:
    await must_get_owned(conversation_id, owner_id, user_type)
    await pg.execute("DELETE FROM ai_conversation WHERE id = %s", (conversation_id,))
    # 连同历史消息一起删
    await pg.execute(
        "DELETE FROM ai_message WHERE conversation_id = %s", (conversation_id,)
    )
    # 清掉 Redis 滑动窗口
    try:
        await get_redis().delete(f"ai:conv:{conversation_id}")
    except Exception as e:
        logger.warning("删除会话时清理 Redis 失败", conversation_id=conversation_id, error=str(e))


async def list_messages(conversation_id: int, owner_id: int, user_type: int) -> list[dict]:
    await must_get_owned(conversation_id, owner_id, user_type)
    rows = await pg.fetch_all(
        """
        SELECT id, conversation_id, role, content, tool_calls, created_at
        FROM ai_message
        WHERE conversation_id = %s
        ORDER BY id ASC
        """,
        (conversation_id,),
    )
    return [msg_vo(r) for r in rows]


async def get_or_create(conversation_id: int | None, owner_id: int, user_type: int) -> tuple:
    """无 conversationId 则新建，否则校验所有权。返回 (id,) 行。"""
    if conversation_id is not None:
        return await must_get_owned(conversation_id, owner_id, user_type)
    created = await create_conversation(owner_id, user_type)
    return (created["id"], created["userId"], created["userType"])


async def touch_conversation(conversation_id: int, last_message: str | None) -> None:
    if last_message is not None and len(last_message) > 200:
        last_message = last_message[:200]
    await pg.execute(
        "UPDATE ai_conversation SET last_message = %s, updated_at = %s WHERE id = %s",
        (last_message, datetime.now(), conversation_id),
    )


async def save_feedback(
    message_id: int, rating: int, comment: str | None, owner_id: int, user_type: int
) -> None:
    row = await pg.fetch_one(
        "SELECT conversation_id, role FROM ai_message WHERE id = %s", (message_id,)
    )
    if row is None or row[1] != "assistant":
        raise BusinessException("反馈目标消息不存在")
    # 校验消息归属：该消息所在会话必须是当前身份的
    await must_get_owned(row[0], owner_id, user_type)
    await pg.execute(
        """
        INSERT INTO ai_feedback (message_id, user_id, rating, comment, created_at)
        VALUES (%s, %s, %s, %s, %s)
        """,
        (message_id, owner_id, rating, comment, datetime.now()),
    )


async def generate_title_task(
    conversation_id: int, first_user_message: str, chat_model
) -> None:
    """
    异步生成会话标题（fire-and-forget，对应 Java generateTitleAsync @Async）。
    失败回退为消息截断，彻底失败保持"新对话"。必须有标题为默认值的会话才生成。
    """
    try:
        row = await pg.fetch_one(
            "SELECT title FROM ai_conversation WHERE id = %s", (conversation_id,)
        )
        if row is None or row[0] != "新对话":
            return

        from app.prompts.constants import GENERATE_TITLE

        resp = await chat_model.ainvoke(GENERATE_TITLE % first_user_message)
        title = str(resp.content or "").strip()
        for ch in "\"'。！!？?\n":
            title = title.replace(ch, "")
        title = title.strip()
        if not title:
            raise ValueError("empty title")
        if len(title) > 20:
            title = title[:20]
        await pg.execute(
            "UPDATE ai_conversation SET title = %s WHERE id = %s AND title = '新对话'",
            (title, conversation_id),
        )
    except Exception as e:
        logger.warning("会话标题生成失败", conversation_id=conversation_id, error=str(e))
        try:
            fallback = first_user_message[:12]
            await pg.execute(
                "UPDATE ai_conversation SET title = %s WHERE id = %s AND title = '新对话'",
                (fallback, conversation_id),
            )
        except Exception:
            pass


def spawn_title_task(conversation_id: int, first_user_message: str, chat_model) -> None:
    """在事件循环里创建 fire-and-forget 任务（对应 Java aiExecutor.execute）。"""
    asyncio.get_running_loop().create_task(
        generate_title_task(conversation_id, first_user_message, chat_model)
    )
