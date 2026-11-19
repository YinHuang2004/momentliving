package com.momentliving.ai.dto;

/**
 * 消息反馈请求。
 */
public record FeedbackRequest(
        Long messageId,
        Integer rating,
        String comment) {
}
