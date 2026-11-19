package com.momentliving.config;

import com.momentliving.interceptor.LoginInterceptor;
import com.momentliving.interceptor.UserContextInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** MVC 配置：注册两级拦截器（用户上下文解析 + 登录校验） */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private UserContextInterceptor userContextInterceptor;

    @Resource
    private LoginInterceptor loginInterceptor;

    /** 两级拦截器：order 0 解析用户上下文，order 1 登录校验（热榜/接口文档放行） */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userContextInterceptor)
                .addPathPatterns("/**")
                .order(0);

        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/blog/hot",
                        "/doc.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                )
                .order(1);
    }
}
