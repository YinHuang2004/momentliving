package com.momentliving.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 会话列表 VO（GET /ai/conversations）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiConversationVO {
    private Long id;
    private String title;
    /** 最后一条消息预览 */
    private String lastMessage;
    private LocalDateTime updatedAt;
}
