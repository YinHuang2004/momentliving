package com.momentliving.interceptor;

import com.momentliving.context.UserHolder;
import com.momentliving.vo.UserVO;
import com.momentliving.security.InternalRequestVerifier;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 从网关透传的 X-User-Id 头解析用户，塞入 ThreadLocal（无登录要求，评价等接口依赖）
 */
@Component
public class UserContextInterceptor implements HandlerInterceptor {
    private final InternalRequestVerifier verifier;

    public UserContextInterceptor(InternalRequestVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 先校验内部身份头（防伪造 X-User-Id 直连），再把网关透传的用户 id 还原进 ThreadLocal
        if (verifier.requiresVerification(request.getRequestURI(), request::getHeader)
                && !verifier.verify(request.getMethod(), request.getRequestURI(), request::getHeader)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "非法内部身份头");
            return false;
        }
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            UserHolder.saveUser(UserVO.builder().id(Long.valueOf(userId)).build());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserHolder.removeUser();
    }
}
