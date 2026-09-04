package com.momentliving.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 商铺推荐结果 VO（POST /ai/recommend/shop）：商铺核心信息 + 每店的 AI 推荐语
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiShopRecommendVO {
    private Long shopId;
    private String name;
    private String area;
    private String address;
    /** 人均（分） */
    private Long avgPrice;
    /** 评分（×10 存储，如 48 = 4.8 分） */
    private Integer score;
    /** AI 生成的推荐理由 */
    private String reason;
}
