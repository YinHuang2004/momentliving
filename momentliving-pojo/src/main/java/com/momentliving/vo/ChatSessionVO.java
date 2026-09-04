package com.momentliving.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话列表项 / ensureSingle 返回体
 *
 * <p>canSend 由服务端算好（首条限制状态机 + 是否发起方），前端只执行：
 * type=单聊 且 first_reply=0 且 当前用户是发起方(user_a_id) 时为 false（输入框锁定）。
 */
@Data
public class ChatSessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 1单聊 2群聊 */
    private Integer type;

    /** 单聊：对方用户ID */
    private Long peerId;

    /** 单聊：对方昵称（Feign 回填，不落 chat 库） */
    private String peerName;

    /** 单聊：对方头像 */
    private String peerAvatar;

    /** 群聊：群ID */
    private Long groupId;

    /** 群聊：群名 */
    private String groupName;

    /** 最后一条消息预览 */
    private String lastMessage;

    private LocalDateTime lastMessageAt;

    /** 该会话的未读消息数（对方发给我且未读） */
    private Long unreadCount;

    /** 是否可发送消息（首条限制：false=发起方等对方回复中，前端锁定输入框） */
    private Boolean canSend;
}
