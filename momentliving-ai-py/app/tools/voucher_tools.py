"""优惠券工具（替代 Spring VoucherTools，Feign → httpx）。"""
import httpx
from langchain_core.tools import tool
from pydantic import Field

from app.clients.auth_header import get_identity_headers
from app.config import get_settings

STATUS_TEXT = {0: "待支付", 1: "已支付未核销", 2: "已核销", 3: "已退款", 4: "已关闭"}

STATUS_MAP = {"unused": 1, "used": 2, "refunded": 3, "closed": 4, "all": None}


def _yuan(cents) -> str:
    if cents is None:
        return "0"
    return f"{cents / 100:.2f}"


@tool
async def get_user_vouchers(
    status: str = Field(default="all", description="状态筛选：unused未使用 / used已使用 / refunded已退款 / closed已关闭过期 / all全部"),
) -> list[dict]:
    """
    查询当前登录用户的优惠券订单列表（待支付/未使用/已使用/已退款），
    回答"我有哪些券/我的订单"类问题必用

    Args:
        status: 状态筛选，默认 all
    """
    settings = get_settings()
    headers = get_identity_headers()

    if "X-User-Id" not in headers and "X-Merchant-Id" not in headers:
        return [{"error": "未获取到用户身份，请重新登录后再试"}]

    status_int = STATUS_MAP.get(status or "all")

    async with httpx.AsyncClient(timeout=settings.upstream.timeout) as client:
        # 真实路径：VoucherClient.myOrders(...) 对应 GET /voucher-order/my
        params: dict = {"current": 1, "pageSize": 20}
        if status_int is not None:
            params["status"] = status_int
        resp = await client.get(
            f"{settings.upstream.voucher_service_url}/voucher-order/my",
            params=params,
            headers=headers,
        )
        resp.raise_for_status()
        result = resp.json()

    orders = result.get("data") or []
    return [
        {
            "orderId": o.get("id"),
            "voucherId": o.get("voucherId"),
            "status": o.get("status"),
            "statusText": STATUS_TEXT.get(o.get("status"), "未知"),
            "createTime": str(o.get("createTime")),
            "payTime": str(o.get("payTime")),
            "useTime": str(o.get("useTime")),
        }
        for o in orders
    ]


@tool
async def get_shop_vouchers(shop_id: int = Field(description="商铺ID")) -> list[dict]:
    """
    根据商铺ID查询该店在售的优惠券列表（标题、售价、抵扣价、类型），
    回答"这家店有什么优惠"类问题必用

    Args:
        shop_id: 商铺ID
    """
    settings = get_settings()

    async with httpx.AsyncClient(timeout=settings.upstream.timeout) as client:
        # 真实路径：VoucherClient.listByShop(shopId) 对应 GET /voucher/list/{shopId}
        resp = await client.get(
            f"{settings.upstream.voucher_service_url}/voucher/list/{shop_id}",
        )
        resp.raise_for_status()
        result = resp.json()

    vouchers = result.get("data") or []
    return [
        {
            "voucherId": v.get("id"),
            "title": v.get("title") or "",
            "subTitle": v.get("subTitle") or "",
            "payPrice": _yuan(v.get("payValue")),
            "deductPrice": _yuan(v.get("actualValue")),
            "type": "秒杀券" if v.get("type") == 2 else "普通券",
        }
        for v in vouchers
    ]
