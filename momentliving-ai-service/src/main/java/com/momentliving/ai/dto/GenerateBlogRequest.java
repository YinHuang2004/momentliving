package com.momentliving.ai.dto;

/**
 * 探店笔记生成请求。style：simple / humor / literary。
 */
public record GenerateBlogRequest(
        Long shopId,
        String style,
        String keywords) {
}
