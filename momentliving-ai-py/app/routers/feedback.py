"""AI 回答反馈（点赞/点踩/评分）。"""
from fastapi import APIRouter, Depends

from app.deps import get_user_id, get_user_type
from app.schemas.common import success
from app.schemas.conversation import FeedbackRequest
from app.services import conversation_service

router = APIRouter()


@router.post("/feedback")
async def feedback(
    dto: FeedbackRequest,
    user_id: int = Depends(get_user_id),
    user_type: int = Depends(get_user_type),
):
    await conversation_service.save_feedback(
        dto.message_id, dto.rating, dto.comment, user_id, user_type
    )
    return success()
