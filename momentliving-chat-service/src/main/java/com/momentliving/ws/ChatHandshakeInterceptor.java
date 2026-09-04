package com.momentliving.ws;

import cn.hutool.core.util.StrUtil;
import com.momentliving.constant.RedisConstants;
import com.momentliving.properties.JwtProperties;
import com.momentliving.utils.JwtUtils;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WS 握手鉴权拦截器
 *
 * <p>token 从 query 取（ws://host:8080/ws/chat?token=xxx）——不用 header 的原因：
 * 浏览器原生 WebSocket API 根本不能自定义头，uni.connectSocket 的 header 参数
 * 在部分小程序端也不生效，query 是唯一全端可靠的方式。
 *
 * <p>校验两层：JWT 签名有效性 + Redis 登录态（login:refresh:{userId} 存活，与网关同标准）。
 * 只验 JWT 不验 Redis 会放过"已退出登录但 accessToken 未过期"的用户。
 *
 * <p>注意：网关 AuthGlobalFilter 对 WS 握手（本质是 HTTP Upgrade 请求）同样生效，
 * 已在网关 PREFIX_WHITE_LIST 放行 /ws/，鉴权完全在本拦截器完成。
 */
@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    @Resource
    private JwtProperties jwtProperties;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // 1. 从 query 取 token
        String token = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("token");

        // 2. JWT 校验拿 userId（复用 momentliving-common 的 JwtUtils，secret 与 user-service/网关一致）
        Long userId = null;
        if (StrUtil.isNotBlank(token)) {
            try {
                userId = JwtUtils.getUserId(token, jwtProperties.getSecret());
            } catch (Exception e) {
                // 签名无效/过期：userId 保持 null
            }
        }

        // 3. Redis 登录态校验（refresh token 存活才放行）
        boolean ok = userId != null && Boolean.TRUE.equals(
                stringRedisTemplate.hasKey(RedisConstants.LOGIN_USER_KEY + userId));

        if (!ok) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;   // 拒绝握手：客户端侧表现为 onOpen 不触发、走 onError/onClose
        }

        // 4. userId 放进 attributes，ChatSocketHandler.afterConnectionEstablished 里取
        attributes.put("userId", userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }
}
