"""AI 推荐与内容生成（C 端）。"""
from fastapi import APIRouter, Depends, Request

from app.deps import get_http_client, get_user_id
from app.schemas.common import success
from app.schemas.generate import GenerateBlogRequest, GenerateReviewRequest, RecommendRequest
from app.services import generate_service

router = APIRouter()


@router.post("/recommend/shop")
async def recommend_shop(
    dto: RecommendRequest,
    request: Request,
    _user_id: int = Depends(get_user_id),
):
    """商铺推荐：偏好描述 → 真实店铺 + AI 推荐语。"""
    items = await generate_service.recommend_shop(
        dto.preference, dto.area,
        request.app.state.settings, get_http_client(request), request.app.state.chat_model,
    )
    return success(items)


@router.post("/generate/blog")
async def generate_blog(
    dto: GenerateBlogRequest,
    request: Request,
    _user_id: int = Depends(get_user_id),
):
    """探店博客草稿生成（返回"标题：xxx + 正文"文本，前端可一键带入发布页）。"""
    text = await generate_service.generate_blog(
        dto.shop_id, dto.style, dto.keywords,
        request.app.state.settings, get_http_client(request), request.app.state.chat_model,
    )
    return success(text)


@router.post("/generate/review")
async def generate_review(
    dto: GenerateReviewRequest,
    request: Request,
    _user_id: int = Depends(get_user_id),
):
    """评价文案生成。"""
    text = await generate_service.generate_review(
        dto.shop_id, dto.rating, dto.impression,
        request.app.state.settings, get_http_client(request), request.app.state.chat_model,
    )
    return success(text)
