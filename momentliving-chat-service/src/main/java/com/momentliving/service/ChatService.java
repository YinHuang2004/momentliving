package com.momentliving.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.momentliving.vo.ChatMessageVO;
import com.momentliving.vo.ChatSessionVO;
import com.momentliving.vo.GroupMemberVO;
import com.momentliving.vo.UserVO;

import java.util.List;
import java.util.Map;

/**
 * 聊天业务接口
 *
 * <p>通道分工铁律：发送与推送走 WebSocket（send/markRead 由 WS 帧触发），
 * HTTP 只做查询与群管理命令（listSessions/listMessages/ensureSingle/groupXxx）。
 */
public interface ChatService {

    /**
     * WS send 帧入口：校验 → 首条限制 → 落库（幂等）→ 推送 → ack；
     * 任何拒绝（首条限制/非成员/重复消息）都不向上抛，而是转 {op:'reject'/'ack'} 帧回给发送者
     *
     * @param senderId 握手时绑定的用户ID
     * @param node     完整 WS 帧JSON（含 clientMsgId/sessionId/type/content）
     */
    void send(Long senderId, JsonNode node);

    /**
     * 标记会话已读（WS read 帧 / POST /chat/read 共用）
     */
    void markRead(Long userId, Long sessionId);

    /**
     * 当前用户会话列表（单聊+群聊，按最后消息时间倒序，含未读数/对端昵称头像/canSend）
     */
    List<ChatSessionVO> listSessions(Long userId);

    /**
     * 历史消息（游标分页，重连补拉离线消息也用它）
     *
     * @return {list: List<ChatMessageVO> 按 id 倒序（新→旧）, canSend: boolean}
     */
    Map<String, Object> listMessages(Long userId, Long sessionId, Long cursor, int size);

    /**
     * 创建/获取单聊会话（幂等：uk_single 唯一索引 + 撞索引回查）
     */
    ChatSessionVO ensureSingle(Long userId, Long peerUserId);

    /**
     * 搜用户：昵称模糊 or 手机号精确（user-service Feign，chat-service 代理）
     */
    List<UserVO> searchUsers(String keyword);

    /**
     * 未读消息总数（Tab 消息红点）
     */
    long unreadCount(Long userId);

    /** 建群（创建者=群主，角色 2） */
    ChatSessionVO createGroup(Long userId, String groupName, List<Long> memberIds);

    /** 群成员列表（含角色与用户资料） */
    List<GroupMemberVO> groupMembers(Long userId, Long groupId);

    /** 退群（群主不能退，只能解散） */
    void leaveGroup(Long userId, Long groupId);

    /** 移除成员（群主/管理可操作，不能移除群主） */
    void removeMember(Long userId, Long groupId, Long targetUserId);

    /** 设置管理员（仅群主） */
    void setAdmin(Long userId, Long groupId, Long targetUserId);

    /** 解散群（仅群主）：删群/成员/会话 */
    void dissolveGroup(Long userId, Long groupId);
}
