package com.momentliving.dto;

import lombok.Data;

/**
 * AI 回答反馈入参 DTO（POST /ai/feedback）
 */
@Data
public class AiFeedbackDTO {

    /** 关联的 assistant 消息 ID（必填） */
    private Long messageId;

    /** 评分：1-5（必填） */
    private Integer rating;

    /** 文字反馈（可选） */
    private String comment;
}
