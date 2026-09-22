"""
Windows 本地启动入口（生产 Linux 不受影响，可直接 uvicorn）。

⚠️ 为什么需要这个文件：两个 Windows 专属坑叠加：

1. psycopg 异步模式只支持 SelectorEventLoop，不支持 Windows 默认的
   ProactorEventLoop（报错：Psycopg cannot use the 'ProactorEventLoop'
   to run in async mode）。
2. 本机安装的 uvicorn 版本在 win32 + 非 reload 模式下**硬编码**返回
   ProactorEventLoop（见 .venv/.../uvicorn/loops/asyncio.py 第 9-10 行），
   连 `asyncio.set_event_loop_policy()` 都会被无视。

所以必须在启动前把 uvicorn 的 loop 工厂替换为 SelectorEventLoop。
跑法：uv run python run.py
"""
import asyncio
import sys

if sys.platform == "win32":
    # 保险：给其他依赖 policy 的库兜底
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())

    # 关键：覆盖 uvicorn 的 Windows loop 工厂（Config.get_loop_factory 每次动态
    # import 该模块后 getattr，因此运行前替换属性即可生效）
    # ⚠️ 必须返回**类**（不加括号）：本版 uvicorn 会把 asyncio_loop_factory 的返回值
    # 当 loop 工厂再调用一次（Runner._loop_factory()），返回实例会报
    # "'_WindowsSelectorEventLoop' object is not callable"
    import uvicorn.loops.asyncio as _uv_asyncio

    _uv_asyncio.asyncio_loop_factory = lambda use_subprocess=False: asyncio.SelectorEventLoop

import uvicorn

if __name__ == "__main__":
    uvicorn.run(
        "app.main:app",
        host="127.0.0.1",
        port=8093,
        log_level="info",
    )
