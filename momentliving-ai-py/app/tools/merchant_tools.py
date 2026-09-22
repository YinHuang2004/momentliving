"""商家经营数据工具（仅商家态可用；C 端工具集不挂这组，替代 Spring MerchantTools）。

链路：网关只透传 X-Merchant-Id → 先调 GET /merchant/me 换 shopId → 再查统计/评分。
"""
import httpx
from langchain_core.tools import tool

from app.clients.auth_header import get_identity_headers
from app.config import get_settings


async def _resolve_shop_id() -> int:
    """用 X-Merchant-Id 换当前商家的 shopId（对应 Java mustGetMerchant）。"""
    settings = get_settings()
    headers = get_identity_headers()
    if "X-Merchant-Id" not in headers:
        raise ValueError("未获取到商家身份，请重新登录商家端")

    async with httpx.AsyncClient(timeout=settings.upstream.timeout) as client:
        # MerchantClient.me() = GET /merchant/me
        resp = await client.get(
            f"{settings.upstream.merchant_service_url}/merchant/me",
            headers=headers,
        )
        resp.raise_for_status()
        me = resp.json().get("data") or {}

    shop_id = me.get("shopId")
    if not shop_id:
        raise ValueError("当前商家未绑定店铺")
    return int(shop_id)


@tool
async def get_merchant_stats(period: str = "today") -> dict:
    """
    获取当前商家的经营数据概览（今日待核销数、已核销数、营收、最近核销记录），
    回答"我今天生意怎么样"类经营问题必用（仅商家身份可用）

    Args:
        period: 时间范围，当前仅支持 today
    """
    settings = get_settings()
    try:
        shop_id = await _resolve_shop_id()
    except ValueError as e:
        return {"error": str(e)}

    async with httpx.AsyncClient(timeout=settings.upstream.timeout) as client:
        # 真实路径：VoucherClient.statsByShop(shopId) 对应
        # GET /feign/voucher-order/verify/stats?shopId=xxx
        resp = await client.get(
            f"{settings.upstream.voucher_service_url}/feign/voucher-order/verify/stats",
            params={"shopId": shop_id},
            headers=get_identity_headers(),
        )
        resp.raise_for_status()
        result = resp.json()

    stats = result.get("data")
    if not stats or not stats.get("today"):
        return {"message": "暂无经营数据"}

    today = stats["today"]
    return {
        "period": "today",
        "pendingVerify": today.get("pendingVerify") or 0,
        "verifiedToday": today.get("verified") or 0,
        "revenueToday": today.get("revenue") or 0,
        "recentVerifies": [
            {
                "voucherTitle": r.get("voucherTitle") or "",
                "buyerNickName": r.get("nickName") or "",
                "verifyTime": str(r.get("verifyTime")),
            }
            for r in (stats.get("recent") or [])[:5]
        ],
    }


@tool
async def get_shop_score() -> dict:
    """获取当前商家店铺的评分信息（平均分、评价数），仅商家身份可用"""
    settings = get_settings()
    try:
        shop_id = await _resolve_shop_id()
    except ValueError as e:
        return {"error": str(e)}

    headers = get_identity_headers()
    # shop-service 的 /review/** 只认 X-User-Id 不认 X-Merchant-Id，
    # Java 版是把商家 ID 临时塞进 X-User-Id 调的（评分是公开聚合数据），照搬
    if "X-Merchant-Id" in headers and "X-User-Id" not in headers:
        headers["X-User-Id"] = headers["X-Merchant-Id"]

    async with httpx.AsyncClient(timeout=settings.upstream.timeout) as client:
        # 真实路径：ShopClient.getScore(shopId) 对应 GET /review/score/{shopId}
        resp = await client.get(
            f"{settings.upstream.shop_service_url}/review/score/{shop_id}",
            headers=headers,
        )
        resp.raise_for_status()
        result = resp.json()

    data = result.get("data")
    return data if data else {"message": "暂无评分数据"}
