"""
MySQL 旧库 → PostgreSQL + Pgvector 全量迁移（对应迁移方案 04 章）。

迁移范围（2026-09-22 盘点结论）：
- ai_conversation / ai_message：MySQL 0 行，无历史会话，不迁；
- ai_knowledge_doc + ai_knowledge_chunk：MySQL 1 doc + 6 chunk，全部迁移；
- 旧库 embedding 字段全为 NULL（Java 版从未算过向量），故全部用
  .env 里的 EMBED_MODEL（BAAI/bge-m3 免费版）重算。

与旧版差异：
- 补上 doc 表迁移（否则 chunk.doc_id 外键断裂）；
- TRUNCATE 后显式插入原 id，并重置序列（setval），后续自增不冲突；
- 迁移后自动回填 doc.chunk_count。

跑法（在 momentliving-ai-py 目录下）：
  uv run --with pymysql python scripts/migrate_embeddings.py             # 全量
  uv run --with pymysql python scripts/migrate_embeddings.py --dry-run   # 只看不写
"""
import argparse
import json
import os
import time

import psycopg
import pymysql
from dotenv import load_dotenv
from openai import OpenAI

load_dotenv()

EMBED_DIM = int(os.getenv("EMBED_DIM", "1024"))  # 与 init_db.sql 的 vector(1024) 对齐

MYSQL = dict(
    host=os.getenv("MYSQL_HOST", "localhost"),
    port=int(os.getenv("MYSQL_PORT", "3306")),
    user=os.getenv("MYSQL_USER", "root"),
    password=os.getenv("MYSQL_PASSWORD", "123456"),
    database=os.getenv("MYSQL_DATABASE", "hmdp"),  # 旧 Java 服务库（hmdp）
    charset="utf8mb4",
)

PG = dict(
    host=os.getenv("PG_HOST", "localhost"),
    port=int(os.getenv("PG_PORT", "5432")),
    user=os.getenv("PG_USER", "momentliving"),
    password=os.getenv("PG_PASSWORD", "momentliving123"),
    dbname=os.getenv("PG_DATABASE", "momentliving_ai"),
)

client = OpenAI(
    api_key=os.getenv("EMBED_API_KEY", ""),
    base_url=os.getenv("EMBED_BASE_URL", "https://api.siliconflow.cn/v1"),
)
EMBED_MODEL = os.getenv("EMBED_MODEL", "BAAI/bge-m3")


def embed_text(text: str) -> list[float]:
    """bge-m3 重算向量（限速 0.2s/条，避免免费档限流）。"""
    resp = client.embeddings.create(model=EMBED_MODEL, input=text[:2000])
    vec = resp.data[0].embedding
    if len(vec) != EMBED_DIM:
        raise ValueError(f"维度不符: {len(vec)} != {EMBED_DIM}")
    return vec


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="只读不写")
    args = parser.parse_args()

    print("=== 知识库迁移开始 ===")
    print(f"MySQL: {MYSQL['host']}:{MYSQL['port']}/{MYSQL['database']}")
    print(f"PostgreSQL: {PG['host']}:{PG['port']}/{PG['dbname']}")
    print(f"Embedding: {EMBED_MODEL} ({EMBED_DIM} dim, 全量重算)\n")

    # ===== 1. 读 MySQL =====
    mysql_conn = pymysql.connect(**MYSQL)
    with mysql_conn.cursor(pymysql.cursors.DictCursor) as cur:
        cur.execute("SELECT id, title, source_type, status, created_at, updated_at FROM ai_knowledge_doc ORDER BY id")
        docs = cur.fetchall()
        cur.execute("SELECT id, doc_id, content FROM ai_knowledge_chunk ORDER BY id")
        chunks = cur.fetchall()
    mysql_conn.close()
    print(f"MySQL: {len(docs)} 个文档, {len(chunks)} 个知识块")
    for d in docs:
        print(f"  doc id={d['id']} title={d['title']!r} source={d['source_type']} status={d['status']}")

    if args.dry_run:
        print("\n[dry-run] 不写库，退出")
        return

    # ===== 2. 向量重算 =====
    print("\n重算 embedding（逐条）...")
    vectors = []
    for c in chunks:
        vec = embed_text(c["content"])
        vectors.append(vec)
        print(f"  chunk id={c['id']} -> {len(vec)} 维 OK")
        time.sleep(0.2)

    # ===== 3. 写 PG（TRUNCATE 重建，保留原 id）=====
    with psycopg.connect(**PG) as pg:
        with pg.cursor() as cur:
            cur.execute("TRUNCATE TABLE ai_knowledge_chunk, ai_knowledge_doc RESTART IDENTITY CASCADE")

            for d in docs:
                cur.execute(
                    """INSERT INTO ai_knowledge_doc (id, title, source_type, status, created_at, updated_at)
                       VALUES (%s, %s, %s, %s, %s, %s)""",
                    (d["id"], d["title"], d["source_type"], d["status"],
                     d["created_at"], d["updated_at"]),
                )
            # 重置 doc 序列：显式插入了原 id，自增要从 max(id)+1 继续
            if docs:
                cur.execute("SELECT setval('ai_knowledge_doc_id_seq', (SELECT MAX(id) FROM ai_knowledge_doc))")

            for c, vec in zip(chunks, vectors):
                cur.execute(
                    "INSERT INTO ai_knowledge_chunk (id, doc_id, content, embedding, created_at) VALUES (%s, %s, %s, %s::vector, NOW())",
                    (c["id"], c["doc_id"], c["content"], "[" + ",".join(repr(float(x)) for x in vec) + "]"),
                )
            if chunks:
                cur.execute("SELECT setval('ai_knowledge_chunk_id_seq', (SELECT MAX(id) FROM ai_knowledge_chunk))")

            # 回填 chunk_count
            cur.execute("""
                UPDATE ai_knowledge_doc d
                SET chunk_count = (SELECT COUNT(*) FROM ai_knowledge_chunk c WHERE c.doc_id = d.id)
            """)
        pg.commit()

    # ===== 4. 验证 =====
    print("\n=== 迁移完成，验证 ===")
    with psycopg.connect(**PG) as pg:
        with pg.cursor() as cur:
            cur.execute("SELECT COUNT(*) FROM ai_knowledge_doc")
            print(f"PG ai_knowledge_doc: {cur.fetchone()[0]} 行")
            cur.execute("SELECT COUNT(*), COUNT(embedding) FROM ai_knowledge_chunk")
            total, with_vec = cur.fetchone()
            print(f"PG ai_knowledge_chunk: {total} 行（{with_vec} 行有向量）")

    print("\n下一步：调 POST /ai/knowledge/search 验证语义检索（管理员身份）")


if __name__ == "__main__":
    main()
