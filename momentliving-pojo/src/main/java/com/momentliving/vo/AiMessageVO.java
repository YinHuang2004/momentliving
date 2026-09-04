package com.momentliving.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 消息 VO（GET /ai/conversations/{id}/messages 与对话接口返回）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiMessageVO {
    private Long id;
    private Long conversationId;
    /** 角色：user / assistant */
    private String role;
    private String content;
    /** 工具调用记录（JSON 数组文本，可空） */
    private String toolCalls;
    private LocalDateTime createdAt;
}
