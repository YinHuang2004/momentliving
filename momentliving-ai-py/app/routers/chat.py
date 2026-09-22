"""对话接口（C 端）：POST /ai/chat、GET /ai/chat/stream（SSE）。"""
from fastapi import APIRouter, Depends, Query, Request
from sse_starlette.sse import EventSourceResponse

from app.deps import get_chat_service, get_user_id, get_user_type
from app.logging_config import get_logger
from app.schemas.chat import ChatRequest
from app.schemas.common import success
from app.services.chat_service import ChatService

logger = get_logger(__name__)

router = APIRouter()


@router.post("/chat")
async def chat(
    dto: ChatRequest,
    user_id: int = Depends(get_user_id),
    user_type: int = Depends(get_user_type),
    chat_service: ChatService = Depends(get_chat_service),
):
    """同步对话（不支持 SSE 的客户端用）。"""
    vo = await chat_service.chat(dto.message, dto.conversation_id, user_id, user_type)
    return success(vo)


@router.get("/chat/stream")
async def chat_stream(
    request: Request,
    message: str = Query(..., min_length=1, max_length=2000),
    conversation_id: int | None = Query(default=None, alias="conversationId"),
    user_id: int = Depends(get_user_id),
    user_type: int = Depends(get_user_type),
    chat_service: ChatService = Depends(get_chat_service),
):
    """
    流式对话（SSE，逐字输出）。
    事件约定：meta=会话ID（JSON）→ 默认 data=文本增量 → done=[DONE] / error=友好提示。
    """
    async def event_generator():
        disconnected = False
        async for event in chat_service.stream_events(message, conversation_id, user_id, user_type):
            # 客户端断连检测：立即停止生成，节省 Token
            if await request.is_disconnected():
                logger.info("客户端断开连接，停止推送", user_id=user_id)
                disconnected = True
                break
            yield event

    return EventSourceResponse(
        event_generator(),
        ping=15,
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",  # 告诉反向代理别缓冲
        },
    )
