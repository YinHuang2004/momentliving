package com.momentliving.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momentliving.entity.ChatGroupMember;
import com.momentliving.entity.ChatMessage;
import com.momentliving.entity.ChatSession;
import com.momentliving.mapper.ChatGroupMemberMapper;
import com.momentliving.mapper.ChatSessionMapper;
import com.momentliving.ws.WsSessionRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WS 推送服务：把帧推给在线用户；离线则跳过（消息已落库，重连后 HTTP 补拉）
 *
 * <p>原则：落库是事实（source of truth），推送只是"加速器"——推送失败只记日志不抛，
 * 可靠性靠"重连后拉历史"兜底，不靠推送必达（这也是 clientMsgId 幂等是刚需的原因）。
 */
@Service
@Slf4j
public class MessagePushService {

    @Resource
    private WsSessionRegistry sessionRegistry;

    @Resource
    private ChatSessionMapper chatSessionMapper;

    @Resource
    private ChatGroupMemberMapper chatGroupMemberMapper;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 推给指定用户的所有在线端（多端登录时每端各推一份）
     */
    public void pushToUser(Long userId, Map<String, Object> payload) {
        Set<WebSocketSession> set = sessionRegistry.getSessions(userId);
        if (set == null || set.isEmpty()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            for (WebSocketSession s : set) {
                if (s.isOpen()) {
                    s.sendMessage(new TextMessage(json));   // decorator 保证线程安全
                }
            }
        } catch (IOException e) {
            log.warn("WS 推送失败 userId={}", userId, e);
        }
    }

    /**
     * 按消息类型推送：单聊推对方；群聊推除发送者外的全部在线成员。
     * 注意：msg.sessionId 是 chat_session.id，查群成员必须先取 ChatSession.groupId，两者不是一个 ID。
     */
    public void pushToTargets(ChatMessage msg, Map<String, Object> payload) {
        if (msg.getReceiverId() != null && msg.getReceiverId() > 0) {
            pushToUser(msg.getReceiverId(), payload);
            return;
        }
        ChatSession session = chatSessionMapper.selectById(msg.getSessionId());
        if (session == null || session.getGroupId() == null) {
            return;
        }
        List<ChatGroupMember> members = chatGroupMemberMapper.selectList(
                new LambdaQueryWrapper<ChatGroupMember>()
                        .eq(ChatGroupMember::getGroupId, session.getGroupId()));
        for (ChatGroupMember m : members) {
            if (!m.getUserId().equals(msg.getSenderId())) {
                pushToUser(m.getUserId(), payload);
            }
        }
    }
}
