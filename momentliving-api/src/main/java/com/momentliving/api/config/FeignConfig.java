package com.momentliving.api.config;

import com.momentliving.api.interceptor.FeignIdentityInterceptor;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

/**
 * Feign 客户端专属配置：仅通过 {@code @FeignClient(configuration = FeignConfig.class)} 加载。
 * ⚠️ 刻意不加 @Configuration —— 否则会被 @SpringBootApplication 组件扫描成全局配置，
 * 导致 RequestInterceptor 被应用到所有 Feign 客户端，并与客户端专属配置重复注册。
 */
public class FeignConfig {
    @Bean
    public RequestInterceptor identityInterceptor() {
        return new FeignIdentityInterceptor();
    }
}
