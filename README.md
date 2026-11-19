# 一刻生活（momentliving）

一刻生活是一个本地生活服务平台，覆盖探店笔记、店铺检索、优惠券、限时秒杀、订单支付、到店核销、即时聊天、商家入驻、管理后台和 AI 助手。

后端以 Spring Boot 3、Spring Cloud Alibaba、Nacos、Redis、RabbitMQ、MySQL 为主；AI 服务统一使用 Spring AI，不再维护 Python 运行时实现。

## 工程结构

```text
momentliving/
├── momentliving-gateway/             # API 网关、JWT 鉴权和身份头注入（8080）
├── momentliving-common/              # Result、异常、JWT、Redis 常量等公共能力
├── momentliving-pojo/                # Entity、DTO、VO
├── momentliving-api/                 # OpenFeign 客户端和身份透传
├── momentliving-user-service/        # 用户、登录、资料、足迹、积分（8081）
├── momentliving-blog-service/        # 探店笔记、点赞、关注、Feed（8083）
├── momentliving-voucher-service/     # 券、秒杀、订单、支付、核销（8084）
├── momentliving-file-service/        # 阿里云 OSS 文件服务（8086）
├── momentliving-admin-service/       # 平台管理、审核和看板（8089）
├── momentliving-merchant-service/    # 商家登录、核销、工作台（8090）
├── momentliving-chat-service/        # WebSocket 单聊和群聊（8091）
├── momentliving-shop-service/        # 店铺缓存、Geo、ES、评价、收藏（8092）
├── momentliving-ai-service/          # Spring AI、RAG、Tools、SSE（8094）
├── momentliving-user-frontend/       # 用户端和商家端 H5（uni-app + Vue 3）
├── momentliving-admin-web/           # 管理后台（Vue 3 + Element Plus）
└── docs/                             # ES、SQL 等可提交的工程资料
```

## 核心业务链路

秒杀请求经过网关鉴权后，由 Redis Lua 原子完成库存判断、限购判断、预扣和预占记录写入；随后通过 RabbitMQ 异步落库（Publisher Confirm 同步确认，失败由定时对账任务查库重投），数据库使用条件更新兜底，消费失败由死信队列 Lua 原子幂等回补，未支付订单由延迟消息和定时扫描关闭。刻意不实现 Outbox/本地消息表，取舍见文档《14-高并发秒杀全流程-5》。

网关统一鉴权（三级白名单 + JWT + Redis 三级登录态回查），鉴权通过后以 HMAC 签名写身份头（X-User-Id 等），下游服务用 InternalRequestVerifier 验签后才信任身份，防止绕过网关伪造用户。网关 Redis 客户端当前为阻塞式 StringRedisTemplate（2026-11-15 为可读性从响应式改回，取舍记录在过滤器类注释与文档《06-网关》3.6）。

AI 服务使用 Spring AI 实现 RAG、工具调用、会话记忆、SSE 流式输出、内容生成和商家经营分析。AI 数据库初始化脚本位于 `momentliving-ai-service/src/main/resources/sql/ai-schema.sql`，安全配置模板位于 `momentliving-ai-service/src/main/resources/application-template.yml`。

## 运行环境

- JDK 17、Maven 3.8+、Node.js 18+
- Nacos 2.x、MySQL 8、Redis 6+、RabbitMQ 3.x
- Elasticsearch 7.17.18 + IK（店铺搜索可降级为 MySQL）
- PostgreSQL + pgvector（AI RAG 和会话持久化）
- 阿里云 OSS、DashScope 和 Embedding 模型 API

## 本地构建

```bash
# 后端
mvn clean test

# 用户端和商家端 H5
cd momentliving-user-frontend
npm ci
npm run build:h5

# 管理后台
cd momentliving-admin-web
npm ci
npm run build
```

建议按以下顺序启动：MySQL、Redis、RabbitMQ、Nacos、PostgreSQL/pgvector、Elasticsearch（可选），然后启动业务服务，最后启动 Gateway 和前端。

## 配置与安全

- 真实密码、API Key、JWT Secret、OSS Key、支付私钥不得提交 Git。
- 各服务优先通过环境变量或 Nacos 注入配置，仓库只提交脱敏模板（Nacos 配置模板见 `docs/nacos/*.yaml.example`，AI 服务模板见其 `application-template.yml`）。
- 服务启动时不会自动修改数据库；建表和升级脚本位于 `docs/sql/`，按 `docs/sql/README.md` 手工执行。
- Nacos 生产环境必须开启鉴权，8848、MySQL、Redis、RabbitMQ、PostgreSQL 等端口不得直接暴露公网。
- `application.yml` 即使加入 `.gitignore`，已经被 Git 跟踪的文件仍需主动移除敏感值并轮换旧密钥。

## 文档

完整学习文档位于本机目录 `D:\Typora\data\前后端项目\一刻生活`。第六部分包含限流设计、Nacos 配置审查、JMeter 标准化压测和 Docker 部署指南。

历史压测数字和含 Token 的压测数据已删除。新的性能结论必须按照标准化压测文档重新采集，并同时记录环境、脚本版本、业务成功率、P95/P99 和 Redis/MySQL 最终对账结果。
