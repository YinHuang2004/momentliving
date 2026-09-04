package com.momentliving.interceptor;

import com.momentliving.context.AdminHolder;
import com.momentliving.context.MerchantHolder;
import com.momentliving.context.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录校验拦截器（第二层）。
 * 放行条件：C 端用户态（X-User-Id）/ 平台管理员态（X-Admin-Id，知识库管理用）/
 * 商家态（X-Merchant-Id，AI 商家接口用）任一存在。
 * ⚠️ 网关对 /ai/merchant/** 走"商家端模式"，只透传 X-Merchant-Id，这里必须认商家态，
 *    否则商家 AI 接口会被 401 拦死。
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 非Controller方法（如静态资源/文档请求）直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        if (UserHolder.getUser() == null && AdminHolder.getAdmin() == null
                && MerchantHolder.getMerchant() == null) {
            response.setStatus(401);
            return false;
        }
        return true;
    }
}
