package com.momentliving.ai.dto;

/**
 * 评价生成请求。
 */
public record GenerateReviewRequest(
        Long shopId,
        Integer rating,
        String impression) {
}
