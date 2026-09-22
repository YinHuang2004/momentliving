"""应用启动/关闭生命周期管理（替代 Spring @PostConstruct / @PreDestroy + Bean 生命周期）。"""
from contextlib import asynccontextmanager
from typing import AsyncIterator

import httpx
from langchain_openai import ChatOpenAI, OpenAIEmbeddings
from psycopg_pool import AsyncConnectionPool

from app.config import get_settings
from app.db import pg
from app.db.redis_client import close_redis
from app.logging_config import get_logger, setup_logging
from app.nacos_client import deregister_from_nacos, register_to_nacos
from app.retrievers.pgvector_retriever import PgvectorRetriever
from app.services.chat_service import ChatService

logger = get_logger(__name__)


@asynccontextmanager
async def lifespan(app) -> AsyncIterator[None]:
    settings = get_settings()

    # ===== 启动 =====
    setup_logging(settings.log_level)
    logger.info("应用启动", app_name=settings.app_name, env=settings.app_env, port=settings.port)
    app.state.settings = settings

    # 1. LLM 客户端（OpenAI 兼容协议）
    app.state.chat_model = ChatOpenAI(
        model=settings.llm.model,
        base_url=settings.llm.base_url,
        api_key=settings.llm.api_key,
        temperature=settings.llm.temperature,
        max_tokens=settings.llm.max_tokens,
        timeout=settings.llm.timeout,
        max_retries=settings.llm.max_retries,
        streaming=True,
    )

    # 2. Embedding 模型（独立于 Chat 模型，可能走另一家供应商）
    # ⚠️ check_embedding_ctx_length=False：非 OpenAI 模型禁用本地 tiktoken 分词；
    #    encoding_format=float：DashScope 兼容层不认默认的 base64
    app.state.embedding_model = OpenAIEmbeddings(
        model=settings.embedding.model,
        base_url=settings.embedding.base_url,
        api_key=settings.embedding.api_key,
        check_embedding_ctx_length=False,
        model_kwargs={"encoding_format": "float"},
    )

    # 3. 上游 Java 微服务共享 HTTP 客户端（连接池复用，替代 Feign）
    app.state.upstream_client = httpx.AsyncClient(
        timeout=settings.upstream.timeout,
        limits=httpx.Limits(max_connections=100, max_keepalive_connections=20),
        headers={"User-Agent": "momentliving-ai-py/0.1"},
    )

    # 4. 对话服务单例（含 C 端 6 个工具的绑定）+ 全局向量检索器
    #    （chat_service 内部持有一份；app.state.retriever 供管理端 /ai/knowledge/search 复用）
    app.state.retriever = PgvectorRetriever(app.state.embedding_model)
    app.state.chat_service = ChatService(settings, app.state.chat_model, app.state.embedding_model)

    # 5. Nacos 注册（网关 lb://ai-py-service 依赖；失败不阻断启动）
    app.state.nacos_client = None
    if settings.nacos.enabled:
        try:
            app.state.nacos_client, _ = register_to_nacos(settings, settings.port)
        except Exception as e:
            logger.warning("Nacos 注册失败（网关将无法发现本服务）", error=str(e))

    # 配置自检：占位 Key 给出明确告警（不影响 /health）
    if "please-fill" in settings.llm.api_key or "please-fill" in settings.embedding.api_key:
        logger.warning("LLM_API_KEY / EMBED_API_KEY 仍是占位值，请复制 .env.example 为 .env 并填写真实 Key")

    logger.info("应用启动完成")
    yield

    # ===== 关闭 =====
    logger.info("应用关闭")
    if app.state.nacos_client is not None:
        deregister_from_nacos(app.state.nacos_client, settings, settings.port)
    await app.state.upstream_client.aclose()
    await close_redis()
    pool: AsyncConnectionPool | None = pg._pool
    if pool is not None:
        await pool.close()
