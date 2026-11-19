# momentliving-ai-service

一刻生活的 Java AI 微服务，使用 Spring AI 1.1.2、Spring AI Alibaba、PostgreSQL/pgvector 和 Redis，实现对话、RAG、工具调用、内容生成与商家分析。正式运行链路只保留该实现。

## 主要能力

- `ChatClient` 同步对话和 SSE 流式对话；
- `KnowledgeRagAdvisor` 为同步/流式请求统一注入知识库上下文；
- `PgKnowledgeVectorStore` 使用 pgvector HNSW 余弦检索，并提供关键词降级；
- `HybridChatMemory` 使用 Redis 保存热窗口、PostgreSQL 保存全量消息；
- `ToolContext` 在服务端传递用户或商家身份，避免模型伪造身份参数；
- 店铺、博客、优惠券、商家数据工具和多种内容生成能力。

## 技术版本

| 组件 | 版本 |
|---|---|
| JDK | 17 |
| Spring Boot | 3.5.11 |
| Spring AI | 1.1.2 |
| Spring AI Alibaba | 1.1.2.x |
| Spring Cloud | 2025.0.0 |
| PostgreSQL | 14+，安装 pgvector |

该模块使用独立的 Spring Boot 3.5 父 POM，因为 Spring AI 1.1.x 与 Boot 3.5.x 对齐。模块仍由根 Maven 工程聚合，但不能让根工程 Boot 3.2.5 的依赖管理覆盖本模块。

## 配置

复制配置模板：

```powershell
Copy-Item src/main/resources/application-template.yml src/main/resources/application.yml
```

至少提供 `AI_PG_PASSWORD`、`REDIS_PASSWORD`、`DASHSCOPE_API_KEY` 和 `AI_EMBEDDING_API_KEY`。不要在 README、Git 或 Nacos 明文中记录真实密码和密钥。

项目当前 Nacos 中的 `ai-service.yaml` 是旧配置，尚未由本模块导入；完成 Nacos Config 接入前，以环境变量和本地 `application.yml` 为准。

## 初始化 PostgreSQL

```bash
psql -U momentliving -d momentliving_ai \
  -f src/main/resources/sql/ai-schema.sql
```

脚本会创建 `vector` 扩展、会话表、消息表、知识文档/分块表、反馈表和 HNSW 索引。当前 Embedding 维度固定为 1024，更换模型时必须同步迁移向量列并重建索引。

## 构建和启动

```bash
mvn -pl momentliving-ai-service -am test
java -jar momentliving-ai-service/target/momentliving-ai-service-1.0.0.jar
```

验证顺序：`GET /health`、Nacos 实例、同步对话、SSE `meta → data → done`、PG 消息落库、Redis 会话窗口、知识检索和各业务工具。

## 上游服务端口

| 服务 | 本地地址 |
|---|---|
| shop-service | `http://localhost:8092` |
| blog-service | `http://localhost:8083` |
| voucher-service | `http://localhost:8084` |
| merchant-service | `http://localhost:8090` |

Docker 部署时通过环境变量把地址改为 Compose 服务名，例如 `http://shop-service:8092`。
