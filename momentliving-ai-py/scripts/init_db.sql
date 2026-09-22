-- ============================================================
-- PostgreSQL DDL（含 Pgvector 向量列 + 索引）
-- 对应迁移方案 04 章；MySQL 侧 ai_* 表保留不动（灰度回滚用）
-- 跑法：psql -U momentliving -d momentliving_ai -f scripts/init_db.sql
-- ============================================================

-- 1. 启用 Pgvector 扩展（必须用 pgvector/pgvector 镜像）
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. 会话表
CREATE TABLE IF NOT EXISTS ai_conversation (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    user_type SMALLINT NOT NULL,           -- 1=C端用户 2=商家
    title VARCHAR(200) DEFAULT '新对话',
    last_message VARCHAR(250),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ai_conv_user ON ai_conversation(user_id, user_type, updated_at DESC);

-- 3. 消息表
CREATE TABLE IF NOT EXISTS ai_message (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,             -- user/assistant/system
    content TEXT,
    tool_calls TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ai_msg_conv ON ai_message(conversation_id, id);

-- 4. 知识库文档
CREATE TABLE IF NOT EXISTS ai_knowledge_doc (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200),
    source_type VARCHAR(20) DEFAULT 'help',
    file_url VARCHAR(500),
    status SMALLINT DEFAULT 0,             -- 0=处理中 1=已入库 2=失败
    chunk_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. 知识库分块（vector(1024) 对应 bge-m3 / text-embedding-v3，换模型必须重建表）
CREATE TABLE IF NOT EXISTS ai_knowledge_chunk (
    id BIGSERIAL PRIMARY KEY,
    doc_id BIGINT NOT NULL,
    content TEXT,
    embedding vector(1024),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ai_chunk_doc ON ai_knowledge_chunk(doc_id);

-- 6. Pgvector 向量索引（HNSW；距离操作符 <=> 与 vector_cosine_ops 必须一致）
CREATE INDEX IF NOT EXISTS idx_ai_chunk_embedding ON ai_knowledge_chunk
    USING hnsw (embedding vector_cosine_ops);

-- 7. 反馈表
CREATE TABLE IF NOT EXISTS ai_feedback (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    rating SMALLINT,
    comment VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ai_feedback_msg ON ai_feedback(message_id);
