package com.momentliving.interceptor;

import cn.hutool.core.util.StrUtil;
import com.momentliving.constant.RedisConstants;
import com.momentliving.context.AdminHolder;
import com.momentliving.properties.JwtProperties;
import com.momentliving.utils.JwtUtils;
import com.momentliving.vo.AdminVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * ★ 管理端鉴权拦截器（/admin/** 专用，与用户端 UserContextInterceptor 隔离）
 * 职责：解析管理端 JWT → 校验 Redis 登录态 → 塞入 AdminHolder
 * 说明：管理端接口必须登录（不放行白名单，登录接口本身除外）
 */
@Component
@Slf4j
public class AdminAuthInterceptor implements HandlerInterceptor {

    @Resource
    private JwtProperties jwtProperties;

    @Resource
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 非 Controller 方法直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        // 1. 取 Authorization Header
        String token = request.getHeader(jwtProperties.getTokenName());
        if (StrUtil.isBlank(token)) {
            return unauthorized(response, "未登录，请先登录");
        }
        // 2. 解析 JWT 拿 adminId
        Long adminId;
        try {
            adminId = JwtUtils.getUserId(token, jwtProperties.getSecret());
        } catch (Exception e) {
            log.warn("管理端 Token 解析失败: {}", e.getMessage());
            return unauthorized(response, "Token 无效或已过期");
        }
        // 3. 校验 Redis 登录态（管理端专用 key）
        String cacheToken = stringRedisTemplate.opsForValue()
                .get(RedisConstants.LOGIN_ADMIN_KEY + adminId);
        if (StrUtil.isBlank(cacheToken) || !cacheToken.equals(token)) {
            return unauthorized(response, "登录已过期，请重新登录");
        }
        // 4. 塞入 AdminHolder（只有 id，可后续查库补全）
        AdminVO admin = AdminVO.builder().id(adminId).build();
        AdminHolder.saveAdmin(admin);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AdminHolder.removeAdmin();
    }

    private boolean unauthorized(HttpServletResponse response, String message) {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        try {
            response.getWriter().write(
                    "{\"code\":0,\"msg\":\"" + message + "\",\"data\":null}");
        } catch (Exception ignored) {
        }
        return false;
    }
}
