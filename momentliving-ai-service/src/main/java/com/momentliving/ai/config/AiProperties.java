package com.momentliving.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI 业务配置（application.yml 里 momentliving.ai.*）。
 *
 * @param enabled            AI 功能总开关（关 → 对话接口报「AI 功能未开启」）
 * @param ragEnabled         RAG 知识库检索开关
 * @param ragTopK            向量检索 top-k
 * @param historyRounds      历史轮数（Redis 滑动窗口 = 轮数 * 2 条消息，最小 4）
 * @param knowledgeMaxChars  RAG 上下文最大字符数
 * @param embedding          Embedding 模型配置（硅基流动 bge-m3，OpenAI 兼容协议）
 * @param upstream           Java 上游服务直连地址（工具调用用）
 */
@ConfigurationProperties(prefix = "momentliving.ai")
public record AiProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("true") boolean ragEnabled,
        @DefaultValue("5") int ragTopK,
        @DefaultValue("10") int historyRounds,
        @DefaultValue("1500") int knowledgeMaxChars,
        Embedding embedding,
        Upstream upstream) {

    public int windowSize() {
        return Math.max(historyRounds * 2, 4);
    }

    public record Embedding(
            String baseUrl,
            String apiKey,
            @DefaultValue("BAAI/bge-m3") String model) {
    }

    /** 工具调用的 Java 上游服务（与 Python 版 UpstreamSettings 一一对应）。 */
    public record Upstream(
            String shopServiceUrl,
            String blogServiceUrl,
            String voucherServiceUrl,
            String merchantServiceUrl,
            @DefaultValue("10") int timeoutSeconds) {
    }
}
