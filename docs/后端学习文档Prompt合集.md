# momentliving 后端学习技术文档 —— Prompt 合集（v1.0）

> 本文件是「生成学习文档的 AI」的完整指令集。将任意一篇的 Prompt 单独取出，配合项目源码 `D:\claude-work\momentliving`，即可生成一篇完整、可面试用的技术文档。
>
> 生成方式：把「一、全局生成规范」+「三、逐篇 Prompt」中对应篇目一起发给执行 AI；重点篇（标注 ★）要求篇幅加倍。

---

## 一、全局生成规范（每篇必须遵守，优先级最高）

### 1. 读者画像（学习文档写给谁看）

- 目标读者：27 届 Java 后端实习生，具备 SSM、Spring Boot、MySQL、Redis、RabbitMQ 基础，正在准备校招 / 实习面试。
- 学习目的：① 快速上手 momentliving 项目后端代码；② 把项目中的高价值机制（缓存、秒杀、双 Token、分布式事务、Feed 流、RAG、WebSocket 等）转化为面试可讲的素材。
- 读者已了解基础概念（Redis 数据结构、MQ 基本用法、Spring 基础），**不需要从零科普**，但需要「原理讲透 + 项目落地 + 面试表达」三位一体。

### 2. 统一四段式结构（顺序不可变）

每篇文档固定四大部分：

| 段落 | 标题 | 内容要求 |
|---|---|---|
| 一 | **为什么选择（Why）** | 从业务痛点 / 技术选型角度解释「项目里为什么这么设计」，尽量引用源码注释中真实的选型动机（本项目源码注释里有大量「为什么」说明，应充分利用） |
| 二 | **原理（What）** | 讲透核心概念与机制原理，可用示意图 / mermaid 流程图 / 对比表格 |
| 三 | **在项目中的实现（How）** | 结合真实源码逐段拆解，引用真实类名、方法名、Redis key、表名、端口；贴关键代码片段并逐行解释 |
| 四 | **面试常问点（Interview）** | 具体问题清单 + 参考答案要点 + 追问链（详见规范第 4 条） |

> 每篇可在这四段之外增加「本章小结」与「相关源码路径」两个辅助小节（放在文末）。

### 3. 内容与事实要求

- 所有类名、方法名、常量、Redis key、端口、表结构、TTL 必须来自源码，**禁止编造**。
- 引用源码必须给出相对路径，如 `momentliving-shop-service/src/main/java/com/momentliving/service/impl/ShopServiceImpl.java`。
- 贴代码片段时保留核心逻辑，注释可精简，但解释必须逐段进行。
- 涉及数字（端口、TTL、分页大小、压测数据等）必须来自源码或 `docs/` 目录文档，不得臆造。
- 每篇文末附「相关源码路径」清单（对照「必读源码」小节，逐条列出真实路径）。

### 4. 面试常问点写作要求（重点）

- 以 `Q：……？` 形式列出**具体问题**，禁止「请简述 xxx」这类泛问。
- 每个问题给出「**参考答案要点**」：3~6 条，直接可背、可直接讲给面试官听。
- 对高价值问题附「**追问链**」：面试官可能顺着往下问什么，一并给出答案要点。
- 数量要求：重点篇（★）至少 8 个问题，普通篇至少 4 个问题；面试常问点篇幅可占全文 30%–40%。
- 每个回答必须结合本项目实现（句式示例：「本项目在 `xxx` 中通过 `xxx` 解决，具体是……」），禁止只讲通用理论。
- 面试问题应覆盖：原理类、方案对比类、场景设计类、深挖类（为什么 / 如果不这样会怎样）。

### 5. 篇幅与拆分规则

- 普通篇：正文 4000–7000 字；重点篇（★）：10000–15000 字。
- **宁可详细，不要压缩**。重点机制（秒杀、缓存三兄弟）必须展开到每一步、每个参数。
- **单份文档超过 7000 字必须拆分**：拆分为两份（若拆分后单份仍明显超过 7000 字，继续按同样规则再拆），拆分点取文档段落边界（优先在四段式的段与段之间），**尽量做到拆分后各份字数差别不大**（建议相差不超过 15%）。
- 拆分文件命名：`NN-标题-1.md`、`NN-标题-2.md`（仅两份时可用 `-上` / `-下`）；每个拆分文件开头注明「本文为《NN xxx》第 x 部分」，且每份结构完整、可独立阅读。
- 若总篇幅过大，也允许按篇目分批输出，但每篇内部必须完整。

### 6. 风格要求

- 中文写作，Markdown 格式；多用表格、mermaid 流程图、代码块。
- 小标题层级清晰（`###` / `####`），每部分开头一句话点明本段将讲什么。
- 语气面向「能讲给面试官听」的标准：概念讲透 + 项目落地 + 面试表达。
- **禁止提及「黑马点评」或其他外部项目做对比**，只讲本项目。

### 7. 禁则

- 不编造源码中不存在的方法 / 接口 / 表 / 配置项。
- 不输出真实配置中的密码、密钥、token（`application.yml` 含真实密码，涉及配置时只描述字段含义与作用，不贴真实值）。
- 不讨论与本项目无关的技术，不跑题。
- 每篇必须完整四段式，缺一段视为不合格。

---

## 二、篇目总览（19 篇 · 5 大部分）

| 编号 | 标题 | 部分 | 篇幅 | 重点 |
|---|---|---|---|---|
| 01 | 项目总览与微服务架构设计 | 一 | 普通 | |
| 02 | 本地环境搭建与快速启动 | 一 | 普通 | |
| 03 | 数据模型与代码分层规范 | 一 | 普通 | |
| 04 | 公共模块 common 详解 | 二 | 普通 | |
| 05 | Feign 服务间通信与 api 模块 | 二 | 普通 | |
| 06 | 网关 gateway 与三模式鉴权 | 二 | 重点 | ★ |
| 07 | 用户登录认证体系（双 Token） | 三 | 重点 | ★ |
| 08 | 用户服务业务功能（资料/签到/足迹） | 三 | 普通 | |
| 09 | 店铺缓存架构实战（缓存三兄弟） | 四 | 重点 | ★ |
| 10 | 缓存进阶：布隆过滤器与缓存一致性 | 四 | 重点 | ★ |
| 11 | 店铺服务：Geo 附近店铺与分类收藏 | 四 | 普通 | |
| 12 | 店铺搜索：Elasticsearch 集成与降级 | 四 | 普通 | |
| 13 | 博客服务：笔记/点赞/关注与 Feed 流 | 四 | 普通 | |
| 14 | 高并发秒杀全流程 | 四 | 重点 | ★ |
| 15 | 支付与核销 | 四 | 普通 | |
| 16 | 管理端与商家端服务（Seata 全局事务） | 四 | 普通 | |
| 17 | 文件服务 OSS 统一上传 | 五 | 普通 | |
| 18 | 聊天服务 WebSocket 即时通讯 | 五 | 普通 | |
| 19 | AI 服务 Spring AI 与 RAG | 五 | 普通 | |

> 注：篇幅上限与拆分规则见「全局生成规范」第 5 条——**单份超过 7000 字即拆分（优先两份均分）**；重点篇通常需要拆分。

---

## 三、逐篇 Prompt

---

### 01 项目总览与微服务架构设计

**定位**：整个文档体系的第一篇，建立全局认知地图。学完后应能对着白板讲出项目全貌。

**学习目标**：
- 一句话说清项目是做什么的；
- 画出 13 个模块的依赖关系与技术栈；
- 走通一条完整业务链路（登录 → 逛店 → 买券 → 秒杀 → 支付 → 核销 → 发笔记 → Feed → 聊天 → AI 问答）。

**必读资料**：
- `README.md`（目录结构、三端、端口、快速启动）
- `pom.xml`（父工程：模块清单、版本管理、Spring AI 里程碑仓库说明）
- 各服务 `src/main/resources/application.yml`（端口、服务名）

**大纲**：
1. **为什么选择**：单体项目在业务扩张后的痛点（模块耦合、独立扩容难、团队协作冲突）；为什么按业务域拆微服务；为什么选 Spring Cloud Alibaba 全家桶（Nacos / Gateway / Feign / Seata）而非 Spring Cloud Netflix。
2. **原理**：微服务核心组件职责（注册中心、配置中心、网关、服务间调用、分布式事务、限流熔断）；微服务拆分原则（按业务域 / 按团队 / 按伸缩性）。
3. **在项目中的实现**：
   - 13 个模块逐一说明职责与端口（gateway 8080 / user 8081 / shop 8092 / blog 8083 / voucher 8084 / file 8086 / admin 8089 / merchant 8090 / chat 8091 / ai 8093 + common / pojo / api 基础模块；**shop 实际端口以 application.yml 为准是 8092，README 中写的 8082 已过期**）；
   - 模块依赖关系图（common/pojo/api 被谁依赖）；
   - 技术栈版本矩阵（Spring Boot 3.2.5、Spring Cloud 2023.0.1、SCA 2023.0.1.0、MyBatis-Plus 3.5.5、Redisson 3.27.2、Spring AI 1.0.0-M6、JDK 17）；
   - 网关路由总表（哪个前缀路由到哪个服务）；
   - 三端前端（uni-app 用户端+商家端 / Vue3 管理端）与后端关系；
   - 一条完整业务链路走查：下单 → 支付 → 核销 → 发笔记的跨服务调用过程。
4. **面试常问点**（至少 4 个）：
   - Q：为什么用微服务？单体不行吗？
   - Q：服务拆分的原则是什么？你项目为什么这么拆？
   - Q：Nacos 在项目里承担什么角色？（注册 + 配置）
   - Q：一个请求从浏览器到后端经历了什么？（前端 → 网关 → 服务 → 中间件）
   - Q：你项目最大的技术亮点是什么？（引导式问题，回答应落到缓存 / 秒杀 / Seata）

**相关源码路径**：README.md、pom.xml、各模块 application.yml。

---

### 02 本地环境搭建与快速启动

**定位**：上手第一步。学完后应能独立把项目跑起来。

**学习目标**：
- 知道需要装哪些环境、每个中间件干什么；
- 理解 `application-template.yml → application.yml` 配置机制；
- 能启动后端与三端前端并验证。

**必读资料**：
- `README.md`「环境依赖 / 快速启动 / 配置说明」
- 各服务 `src/main/resources/application-template.yml`、`bootstrap.yml`
- `docs/nacos/momentliving-common.yaml.example`（Nacos 共享配置模板）
- `docs/es/docker-compose.yml`、`docs/es/setup.sh`（ES 一键部署）

**大纲**：
1. **为什么选择**：项目中间件多（Nacos/MySQL/Redis/RabbitMQ/ES/OSS），环境是上手第一道门槛；真实配置含密码，必须脱敏、不能入库。
2. **原理**：Maven 聚合工程；Spring Boot 配置加载优先级（本地 yml vs Nacos 配置中心）；`bootstrap.yml` 与 Nacos Config 的关系；模板配置机制（template 复制为正式文件）。
3. **在项目中的实现**：
   - 环境依赖清单与版本（JDK 17、Maven 3.8+、Node 18+、Nacos、MySQL 8、Redis、RabbitMQ、ES 7.17.18+IK 可选、阿里云 OSS）；
   - 后端启动步骤（IDEA 打开根目录 / `mvn clean package -DskipTests`）；
   - 配置模板机制：哪些服务有 template、复制成 `application.yml` 后要填什么；
   - Nacos 共享配置说明（`momentliving-common.yaml.example` 内容解读）；
   - ES 部署（docker-compose + setup.sh）；
   - 三端启动命令（uni-app `npm run dev:h5`、admin-web `npm run dev`）与验证方式；
   - 常见坑：Spring AI 里程碑版需要 `spring-milestones` 仓库（pom.xml 已有）；Lombok 与 JDK17；ES 不可用时店铺搜索自动降级。
4. **面试常问点**（至少 4 个）：
   - Q：配置中心（Nacos Config）解决了什么问题？配置热更新原理？
   - Q：项目怎么做到配置不泄露？（template 机制 + gitignore）
   - Q：Spring Boot 配置文件的加载优先级？
   - Q：如果 ES 挂了项目还能跑吗？（降级机制，提前剧透第 12 篇）

**相关源码路径**：README.md、各服务 application-template.yml / bootstrap.yml、docs/nacos/、docs/es/。

---

### 03 数据模型与代码分层规范

**定位**：建数据库表与代码结构的全局认知。学完后看到任意实体能说出它属于哪个业务域、被谁使用。

**学习目标**：
- 掌握 29 个实体对应的表结构（字段、主键策略、关键关联）；
- 理解 DTO / VO / 实体 的划分边界；
- 掌握统一返回 Result 与常量类设计。

**必读资料**：
- `momentliving-pojo/src/main/java/com/momentliving/entity/`（全部 29 个实体）
- `momentliving-pojo/src/main/java/com/momentliving/dto/`、`vo/`
- `momentliving-common/src/main/java/com/momentliving/result/Result.java`
- `momentliving-common/src/main/java/com/momentliving/constant/SystemConstants.java`、`RedisConstants.java`

**大纲**：
1. **为什么选择**：为什么实体不能直接返回前端（字段泄露、契约不稳定）；DTO 用于入参校验、VO 用于出参裁剪；为什么常量要集中管理。
2. **原理**：三层架构（controller/service/mapper）职责；实体 / DTO / VO 边界；MyBatis-Plus 注解式映射（`@TableName`/`@TableId`/`@TableField`）。
3. **在项目中的实现**：
   - 按业务域分组列出全部表：用户域（user、user_info）、店铺域（shop、shop_type、shop_favorite、review）、博客域（blog、blog_comments、blog_favorite、follow）、券域（voucher、seckill_voucher、voucher_order、voucher_shop、voucher_verify、payment）、商家域（merchant、merchant_apply、shop_apply）、管理域（admin）、聊天域（chat_session、chat_message、chat_group、chat_group_member）、AI 域（ai_conversation、ai_message、ai_knowledge_doc、ai_knowledge_chunk、ai_feedback）；
   - 每个实体：字段清单、主键策略（注意 VoucherOrder 用 `RedisIdWorker` 全局 ID 而非自增）、关键外键关联；
   - 典型 DTO/VO 解析（LoginFormDTO、ShopQueryDTO、BlogVO、LoginVO 等，讲清为什么需要它们）；
   - `Result<T>` 结构（code/message/data，code=1 成功）与使用约定；
   - 常量类设计（SystemConstants 分页大小、RedisConstants 全部 key 模式）。
4. **面试常问点**（至少 4 个）：
   - Q：VO / DTO / PO 的区别？为什么不能直接返回实体？
   - Q：统一返回 Result 有什么好处？
   - Q：为什么订单表主键不用数据库自增？（剧透 RedisIdWorker，见第 14 篇）
   - Q：MyBatis-Plus 的注解映射怎么工作？

**相关源码路径**：momentliving-pojo/src/main/java/com/momentliving/**、momentliving-common/.../result/Result.java、constant/*。**注意**：仓库无 SQL 建表脚本，表结构需从实体注解推导，文档中应明确标注「表结构为根据实体推导」。

---

### 04 公共模块 common 详解

**定位**：所有服务的地基。学完后能说清项目里「统一返回、异常、登录上下文、JWT、Redis key 规范」是怎么设计的。

**学习目标**：
- 掌握三套 ThreadLocal 身份上下文（用户/管理员/商家）的设计；
- 掌握 JWT 双 Token 工具与配置；
- 掌握 RedisConstants 全量 key 设计（含 TTL）。

**必读资料**：
- `momentliving-common/src/main/java/com/momentliving/`（全部类）
  - `result/Result.java`、`exception/*`（BaseException、BadRequestException、VerificationCodeException、PayException）
  - `context/UserHolder.java`、`AdminHolder.java`、`MerchantHolder.java`
  - `utils/JwtUtils.java`、`utils/RedisData.java`、`utils/RegexUtils.java`、`utils/RegexPatterns.java`
  - `constant/RedisConstants.java`、`constant/SystemConstants.java`、`constant/MessageConstant.java`、`constant/OrderStatus.java`
  - `properties/JwtProperties.java`、`config/MybatisPlusConfig.java`

**大纲**：
1. **为什么选择**：多服务复用（Result/异常/JWT/常量）；为什么身份用 ThreadLocal（请求线程内传递，避免方法传参污染）；为什么 Token 方案用 JWT + Redis 双校验。
2. **原理**：ThreadLocal 原理与内存泄漏（ThreadLocalMap、弱引用）；JWT 结构（Header/Payload/Signature）与 HMAC256；异常体系设计原则；MyBatis-Plus 分页插件原理。
3. **在项目中的实现**：
   - 三套 Holder 源码解析（为什么用 ThreadLocal、存什么、谁写入——拦截器写入）；
   - `JwtUtils` 源码：createAccessToken / createRefreshToken / getUserId 三方法解析；
   - `RedisConstants` 全量 key 表格：key 模式 | TTL | 用途（**以源码为准，包括但不限于**：LOGIN_USER_KEY（注意其值为 `login:refresh:`）、LOGIN_ADMIN_KEY、LOGIN_MERCHANT_KEY、LOGIN_CODE_KEY、LOGIN_CODE_INTERVAL_KEY、CACHE_SHOP_KEY、LOCK_SHOP_KEY、BLOG_LIKED_KEY、BLOG_MY_LIKED_KEY、FEED_KEY、SECKILL_STOCK_KEY、SECKILL_COUNT_KEY、USER_SIGN_KEY、LOCK_ORDER_KEY、CACHE_SHOP_TYPE_KEY、BLOOM_FILTER_SHOP_KEY_PREFIX/ACTIVE_KEY、FOLLOW_USER_KEY、VERIFY_CODE_KEY、SHOP_SCORE_KEY、FOOTPRINT_VISIBLE_KEY、FOOTPRINT_CLEARED_KEY、CREDITS_CLAIM_KEY 等）；
   - 异常继承链与全局异常处理（每个服务的 GlobalExceptionHandler 如何用）；
   - `RedisData<T>`（data + expireTime 逻辑过期封装）设计；
   - `MybatisPlusConfig` 分页插件配置。
4. **面试常问点**（至少 4 个）：
   - Q：ThreadLocal 原理？为什么会有内存泄漏？项目里怎么用？
   - Q：JWT 由哪几部分组成？能加密吗？
   - Q：项目里为什么 Redis key 要统一前缀管理？TTL 怎么定？
   - Q：自定义异常体系的好处？全局异常处理器怎么工作？

**相关源码路径**：momentliving-common/src/main/java/com/momentliving/**（完整目录）。

---

### 05 Feign 服务间通信与 api 模块

**定位**：微服务间协作的枢纽。学完后能画出「谁调谁、传什么、失败怎么办」的完整调用图。

**学习目标**：
- 掌握 6 个 Feign Client 的方法与用途；
- 掌握 Feign 身份头透传拦截器原理；
- 理解 Seata XID 如何跨服务传播（为第 16 篇铺垫）。

**必读资料**：
- `momentliving-api/src/main/java/com/momentliving/api/client/`（UserClient、ShopClient、BlogClient、VoucherClient、MerchantClient、FileClient）
- `momentliving-api/src/main/java/com/momentliving/interceptor/FeignIdentityInterceptor.java`
- `momentliving-api/src/main/java/com/momentliving/config/FeignConfig.java`、`FileClientConfig.java`
- 被调用方示例：`momentliving-voucher-service/.../controller/InternalVoucherController.java`、`momentliving-user-service/.../controller/UserController.java`

**大纲**：
1. **为什么选择**：微服务拆分后必然有跨服务调用（博客要校验购买记录 → 调 voucher；足迹可见性 → 调 user；审核建店 → 调 shop）；为什么统一封装 Feign 而非各写 RestTemplate。
2. **原理**：OpenFeign 动态代理原理；Feign 拦截器（RequestInterceptor）机制；负载均衡（Spring Cloud LoadBalancer）；Seata 的 XID 传播（通过 Feign 头传递 XID）。
3. **在项目中的实现**：
   - 6 个 Feign Client 逐一解析：接口方法、参数、返回类型、被哪个服务的哪个类调用；
   - `FeignIdentityInterceptor` 源码：如何把当前身份（X-User-Id / X-Admin-Id / X-Merchant-Id）透传给下游；
   - 跨服务调用全景图（表格 + 图）：blog→voucher/shop/user、voucher→user、admin→shop、chat→user 等；
   - 服务内部 Feign 接口的 Controller 实现（InternalVoucherController 等）——「内部接口」的命名与鉴权约定。
4. **面试常问点**（至少 4 个）：
   - Q：Feign 和 RestTemplate 有什么区别？为什么选 Feign？
   - Q：Feign 调用时身份信息怎么传递？（拦截器 + 头透传）
   - Q：Feign 超时、重试、降级怎么配置？
   - Q：Seata 的 XID 是怎么跨服务传播的？（回答后可引导到第 16 篇）

**相关源码路径**：momentliving-api/src/main/java/com/momentliving/**、各服务 controller 中的内部接口。

---

### 06 网关 gateway 与三模式鉴权 ★

**定位**：项目安全架构的核心，面试高频。学完后能完整复述 `AuthGlobalFilter` 的 8 步流程。

**学习目标**：
- 掌握网关路由配置（含 WS 路由）；
- 掌握三级白名单策略（精确 / 前缀 / 方法感知）；
- 掌握「用户 / 管理员 / 商家」三模式鉴权与身份头透传。

**必读资料**：
- `momentliving-gateway/src/main/java/com/momentliving/filter/AuthGlobalFilter.java`（**全文精读**）
- `momentliving-gateway/src/main/resources/application.yml`（路由表、JWT 配置）
- `momentliving-common/.../utils/JwtUtils.java`、`properties/JwtProperties.java`
- `momentliving-common/.../constant/RedisConstants.java`（三个 LOGIN_*_KEY）

**大纲**：
1. **为什么选择**：为什么鉴权放网关而不放各服务（统一收口、避免重复、安全一致）；为什么用户/管理员/商家要三套登录态隔离；为什么用 JWT（无状态、跨服务）但又要 Redis 校验（可控踢人）。
2. **原理**：Spring Cloud Gateway 路由与过滤器链；GlobalFilter 执行顺序（Ordered）；WebFlux 响应式编程与 Servlet 的差异（不能用 HttpServletRequest）；JWT + Redis 双校验模型。
3. **在项目中的实现**（AuthGlobalFilter 逐段拆解）：
   - ① 三级白名单：精确白名单（/pay/alipay/notify 等）→ 前缀白名单（/user/code、/shop/ 等）→ 方法感知白名单（/voucher/ 仅 GET）；**特别讲解 `/shop/favorite` 例外**（为什么在 /shop/ 前缀下仍强制登录）；
   - ② JWT 解析（getUserId）；
   - ③ Redis 登录态校验：用户态缺失 → 回查管理员态（ADMIN_MANAGE_PREFIX）→ 回查商家态（/merchant/、/ai/merchant/）；
   - ④ RefreshToken 续期 7 天（管理端不续期）；
   - ⑤ 身份头透传：X-User-Id / X-Admin-Id / X-Merchant-Id；
   - ⑥ WebFlux 下写 401 JSON 响应的方式；
   - 路由表解析：lb:// 与 lb:ws:// 的区别（chat WebSocket 路由）。
4. **面试常问点**（至少 8 个，含追问链）：
   - Q：网关在项目里做了哪些事？为什么选 Spring Cloud Gateway？
   - Q：JWT 是无状态的，怎么实现「踢人下线」？（答案：Redis 存登录态，删 key 即失效——本项目正是双校验）
   - Q：用户 / 管理员 / 商家三种登录态怎么隔离？
   - Q：白名单为什么分三种匹配方式？/voucher/ 为什么只放行 GET？（防止越权上架）
   - Q：WebFlux 和 Servlet 有什么区别？网关为什么用响应式？
   - Q：X-User-Id 由客户端传会被伪造吗？（网关写入覆盖）
   - Q：网关如何做限流 / 熔断？（可结合 Sentinel 扩展讨论）
   - Q：网关挂了怎么办？（高可用 / 降级思路）
   - 追问链：双 Token 在网关怎么校验 → 续期时机 → 与第 7 篇的刷新接口衔接。

**相关源码路径**：momentliving-gateway/**（filter/AuthGlobalFilter.java、resources/application.yml）。

---

### 07 用户登录认证体系（双 Token） ★

**定位**：认证体系核心，面试必考「双 Token」。学完后能完整讲出验证码登录 + 双 Token + 令牌轮换全流程。

**学习目标**：
- 掌握邮箱 / 手机双验证码通道与防刷；
- 掌握 AccessToken + RefreshToken 设计与刷新流程；
- 掌握三端登录态 Redis key 隔离。

**必读资料**：
- `momentliving-user-service/src/main/java/com/momentliving/service/impl/UserServiceImpl.java`（**全文精读**：sendCode、sendPhoneCode、loginByEmail、loginByPhone、buildLoginVO、refreshToken、logout）
- `momentliving-user-service/.../interceptor/LoginInterceptor.java`、`UserContextInterceptor.java`
- `momentliving-pojo/.../dto/LoginFormDTO.java`、`vo/LoginVO.java`、`vo/CaptchaVO.java`
- `momentliving-common/.../utils/JwtUtils.java`、`properties/JwtProperties.java`（access-token-ttl=30 分钟、refresh-token-ttl=7 天）

**大纲**：
1. **为什么选择**：单 Token 痛点（源码注释原文有「token 有效期短，安全，但用户半小时就要重新登录，体验差；Token 有效期长方便，但一旦泄露攻击者能用 7 天」）；验证码登录免密码、防撞库。
2. **原理**：双 Token 模型；令牌轮换（RefreshToken 用后即换）；验证码防刷（发送间隔 TTL + 验证码 TTL + 用后即删）。
3. **在项目中的实现**：
   - `sendCode`（邮箱 QQ SMTP 真实发送）与 `sendPhoneCode`（演示模式返回前端）双通道对比；
   - 60s 发送间隔防刷（`LOGIN_CODE_INTERVAL_KEY` + setIfAbsent）；
   - 登录流程逐行拆解：校验格式 → 校验验证码 → **用后即删** → 查用户 → 不存在则注册 → `buildLoginVO`；
   - `buildLoginVO`：签发 AccessToken(30min) + RefreshToken(7d)、RefreshToken 存 Redis（`LOGIN_USER_KEY:{id}`）、双 Token 返回；
   - `refreshToken`：解析 → 校验 Redis 一致性 → 签发新双 Token（令牌轮换）→ 覆盖旧 RefreshToken；
   - `logout` / 踢下线：删 Redis；
   - `LoginInterceptor` + `UserContextInterceptor` 在服务内部如何二次校验与填充 UserHolder；
   - 三端隔离：用户端 `login:refresh:{id}`（LOGIN_USER_KEY）/ `login:admin:{id}` / `login:merchant:{id}`。
4. **面试常问点**（至少 8 个，含追问链）：
   - Q：为什么需要双 Token？单 Token 痛点是什么？
   - Q：AccessToken 过期后前端怎么处理？（401 → 调 /user/refresh）
   - Q：RefreshToken 泄露了怎么办？（Redis 校验 + 令牌轮换 + 可删除踢下线）
   - Q：怎么实现单端登录 / 强制下线？（删 Redis key）
   - Q：验证码怎么防刷？发送间隔怎么控制？
   - Q：为什么验证码用完要立即删？
   - Q：手机验证码在项目里是演示模式，生产环境要注意什么？
   - Q：JWT 里能存用户信息吗？本项目存了什么？（只存 userId）
   - 追问链：双 Token → 网关怎么校验 → 续期 → 与服务内拦截器的关系。

**相关源码路径**：momentliving-user-service/**（UserServiceImpl.java、LoginInterceptor.java、UserContextInterceptor.java、MvcConfig.java）。

---

### 08 用户服务业务功能（资料/签到/足迹）

**定位**：用户域业务细节。重点讲位图签到（Redis 位图是加分项）。

**学习目标**：
- 掌握 Redis 位图签到与连续签到统计算法；
- 掌握资料更新防越权设计；
- 掌握足迹可见性 / 清空 / 他人查看校验。

**必读资料**：
- `momentliving-user-service/.../service/impl/UserServiceImpl.java`（sign、signCount、updateInfo、updateAvatar、足迹相关）
- `momentliving-user-service/.../service/impl/UserInfoServiceImpl.java`
- `momentliving-pojo/.../vo/CreditsVO.java`、`FootprintSettingVO.java`、`FootprintItemVO.java`
- `momentliving-common/.../constant/RedisConstants.java`（USER_SIGN_KEY）

**大纲**：
1. **为什么选择**：签到用位图（1 个用户 1 年只占约 46 字节）；足迹涉及隐私必须可控可见；为什么昵称/头像存 user 表而详细资料存 user_info 表。
2. **原理**：Redis 位图（setBit / bitField GET uN）；连续签到统计算法（十进制数与 1 相与 + 无符号右移）；为什么 `setBit` 返回旧值可用于判重。
3. **在项目中的实现**：
   - `sign` 源码：key 设计（`USER_SIGN_KEY:{userId}:yyyyMM`）、`setBit(key, dayOfMonth-1, true)`、已签到判重；
   - `signCount` 源码：`bitField GET uN` 取出本月位图 → 循环 `num & 1` + `num >>>= 1` 统计连续天数；
   - `updateInfo`：强制归属当前用户、昵称走 user 表（摘出透传字段避免未知列）、长度校验；
   - `updateAvatar`：只更新头像字段；
   - 足迹：写入来源（voucher 订单支付/核销）、可见性开关、清空足迹（记录 clearedTime 时间戳）、他人查看校验（隐藏/清空后的过滤，Feign 调 user 服务）。
4. **面试常问点**（至少 4 个）：
   - Q：签到用位图怎么做？key 怎么设计？
   - Q：连续签到天数怎么统计？（位运算过程要能讲清）
   - Q：位图比字符串省多少内存？（算一下：365 天 46 字节 vs 字符串）
   - Q：足迹功能怎么保护用户隐私？

**相关源码路径**：momentliving-user-service/**（UserServiceImpl.java、UserInfoServiceImpl.java）、momentliving-pojo/.../vo/*。

---

### 09 店铺缓存架构实战（缓存三兄弟） ★

**定位**：**全项目最核心的面试篇**。缓存穿透 / 击穿 / 雪崩三大问题在本项目有完整落地，必须写到极致。

**学习目标**：
- 理解缓存穿透 / 击穿 / 雪崩的成因与通用解法；
- 逐行吃透 `queryById` 的完整链路（布隆拦截 → 缓存判断 → 逻辑过期 → 互斥锁 → 双检 → 异步重建）；
- 能讲清「互斥锁 vs 逻辑过期」的选型理由。

**必读资料**：
- `momentliving-shop-service/src/main/java/com/momentliving/service/impl/ShopServiceImpl.java`（**全文精读**：queryById、buildCacheAndReturn、saveShop2Redis、update、add、delete）
- `momentliving-shop-service/.../service/ShopBloomFilterService.java`
- `momentliving-common/.../utils/RedisData.java`（逻辑过期封装）
- `momentliving-common/.../constant/RedisConstants.java`（CACHE_SHOP_KEY、LOCK_SHOP_KEY、CACHE_SHOP_TTL、LOCK_SHOP_TTL）
- `momentliving-shop-service/.../config/RedissonConfig.java`

**大纲**：
1. **为什么选择**：店铺是最高频读取数据（首页 / 详情 / 列表），DB 扛不住 QPS；三个经典问题的成因（查不存在数据 / 热点 key 过期 / 大面积同时过期）。
2. **原理**：
   - 缓存穿透：成因、通用解法（空值缓存、布隆过滤器、参数校验）；
   - 缓存击穿：成因（热点 key 过期瞬间大量请求打 DB）、通用解法（互斥锁、逻辑过期）；
   - 缓存雪崩：成因（大量 key 同时过期 / Redis 宕机）、通用解法（随机 TTL、多级缓存、集群）；
   - 互斥锁 vs 逻辑过期优缺点对比表（一致性 / 可用性 / 复杂度 / 适用场景）。
3. **在项目中的实现**（queryById 逐段拆解）：
   - ① 布隆过滤器拦截（一定不存在的 id 直接抛异常）；
   - ② 缓存未命中 → `buildCacheAndReturn` 同步重建（互斥锁 + 空值缓存 5 分钟防穿透 + 双检）；
   - ③ 缓存命中 → 反序列化 `RedisData<Shop>` → 判断逻辑过期；
   - ④ 未过期直接返回；过期 → 抢互斥锁（`setIfAbsent` + 锁 TTL）→ **双检**（为什么双检：锁释放瞬间其他线程抢到锁后避免重复查库）→ 提交异步线程池重建（逻辑过期 30min + 随机 0-5min）→ 释放锁；
   - ⑤ 无论是否拿到锁都返回数据（拿到锁返回新数据 / 没拿到返回旧数据——**保证可用性**）；
   - `saveShop2Redis`：逻辑过期 + 物理 TTL（逻辑过期 + 10~30 分钟随机）设计意图；
   - 写路径：update 先改库再删缓存再同步 ES；add/delete 同步缓存与 ES。
4. **面试常问点**（至少 8 个，含追问链）：
   - Q：缓存穿透 / 击穿 / 雪崩分别是什么？有什么区别？
   - Q：本项目分别怎么解决？（穿透→布隆+空值、击穿→逻辑过期+互斥锁、雪崩→随机 TTL+物理 TTL）
   - Q：互斥锁和逻辑过期怎么选？各自优缺点？
   - Q：为什么拿到锁后还要双检一次？
   - Q：逻辑过期期间返回旧数据，数据不一致怎么解释？（最终一致 + 异步重建）
   - Q：为什么物理 TTL 要大于逻辑过期时间？
   - Q：先更新数据库还是先删缓存？本项目怎么做的？
   - Q：缓存雪崩除了随机 TTL 还有什么兜底？（Redis 集群、多级缓存、限流降级）
   - Q：热点 key 怎么发现和处理？
   - 追问链：击穿 → 逻辑过期实现 → 为什么要封装 RedisData → 线程池重建 → 锁的粒度。

**相关源码路径**：momentliving-shop-service/**（ShopServiceImpl.java、ShopBloomFilterService.java、config/RedissonConfig.java、task/*）。

---

### 10 缓存进阶：布隆过滤器与缓存一致性 ★

**定位**：第 9 篇的延伸，面试深挖向。重点讲布隆过滤器原理与双桶重建、缓存一致性方案。

**学习目标**：
- 掌握布隆过滤器数学原理（k 个哈希、误判率、不可删除）；
- 掌握双布隆桶重建机制；
- 掌握缓存与 DB 一致性方案对比与项目实践。

**必读资料**：
- `momentliving-shop-service/.../service/ShopBloomFilterService.java`
- `momentliving-shop-service/.../task/BloomFilterInitTask.java`、`BloomRebuildTask.java`、`ShopCachePreLoadTask.java`
- `momentliving-shop-service/.../service/impl/ShopServiceImpl.java`（add/update/delete 的缓存与 ES 同步）
- `momentliving-shop-service/.../config/ElasticsearchConfig.java`

**大纲**：
1. **为什么选择**：空值缓存治标不治本（恶意 key 会打爆 Redis 内存）；缓存与 DB 一致性是读多写少系统的核心工程难点；ES 是第三个存储副本，一致性更难。
2. **原理**：
   - 布隆过滤器：m 位数组 + k 个 hash 函数、判断「一定不存在」与「可能存在」、误判率公式、**为什么不支持删除**；
   - 双布隆桶（active/standby 交替）解决「重建期间新增数据丢失」与「不可删除」问题；
   - 缓存一致性方案对比：Cache Aside（旁路）、Read/Write Through、异步删除、延迟双删、binlog 订阅（Canal）；
   - 为什么「先更新 DB 再删缓存」比「先删缓存再更新 DB」更优。
3. **在项目中的实现**：
   - `ShopBloomFilterService`：contains / add 实现；
   - `BloomFilterInitTask`：启动时预热全量店铺 id；
   - `BloomRebuildTask`：定期重建双布隆桶（交替切换）的完整流程；
   - `ShopCachePreLoadTask`：缓存预热任务；
   - 写路径的一致性处理：update（改库 → 删缓存 → 同步 ES best-effort）、add（插库 → 布隆 add → 同步 ES）、delete（删库 → 删缓存 → 删 ES 文档）；
   - 为什么 ES 同步是 best-effort（ES 非 XA 资源，失败靠 reindex 兜底）——衔接第 12 篇。
4. **面试常问点**（至少 8 个，含追问链）：
   - Q：布隆过滤器原理？为什么说它「一定不存在」才是确定的？
   - Q：误判率怎么算？怎么降低？（调 m/k）
   - Q：布隆过滤器不能删除，项目怎么解决？（双桶重建 / 计数布隆）
   - Q：双布隆桶重建的具体流程？
   - Q：缓存与数据库一致性有哪些方案？各有什么问题？
   - Q：为什么先更新 DB 再删缓存？
   - Q：删缓存失败怎么办？（重试、延迟双删、binlog 订阅）
   - Q：本项目的缓存、DB、ES 三份数据一致性怎么保证？
   - 追问链：布隆 → 误判 → 双桶 → 重建时机 → 与缓存预热的关系。

**相关源码路径**：momentliving-shop-service/**（ShopBloomFilterService.java、task/BloomFilterInitTask.java、task/BloomRebuildTask.java、task/ShopCachePreLoadTask.java）。

---

### 11 店铺服务：Geo 附近店铺与分类收藏

**定位**：LBS 功能 + 轻量业务。重点讲 Redis GEO。

**学习目标**：
- 掌握 Redis GEO 原理（ZSet + geohash）与附近店铺实现；
- 掌握店铺收藏表设计。

**必读资料**：
- `momentliving-shop-service/.../service/impl/ShopServiceImpl.java`（queryShopByType 的 GEO 分支、favoriteShop、isFavoriteShop、myFavoriteShops）
- `momentliving-shop-service/.../controller/ShopTypeController.java`、`ShopTypeServiceImpl.java`
- `momentliving-pojo/.../entity/ShopType.java`、`ShopFavorite.java`、`dto/ShopQueryDTO.java`

**大纲**：
1. **为什么选择**：「附近店铺」是本地生活服务的核心 LBS 功能；为什么用 Redis GEO 而不是 MySQL 计算距离。
2. **原理**：GEO 底层是 ZSet（member=店铺 id，score=geohash 编码）、GEOSEARCH 距离计算与排序；滚动分页 vs 页码分页。
3. **在项目中的实现**：
   - `queryShopByType`：无坐标 → MySQL 分页；有坐标 + 类型 → GEO 查询（from/end 分页、距离回填）；
   - 首页「附近店铺」（typeId 为空查全部）逻辑；
   - 店铺类型列表（ShopType）；
   - 店铺收藏 toggle（shop_favorite 表存在即收藏，删行/插行）；
   - 「我收藏的店铺」：收藏表倒序 → 批量回填详情。
4. **面试常问点**（至少 4 个）：
   - Q：Redis GEO 原理？附近的人怎么实现？
   - Q：GEO 分页怎么做？为什么不用页码分页？
   - Q：店铺收藏为什么用表而不是 Redis Set？
   - Q：距离是怎么算的？（geohash 近似 vs 球面距离）

**相关源码路径**：momentliving-shop-service/**（ShopServiceImpl.java、ShopTypeServiceImpl.java、controller/*）。

---

### 12 店铺搜索：Elasticsearch 集成与降级

**定位**：搜索能力 + 降级设计。重点讲倒排索引、IK 分词、数据同步与降级。

**学习目标**：
- 掌握 ES 索引 mapping 设计与 IK 分词策略；
- 掌握索引初始化 / 全量重建 / 脏文档清理；
- 掌握 ES 不可用时的 MySQL like 降级。

**必读资料**：
- `momentliving-shop-service/.../service/impl/ShopSearchServiceImpl.java`（**全文精读**：INDEX_MAPPING、ensureIndex、importAll、reindex、purgeStale、bulkUpsert、search）
- `momentliving-shop-service/.../config/ElasticsearchConfig.java`、`EsProperties.java`
- `momentliving-shop-service/.../task/ShopEsInitTask.java`
- `momentliving-shop-service/.../mapper/ShopMapper.xml`（like 降级查询）
- `docs/es/docker-compose.yml`、`docs/es/setup.sh`

**大纲**：
1. **为什么选择**：MySQL LIKE 慢、不支持分词、无法按相关性排序；ES 倒排索引天然适合全文检索；但引入 ES 带来同步与降级两个新问题。
2. **原理**：倒排索引（文档 → 词项 → 文档列表）；IK 分词器（ik_max_word 最细切分 vs ik_smart 粗切分，为什么索引/搜索用不同 analyzer）；mapping 字段类型（keyword vs text）。
3. **在项目中的实现**：
   - `INDEX_MAPPING` 逐字段解读（name 用 ik_max_word 索引 + ik_smart 搜索 + keyword 子字段）；
   - `ShopEsInitTask`：启动初始化；
   - `importAll`（分页全量导入 500 条/批）→ `reindex`（导入 + `purgeStale` 清理脏文档）→ 脏文档判定逻辑（ES 与 DB id 差集）；
   - 写路径同步：shop 增删改时 `syncUpsert`/`syncDelete`（best-effort）；
   - 查询：`SearchRequest` 组装（match 查询 name/address、按 typeId 过滤、高亮等）；
   - **降级**：ES 不可用时回退 MySQL like 的触发条件与代码路径；
   - ES 部署（docker-compose + IK 插件）。
4. **面试常问点**（至少 4 个）：
   - Q：倒排索引原理？为什么比 like 快？
   - Q：为什么索引用 ik_max_word、搜索用 ik_smart？
   - Q：ES 和数据库数据一致性怎么保证？（本项目 best-effort + reindex 兜底）
   - Q：ES 挂了怎么办？（降级 MySQL like，讲清触发条件）
   - Q：reindex 怎么做？脏文档怎么清理？

**相关源码路径**：momentliving-shop-service/**（ShopSearchServiceImpl.java、ShopEsInitTask.java、config/ElasticsearchConfig.java、mapper/ShopMapper.xml）、docs/es/。

---

### 13 博客服务：笔记/点赞/关注与 Feed 流

**定位**：社区功能核心。重点讲 ZSet 点赞排行榜、推模式 Feed 流、共同关注。

**学习目标**：
- 掌握发笔记三重校验（「买过才可发」）；
- 掌握 ZSet 点赞与「我的喜欢」反向索引；
- 掌握推模式 Feed 流与滚动分页。

**必读资料**：
- `momentliving-blog-service/.../service/impl/BlogServiceImpl.java`（**全文精读**：saveBlog、likeBlog、favoriteBlog、myLikedBlogs、myFavoriteBlogs、queryHotBlog、queryBlogOfFollow 等）
- `momentliving-blog-service/.../service/impl/FollowServiceImpl.java`（关注/取关/共同关注）
- `momentliving-blog-service/.../service/impl/BlogCommentsServiceImpl.java`
- `momentliving-pojo/.../dto/ScrollResult.java`、`entity/Follow.java`

**大纲**：
1. **为什么选择**：发笔记必须「购买过该店铺的券」防云探店/假笔记；点赞要排行（时间顺序）所以用 ZSet 而非 Set；Feed 流「关注的人动态」用推模式解决实时性。
2. **原理**：ZSet 有序集合（score=时间戳）；Feed 流三种模式（推 / 拉 / 推拉结合）对比表（粉丝量、关注量、写放大、读放大）；共同关注 = 集合交集。
3. **在项目中的实现**：
   - `saveBlog` 三重校验：shopId 必填 → Feign 调 voucher 查「已购店铺 id 集合」→ Feign 调 shop 校验店铺存在 → 入库 → **推 Feed**（给所有粉丝的 ZSet 加当前时间戳）；
   - `likeBlog`：ZSet score 判重 → 原子增减 liked 字段 → 点赞 ZSet（BLOG_LIKED_KEY）→ 反向索引（BLOG_MY_LIKED_KEY：「我的喜欢」列表）；
   - 博客收藏（表驱动 toggle）、我的收藏（批量回填详情）；
   - 关注 / 取关 / 共同关注（sinter）；
   - 推模式 Feed 流查询：`queryBlogOfFollow`（ZSet 倒序 + ScrollResult 滚动分页：lastId + offset 防止分页重复/跳页）；
   - 热门博客（ZSet 按点赞排序）。
4. **面试常问点**（至少 4 个）：
   - Q：点赞排行榜为什么用 ZSet？怎么取 TopN？
   - Q：Feed 流推 / 拉模式怎么选？项目为什么用推？（结合粉丝量级讲）
   - Q：滚动分页和页码分页区别？为什么 Feed 流用滚动分页（offset + lastId 防重复）？
   - Q：共同关注怎么实现？
   - Q：怎么防止用户发假探店笔记？（买过才可发，跨服务校验）

**相关源码路径**：momentliving-blog-service/**（BlogServiceImpl.java、FollowServiceImpl.java、BlogCommentsServiceImpl.java、controller/*）。

---

### 14 高并发秒杀全流程 ★

**定位**：**全项目第二个核心面试篇**，分布式高并发集大成者。必须展开到每一步、每个参数。

**学习目标**：
- 掌握 RedisIdWorker 全局唯一 ID 生成；
- 逐行吃透 `seckill.lua`（库存校验 + 限购校验 + 扣库存 + 记用户；**限购规则为一人最多 3 单，`RedisConstants.SECKILL_LIMIT = 3`**）；
- 掌握 MQ 异步落库 + 幂等 + CAS 防超卖 + 死信补偿 + 订单超时关闭全链路。

**必读资料**：
- `momentliving-voucher-service/src/main/resources/lua/seckill.lua`（**逐行精读**）
- `momentliving-voucher-service/.../service/impl/VoucherOrderServiceImpl.java`（**全文精读**：seckillVoucher、createVoucherOrder、createBuyOrder、queryMyOrders、queryUserFootprint）
- `momentliving-voucher-service/.../config/RedisIdWorker.java`
- `momentliving-voucher-service/.../consumer/SeckillOrderConsumer.java`、`SeckillDlxConsumer.java`、`OrderCloseConsumer.java`、`OrderCloseHandler.java`
- `momentliving-voucher-service/.../config/RabbitMQConfig.java`、`RabbitConfirmConfig.java`
- `momentliving-voucher-service/.../task/OrderTimeoutScanTask.java`
- `momentliving-voucher-service/.../service/impl/SeckillVoucherServiceImpl.java`（deductStock CAS）
- `docs/秒杀接口压测报告.md`、`docs/perf/loadtest.py`

**大纲**：
1. **为什么选择**：秒杀是超高并发写场景（瞬时流量、库存扣减、**一人限购 3 单**、防超卖）；为什么需要全局唯一 ID（订单主键不能自增）；为什么用 Lua（原子性）、为什么用 MQ（削峰）。
2. **原理**：
   - 全局 ID：Redis INCR 自增 + 时间戳拼接（RedisIdWorker：符号位 + 时间戳 + 序列号）与雪花算法对比；
   - Lua 脚本：Redis 单线程执行保证原子性；
   - MQ 异步削峰：同步下单（Redis 层）→ 异步落库（MQ 消费）；
   - 消息可靠性：publisher-confirm、消费者手动 ACK、死信队列（DLX）；
   - 幂等：DB 唯一约束 / 状态判断；
   - 订单超时：延迟队列 vs 定时扫描。
3. **在项目中的实现**：
   - `RedisIdWorker.nextId` 源码解析；
   - `seckill.lua` 逐行拆解：ARGV（voucherId/userId/limit，**limit 由 `SECKILL_LIMIT=3` 传入**）→ stockKey/countKey → 库存不足返回 1 → 超过限购（每人 3 单）返回 2 → 扣库存 → 记用户 → 返回 0；
   - `seckillVoucher`：校验秒杀时间 → 执行 Lua → 成功则生成订单号 → 发 MQ（携带 CorrelationData 关联业务单号）→ **立即返回订单 id**（用户体验）；
   - `SeckillOrderConsumer` 异步落库：**DB 幂等兜底**（每人限购 3 单：`selectCount ≥ SECKILL_LIMIT(3)` 直接返回）→ **CAS 扣库存**（deductStock：`UPDATE seckill_voucher SET stock = stock - 1 WHERE voucher_id = ? AND stock > 0`）→ 插入订单；
   - 库存不足抛异常 → NACK → 死信队列 → `SeckillDlxConsumer` 补偿（Redis 回补库存）；
   - 订单超时：`OrderCloseConsumer`（延迟队列）与 `OrderTimeoutScanTask`（定时扫描兜底）双保险；
   - RabbitMQ 配置：交换机 / 队列 / routing key / 死信参数 / confirm 模式；
   - `docs/秒杀接口压测报告.md` 数据解读（QPS、成功率、优化前后对比）与 `docs/perf/loadtest.py` 压测脚本说明。
4. **面试常问点**（至少 10 个，含追问链）：
   - Q：秒杀系统的难点有哪些？（高并发读、库存扣减、**一人限购 3 单**、订单超时、消息可靠）
   - Q：为什么用 Lua 脚本？不用 Lua 会怎样？（超卖 / 超限）
   - Q：怎么防超卖？（Lua 扣 Redis 库存 + DB CAS 双重）
   - Q：怎么保证一人限购 3 单？（Redis Lua 限购 countKey 计数 + DB 幂等校验双重）
   - Q：为什么下单后要发 MQ 而不是直接落库？（削峰填谷、快速响应）
   - Q：MQ 消息丢失怎么处理？（confirm + 手动 ACK）
   - Q：MQ 消息重复消费怎么办？（幂等：限购校验 + 唯一索引）
   - Q：订单超时未支付怎么处理？（延迟队列 + 定时扫描，讲清两者关系）
   - Q：Redis 库存和 DB 库存不一致怎么办？（死信补偿 / 对账）
   - Q：RedisIdWorker 和雪花算法有什么区别？为什么不用数据库自增？
   - Q：压测报告里优化后 QPS 多少？（引用 docs 真实数据）
   - 追问链：Lua → 原子性 → 如果 Redis 挂了 → 秒杀接口降级方案。

**相关源码路径**：momentliving-voucher-service/**（resources/lua/seckill.lua、service/impl/*、consumer/*、config/RabbitMQConfig.java、config/RedisIdWorker.java、task/OrderTimeoutScanTask.java）、docs/秒杀接口压测报告.md、docs/perf/。

---

### 15 支付与核销

**定位**：交易闭环。重点讲策略模式、支付宝回调验签、16 位核销码与扫码核销。

**学习目标**：
- 掌握支付渠道策略模式设计；
- 掌握支付宝异步回调的验签与幂等处理；
- 掌握核销码生成与扫码核销流程。

**必读资料**：
- `momentliving-voucher-service/.../pay/PayProvider.java`、`AliPayProvider.java`、`MockPayProvider.java`、`WechatPayProvider.java`
- `momentliving-voucher-service/.../config/PayProperties.java`
- `momentliving-voucher-service/.../controller/PayController.java`、`PayNotifyController.java`
- `momentliving-voucher-service/.../service/impl/PaymentServiceImpl.java`、`VerifyServiceImpl.java`
- `momentliving-pojo/.../entity/Payment.java`、`VoucherVerify.java`、`vo/PayOrderVO.java`、`VerifyOrderPreviewVO.java`、`VerifyRecordsVO.java`

**大纲**：
1. **为什么选择**：多渠道支付（支付宝 / 微信 / 模拟）→ 策略模式解耦；回调安全靠 RSA2 验签；核销码 16 位随机防伪。
2. **原理**：策略模式（接口 + 多实现 + 运行时选择）；支付宝异步通知机制（notify_url、验签、幂等、金额校验）；订单状态机（待支付 → 已支付 → 已核销 / 已关闭）。
3. **在项目中的实现**：
   - `PayProvider` 接口与三个实现：AliPay（沙箱 trade.page.pay）、Mock（本地模拟）、Wechat（预留商户号，未配置不启用）；
   - `PayNotifyController` 回调处理流程：验签（RSA2）→ 幂等校验 → 金额一致性校验 → 更新订单状态 → 生成 16 位核销码（voucher_verify）；
   - `PaymentServiceImpl`：支付单创建与状态更新；
   - `VerifyServiceImpl`：商家扫码核销（校验核销码 → 校验归属 → 更新状态）；
   - 订单状态流转图（mermaid）；
   - 网关对 `/pay/alipay/notify` 的精确白名单放行（为什么这个端点不需要登录——渠道验签保护）。
4. **面试常问点**（至少 4 个）：
   - Q：支付回调为什么必须验签？验签流程？
   - Q：回调重复通知 / 丢失怎么办？（幂等 + 主动查单）
   - Q：订单状态机怎么设计？状态流转图？
   - Q：策略模式在支付里的应用？新增一个支付渠道要改哪些代码？
   - Q：核销码怎么防伪？（随机生成 + 数据库校验 + 一次性使用）

**相关源码路径**：momentliving-voucher-service/**（pay/*、controller/PayController.java、PayNotifyController.java、service/impl/PaymentServiceImpl.java、VerifyServiceImpl.java）。

---

### 16 管理端与商家端服务（Seata 全局事务）

**定位**：管理后台 + 商家端 + 分布式事务。重点讲 Seata AT 模式跨服务建店。

**学习目标**：
- 掌握 Seata AT 模式原理（TC/TM/RM、undo_log）；
- 掌握「审核通过 → 跨服务建店」的全局事务实现；
- 掌握商家账号体系与用户体系隔离。

**必读资料**：
- `momentliving-admin-service/.../service/impl/ShopApplyAuditServiceImpl.java`（**全文精读**：@GlobalTransactional audit）
- `momentliving-admin-service/.../interceptor/AdminAuthInterceptor.java`、`controller/AdminController.java`、`AdminDashboardController.java`、`controller/MerchantApplyController.java`、`controller/ShopApplyController.java`
- `momentliving-admin-service/.../service/impl/AdminServiceImpl.java`、`MerchantApplyServiceImpl.java`
- `momentliving-admin-service/.../mapper/DashboardMapper.java`
- `momentliving-merchant-service/.../service/impl/MerchantServiceImpl.java`（BCrypt 登录）
- `momentliving-merchant-service/.../controller/MerchantVerifyController.java`、`MerchantShopApplyController.java`
- `momentliving-merchant-service/.../interceptor/MerchantAuthInterceptor.java`

**大纲**：
1. **为什么选择**：入驻审核跨两个服务（admin 审核 → shop 建店），本地事务不够用，需要分布式事务；商家账号体系独立于用户体系（merchant 表 + login:merchant: key）；为什么管理端也要独立 JWT。
2. **原理**：
   - Seata AT 模式：TC（事务协调器）/ TM（事务管理器）/ RM（资源管理器）、全局事务与分支事务、undo_log 回滚原理（前置镜像 / 后置镜像）；
   - 分布式事务方案对比：2PC / TCC / MQ 最终一致 / Seata AT——各适用场景；
   - 什么场景用全局事务（强一致）、什么场景用最终一致（高并发）。
3. **在项目中的实现**：
   - `audit` 方法逐行拆解：校验管理员 → 校验申请状态 → 通过则拼 ShopDTO → Feign 调 shop-service 建店（XID 透传，下游注册分支事务）→ 回填申请状态 → 拒绝则记原因；
   - **回滚演练开关**（drillFail 抛异常验证 Seata 把店铺与申请状态一起回滚）——讲清 undo_log 如何逆补偿；
   - admin-service 独立鉴权（AdminAuthInterceptor + X-Admin-Id）；
   - 运营看板（DashboardMapper 今日统计：订单/用户/销售额等）；
   - 商家服务：BCrypt 密码校验、登录态 `login:merchant:{id}`、工作台统计、扫码核销入口、入驻申请状态机（待审核 → 通过/拒绝）。
4. **面试常问点**（至少 4 个）：
   - Q：Seata AT 模式原理？TC/TM/RM 各自职责？
   - Q：undo_log 怎么实现回滚？（前后镜像 + 逆 SQL）
   - Q：AT 模式和 TCC 有什么区别？
   - Q：什么场景必须用分布式事务？为什么秒杀订单不用 Seata 而用最终一致？（衔接第 14 篇）
   - Q：商家登录态和用户登录态怎么隔离？

**相关源码路径**：momentliving-admin-service/**、momentliving-merchant-service/**。

---

### 17 文件服务 OSS 统一上传

**定位**：基础设施服务。较短篇，但接口抽象值得学。

**学习目标**：
- 掌握 ImageStorage 接口抽象；
- 掌握 OSS 对象键生成与上传删除。

**必读资料**：
- `momentliving-file-service/.../controller/FileController.java`
- `momentliving-file-service/.../service/ImageStorage.java`、`impl/OssImageStorage.java`
- `momentliving-file-service/.../utils/OssUtil.java`

**大纲**：
1. **为什么选择**：图片分散在各业务上传难管理；统一文件服务 + 抽象接口（可替换存储实现）。
2. **原理**：OSS 对象存储模型（Bucket / ObjectKey / URL）；面向接口编程。
3. **在项目中的实现**：
   - `ImageStorage` 接口与 `OssImageStorage` 实现（upload/delete）；
   - `OssUtil`：对象键生成（按目录：博客图/店铺图/头像/券图）、上传、删除、URL 与 ObjectKey 互转；
   - FileController 的上传/删除接口与网关 `/file/**` 路由。
4. **面试常问点**（至少 4 个）：
   - Q：前端直传 OSS 还是后端转发？有什么区别？
   - Q：文件类型 / 大小怎么校验？
   - Q：为什么用接口抽象存储层？
   - Q：OSS URL 会过期吗？（私有 Bucket 签名 URL）

**相关源码路径**：momentliving-file-service/**。

---

### 18 聊天服务 WebSocket 即时通讯

**定位**：实时通讯能力。重点讲握手鉴权、首条限制状态机、幂等。

**学习目标**：
- 掌握 WebSocket 握手鉴权（token 在 query）；
- 掌握帧协议与消息分发；
- 掌握单聊首条限制三态状态机（防骚扰）。

**必读资料**：
- `momentliving-chat-service/.../ws/ChatSocketHandler.java`、`ChatHandshakeInterceptor.java`、`ChatRejectException.java`、`WsSessionRegistry.java`
- `momentliving-chat-service/.../service/impl/ChatServiceImpl.java`（**全文精读**：send、markRead、canSend 等）
- `momentliving-chat-service/.../service/MessagePushService.java`、`config/WebSocketConfig.java`
- `momentliving-gateway/.../application.yml`（lb:ws://chat-service 路由）

**大纲**：
1. **为什么选择**：HTTP 轮询低效（频繁建连、延迟高），WebSocket 全双工实时推送；单聊要做防骚扰（首条限制）；消息要有幂等（断线重发）。
2. **原理**：WebSocket 握手（HTTP Upgrade）与帧协议；为什么握手不能带 Authorization 头（token 走 query）；幂等设计（clientMsgId 唯一索引）。
3. **在项目中的实现**：
   - `ChatHandshakeInterceptor`：从 query 取 token 校验 JWT + Redis 登录态（与网关校验互补）；
   - `ChatSocketHandler`：onOpen 注册 / onMessage 分发（op: send / read / ping）/ onClose 注销；
   - **首条限制三态状态机**（INIT → WAIT_REPLY → FREE）：谁发首条谁是发起方（以真实首条消息发送者为准，不回信表里猜的 initiator_id）、CAS 推进状态、发起方在对方回复前再发被拒绝、接收方回复解除限制；
   - `clientMsgId` 幂等：断线重发撞唯一索引 → 查回原消息按成功 ack（静默去重）；
   - 消息推送：在线推送（pushToTargets）、离线落库补拉；
   - 单聊 / 群聊（chat_group、chat_group_member）、博客卡片分享；
   - 网关 `lb:ws://chat-service` 路由与 REST 路由（/chat/**）分离。
4. **面试常问点**（至少 4 个）：
   - Q：WebSocket 和 HTTP 区别？为什么聊天不用轮询？
   - Q：握手鉴权怎么做？（token 在 query + 拦截器）
   - Q：消息幂等怎么实现？（clientMsgId + 唯一索引）
   - Q：分布式多实例下 WebSocket 怎么扩展？（session 共享 / Redis 广播）
   - Q：首条限制状态机解决什么问题？为什么不信表里的 initiator_id？
   - Q：心跳机制（ping/pong）？

**相关源码路径**：momentliving-chat-service/**（ws/*、service/impl/ChatServiceImpl.java、config/WebSocketConfig.java）。

---

### 19 AI 服务 Spring AI 与 RAG

**定位**：AI 能力集成。重点讲 Spring AI、RAG、Function Calling、SSE、防注入。

**学习目标**：
- 掌握 Spring AI ChatClient 使用；
- 掌握 RAG 知识库流程（文档 → 分块 → 检索 → 拼上下文）；
- 掌握 Function Calling 工具与身份隔离、SSE 流式协议。

**必读资料**：
- `momentliving-ai-service/.../service/impl/AiChatServiceImpl.java`（**全文精读**：chat、chatStream、buildSystem、loadHistory）
- `momentliving-ai-service/.../config/AiConfig.java`、`AiProperties.java`
- `momentliving-ai-service/.../service/impl/AiKnowledgeServiceImpl.java`
- `momentliving-ai-service/.../tools/ShopTools.java`、`BlogTools.java`、`VoucherTools.java`、`MerchantTools.java`
- `momentliving-ai-service/.../prompt/AiPromptConstants.java`
- `momentliving-ai-service/.../controller/AiChatController.java`、`AiKnowledgeController.java`、`AiGenerateController.java`、`AiMerchantController.java`、`AiConversationController.java`、`AiFeedbackController.java`
- `momentliving-pojo/.../entity/AiKnowledgeDoc.java`、`AiKnowledgeChunk.java`、`AiConversation.java`、`AiMessage.java`

**大纲**：
1. **为什么选择**：平台需要 AI 助手（业务问答 / 商铺推荐 / 内容生成 / 商家经营分析）；为什么用 Spring AI（统一抽象、OpenAI 兼容协议、里程碑版 M6）；为什么 RAG（让模型回答项目私有知识，不用微调）。
2. **原理**：LLM 对话原理（System/User/Assistant 消息）；RAG 流程（文档 → 分块 → 向量化 → 相似度检索 → 拼 System Prompt）；Function Calling（模型决定调哪个工具、参数由模型生成）；SSE 流式协议；Prompt 注入原理与防御。
3. **在项目中的实现**：
   - `chat`（非流式）与 `chatStream`（SSE 流式）逐段拆解；
   - 多轮记忆：最近 N 轮历史（historyRounds）加载；
   - RAG：`AiKnowledgeService.retrieveContext`（TopK + 最大字符限制）→ `buildSystem` 把知识片段拼入 System Prompt；
   - Function Calling：`.tools(shopTools, blogTools, voucherTools)` + `.toolContext(Map.of("aiUserId", ...))`——**身份由服务端写入，AI 决定不了查谁**；
   - 防 Prompt 注入设计：用户输入只作为 User Message，禁止拼入 System Prompt；
   - SSE 事件协议：meta（conversationId）→ data（流式内容）→ done（[DONE]）→ error；
   - 会话管理：getOrCreate、appendMessage、touchConversation、generateTitleAsync（异步生成标题）；
   - 内容生成（博客/评价/商家文案）、商铺推荐、商家经营分析（AiMerchantService + MerchantTools）。
4. **面试常问点**（至少 4 个）：
   - Q：RAG 和微调有什么区别？什么时候用 RAG？
   - Q：Function Calling 原理？工具参数谁来生成？
   - Q：SSE 和 WebSocket 区别？为什么 AI 流式用 SSE？
   - Q：怎么防 Prompt 注入？（本项目：用户输入只作 User Message + 工具身份服务端注入）
   - Q：AI 回答不准确 / 幻觉怎么办？（RAG 上下文 + 限制字符 + 兜底话术）

**相关源码路径**：momentliving-ai-service/**（service/impl/*、tools/*、config/*、prompt/*、controller/*）。

---

## 四、生成与交付要求（给执行 AI）

1. **输出根目录**：所有生成的文档统一放到 `D:\Typora\data\前后端项目\一刻生活`（目录不存在则创建）。
2. **子目录**：在根目录下按文档体系的 5 大部分建立子目录，分别命名为 `第一部分`、`第二部分`、`第三部分`、`第四部分`、`第五部分`；每篇文档放入其所属部分的子目录（对应「篇目总览」表中的「部分」列）。
3. 文件命名建议：`01-项目总览与微服务架构设计.md` … `19-AI服务SpringAI与RAG.md`；拆分的篇目用 `-1` / `-2`（仅两份时可用 `-上` / `-下`）后缀。
4. 每篇开头加一行元信息：`> 所属部分：x · 编号：NN · 重点：是/否`。
5. 每篇结尾附「相关源码路径」清单。
6. 生成前先读对应「必读资料」中的源码文件，再动笔；所有引用必须与源码一致。
7. **单份文档超过 7000 字必须拆分**，拆分后各份字数尽量均衡（规则见「全局生成规范」第 5 条）。
8. 全部完成后，提供篇目清单、所在子目录与每份字数，便于核对完整性。
