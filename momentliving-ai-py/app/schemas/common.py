"""
统一响应包装（与 Spring Result{code:1/0, msg, data} 对齐）。

⚠️ code=1 表示成功，code=0 表示业务异常 —— 与 FastAPI 社区惯例相反，以 Spring 为准。
成功时 msg 固定为 "success"。
"""
from typing import Any


def success(data: Any = None, msg: str = "success") -> dict:
    return {"code": 1, "msg": msg, "data": data}


def fail(msg: str, code: int = 0) -> dict:
    return {"code": code, "msg": msg, "data": None}
