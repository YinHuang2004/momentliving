"""Redis 访问层：全局共享异步客户端（会话滑动窗口 / 会话删除时清缓存）。"""
from redis.asyncio import Redis

from app.config import get_settings

_client: Redis | None = None


def get_redis() -> Redis:
    global _client
    if _client is None:
        s = get_settings().redis
        _client = Redis(
            host=s.host,
            port=s.port,
            password=s.password,
            db=s.db,
            decode_responses=True,
            # ⚠️ 必须显式 RESP2：redis-py 8.x 默认 RESP3，连接即发 HELLO 命令，
            # 而老版本 Redis（如 Windows 3.2.100）不认识 HELLO，每次操作都报
            # "unknown command 'HELLO'" 导致全部降级 PG（踩过的坑）
            protocol=2,
            # ⚠️ Redis 挂掉时必须快速失败：本机安全软件会 DROP 而非 REJECT，
            # 不设超时每次操作拖 ~27s，把整条对话链路拖死（踩过的坑）
            socket_connect_timeout=1.5,
            socket_timeout=2,
            retry_on_timeout=False,
            retry_on_error=False,
        )
    return _client


async def close_redis() -> None:
    global _client
    if _client is not None:
        await _client.aclose()
        _client = None
