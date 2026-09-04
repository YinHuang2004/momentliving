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
 * 聊天消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("chat_message")
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息类型：0文本 1图片(content为URL) 2博客卡片(content为JSON) */
    public static final int TYPE_TEXT = 0;
    public static final int TYPE_IMAGE = 1;
    public static final int TYPE_BLOG_CARD = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long senderId;

    /** 接收者用户ID（单聊=对方；群聊=0，广播按群成员表） */
    private Long receiverId;

    /** 0文本 1图片 2博客卡片 */
    private Integer type;

    /** 消息内容（type=2 时是卡片 JSON：{blogId,title,cover,author}） */
    private String content;

    /** 0未读 1已读 */
    private Integer isRead;

    /** 客户端生成的幂等ID（防断线重发重复，uk_client_msg 唯一索引） */
    private String clientMsgId;

    private LocalDateTime createTime;
}
