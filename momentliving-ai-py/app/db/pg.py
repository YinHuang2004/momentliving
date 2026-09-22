"""PostgreSQL 访问层：全局共享异步连接池（替代 MyBatis-Plus + HikariCP）。

惰性创建：首次访问才建池并 open，应用启动不依赖 PG（/health 无库也能通）。

⚠️ psycopg_pool >= 3.2 语义：构造时传 open=False 后必须显式 await pool.open()，
否则首次取连接抛 PoolClosed("the pool is not open yet")——本项目踩过的坑。
"""
import asyncio

from psycopg_pool import AsyncConnectionPool

from app.config import get_settings

_pool: AsyncConnectionPool | None = None
_lock = asyncio.Lock()


async def get_pool() -> AsyncConnectionPool:
    """双重检查锁：并发首访只建池/开池一次。"""
    global _pool
    if _pool is None:
        async with _lock:
            if _pool is None:
                pool = AsyncConnectionPool(
                    get_settings().pg.dsn,
                    min_size=1,
                    max_size=10,
                    open=False,
                )
                await pool.open(wait=False, timeout=10.0)
                _pool = pool
    return _pool


async def fetch_all(sql: str, params: tuple | list | None = None) -> list[tuple]:
    pool = await get_pool()
    async with pool.connection() as conn:
        async with conn.cursor() as cur:
            await cur.execute(sql, params)
            return await cur.fetchall()


async def fetch_one(sql: str, params: tuple | list | None = None) -> tuple | None:
    rows = await fetch_all(sql, params)
    return rows[0] if rows else None


async def execute(sql: str, params: tuple | list | None = None) -> None:
    pool = await get_pool()
    async with pool.connection() as conn:
        async with conn.cursor() as cur:
            await cur.execute(sql, params)


async def execute_returning_id(sql: str, params: tuple | list | None = None) -> int | None:
    """INSERT ... RETURNING id，返回新行 id。"""
    pool = await get_pool()
    async with pool.connection() as conn:
        async with conn.cursor() as cur:
            await cur.execute(sql, params)
            row = await cur.fetchone()
            return row[0] if row else None
