package com.momentliving.interceptor;

import com.momentliving.context.AdminHolder;
import com.momentliving.context.MerchantHolder;
import com.momentliving.context.UserHolder;
import com.momentliving.vo.AdminVO;
import com.momentliving.vo.MerchantVO;
import com.momentliving.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 身份还原拦截器（第一层）：把网关透传的身份头塞进 ThreadLocal。
 *
 * <p>三种身份（与网关 AuthGlobalFilter 透传头一致，也与 FeignIdentityInterceptor 对齐）：
 * C 端用户 X-User-Id / 商家 X-Merchant-Id / 平台管理员 X-Admin-Id。
 * AI 商家接口（/ai/merchant/**）读 MerchantHolder，知识库管理（/ai/knowledge/**）读 AdminHolder。
 */
@Component
public class UserContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userIdStr = request.getHeader("X-User-Id");
        if (userIdStr != null) {
            UserHolder.saveUser(UserVO.builder().id(Long.valueOf(userIdStr)).build());
        }
        String merchantIdStr = request.getHeader("X-Merchant-Id");
        if (merchantIdStr != null) {
            // 商家 VO 只填 id；商家 AI 接口需要 shopId 时由 Feign 回查或走 ToolContext 透传
            MerchantHolder.saveMerchant(MerchantVO.builder().id(Long.valueOf(merchantIdStr)).build());
        }
        String adminIdStr = request.getHeader("X-Admin-Id");
        if (adminIdStr != null) {
            AdminHolder.saveAdmin(AdminVO.builder().id(Long.valueOf(adminIdStr)).build());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserHolder.removeUser();
        MerchantHolder.removeMerchant();
        AdminHolder.removeAdmin();
    }
}
