package com.momentliving.ai.dto;

/**
 * 对话请求（字段名与 Python 版 ChatRequest 对齐：message 必填，conversationId 为空则新建会话）。
 */
public record ChatRequest(
        String message,
        Long conversationId) {
}
