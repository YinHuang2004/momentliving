package com.momentliving.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * AI 消息 VO（camelCase，与 Python 版 chat_service._message_vo 对齐）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiMessageVO(
        long id,
        long conversationId,
        String role,
        String content,
        Object toolCalls,
        String createdAt) {
}
