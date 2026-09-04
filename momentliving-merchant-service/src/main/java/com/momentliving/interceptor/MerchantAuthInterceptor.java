package com.momentliving.interceptor;

import cn.hutool.core.util.StrUtil;
import com.momentliving.constant.RedisConstants;
import com.momentliving.context.MerchantHolder;
import com.momentliving.properties.JwtProperties;
import com.momentliving.utils.JwtUtils;
import com.momentliving.vo.MerchantVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * ★ 商家端鉴权拦截器（/merchant/** 专用，与 AdminAuthInterceptor 隔离）
 * 职责：解析商家端 JWT → 校验 Redis 登录态（login:merchant:{id}）→ 塞入 MerchantHolder
 * 说明：商家账号在独立 merchant 表，与管理员账号体系彻底分离
 */
@Component
@Slf4j
public class MerchantAuthInterceptor implements HandlerInterceptor {

    @Resource
    private JwtProperties jwtProperties;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

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
        // 2. 解析 JWT 拿 merchantId
        Long merchantId;
        try {
            merchantId = JwtUtils.getUserId(token, jwtProperties.getSecret());
        } catch (Exception e) {
            log.warn("商家端 Token 解析失败: {}", e.getMessage());
            return unauthorized(response, "Token 无效或已过期");
        }
        // 3. 校验 Redis 登录态（商家端专用 key）
        String cacheToken = stringRedisTemplate.opsForValue()
                .get(RedisConstants.LOGIN_MERCHANT_KEY + merchantId);
        if (StrUtil.isBlank(cacheToken) || !cacheToken.equals(token)) {
            return unauthorized(response, "登录已过期，请重新登录");
        }
        // 4. 塞入 MerchantHolder（只有 id，可后续查库补全）
        MerchantVO merchant = MerchantVO.builder().id(merchantId).build();
        MerchantHolder.saveMerchant(merchant);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        MerchantHolder.removeMerchant();
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
