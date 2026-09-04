package com.momentliving.config;

import com.momentliving.interceptor.MerchantAuthInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 商家端拦截器注册：/merchant/** 统一走 MerchantAuthInterceptor（login:merchant:{id}）
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private MerchantAuthInterceptor merchantAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(merchantAuthInterceptor)
                .addPathPatterns("/merchant/**")
                .excludePathPatterns(
                        "/merchant/login",          // 商家登录接口放行
                        "/merchant/apply",          // 商家入驻申请：公开（未登录提交，审核后才生成账号）
                        "/doc.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                );
    }
}
