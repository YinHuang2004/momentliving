package com.momentliving.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momentliving.service.ChatService;
import com.momentliving.service.MessagePushService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * WS 核心处理器：onOpen 注册 / onMessage 分发 / onClose 注销
 *
 * <p>本类只做"解析 + 分发"，业务全部在 ChatService（事务、校验、落库都在那边）。
 * 客户端 → 服务端的帧协议见《21 聊天技术文档》4.4 节：
 * {op:'send'|'read'|'ping', ...}
 */
@Component
@Slf4j
public class ChatSocketHandler extends TextWebSocketHandler {

    @Resource
    private WsSessionRegistry sessionRegistry;

    @Resource
    private ChatService chatService;

    @Resource
    private MessagePushService pushService;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        sessionRegistry.register(userId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        JsonNode node;
        try {
            node = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            log.warn("非法 WS 帧 userId={} payload={}", userId, message.getPayload());
            return;
        }
        switch (node.path("op").asText("")) {
            case "ping" -> pushService.pushToUser(userId, Map.of("op", "pong"));
            case "send" -> chatService.send(userId, node);   // 落库 + 推送 + ack/reject（内部全捕获，不向上抛）
            case "read" -> chatService.markRead(userId, node.path("sessionId").asLong());
            default -> {
                // 未知 op 静默忽略，不留后门
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        sessionRegistry.unregister(userId, session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long userId = (Long) session.getAttributes().get("userId");
        log.warn("WS 传输异常 userId={} msg={}", userId, exception.getMessage());
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (Exception ignored) {
        }
        sessionRegistry.unregister(userId, session);
    }
}
