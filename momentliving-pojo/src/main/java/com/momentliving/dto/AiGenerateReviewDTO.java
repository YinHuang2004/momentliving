package com.momentliving.dto;

import lombok.Data;

/**
 * AI 评价文案生成入参 DTO（POST /ai/generate/review）
 */
@Data
public class AiGenerateReviewDTO {

    /** 店铺 ID（必填） */
    private Long shopId;

    /** 星级 1-5（必填，AI 会按星级调整语气） */
    private Integer rating;

    /** 简短感受（必填，AI 扩写为完整评价） */
    private String impression;
}
