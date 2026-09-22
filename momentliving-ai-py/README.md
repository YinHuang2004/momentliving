# momentliving-ai-py

一刻生活 AI 助手服务 —— 按迁移方案（`momentliving-ai-migration`）把 `momentliving-ai-service`（Spring Boot + Spring AI 1.0.0-M6）替换成 **FastAPI + LangChain** 微服务。

**业务调用方零改动**：17 个业务端点（含 1 个 SSE）的 URL / Method / 请求响应体 / SSE 事件协议（`meta` / 默认 data / `done` / `error`）与 Spring 版逐字对齐；响应统一 `{ code: 1, msg: "success", data }`。

## 目录结构

```
app/
├── main.py              # 入口：lifespan + 路由注册（prefix=/ai）
├── config.py            # pydantic-settings 配置（替代 application.yml）
├── lifespan.py          # LLM/Embedding/httpx 客户端生命周期
├── deps.py              # 身份头依赖（X-User-Id / X-Merchant-Id / X-Admin-Id）
├── exception_handlers.py# 异常 → Result{code:0}（HTTP 200，与 Spring 一致）
├── db/                  # PG 异步连接池 + Redis 客户端
├── memory/              # Hybrid 会话存储：Redis 滑动窗口 + PG 全量
├── retrievers/          # Pgvector 余弦检索（score>0.2）
├── prompts/             # AiPromptConstants 逐字迁移
├── tools/               # 6 个 C 端 @tool（httpx 替代 Feign，身份 ContextVar 透传）
├── services/            # chat（工具调用循环+流式）/会话/知识库/生成/商家
└── routers/             # 17 个端点 + SSE
scripts/
├── init_db.sql          # PostgreSQL + Pgvector DDL（5 张 ai_* 表）
└── migrate_embeddings.py# MySQL JSON embedding → Pgvector 迁移脚本
```

## 本地跑起来

```powershell
cd momentliving-ai-py
uv sync                       # 安装依赖（Python 3.12）
Copy-Item .env.example .env   # 填 LLM_API_KEY / EMBED_API_KEY / PG / Redis 等

# 1. 准备 PostgreSQL + Pgvector（docker，运维部分按需）
#    本机无 Docker 时的替代：PG14（D:\software\PostgreSQL\14）自带 pgvector，
#    建角色 momentliving / 库 momentliving_ai + vector 扩展（超级用户建），
#    再执行 scripts/init_db.sql 建表；.env 里 PG_PORT=5432
# 可选：uv run python scripts/migrate_embeddings.py  # 从 MySQL 旧库迁知识库

# 2. 启动（⚠️ Windows 必须用 run.py，不要用 uvicorn --reload）
uv run python run.py

#    为什么：uvicorn 在 win32 非 reload 模式硬编码返回 ProactorEventLoop，
#    而 psycopg 异步只支持 SelectorEventLoop，直接起会报
#    "Psycopg cannot use the 'ProactorEventLoop'"。run.py 里已 monkeypatch 修掉。

# 3. 验证
curl http://localhost:8093/health
curl -N "http://localhost:8093/ai/chat/stream?message=你好" -H "X-User-Id: 1"
# 浏览器打开 http://localhost:8093/docs 看 18 条路由
```

### 本机依赖与已踩的坑（Windows 实测）

| 依赖 | 本机现状 | 说明 |
|---|---|---|
| PostgreSQL 14 + pgvector | `D:\software\PostgreSQL\14`，超级用户密码 postgres | 已建角色 momentliving（密码 momentliving123）、库 momentliving_ai、vector 扩展、5 张 ai_* 表 + HNSW 索引 |
| Redis | 虚机 192.168.19.131:6379（Redis 6.2，密码 210672），与 Java 微服务共用 | 会话 key 前缀 `ai:` 隔离；虚机没开时本项目降级 PG，但每次操作拖 27s，建议保持虚机开机 |
| LLM | DashScope qwen-plus（Key 从环境变量 `ALIBAILIAN_API_KEY` 注入 `.env`） | Chat 走 DashScope |
| Embedding | `BAAI/bge-m3`（1024 维，硅基流动免费版） | 检索稳态 300~700ms（免费档首次调用有冷启动，可达数秒）；⚠️ 硅基流动免费模型也要求账户不欠费，余额为 0/赠金耗尽时连免费模型都报 402 |

已修的 Windows 专属坑（代码里均有注释）：

1. **uvicorn ProactorEventLoop**：见上方「启动」说明，入口 `run.py`；
2. **pg_pool `open=False` 不 open**：psycopg_pool ≥3.2 传 `open=False` 后必须显式 `await pool.open()`，否则 `PoolClosed`（`app/db/pg.py` 已修）；
3. **redis-py 8.x 默认 RESP3**：连接即发 `HELLO` 命令，Redis 3.2.100 不认识 → 全部操作报 `unknown command 'HELLO'` 降级 PG。`app/db/redis_client.py` 已显式 `protocol=2`；
4. **Redis 快速失败**：安全软件对关闭端口 DROP 而非 REJECT，不设超时每次操作拖 ~27s。已设 `socket_connect_timeout=1.5`；
5. **DashScope embeddings 400**：若 Embedding 用 DashScope 需 `check_embedding_ctx_length=False` + `encoding_format=float`（`app/lifespan.py`）；现用硅基流动 bge-m3 无此问题，但参数保留无害；
6. **管理端 /ai/knowledge/search 必须传 retriever**：不传会静默降级关键词匹配，向量检索形同虚设（`app/routers/knowledge.py` 已修）。

## 网关切换

`momentliving-gateway/src/main/resources/application.yml` 中 AI 路由已改为：

```yaml
- id: ai-service-route
  uri: lb://ai-py-service      # 原 lb://ai-service
  predicates:
    - Path=/ai/**
```

Python 服务需注册进 Nacos（`.env` 里 `NACOS_ENABLED=true`，namespace 与网关一致）。本地不起网关联调时保持 `NACOS_ENABLED=false`，直连 `localhost:8093` 并带身份头即可（网关会剥离客户端伪造身份头，直连仅限本机调试）。

## 端点清单（18 条）

| Method | URL | 身份 |
|---|---|---|
| POST | /ai/chat | 用户/商家 |
| GET | /ai/chat/stream（SSE） | 用户/商家 |
| POST | /ai/feedback | 用户/商家 |
| GET/POST | /ai/conversations | 用户/商家 |
| DELETE | /ai/conversations/{id} | 用户/商家 |
| GET | /ai/conversations/{id}/messages | 用户/商家 |
| POST | /ai/recommend/shop | 用户 |
| POST | /ai/generate/blog | 用户 |
| POST | /ai/generate/review | 用户 |
| POST | /ai/knowledge/upload | 管理员 |
| GET | /ai/knowledge/list | 管理员 |
| DELETE | /ai/knowledge/{id} | 管理员 |
| POST | /ai/knowledge/search | 管理员 |
| POST | /ai/merchant/analysis | 商家 |
| POST | /ai/merchant/copywriting | 商家 |
| POST | /ai/merchant/shop-intro | 商家 |
| GET | /health | 公开 |

## 与 Spring 版的差异说明

- 旧 Java 服务保留可启动（灰度回滚用），数据层换到 PostgreSQL + Pgvector；MySQL 的 `ai_*` 表与 `momentliving-ai-service` 一行未动；
- 会话历史：Redis 滑动窗口（`ai:conv:{id}`，7 天 TTL）+ PG `ai_message` 全量；清 Redis 即回退 PG 读；
- RAG：Pgvector HNSW 余弦检索（bge-m3 1024 维），embedding 失败自动降级关键词匹配（与 Java 降级策略一致）；
- 灰度/监控/回滚预案见迁移方案 10/11 章（本次未落地）。
