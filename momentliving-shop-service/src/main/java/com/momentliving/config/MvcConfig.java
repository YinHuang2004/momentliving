package com.momentliving.config;

import com.momentliving.interceptor.LoginInterceptor;
import com.momentliving.interceptor.UserContextInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * shop-service 拦截器配置：
 * 原有 /shop/**、/shop-type/** 全公开（网关白名单放行，无用户态）；
 * 🆕 评价体系（/review/**）需要登录 —— 网关已强制鉴权并透传 X-User-Id，
 * 本地两层拦截器还原用户上下文 + 兜底 401（防绕过网关直连）。
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private UserContextInterceptor userContextInterceptor;

    @Resource
    private LoginInterceptor loginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 第一层：从网关透传的 X-User-Id 头解析用户，塞入 ThreadLocal（无需登录也执行）
        registry.addInterceptor(userContextInterceptor)
                .addPathPatterns("/**")
                .order(0);

        // 第二层：校验登录态（UserHolder 为空则 401）
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // 店铺/类型查询：公开接口（网关白名单已放行，请求头里没有 X-User-Id）
                        "/shop/**",
                        "/shop-type/**",
                        // 接口文档
                        "/doc.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                )
                .order(1);

        // 🆕 店铺收藏（/shop/favorite/**）：/shop/** 整体公开的唯一例外，单独要求登录。
        //   上面的注册被 excludePathPatterns("/shop/**") 排除，这里把收藏路径再精确纳入
        //   （同一拦截器实例可注册多次，Spring 按注册逐条匹配）
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/shop/favorite/**")
                .order(2);
    }
}
