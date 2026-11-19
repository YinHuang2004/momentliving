package com.momentliving.ai.dto;

/**
 * 推荐结果项（结构化输出目标，字段名即 JSON key，与 Python 版一致）。
 */
public record RecommendShopVO(
        long shopId,
        String name,
        String area,
        String address,
        String avgPrice,
        Double score,
        String reason) {
}
