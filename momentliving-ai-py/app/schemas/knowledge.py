"""请求模型：知识库管理（替代 KnowledgeUploadDTO / 检索预览 body）。"""
from pydantic import BaseModel, ConfigDict, Field


class KnowledgeUploadRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    title: str = Field(..., min_length=1, max_length=200)
    source_type: str | None = Field(default="help", alias="sourceType", max_length=20)
    content: str = Field(..., min_length=1, description="文档全文")


class KnowledgeSearchRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    query: str = Field(default="", max_length=500)
