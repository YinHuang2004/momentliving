package com.momentliving.interceptor;

import com.momentliving.context.UserHolder;
import com.momentliving.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * ★ 微服务改造新增：从 Gateway 透传的 Header 中解析 userId
 * 替代单体的 RefreshTokenInterceptor（JWT 解析逻辑已上移到 Gateway）
 */
@Component
public class UserContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 从 Header 中取 Gateway 写入的 userId
        String userIdStr = request.getHeader("X-User-Id");
        if (userIdStr != null) {
            Long userId = Long.valueOf(userIdStr);
            UserVO userVO = UserVO.builder().id(userId).build();
            // 存入 ThreadLocal（这里只有 userId，后续功能可缓存全量用户信息）
            UserHolder.saveUser(userVO);
        }
        // 即使 userId 为空也放行（让 LoginInterceptor 去拦截需要登录的接口）
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserHolder.removeUser();
    }
}