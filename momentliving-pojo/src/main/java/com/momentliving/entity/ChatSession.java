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
 * 聊天会话（单聊 + 群聊统一存放）
 *
 * <p>单聊约定：user_a_id 存两端中较小的用户ID、user_b_id 存较大的，
 * 因此 A 找 B 和 B 找 A 命中同一行；配合 uk_single 唯一索引保证 ensureSingle 幂等。
 * 注意：user_a_id 只是归一化存储，不代表发起方——真实发起方看 initiator_id。
 *
 * <p>首条限制（防骚扰，仅单聊）三态状态机（first_reply）：
 * 0 = INIT：会话刚创建，双方都可开口；
 * 2 = WAIT_REPLY：发起方已发首条，锁定发起方，等对方回复；
 * 1 = FREE：对方已回复（或主动先开口），双方自由聊。
 * 状态翻转全部用条件更新（where first_reply=期望值）保证并发安全。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("chat_session")
public class ChatSession implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话类型：1 单聊，2 群聊 */
    public static final int TYPE_SINGLE = 1;
    public static final int TYPE_GROUP = 2;

    /** 首条限制状态：INIT（会话刚建，双方可开口） */
    public static final int FR_INIT = 0;
    /** 首条限制状态：FREE（双方自由聊） */
    public static final int FR_FREE = 1;
    /** 首条限制状态：WAIT_REPLY（发起方已发首条，等对方回复） */
    public static final int FR_WAIT_REPLY = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 1单聊 2群聊 */
    private Integer type;

    /** 单聊：发起方用户ID（⚠️ 仅为归一化存储的两端较小ID，不是真实发起方）；群聊：群主ID */
    private Long userAId;

    /** 单聊：接收方用户ID（约定为两端较大ID）；群聊：NULL */
    private Long userBId;

    /** 单聊：真实发起方用户ID（首条限制状态机的判定依据；群聊：0） */
    private Long initiatorId;

    /** 群聊：群ID；单聊：NULL */
    private Long groupId;

    /** 最后一条消息预览（文本=内容截断 / [图片] / [博客]） */
    private String lastMessage;

    /** 最后消息时间（会话列表排序依据） */
    private LocalDateTime lastMessageAt;

    /** 首条限制状态机（仅单聊）：0=INIT 双方可开口 2=WAIT_REPLY 发起方已发等回复 1=FREE 双方自由 */
    private Integer firstReply;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
