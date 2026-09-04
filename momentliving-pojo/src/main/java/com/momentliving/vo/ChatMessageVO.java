package com.momentliving.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聊天消息 VO：实体字段 + 发送者展示信息（Feign 回填）
 */
@Data
public class ChatMessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long sessionId;

    private Long senderId;

    /** 发送者昵称（群聊气泡展示用；群名片优先，暂统一用用户昵称） */
    private String senderName;

    /** 发送者头像 */
    private String senderAvatar;

    /** 0文本 1图片 2博客卡片 */
    private Integer type;

    /** 文本 / 图片URL / 博客卡片JSON（{blogId,title,cover,author}） */
    private String content;

    private LocalDateTime createTime;
}
