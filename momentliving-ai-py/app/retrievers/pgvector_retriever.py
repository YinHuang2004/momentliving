"""
Pgvector 检索器（替代 Spring 自写余弦相似度 + MySQL JSON embedding）。

- 直接 psycopg 查（SQL 透明、可控），不用 LangChain 的 PGVector 类；
- 余弦距离 <=> 越小越相似，相似度 = 1 - 距离，阈值与 Spring 一致（score > 0.2）；
- embedding 失败时由 knowledge_service 降级到关键词匹配。
"""
from typing import List

from langchain_core.embeddings import Embeddings

from app.db import pg


class RetrievedChunk:
    __slots__ = ("content", "score", "doc_id", "chunk_id")

    def __init__(self, content: str, score: float, doc_id: int, chunk_id: int):
        self.content = content
        self.score = score
        self.doc_id = doc_id
        self.chunk_id = chunk_id


def _vec_literal(vec: List[float]) -> str:
    return "[" + ",".join(repr(float(x)) for x in vec) + "]"


class PgvectorRetriever:
    def __init__(self, embedding: Embeddings):
        self.embedding = embedding

    async def retrieve(
        self,
        query: str,
        top_k: int = 5,
        source_type: str | None = None,
        min_score: float = 0.2,
    ) -> List[RetrievedChunk]:
        """检索 top_k 最相似的知识块（仅已入库 status=1 的文档）。"""
        if not query.strip():
            return []

        query_vec = await self.embedding.aembed_query(query)
        vec_str = _vec_literal(query_vec)

        source_filter = "AND d.source_type = %s" if source_type else ""
        sql = f"""
            SELECT c.id, c.doc_id, c.content,
                   1 - (c.embedding <=> %s::vector) AS score
            FROM ai_knowledge_chunk c
            JOIN ai_knowledge_doc d ON c.doc_id = d.id
            WHERE d.status = 1
              AND c.content IS NOT NULL
              AND (c.embedding <=> %s::vector) < %s
              {source_filter}
            ORDER BY c.embedding <=> %s::vector
            LIMIT %s
        """
        params: list = [vec_str, vec_str, 1 - min_score]
        if source_type:
            params.append(source_type)
        params.extend([vec_str, top_k])

        rows = await pg.fetch_all(sql, params)
        return [
            RetrievedChunk(content=r[2], score=float(r[3]), doc_id=r[1], chunk_id=r[0])
            for r in rows
        ]
