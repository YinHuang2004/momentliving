package com.momentliving.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 消息记录表（ai_message）：会话内的 user / assistant / system 消息，多轮对话的上下文来源
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_message")
public class AiMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    /** 角色：user / assistant / system */
    private String role;
    /** 消息内容 */
    private String content;
    /** 本次回答触发的工具调用记录（JSON 数组，可空）：[{name, arguments, costMs}] */
    private String toolCalls;
    private LocalDateTime createdAt;
}
