"""AI 知识库管理（仅平台管理员：网关管理端模式透传 X-Admin-Id）。"""
from fastapi import APIRouter, Depends, Request

from app.deps import require_admin
from app.schemas.common import success
from app.schemas.knowledge import KnowledgeSearchRequest, KnowledgeUploadRequest
from app.services import knowledge_service

router = APIRouter()


@router.post("/knowledge/upload")
async def upload(
    dto: KnowledgeUploadRequest,
    request: Request,
    _admin_id: int = Depends(require_admin),
):
    """上传文档（纯文本/Markdown 内容；文件类内容先读成文本再传）。"""
    doc = await knowledge_service.upload(
        dto.title, dto.source_type, dto.content, request.app.state.embedding_model
    )
    return success(doc)


@router.get("/knowledge/list")
async def list_docs(_admin_id: int = Depends(require_admin)):
    """文档列表。"""
    return success(await knowledge_service.list_docs())


@router.delete("/knowledge/{doc_id}")
async def delete_doc(
    doc_id: int,
    _admin_id: int = Depends(require_admin),
):
    """删除文档（连同知识块）。"""
    await knowledge_service.delete_doc(doc_id)
    return success()


@router.post("/knowledge/search")
async def search(
    dto: KnowledgeSearchRequest,
    request: Request,
    _admin_id: int = Depends(require_admin),
):
    """检索测试（管理端调优知识库用：看某问题会命中哪些知识片段）。"""
    # ⚠️ 必须传 retriever：不传会静默降级关键词匹配，向量检索形同虚设（踩过的坑）
    context = await knowledge_service.retrieve_context(
        dto.query, 5, 1500, request.app.state.retriever
    )
    return success(context)
