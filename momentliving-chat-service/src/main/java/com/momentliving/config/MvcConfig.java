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
        // 第一层：从网关透传的 X-User-Id 头解析用户，塞入 ThreadLocal（无需登录也执行）
        registry.addInterceptor(userContextInterceptor)
                .addPathPatterns("/**")
                .order(0);

        // 第二层：校验登录态（UserHolder 为空则 401）
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // 接口文档
                        "/doc.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                )
                .order(1);
    }
}
