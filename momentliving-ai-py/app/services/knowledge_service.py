"""知识库服务（替代 AiKnowledgeServiceImpl）。

入库：按空行分段 + 超长按句切（与 Java split 逻辑一致）→ 向量化 → 存 ai_knowledge_chunk；
检索：pgvector 余弦 top-k（score > 0.2），embedding 失败降级关键词匹配（2 字滑窗）。
"""
import re

from langchain_core.embeddings import Embeddings

from app.db import pg
from app.exception_handlers import BusinessException
from app.logging_config import get_logger
from app.retrievers.pgvector_retriever import PgvectorRetriever

logger = get_logger(__name__)

MAX_CHUNK_CHARS = 500
MIN_SCORE = 0.2  # 相似度过低丢弃（与 Spring 一致）


def split(content: str) -> list[str]:
    """按空行分段，段超 500 字再按句切（逐字对照 Java AiKnowledgeServiceImpl.split）。"""
    result: list[str] = []
    paragraphs = [p.strip() for p in re.split(r"\n\s*\n|\r\n\s*\r\n", content) if p.strip()]
    for paragraph in paragraphs:
        if len(paragraph) <= MAX_CHUNK_CHARS:
            result.append(paragraph)
            continue
        current = ""
        for sentence in re.split(r"(?<=[。！？!?；;\n])", paragraph):
            if len(current) + len(sentence) > MAX_CHUNK_CHARS and current:
                result.append(current.strip())
                current = ""
            current += sentence
        if current:
            result.append(current.strip())
    return result if result else [content.strip()]


def _vec_literal(vec) -> str:
    return "[" + ",".join(repr(float(x)) for x in vec) + "]"


# ===== 管理（/ai/knowledge/**） =====

async def upload(title: str, source_type: str | None, content: str, embedding: Embeddings) -> dict:
    if not title or not title.strip() or not content or not content.strip():
        raise BusinessException("文档标题与内容不能为空")

    from datetime import datetime

    now = datetime.now()
    doc_id = await pg.execute_returning_id(
        """
        INSERT INTO ai_knowledge_doc (title, source_type, status, created_at, updated_at)
        VALUES (%s, %s, 0, %s, %s)
        RETURNING id
        """,
        (title.strip(), source_type or "help", now, now),
    )

    status, chunk_count = 1, 0
    try:
        chunks = split(content)
        # 批量向量化；失败则该批以 NULL 入库（关键词检索仍可用，与 Java tryEmbed 语义一致）
        vectors: list = []
        try:
            vectors = await embedding.aembed_documents([c[:2000] for c in chunks])
        except Exception as e:
            logger.warning("批量向量化失败，本篇知识块将走关键词检索", doc_id=doc_id, error=str(e))
            vectors = []

        dim = len(vectors[0]) if vectors else 0
        for i, chunk in enumerate(chunks):
            vec = vectors[i] if i < len(vectors) and dim else None
            await pg.execute(
                """
                INSERT INTO ai_knowledge_chunk (doc_id, content, embedding, created_at)
                VALUES (%s, %s, %s, %s)
                """,
                (doc_id, chunk, _vec_literal(vec) if vec else None, datetime.now()),
            )
        chunk_count = len(chunks)
        logger.info("知识库文档入库成功", doc_id=doc_id, title=title, chunks=chunk_count)
    except Exception as e:
        status = 2  # 失败（知识块已尽力保存，关键词检索仍可用）
        logger.error("知识库文档入库异常", doc_id=doc_id, error=str(e))

    updated = datetime.now()
    await pg.execute(
        "UPDATE ai_knowledge_doc SET status = %s, chunk_count = %s, updated_at = %s WHERE id = %s",
        (status, chunk_count, updated, doc_id),
    )
    return _doc_vo((doc_id, title.strip(), source_type or "help", status, chunk_count, now, updated))


async def list_docs() -> list[dict]:
    rows = await pg.fetch_all(
        """
        SELECT id, title, source_type, status, chunk_count, created_at, updated_at
        FROM ai_knowledge_doc
        ORDER BY id DESC
        LIMIT 200
        """
    )
    return [_doc_vo(r) for r in rows]


async def delete_doc(doc_id: int) -> None:
    row = await pg.fetch_one("SELECT id FROM ai_knowledge_doc WHERE id = %s", (doc_id,))
    if row is None:
        raise BusinessException("文档不存在")
    await pg.execute("DELETE FROM ai_knowledge_doc WHERE id = %s", (doc_id,))
    await pg.execute("DELETE FROM ai_knowledge_chunk WHERE doc_id = %s", (doc_id,))


def _doc_vo(row) -> dict:
    return {
        "id": row[0],
        "title": row[1],
        "sourceType": row[2],
        "status": row[3],
        "chunkCount": row[4],
        "createdAt": row[5].isoformat(timespec="seconds") if row[5] else None,
        "updatedAt": row[6].isoformat(timespec="seconds") if row[6] else None,
    }


# ===== 检索（RAG / 管理端预览） =====

async def retrieve_context(
    query: str,
    top_k: int,
    max_chars: int,
    retriever: PgvectorRetriever | None = None,
) -> str:
    """向量检索 top-k，失败降级关键词匹配；拼接不超过 max_chars 的知识上下文。"""
    if not query or not query.strip():
        return ""

    matched_contents: list[str] = []
    try:
        if retriever is None:
            raise ValueError("retriever 未就绪")
        chunks = await retriever.retrieve(query, top_k=top_k, min_score=MIN_SCORE)
        matched_contents = [c.content for c in chunks if c.content]
    except Exception as e:
        logger.warning("向量检索失败，降级关键词匹配", error=str(e))
        matched_contents = await _retrieve_by_keyword(query, top_k)

    parts: list[str] = []
    total = 0
    for content in matched_contents:
        if total + len(content) > max_chars:
            break
        parts.append(content)
        total += len(content)
    return "\n".join(parts).strip()


async def _retrieve_by_keyword(query: str, top_k: int) -> list[str]:
    """关键词降级：按 2 字滑窗切词，命中越多越靠前（逐字对照 Java retrieveByKeyword）。"""
    q = re.sub(r"[\s，。？！,\?!]", "", query)
    terms = [q[i:i + 2] for i in range(len(q) - 1)]
    if not terms:
        return []

    rows = await pg.fetch_all(
        """
        SELECT c.content
        FROM ai_knowledge_chunk c
        JOIN ai_knowledge_doc d ON c.doc_id = d.id
        WHERE d.status = 1 AND c.content IS NOT NULL
        LIMIT 5000
        """
    )

    def hits(content: str) -> int:
        return sum(1 for t in terms if t in content)

    scored = [(content, hits(content)) for content, in rows]
    scored = [sc for sc in scored if sc[1] > 0]
    scored.sort(key=lambda x: x[1], reverse=True)
    return [c for c, _ in scored[:top_k]]
