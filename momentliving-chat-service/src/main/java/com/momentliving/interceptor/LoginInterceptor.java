package com.momentliving.interceptor;

import com.momentliving.context.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * REST 登录态校验：UserHolder 为空则 401（WS 握手不走这里，由 ChatHandshakeInterceptor 负责）
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 非Controller方法（如静态资源请求）直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        if (UserHolder.getUser() == null) {
            response.setStatus(401);
            return false;
        }
        return true;
    }
}
