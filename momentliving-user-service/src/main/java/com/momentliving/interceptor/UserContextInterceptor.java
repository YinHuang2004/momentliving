package com.momentliving.interceptor;

import com.momentliving.context.UserHolder;
import com.momentliving.vo.UserVO;
import com.momentliving.security.InternalRequestVerifier;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 用户上下文拦截器（第一层）：HMAC 验签网关签名的内部身份头，再把 X-User-Id 装入 ThreadLocal（UserHolder）
 */
@Component
public class UserContextInterceptor implements HandlerInterceptor {
    private final InternalRequestVerifier verifier;

    public UserContextInterceptor(InternalRequestVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 先验签网关签名的内部身份头（防内网伪造），失败直接 401
        if (verifier.requiresVerification(request.getRequestURI(), request::getHeader)
                && !verifier.verify(request.getMethod(), request.getRequestURI(), request::getHeader)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "非法内部身份头");
            return false;
        }
        // 网关已验签放行，把 X-User-Id 装入 ThreadLocal，供本次请求链路使用
        String userIdStr = request.getHeader("X-User-Id");
        if (userIdStr != null) {
            Long userId = Long.valueOf(userIdStr);
            UserVO userVO = UserVO.builder().id(userId).build();
            UserHolder.saveUser(userVO);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 请求结束清理 ThreadLocal（Tomcat 线程复用，不清理会串号/内存泄漏）
        UserHolder.removeUser();
    }
}
