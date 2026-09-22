"""内容生成与推荐服务（替代 AiGenerateServiceImpl）。

推荐链路：偏好描述 → AI 提取搜索关键词 → shop-service 搜真实店铺 → AI 生成推荐理由（JSON 解析，失败兜底）。
生成链路：店铺真实信息（GET /shop/{id}）+ 提示词 → 文案。
"""
import json
import re

import httpx
from langchain_core.language_models import BaseChatModel
from langchain_core.messages import HumanMessage, SystemMessage

from app.config import Settings
from app.exception_handlers import BusinessException
from app.logging_config import get_logger
from app.prompts.constants import (
    C_END_SYSTEM,
    GENERATE_BLOG,
    GENERATE_REVIEW,
    RECOMMEND_EXTRACT,
    RECOMMEND_REASON,
)

logger = get_logger(__name__)

STYLE_MAP = {
    "humor": "幽默风趣，带点段子感",
    "literary": "文艺清新，有画面感",
    "simple": "简洁自然，信息量足",
}

DEFAULT_REASON = "契合你的偏好，值得一试"


def _text(resp) -> str:
    content = resp.content
    if isinstance(content, list):
        return "".join(c if isinstance(c, str) else str(c.get("text") or "") for c in content)
    return str(content or "")


def _strip_code_fence(reply: str | None) -> str:
    if reply is None:
        return "[]"
    return re.sub(r"```(json)?", "", reply).strip()


def _nz(s) -> str:
    return "-" if s is None else str(s)


async def _must_get_shop(http: httpx.AsyncClient, settings: Settings, shop_id: int | None) -> dict:
    if shop_id is None:
        raise BusinessException("店铺 ID 不能为空")
    resp = await http.get(f"{settings.upstream.shop_service_url}/shop/{shop_id}")
    resp.raise_for_status()
    result = resp.json()
    if not result.get("data"):
        raise BusinessException("店铺不存在")
    return result["data"]


def _recommend_vo(shop: dict, reason: str | None) -> dict:
    return {
        "shopId": shop.get("id"),
        "name": shop.get("name"),
        "area": shop.get("area"),
        "address": shop.get("address"),
        "avgPrice": shop.get("avgPrice"),
        "score": shop.get("score"),
        "reason": reason if reason and reason.strip() else DEFAULT_REASON,
    }


async def recommend_shop(
    preference: str, area: str | None, settings: Settings, http: httpx.AsyncClient, chat_model: BaseChatModel
) -> list[dict]:
    if not preference or not preference.strip():
        raise BusinessException("偏好描述不能为空")

    # 1. 提取搜索关键词
    resp = await chat_model.ainvoke(RECOMMEND_EXTRACT % preference.strip())
    keyword = _text(resp).strip()
    for ch in "\"'。":
        keyword = keyword.replace(ch, "")
    keyword = keyword.strip()
    if len(keyword) > 20:
        keyword = keyword[:20]
    if not keyword:
        raise BusinessException("没能从偏好描述中提取到店铺关键词，请描述得更具体些")

    # 2. 搜真实店铺（ShopQueryDTO 无 area 字段，关键词追加区域提高命中率）
    name = f"{keyword} {area.strip()}" if area and area.strip() else keyword
    search = await http.get(
        f"{settings.upstream.shop_service_url}/shop/of/name",
        params={"name": name, "current": 1},
    )
    search.raise_for_status()
    shops = search.json().get("data") or []
    if not shops:
        raise BusinessException(f"没有找到与「{keyword}」相关的店铺，换个关键词试试")

    # 3. AI 生成推荐理由（JSON 输出解析，失败兜底）
    candidates = json.dumps(
        [
            {
                "shopId": s.get("id"),
                "name": _nz(s.get("name")),
                "area": _nz(s.get("area")),
                "avgPrice": s.get("avgPrice"),
                "score": s.get("score"),
            }
            for s in shops[:10]
        ],
        ensure_ascii=False,
    )
    recommendations: list[dict] = []
    try:
        resp = await chat_model.ainvoke(RECOMMEND_REASON % (preference.strip(), candidates))
        items = json.loads(_strip_code_fence(_text(resp)))
        for item in items:
            shop_id = item.get("shopId")
            match = next((s for s in shops if s.get("id") == shop_id), None)
            if match:
                recommendations.append(_recommend_vo(match, item.get("reason")))
            if len(recommendations) >= 5:
                break
    except Exception as e:
        logger.warning("推荐理由解析失败，使用兜底推荐语", keyword=keyword, error=str(e))

    if not recommendations:
        # 兜底：取前 5 家 + 默认推荐语
        recommendations = [
            _recommend_vo(s, f"契合你的偏好「{preference.strip()}」，值得一试") for s in shops[:5]
        ]
    return recommendations


async def generate_blog(
    shop_id: int, style: str, keywords: str | None,
    settings: Settings, http: httpx.AsyncClient, chat_model: BaseChatModel,
) -> str:
    shop = await _must_get_shop(http, settings, shop_id)
    style_text = STYLE_MAP.get(style or "simple", STYLE_MAP["simple"])
    keyword_line = (
        "" if not keywords or not keywords.strip()
        else "用户想突出的关键词/内容：" + keywords
    )
    prompt = GENERATE_BLOG % (
        style_text,
        keyword_line,
        _nz(shop.get("name")),
        _nz(shop.get("area")),
        _nz(shop.get("address")),
        _nz(shop.get("avgPrice")),
        _nz(shop.get("score")),
        _nz(shop.get("openHours")),
    )
    resp = await chat_model.ainvoke(
        [SystemMessage(content=C_END_SYSTEM), HumanMessage(content=prompt)]
    )
    return _text(resp)


async def generate_review(
    shop_id: int, rating: int, impression: str,
    settings: Settings, http: httpx.AsyncClient, chat_model: BaseChatModel,
) -> str:
    if rating is None or rating < 1 or rating > 5:
        raise BusinessException("评分必须为 1-5 星")
    if not impression or not impression.strip():
        raise BusinessException("简短感受不能为空")
    shop = await _must_get_shop(http, settings, shop_id)
    prompt = GENERATE_REVIEW % (shop.get("name"), rating, impression.strip())
    resp = await chat_model.ainvoke(
        [SystemMessage(content=C_END_SYSTEM), HumanMessage(content=prompt)]
    )
    return _text(resp)
