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
 * AI 会话表（ai_conversation）：一个用户可开多个会话，user_type 区分 C 端用户 / B 端商家
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_conversation")
public class AiConversation implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 用户 ID（C 端 user.id 或商家 merchant.id，由 userType 区分） */
    private Long userId;
    /** 身份类型：1=C 端用户 2=商家 */
    private Integer userType;
    /** 会话标题（首次对话后生成，默认取首条消息截断） */
    private String title;
    /** 最后一条消息预览（会话列表展示用） */
    private String lastMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
