package com.momentliving.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线连接注册表（本机内存版）
 *
 * <p>两个必踩的坑都在这里解决：
 * 1. 一个用户可多端在线（H5 + App 同时），值必须用 Set，不能单存一个 session；
 * 2. 底层 WebSocketSession.sendMessage 非线程安全，并发写直接抛
 *    IllegalStateException(TEXT_PARTIAL_WRITING)——用 ConcurrentWebSocketSessionDecorator
 *    包一层，内部排队串行化；发送超时 5s 或缓冲超 256KB 判定死连接并关闭（顺带解决慢消费者背压）。
 *
 * <p>局限（水平扩展延伸）：本表只在本实例有效。chat-service 多实例部署时，
 * A 连实例1、B 连实例2，实例1 推不到 B——届时推送需改走 Redis pub/sub 或 MQ 广播。
 */
@Component
@Slf4j
public class WsSessionRegistry {

    /** userId → 该用户所有在线连接（已装饰） */
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession rawSession) {
        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(rawSession, 5_000, 256 * 1024);
        sessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("WS 上线 userId={}, session={}, 当前连接数={}", userId, rawSession.getId(),
                sessions.get(userId).size());
    }

    public void unregister(Long userId, WebSocketSession rawSession) {
        Set<WebSocketSession> set = sessions.get(userId);
        if (set == null) {
            return;
        }
        // 按 raw 的 sessionId 匹配移除（注册时包了装饰器）
        set.removeIf(s -> {
            WebSocketSession delegate = ((ConcurrentWebSocketSessionDecorator) s).getDelegate();
            return delegate.getId().equals(rawSession.getId());
        });
        if (set.isEmpty()) {
            sessions.remove(userId);
        }
        log.info("WS 下线 userId={}, session={}", userId, rawSession.getId());
    }

    /**
     * 取用户全部在线连接；null/空 = 当前不在线（消息已落库，重连后 HTTP 补拉）
     */
    public Set<WebSocketSession> getSessions(Long userId) {
        return sessions.get(userId);
    }
}
