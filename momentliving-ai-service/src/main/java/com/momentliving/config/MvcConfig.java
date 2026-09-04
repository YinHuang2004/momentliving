package com.momentliving.config;

import com.momentliving.interceptor.LoginInterceptor;
import com.momentliving.interceptor.UserContextInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private UserContextInterceptor userContextInterceptor;

    @Resource
    private LoginInterceptor loginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 第一层：还原身份（网关透传 X-User-Id / X-Merchant-Id / X-Admin-Id，无需登录也执行）
        registry.addInterceptor(userContextInterceptor)
                .addPathPatterns("/**")
                .order(0);

        // 第二层：校验登录态（C 端对话/会话/反馈需登录；管理端知识库由 Controller 校验 AdminHolder）
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // 服务间内部接口（预留：其他服务经 Feign 直连调用 AI）
                        "/ai/feign/**",
                        // 接口文档
                        "/doc.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                )
                .order(1);
    }
}
