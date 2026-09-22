"""
配置中心（替代 Spring 的 application.yml + Nacos ai-service.yaml）。

只用 pydantic-settings 一个文件覆盖：
- .env 文件加载（开发）
- 环境变量直接注入（生产由部署平台注入）
- 类型校验
"""
from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

_ENV_CONFIG = SettingsConfigDict(
    env_file=".env",
    env_file_encoding="utf-8",
    case_sensitive=False,
    extra="ignore",
)


class LLMSettings(BaseSettings):
    """LLM 供应商配置（OpenAI 兼容协议）"""
    model_config = _ENV_CONFIG

    base_url: str = Field(default="https://dashscope.aliyuncs.com/compatible-mode/v1", alias="LLM_BASE_URL")
    api_key: str = Field(default="sk-please-fill", alias="LLM_API_KEY")
    model: str = Field(default="qwen-plus", alias="LLM_MODEL")
    temperature: float = Field(default=0.7, alias="LLM_TEMPERATURE")
    max_tokens: int = Field(default=2048, alias="LLM_MAX_TOKENS")
    timeout: int = Field(default=60, alias="LLM_TIMEOUT")
    max_retries: int = Field(default=2, alias="LLM_MAX_RETRIES")


class EmbeddingSettings(BaseSettings):
    """Embedding 模型配置（与 LLM 分开，可能是另一家供应商）"""
    model_config = _ENV_CONFIG

    base_url: str = Field(default="https://api.siliconflow.cn/v1", alias="EMBED_BASE_URL")
    api_key: str = Field(default="sk-please-fill", alias="EMBED_API_KEY")
    model: str = Field(default="Pro/BAAI/bge-m3", alias="EMBED_MODEL")
    dim: int = Field(default=1024, alias="EMBED_DIM")


class PostgresSettings(BaseSettings):
    model_config = _ENV_CONFIG

    host: str = Field(default="localhost", alias="PG_HOST")
    port: int = Field(default=5433, alias="PG_PORT")
    user: str = Field(default="momentliving", alias="PG_USER")
    password: str = Field(default="momentliving123", alias="PG_PASSWORD")
    database: str = Field(default="momentliving_ai", alias="PG_DATABASE")

    @property
    def dsn(self) -> str:
        return (
            f"postgresql://{self.user}:{self.password}"
            f"@{self.host}:{self.port}/{self.database}"
        )


class RedisSettings(BaseSettings):
    model_config = _ENV_CONFIG

    host: str = Field(default="127.0.0.1", alias="REDIS_HOST")
    port: int = Field(default=6379, alias="REDIS_PORT")
    password: str | None = Field(default=None, alias="REDIS_PASSWORD")
    db: int = Field(default=0, alias="REDIS_DB")


class UpstreamSettings(BaseSettings):
    """上游 Java 微服务地址（Tools / 商家与生成接口会调用）"""
    model_config = _ENV_CONFIG

    shop_service_url: str = Field(default="http://localhost:8092", alias="SHOP_SERVICE_URL")
    blog_service_url: str = Field(default="http://localhost:8083", alias="BLOG_SERVICE_URL")
    voucher_service_url: str = Field(default="http://localhost:8084", alias="VOUCHER_SERVICE_URL")
    merchant_service_url: str = Field(default="http://localhost:8090", alias="MERCHANT_SERVICE_URL")
    timeout: int = Field(default=10, alias="UPSTREAM_TIMEOUT")


class NacosSettings(BaseSettings):
    """Nacos 服务注册配置（只做注册，不读配置中心）"""
    model_config = _ENV_CONFIG

    enabled: bool = Field(default=False, alias="NACOS_ENABLED")
    server_addr: str = Field(default="127.0.0.1:8848", alias="NACOS_SERVER_ADDR")
    namespace: str = Field(default="public", alias="NACOS_NAMESPACE")
    username: str = Field(default="nacos", alias="NACOS_USERNAME")
    password: str = Field(default="nacos", alias="NACOS_PASSWORD")


class BusinessSettings(BaseSettings):
    """业务开关与调优（替代 momentliving.ai.*）"""
    model_config = _ENV_CONFIG

    enabled: bool = Field(default=True, alias="AI_ENABLED")
    rag_enabled: bool = Field(default=True, alias="AI_RAG_ENABLED")
    rag_top_k: int = Field(default=5, alias="AI_RAG_TOP_K")
    history_rounds: int = Field(default=10, alias="AI_HISTORY_ROUNDS")
    knowledge_max_chars: int = Field(default=1500, alias="AI_KNOWLEDGE_MAX_CHARS")
    sse_timeout_ms: int = Field(default=60_000, alias="AI_SSE_TIMEOUT_MS")


class Settings(BaseSettings):
    """总配置（聚合所有子配置）"""
    model_config = _ENV_CONFIG

    app_name: str = Field(default="momentliving-ai-py", alias="APP_NAME")
    app_env: str = Field(default="dev", alias="APP_ENV")
    log_level: str = Field(default="INFO", alias="LOG_LEVEL")
    host: str = Field(default="0.0.0.0", alias="HOST")
    port: int = Field(default=8093, alias="PORT")

    llm: LLMSettings = Field(default_factory=LLMSettings)
    embedding: EmbeddingSettings = Field(default_factory=EmbeddingSettings)
    pg: PostgresSettings = Field(default_factory=PostgresSettings)
    redis: RedisSettings = Field(default_factory=RedisSettings)
    upstream: UpstreamSettings = Field(default_factory=UpstreamSettings)
    nacos: NacosSettings = Field(default_factory=NacosSettings)
    business: BusinessSettings = Field(default_factory=BusinessSettings)


@lru_cache
def get_settings() -> Settings:
    """单例 Settings（lru_cache 保证全局一份）。"""
    return Settings()
