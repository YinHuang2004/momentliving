package com.momentliving.interceptor;

import com.momentliving.context.UserHolder;
import com.momentliving.vo.UserVO;
import com.momentliving.security.InternalRequestVerifier;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 从网关透传的 X-User-Id 头解析用户，塞入 ThreadLocal（与其他服务一致）
 */
@Component
public class UserContextInterceptor implements HandlerInterceptor {
    private final InternalRequestVerifier verifier;

    public UserContextInterceptor(InternalRequestVerifier verifier) {
        this.verifier = verifier;
    }

    /** 先验内部身份头，再解析网关透传的 X-User-Id 存入 ThreadLocal（无头也放行，交给 LoginInterceptor 拦） */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (verifier.requiresVerification(request.getRequestURI(), request::getHeader)
                && !verifier.verify(request.getMethod(), request.getRequestURI(), request::getHeader)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "非法内部身份头");
            return false;
        }
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
        UserHolder.removeUser();
    }
}
