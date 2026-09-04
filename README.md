# 一刻生活（momentliving）

本地生活服务平台：探店博客、店铺优惠券、限时秒杀、到店核销、即时聊天、商家入驻。
后端为 Spring Boot 3 + Spring Cloud Alibaba 微服务，前端包含 **用户端 H5、商家端 H5、管理端 Web** 三端。

## 目录结构

```
momentliving/
├── pom.xml                  # Maven 父工程（聚合全部后端模块）
├── momentliving-gateway/            # 网关：统一入口 + 路由 + JWT 鉴权（8080）
├── momentliving-common/             # 公共包：Result/JWT/异常/Redis 常量/UserHolder
├── momentliving-pojo/               # 实体 / DTO / VO
├── momentliving-api/                # Feign 客户端与透传拦截器
├── momentliving-user-service/       # 用户服务：登录/资料/足迹/积分（8081）
├── momentliving-shop-service/       # 店铺服务：缓存/Geo/ES 搜索/评价/收藏（8082）
├── momentliving-blog-service/       # 博客服务：笔记/点赞/收藏/关注/Feed 流（8083）
├── momentliving-voucher-service/    # 券服务：秒杀(Redis+Lua+MQ)/订单/支付/核销（8084）
├── momentliving-file-service/       # 文件服务：阿里云 OSS 统一上传（8086）
├── momentliving-admin-service/      # 管理端服务：入驻审核/店铺管理/看板（8089）
├── momentliving-merchant-service/   # 商家端服务：商家登录/核销/工作台（8090）
├── momentliving-chat-service/       # 聊天服务：WebSocket 单聊/群聊（8091）
├── momentliving-ai-service/         # AI 服务：智能问答(RAG)/商铺推荐/内容生成/商家分析（8093）
├── momentliving-user-frontend/      # 用户端 + 商家端 H5（uni-app + Vue3，一套工程两端页面）
├── momentliving-admin-web/          # 管理端 Web（Vue3 + Vite + Element Plus）
└── docs/                    # 设计文档、变更记录、SQL 迁移脚本、ES 部署脚本
```

## 三端前端

| 端 | 目录 | 技术栈 | Dev 端口 | 说明 |
|---|---|---|---|---|
| 用户端 H5 | `momentliving-user-frontend` | uni-app + Vue3 + Vite | 5173 | `npm run dev:h5`；`pages/user/**` |
| 商家端 H5 | `momentliving-user-frontend` | 同上（同一工程） | 5173 | `pages/merchant/**`，登录态与用户端隔离 |
| 管理端 Web | `momentliving-admin-web` | Vue3 + Vite + Element Plus | 5174 | `npm run dev` |

三端统一经 `/api` 代理到网关 8080，由网关按登录态（用户 / 管理员 / 商家三套 Redis key）鉴权并透传身份头。

## 后端服务与端口

| 服务 | 端口 | 职责要点 |
|---|---|---|
| gateway | 8080 | 路由、三模式 JWT 鉴权（用户/管理员/商家）、白名单 |
| user-service | 8081 | 验证码登录、资料、足迹、每日积分 |
| shop-service | 8082 | 店铺缓存（逻辑过期+互斥）、Geo 附近店、ES 搜索（降级 MySQL）、评价、店铺收藏 |
| blog-service | 8083 | 探店笔记、点赞（ZSet）、博客收藏、关注/共同关注、推模式 Feed |
| voucher-service | 8084 | 秒杀（Lua 限购 + Redisson + RabbitMQ 异步落库）、订单、支付宝支付、核销 |
| file-service | 8086 | OSS 图片上传/删除 |
| admin-service | 8089 | 入驻/开店审核（Seata 全局事务建店）、运营看板 |
| merchant-service | 8090 | 商家登录、扫码核销、工作台统计 |
| chat-service | 8091 | WebSocket 单聊/群聊、博客卡片分享 |
| ai-service | 8093 | Spring AI 接入（OpenAI 兼容协议）：RAG 知识库问答、SSE 流式对话、商铺推荐、博客/评价生成、商家经营分析 |

## 环境依赖

- JDK 17、Maven 3.8+、Node 18+
- Nacos（注册+配置，默认 `127.0.0.1:8848`）、MySQL、Redis、RabbitMQ
- Elasticsearch 7.17.18 + IK（可选，见 `docs/es/`；不可用时店铺搜索自动降级 MySQL like）
- 阿里云 OSS（图片上传，`file-service`）

## 快速启动

```bash
# 后端：IDEA 打开根目录（Maven 自动聚合 13 个模块），或
mvn clean package -DskipTests

# 用户端/商家端 H5
cd momentliving-user-frontend && npm i && npm run dev:h5

# 管理端 Web
cd momentliving-admin-web && npm i && npm run dev
```

## 配置说明

真实配置含密码，**不入库**（.gitignore 已忽略 `application.yml`）。
各服务提供 `application-template.yml` 模板，部署时复制为 `application.yml` 并填入本机 Nacos/MySQL/Redis/RabbitMQ/OSS 信息。

## 文档与脚本

- `docs/es/`：Elasticsearch + Kibana + IK 分词器一键部署脚本（docker-compose + setup.sh）
- `docs/nacos/momentliving-common.yaml.example`：Nacos 共享配置模板（含支付配置示例）
