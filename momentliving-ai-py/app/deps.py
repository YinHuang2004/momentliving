"""依赖注入工厂（替代 Spring 拦截器 UserHolder / MerchantHolder / AdminHolder）。

身份约定：网关鉴权通过后注入 X-User-Id / X-Merchant-Id / X-Admin-Id（互斥）。
所有业务异常都抛 BusinessException（全局异常处理器转成 HTTP 200 + code=0，
与 Spring 端 LoginInterceptor + GlobalExceptionHandler 行为一致）。
"""
from typing import Annotated

from fastapi import Depends, Header, Request

from app.config import Settings, get_settings
from app.exception_handlers import BusinessException

# 身份类型（与 Spring AiConversation.userType 对齐）
USER_TYPE_C = 1
USER_TYPE_MERCHANT = 2
USER_TYPE_ADMIN = 3


async def get_user_id(
    x_user_id: Annotated[str | None, Header()] = None,
    x_merchant_id: Annotated[str | None, Header()] = None,
) -> int:
    """当前身份 ID（商家态优先，与 Java currentOwnerId 对齐）。"""
    raw = x_merchant_id or x_user_id
    if not raw:
        raise BusinessException("未登录")
    try:
        return int(raw)
    except ValueError:
        raise BusinessException("身份头格式错误")


async def get_user_type(
    x_user_id: Annotated[str | None, Header()] = None,
    x_merchant_id: Annotated[str | None, Header()] = None,
    x_admin_id: Annotated[str | None, Header()] = None,
) -> int:
    """身份类型：1=C 端用户 2=商家 3=管理员。"""
    if x_admin_id:
        return USER_TYPE_ADMIN
    if x_merchant_id:
        return USER_TYPE_MERCHANT
    if x_user_id:
        return USER_TYPE_C
    raise BusinessException("未登录")


async def require_admin(x_admin_id: Annotated[str | None, Header()] = None) -> int:
    """管理端校验（/ai/knowledge/**，替代 Java AdminHolder 校验）。"""
    if not x_admin_id:
        raise BusinessException("仅平台管理员可管理知识库")
    try:
        return int(x_admin_id)
    except ValueError:
        raise BusinessException("身份头格式错误")


async def require_merchant(x_merchant_id: Annotated[str | None, Header()] = None) -> int:
    """商家态校验（/ai/merchant/**，替代 Java MerchantHolder 校验）。"""
    if not x_merchant_id:
        raise BusinessException("请先登录商家端")
    try:
        return int(x_merchant_id)
    except ValueError:
        raise BusinessException("身份头格式错误")


def get_http_client(request: Request):
    """上游 Java 微服务的共享 httpx 客户端（lifespan 启动时创建）。"""
    return request.app.state.upstream_client


def get_chat_service(request: Request):
    """对话服务单例（lifespan 启动时创建，含 LLM / 检索器 / 工具集）。"""
    return request.app.state.chat_service


def get_settings_dep() -> Settings:
    return get_settings()
