"""
FastAPI 应用入口。
- 装配 lifespan
- 注册全局异常处理
- 注册所有 router（prefix 统一 /ai，与 Spring 完全一致）
- 提供 /docs（Swagger UI）与 /health
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import get_settings
from app.exception_handlers import register_exception_handlers
from app.lifespan import lifespan
from app.routers import chat, conversation, feedback, generate, knowledge, merchant

settings = get_settings()

app = FastAPI(
    title="momentliving-ai-py",
    description="FastAPI + LangChain 重构 Spring AI 模块（接口契约与 Spring 版零改动）",
    version="0.1.0",
    lifespan=lifespan,
    docs_url="/docs" if settings.app_env == "dev" else None,
    redoc_url="/redoc" if settings.app_env == "dev" else None,
)

# ===== CORS（前端跨域）=====
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"] if settings.app_env == "dev" else [],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ===== 全局异常处理 =====
register_exception_handlers(app)

# ===== 路由注册（17 个业务端点，见 momentliving-ai-migration 03-接口契约）=====
app.include_router(chat.router, prefix="/ai", tags=["对话"])
app.include_router(conversation.router, prefix="/ai", tags=["会话"])
app.include_router(feedback.router, prefix="/ai", tags=["反馈"])
app.include_router(knowledge.router, prefix="/ai", tags=["知识库"])
app.include_router(merchant.router, prefix="/ai", tags=["商家"])
app.include_router(generate.router, prefix="/ai", tags=["内容生成"])


@app.get("/health", tags=["健康检查"])
async def health():
    """健康检查（Spring Boot Actuator /actuator/health 等价物）。"""
    return {"status": "ok", "app": settings.app_name, "env": settings.app_env}
