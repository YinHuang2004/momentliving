package com.momentliving.dto;

import lombok.Data;

/**
 * AI 探店博客草稿生成入参 DTO（POST /ai/generate/blog）
 */
@Data
public class AiGenerateBlogDTO {

    /** 店铺 ID（必填） */
    private Long shopId;

    /** 文风：humor 幽默 / literary 文艺 / simple 简洁（默认 simple） */
    private String style;

    /** 想突出的关键词，如菜品/项目（可选） */
    private String keywords;
}
