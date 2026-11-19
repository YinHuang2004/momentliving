package com.momentliving.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 会话内历史消息 VO。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageVO(
        long id,
        long conversationId,
        String role,
        String content,
        Object toolCalls,
        String createdAt) {
}
