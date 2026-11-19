package com.momentliving.ai.dto;

/**
 * AI 推荐店铺请求。
 */
public record RecommendRequest(
        String preference,
        String area) {
}
