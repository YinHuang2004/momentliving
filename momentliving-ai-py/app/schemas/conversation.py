"""请求模型：会话反馈（替代 AiFeedbackDTO）。"""
from pydantic import BaseModel, ConfigDict, Field


class FeedbackRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    message_id: int = Field(..., alias="messageId", description="被反馈的消息ID")
    rating: int = Field(..., ge=1, le=5, description="评分1-5")
    comment: str | None = Field(default=None, max_length=500)
