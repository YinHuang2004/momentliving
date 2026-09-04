package com.momentliving.config;

import com.momentliving.ws.ChatHandshakeInterceptor;
import com.momentliving.ws.ChatSocketHandler;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 端点注册：/ws/chat
 *
 * <p>链路：网关 lb:ws://chat-service（Path=/ws/**，网关白名单放行，不在网关做鉴权）
 * → 本服务握手拦截器做 JWT + Redis 登录态校验 → ChatSocketHandler 处理帧。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Resource
    private ChatHandshakeInterceptor handshakeInterceptor;

    @Resource
    private ChatSocketHandler chatSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatSocketHandler, "/ws/chat")
                .addInterceptors(handshakeInterceptor)
                // uniapp 多端 + H5 dev 跨端口连接，必须放开源限制
                .setAllowedOriginPatterns("*");
    }
}
