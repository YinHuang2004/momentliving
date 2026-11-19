-- 一刻生活 AI 服务 PostgreSQL + pgvector 初始化脚本
-- 执行示例：psql -U momentliving -d momentliving_ai -f ai-schema.sql

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS ai_conversation (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    user_type SMALLINT NOT NULL,
    title VARCHAR(200) DEFAULT '新对话',
    last_message VARCHAR(250),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ai_conv_user
    ON ai_conversation(user_id, user_type, updated_at DESC);

CREATE TABLE IF NOT EXISTS ai_message (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT,
    tool_calls TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ai_msg_conv
    ON ai_message(conversation_id, id);

CREATE TABLE IF NOT EXISTS ai_knowledge_doc (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200),
    source_type VARCHAR(20) DEFAULT 'help',
    file_url VARCHAR(500),
    status SMALLINT DEFAULT 0,
    chunk_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 当前 embedding 模型 BAAI/bge-m3 输出 1024 维；更换维度后必须迁移该列并重建索引。
CREATE TABLE IF NOT EXISTS ai_knowledge_chunk (
    id BIGSERIAL PRIMARY KEY,
    doc_id BIGINT NOT NULL,
    content TEXT,
    embedding vector(1024),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ai_chunk_doc
    ON ai_knowledge_chunk(doc_id);
CREATE INDEX IF NOT EXISTS idx_ai_chunk_embedding
    ON ai_knowledge_chunk USING hnsw (embedding vector_cosine_ops);

CREATE TABLE IF NOT EXISTS ai_feedback (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    rating SMALLINT,
    comment VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
