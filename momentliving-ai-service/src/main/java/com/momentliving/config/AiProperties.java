package com.momentliving.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 业务配置项（momentliving.ai.*，见 application.yml；可在 Nacos 动态刷新）
 */
@Data
@Component
@ConfigurationProperties(prefix = "momentliving.ai")
public class AiProperties {

    /** AI 总开关（false 时对话接口返回"AI 功能未开启"） */
    private boolean enabled = true;

    /** RAG 知识库开关 */
    private boolean ragEnabled = true;

    /** 检索返回的知识块数 top-k */
    private int ragTopK = 5;

    /** 多轮对话携带的历史轮数（1 轮 = user + assistant） */
    private int historyRounds = 10;

    /** SSE 流式超时（毫秒） */
    private long sseTimeoutMs = 60_000L;

    /** 拼入 System Prompt 的知识片段总长度上限（Token 成本控制） */
    private int knowledgeMaxChars = 1500;
}
