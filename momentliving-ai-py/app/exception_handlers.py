"""
全局异常处理：把所有异常转成 { code, msg, data: null } 格式。

与 Spring Result 严格对齐：成功 code=1，业务失败 code=0；
HTTP 状态码保持 200（前端 axios 拦截器只看 body.code —— 老项目定死的契约）。
SSE 不走这里：流式异常只能走 event: error 事件（见 services/chat_service.py）。
"""
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.logging_config import get_logger

logger = get_logger(__name__)


class BusinessException(Exception):
    """业务异常（替代 Java BadRequestException，message 直接透传给前端）"""

    def __init__(self, message: str):
        self.message = message
        super().__init__(message)


def _fail(msg: str) -> dict:
    return {"code": 0, "msg": msg, "data": None}


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(BusinessException)
    async def business_handler(request: Request, exc: BusinessException):
        logger.info("业务异常", path=request.url.path, msg=exc.message)
        return JSONResponse(status_code=200, content=_fail(exc.message))

    @app.exception_handler(RequestValidationError)
    async def validation_handler(request: Request, exc: RequestValidationError):
        first = exc.errors()[0] if exc.errors() else {}
        loc = ".".join(str(x) for x in first.get("loc", []))
        msg = f"参数 {loc} 校验失败: {first.get('msg', '')}" if loc else "请求参数格式错误"
        logger.info("参数校验失败", path=request.url.path, errors=exc.errors())
        return JSONResponse(status_code=200, content=_fail(msg))

    @app.exception_handler(Exception)
    async def fallback_handler(request: Request, exc: Exception):
        logger.exception("未捕获异常", path=request.url.path, error=str(exc))
        return JSONResponse(status_code=200, content=_fail("系统繁忙，请稍后重试"))
