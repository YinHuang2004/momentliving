package com.momentliving.interceptor;

import com.momentliving.context.UserHolder;
import com.momentliving.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 从网关透传的 X-User-Id 头解析用户，塞入 ThreadLocal（与其他服务一致）
 */
@Component
public class UserContextInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
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
