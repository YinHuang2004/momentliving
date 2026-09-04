package com.momentliving.config;

import com.momentliving.interceptor.AdminAuthInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 管理端拦截器注册：/admin/** 走 AdminAuthInterceptor（login:admin:{id}，平台管理员）
 * 商家端已拆分至 merchant-service（/merchant/** 由其自身 MvcConfig 注册拦截器）
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private AdminAuthInterceptor adminAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns(
                        "/admin/login",             // 管理员登录接口放行
                        "/doc.html",                // 接口文档
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                );
    }
}
