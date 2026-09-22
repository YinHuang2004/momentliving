"""博客工具（替代 Spring BlogTools，Feign → httpx）。"""
import httpx
from langchain_core.tools import tool
from pydantic import Field

from app.config import get_settings


def _abbreviate(s: str | None, max_len: int) -> str:
    if not s:
        return ""
    return s if len(s) <= max_len else s[:max_len] + "..."


@tool
async def get_hot_blogs(limit: int = Field(default=5, description="返回数量，默认5，最大10")) -> list[dict]:
    """
    获取平台热门探店笔记列表（标题、店铺、内容摘要），回答"有什么推荐内容/热门笔记"类问题可用

    Args:
        limit: 返回数量，1-10 之间，默认 5
    """
    settings = get_settings()
    max_limit = max(1, min(int(limit or 5), 10))

    async with httpx.AsyncClient(timeout=settings.upstream.timeout) as client:
        # 真实路径：BlogClient.queryHotBlog(1) 对应 GET /blog/hot
        resp = await client.get(
            f"{settings.upstream.blog_service_url}/blog/hot",
            params={"current": 1},
        )
        resp.raise_for_status()
        result = resp.json()

    blogs = (result.get("data") or [])[:max_limit]
    return [
        {
            "blogId": b.get("id"),
            "title": b.get("title") or "",
            "shopId": b.get("shopId") or 0,
            "liked": b.get("liked") or 0,
            "summary": _abbreviate(b.get("content"), 80),
        }
        for b in blogs
    ]
