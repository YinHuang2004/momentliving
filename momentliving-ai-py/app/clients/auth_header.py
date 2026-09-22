"""请求上下文身份传递（替代 Spring FeignIdentityInterceptor / ToolContext）。

ChatService 在调链之前把身份写入 ContextVar，工具内部调上游时读出来透传
X-User-Id / X-Merchant-Id —— AI 决定不了查谁，防越权。
用 Token + finally reset，避免身份泄漏到下一个请求。
"""
from contextvars import ContextVar

_current_user_id: ContextVar[int | None] = ContextVar("current_user_id", default=None)
_current_merchant_id: ContextVar[int | None] = ContextVar("current_merchant_id", default=None)


def set_current_user_id(uid: int):
    return _current_user_id.set(uid)


def set_current_merchant_id(mid: int):
    return _current_merchant_id.set(mid)


def reset_current_user_id(token) -> None:
    _current_user_id.reset(token)


def reset_current_merchant_id(token) -> None:
    _current_merchant_id.reset(token)


def get_identity_headers() -> dict[str, str]:
    """给上游 HTTP 调用用的身份头。"""
    headers: dict[str, str] = {}
    uid = _current_user_id.get()
    mid = _current_merchant_id.get()
    if uid:
        headers["X-User-Id"] = str(uid)
    if mid:
        headers["X-Merchant-Id"] = str(mid)
    return headers
