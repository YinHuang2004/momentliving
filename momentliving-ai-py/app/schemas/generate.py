"""请求模型：内容生成与推荐（替代 AiRecommendDTO / AiGenerateBlogDTO / AiGenerateReviewDTO）。"""
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class RecommendRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    preference: str = Field(..., min_length=1, max_length=500, description="偏好描述")
    area: str | None = Field(default=None, max_length=50, description="可选区域")


class GenerateBlogRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    shop_id: int = Field(..., alias="shopId")
    style: Literal["simple", "humor", "literary"] = Field(default="simple")
    keywords: str | None = Field(default=None, max_length=200)


class GenerateReviewRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    shop_id: int = Field(..., alias="shopId")
    rating: int = Field(..., ge=1, le=5)
    impression: str = Field(..., min_length=1, max_length=500)
