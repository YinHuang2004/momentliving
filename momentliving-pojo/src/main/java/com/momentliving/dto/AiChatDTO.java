package com.momentliving.dto;

import lombok.Data;

/**
 * AI 对话入参 DTO（POST /ai/chat）
 */
@Data
public class AiChatDTO {

    /** 用户输入（必填，≤2000 字；只作为 User Message，禁止拼进 System Prompt 防注入） */
    private String message;

    /** 会话 ID（可空：为空则自动新建会话） */
    private Long conversationId;
}
