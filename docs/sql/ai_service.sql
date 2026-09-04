-- ============================================================
-- AI 助手服务建表脚本（ai-service）
-- 数据库：复用 momentliving 主库（与 Nacos momentliving-common.yaml 数据源一致），表统一 ai_ 前缀
-- 执行方式：mysql -uroot -p momentliving < ai_service.sql
-- ============================================================

-- 1. AI 会话表
CREATE TABLE IF NOT EXISTS `ai_conversation` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    `user_id`      BIGINT       NOT NULL COMMENT '用户ID（C端user.id 或 商家merchant.id，user_type区分）',
    `user_type`    TINYINT      NOT NULL DEFAULT 1 COMMENT '身份类型：1=C端用户 2=商家',
    `title`        VARCHAR(100) NOT NULL DEFAULT '新对话' COMMENT '会话标题（AI自动生成）',
    `last_message` VARCHAR(500) DEFAULT NULL COMMENT '最后一条消息预览',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`, `updated_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI会话表';

-- 2. AI 消息表
CREATE TABLE IF NOT EXISTS `ai_message` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `conversation_id` BIGINT      NOT NULL COMMENT '会话ID',
    `role`            VARCHAR(20) NOT NULL COMMENT '角色：user/assistant/system',
    `content`         TEXT        NOT NULL COMMENT '消息内容',
    `tool_calls`      JSON        DEFAULT NULL COMMENT '工具调用记录（可选）',
    `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_conversation_id` (`conversation_id`, `id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI消息记录表';

-- 3. AI 回答反馈表
CREATE TABLE IF NOT EXISTS `ai_feedback` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '反馈ID',
    `message_id` BIGINT       NOT NULL COMMENT '关联的assistant消息ID（ai_message.id）',
    `user_id`    BIGINT       NOT NULL COMMENT '反馈人用户ID',
    `rating`     TINYINT      NOT NULL COMMENT '评分1-5（或1=赞 0=踩）',
    `comment`    VARCHAR(500) DEFAULT NULL COMMENT '文字反馈',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_message_id` (`message_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI回答反馈表';

-- 4. AI 知识库文档表（元数据）
CREATE TABLE IF NOT EXISTS `ai_knowledge_doc` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '文档ID',
    `title`       VARCHAR(200) NOT NULL COMMENT '文档标题',
    `source_type` VARCHAR(20)  NOT NULL DEFAULT 'help' COMMENT '来源类型：faq/help/rule',
    `file_url`    VARCHAR(500) DEFAULT NULL COMMENT '原始文件URL（OSS，可空）',
    `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0=处理中 1=已入库 2=失败',
    `chunk_count` INT          DEFAULT NULL COMMENT '切分块数',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI知识库文档表';

-- 5. AI 知识块表（轻量RAG：向量以JSON文本存MySQL，内存余弦检索；无向量时降级关键词匹配）
CREATE TABLE IF NOT EXISTS `ai_knowledge_chunk` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '知识块ID',
    `doc_id`     BIGINT   NOT NULL COMMENT '所属文档ID',
    `content`    TEXT     NOT NULL COMMENT '切分后的文本片段',
    `embedding`  TEXT     DEFAULT NULL COMMENT '向量（JSON数组文本；未向量化时为NULL）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_doc_id` (`doc_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI知识块表';

-- ============================================================
-- 初始知识库 FAQ（embedding 为 NULL，走关键词检索即可生效；
-- 后续可通过 POST /ai/knowledge/upload 重新上传获得向量检索能力）
-- ============================================================
INSERT INTO `ai_knowledge_doc` (`title`, `source_type`, `status`, `chunk_count`)
VALUES ('平台常见问题FAQ', 'faq', 1, 6);

SET @faq_doc_id = LAST_INSERT_ID();

INSERT INTO `ai_knowledge_chunk` (`doc_id`, `content`) VALUES
(@faq_doc_id, '【秒杀券怎么用】购买秒杀券后，在"我的订单"中查看核销码，到店后向商家出示核销码扫码核销即可使用。秒杀券每人限购3张，未支付的订单超过15分钟会自动关闭。'),
(@faq_doc_id, '【退款规则】已支付未核销的券可以申请退款，退款原路退回，一般1-3个工作日到账。已核销的券不支持退款。秒杀券同样支持未核销退款。'),
(@faq_doc_id, '【商家入驻流程】在商家端点击"商家入驻申请"，填写店铺信息与资质后提交，平台管理员审核通过后会自动生成商家账号，用申请时填写的手机号登录商家端。入驻审核一般1-3个工作日完成。'),
(@faq_doc_id, '【积分有什么用】每日签到可以获得积分，积分是个人成长体系的一部分，可在个人主页查看。连续签到获得的积分越多。'),
(@faq_doc_id, '【如何发布探店笔记】只有在平台购买过并核销的店铺才能发布探店笔记，进入店铺详情页或博客页点击"发布"即可，发布后其他用户可以点赞、收藏和评论你的笔记。'),
(@faq_doc_id, '【优惠券说明】团购券是店铺的通用代金券，购买价低于抵扣价，例如售价60元抵100元。秒杀券是限时限量抢购的特价券，抢完即止。核销时出示核销码给商家扫码即可。');
