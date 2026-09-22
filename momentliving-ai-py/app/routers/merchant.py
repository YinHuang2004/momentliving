"""商家 AI 接口（B 端：X-Merchant-Id 透传）。"""
from fastapi import APIRouter, Depends, Request

from app.deps import get_http_client, require_merchant
from app.schemas.common import success
from app.schemas.merchant import CopywritingRequest
from app.services import merchant_service

router = APIRouter()


@router.post("/merchant/analysis")
async def analysis(
    request: Request,
    merchant_id: int = Depends(require_merchant),
):
    """经营分析助手。"""
    text = await merchant_service.analysis(
        merchant_id, request.app.state.settings, get_http_client(request), request.app.state.chat_model
    )
    return success(text)


@router.post("/merchant/copywriting")
async def copywriting(
    dto: CopywritingRequest,
    request: Request,
    merchant_id: int = Depends(require_merchant),
):
    """营销文案生成。"""
    text = await merchant_service.copywriting(
        merchant_id, dto.voucher_desc, dto.selling_point,
        request.app.state.settings, get_http_client(request), request.app.state.chat_model,
    )
    return success(text)


@router.post("/merchant/shop-intro")
async def shop_intro(
    request: Request,
    merchant_id: int = Depends(require_merchant),
):
    """店铺介绍优化。"""
    text = await merchant_service.shop_intro(
        merchant_id, request.app.state.settings, get_http_client(request), request.app.state.chat_model
    )
    return success(text)
