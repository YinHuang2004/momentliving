"""商铺工具（替代 Spring ShopTools，含身份敏感调用）。"""
import httpx
from langchain_core.tools import tool
from pydantic import Field

from app.clients.auth_header import get_identity_headers
from app.config import get_settings


def _abbreviate(s: str | None, max_len: int) -> str:
    if not s:
        return ""
    return s if len(s) <= max_len else s[:max_len] + "..."


def _shop_to_map(shop: dict) -> dict:
    return {
        "shopId": shop.get("id"),
        "name": shop.get("name") or "",
        "area": shop.get("area") or "",
        "address": shop.get("address") or "",
        "avgPrice": shop.get("avgPrice") or 0,
        "score": shop.get("score") or 0,
        "sold": shop.get("sold") or 0,
        "openHours": shop.get("openHours") or "",
    }


@tool
async def search_shops(
    keyword: str = Field(description="搜索关键词，如火锅店、咖啡"),
    sort: str | None = Field(default=None, description="排序方式：score评分最高 / sold销量最高 / other默认"),
) -> list[dict]:
    """
    根据关键词搜索商铺，返回商铺列表（名称、区域、地址、评分、人均、销量），
    回答"找店/推荐店铺"类问题必用

    Args:
        keyword: 搜索关键词
        sort: 排序方式，可选
    """
    settings = get_settings()

    async with httpx.AsyncClient(timeout=settings.upstream.timeout) as client:
        # 真实路径：ShopClient.searchShops(SpringQueryMap) 对应 GET /shop/of/name
        resp = await client.get(
            f"{settings.upstream.shop_service_url}/shop/of/name",
            params={"name": keyword, "current": 1, "sort": sort or "other"},
        )
        resp.raise_for_status()
        result = resp.json()

    shops = result.get("data") or []
    if sort == "score":
        shops = sorted(shops, key=lambda s: s.get("score") or 0, reverse=True)
    elif sort == "sold":
        shops = sorted(shops, key=lambda s: s.get("sold") or 0, reverse=True)

    return [_shop_to_map(s) for s in shops[:10]]


@tool
async def get_shop_detail(shop_id: int = Field(description="商铺ID")) -> dict:
    """根据商铺ID获取商铺详情（名称、区域、地址、人均、评分、销量、营业时间）"""
    settings = get_settings()

    async with httpx.AsyncClient(timeout=settings.upstream.timeout) as client:
        resp = await client.get(f"{settings.upstream.shop_service_url}/shop/{shop_id}")
        resp.raise_for_status()
        result = resp.json()

    shop = result.get("data")
    if not shop:
        return {"error": "商铺不存在"}
    return _shop_to_map(shop)


@tool
async def get_shop_reviews(shop_id: int = Field(description="商铺ID")) -> list[dict]:
    """
    获取商铺的用户评价列表（星级、内容、昵称），回答"这家店评价怎么样"类问题可用

    Args:
        shop_id: 商铺ID
    """
    settings = get_settings()
    headers = get_identity_headers()

    # 上游 shop-service 的 /review/** 路径要求登录态（X-User-Id）
    if "X-User-Id" not in headers and "X-Merchant-Id" not in headers:
        return [{"error": "未获取到用户身份，请重新登录后再试"}]

    async with httpx.AsyncClient(timeout=settings.upstream.timeout) as client:
        # 真实路径：ShopClient.getReviews(shopId, current) 对应 GET /review/list/{shopId}
        resp = await client.get(
            f"{settings.upstream.shop_service_url}/review/list/{shop_id}",
            params={"current": 1},
            headers=headers,
        )
        resp.raise_for_status()
        result = resp.json()

    reviews = result.get("data") or []
    return [
        {
            "rating": r.get("rating") or 5,
            "content": _abbreviate(r.get("content"), 100),
            "nickName": r.get("nickName") or "匿名用户",
        }
        for r in reviews[:10]
    ]
