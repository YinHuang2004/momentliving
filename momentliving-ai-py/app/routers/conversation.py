"""AI 会话管理（C 端 / 商家端通用，身份由请求头决定）。"""
from fastapi import APIRouter, Depends

from app.deps import get_user_id, get_user_type
from app.schemas.common import success
from app.services import conversation_service

router = APIRouter()


@router.get("/conversations")
async def conversations(
    user_id: int = Depends(get_user_id),
    user_type: int = Depends(get_user_type),
):
    """会话列表（按最后消息时间倒序）。"""
    return success(await conversation_service.list_conversations(user_id, user_type))


@router.post("/conversations")
async def create_conversation(
    user_id: int = Depends(get_user_id),
    user_type: int = Depends(get_user_type),
):
    """新建会话。"""
    return success(await conversation_service.create_conversation(user_id, user_type))


@router.delete("/conversations/{conversation_id}")
async def delete_conversation(
    conversation_id: int,
    user_id: int = Depends(get_user_id),
    user_type: int = Depends(get_user_type),
):
    """删除会话（连同历史消息）。"""
    await conversation_service.delete_conversation(conversation_id, user_id, user_type)
    return success()


@router.get("/conversations/{conversation_id}/messages")
async def messages(
    conversation_id: int,
    user_id: int = Depends(get_user_id),
    user_type: int = Depends(get_user_type),
):
    """会话历史消息。"""
    return success(await conversation_service.list_messages(conversation_id, user_id, user_type))
