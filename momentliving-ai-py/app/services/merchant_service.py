"""商家 AI 服务（替代 AiMerchantServiceImpl，B 端，身份 X-Merchant-Id）。

数据全部来自实时接口（工作台统计 + 店铺评分 + 店铺详情），AI 只负责解读与撰写。
"""
import json

import httpx
from langchain_core.language_models import BaseChatModel
from langchain_core.messages import HumanMessage, SystemMessage

from app.config import Settings
from app.exception_handlers import BusinessException
from app.prompts.constants import (
    B_END_SYSTEM,
    MERCHANT_ANALYSIS,
    MERCHANT_COPYWRITING,
    SHOP_INTRO,
)


def _nz(o) -> str:
    return "-" if o is None else str(o)


async def _must_get_merchant(http: httpx.AsyncClient, settings: Settings, merchant_id: int) -> dict:
    """用 X-Merchant-Id 回查 /merchant/me（对应 Java mustGetMerchant + Feign 透传）。"""
    resp = await http.get(
        f"{settings.upstream.merchant_service_url}/merchant/me",
        headers={"X-Merchant-Id": str(merchant_id)},
    )
    resp.raise_for_status()
    merchant = resp.json().get("data") or {}
    if not merchant:
        raise BusinessException("请先登录商家端")
    if not merchant.get("shopId"):
        raise BusinessException("商家账号未绑定店铺，无法使用经营分析")
    return merchant


async def _must_get_shop(http: httpx.AsyncClient, settings: Settings, shop_id: int) -> dict:
    resp = await http.get(f"{settings.upstream.shop_service_url}/shop/{shop_id}")
    resp.raise_for_status()
    result = resp.json()
    if not result.get("data"):
        raise BusinessException("店铺不存在")
    return result["data"]


async def analysis(
    merchant_id: int, settings: Settings, http: httpx.AsyncClient, chat_model: BaseChatModel
) -> str:
    merchant = await _must_get_merchant(http, settings, merchant_id)
    shop_id = merchant["shopId"]

    # 拉真实经营数据（核销统计 + 店铺评分）
    data_parts: list[str] = []
    stats = None
    try:
        resp = await http.get(
            f"{settings.upstream.voucher_service_url}/feign/voucher-order/verify/stats",
            params={"shopId": shop_id},
        )
        resp.raise_for_status()
        stats = resp.json().get("data")
    except Exception:
        stats = None

    today = (stats or {}).get("today")
    if today:
        data_parts.append(
            f"今日待核销：{_nz(today.get('pendingVerify'))}"
            f"；今日已核销：{_nz(today.get('verified'))}"
            f"；今日营收：{_nz(today.get('revenue'))} 元；"
        )
    else:
        data_parts.append("今日暂无核销数据；")

    # shop-service 的 /review/** 只认 X-User-Id；商家 ID 临时充当查询身份（评分是公开聚合数据）
    try:
        resp = await http.get(
            f"{settings.upstream.shop_service_url}/review/score/{shop_id}",
            headers={"X-User-Id": str(merchant_id)},
        )
        resp.raise_for_status()
        score_data = resp.json().get("data")
    except Exception:
        score_data = None
    if score_data is not None:
        data_parts.append(f"店铺评分：{json.dumps(score_data, ensure_ascii=False)}；")

    recent = (stats or {}).get("recent") or []
    if recent:
        data_parts.append("最近核销：")
        for item in recent[:5]:
            data_parts.append(f"{item.get('voucherTitle')}（{item.get('nickName')}）")

    shop = await _must_get_shop(http, settings, shop_id)
    prompt = MERCHANT_ANALYSIS % (
        _nz(merchant.get("name")),
        _nz(shop.get("name")),
        _nz(shop.get("score")),
        "".join(data_parts),
    )
    resp = await chat_model.ainvoke(
        [SystemMessage(content=B_END_SYSTEM), HumanMessage(content=prompt)]
    )
    return str(resp.content or "")


async def copywriting(
    merchant_id: int,
    voucher_desc: str,
    selling_point: str | None,
    settings: Settings,
    http: httpx.AsyncClient,
    chat_model: BaseChatModel,
) -> str:
    if not voucher_desc or not voucher_desc.strip():
        raise BusinessException("券信息不能为空")
    await _must_get_merchant(http, settings, merchant_id)
    prompt = MERCHANT_COPYWRITING % (voucher_desc, selling_point or "无")
    resp = await chat_model.ainvoke(
        [SystemMessage(content=B_END_SYSTEM), HumanMessage(content=prompt)]
    )
    return str(resp.content or "")


async def shop_intro(
    merchant_id: int, settings: Settings, http: httpx.AsyncClient, chat_model: BaseChatModel
) -> str:
    merchant = await _must_get_merchant(http, settings, merchant_id)
    shop = await _must_get_shop(http, settings, merchant["shopId"])
    prompt = SHOP_INTRO % (
        _nz(shop.get("name")),
        _nz(shop.get("area")),
        _nz(shop.get("avgPrice")),
        _nz(shop.get("score")),
        _nz(shop.get("openHours")),
    )
    resp = await chat_model.ainvoke(
        [SystemMessage(content=B_END_SYSTEM), HumanMessage(content=prompt)]
    )
    return str(resp.content or "")
