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
 * AI 回答反馈表（ai_feedback）：用户对某条 assistant 消息点赞/点踩/评分，用于优化 Prompt 与知识库
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_feedback")
public class AiFeedback implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 关联的 assistant 消息 ID（ai_message.id） */
    private Long messageId;
    private Long userId;
    /** 评分：1-5 星（也可用 1=赞 0=踩） */
    private Integer rating;
    /** 文字反馈（可空） */
    private String comment;
    private LocalDateTime createdAt;
}
