"""请求模型：对话（替代 AiChatDTO）。响应统一在 services 层转 camelCase dict。"""
from pydantic import BaseModel, ConfigDict, Field


class ChatRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    message: str = Field(..., min_length=1, max_length=2000, description="用户消息")
    conversation_id: int | None = Field(default=None, alias="conversationId", description="会话ID，为空则新建")
