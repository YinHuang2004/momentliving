package com.momentliving.api.config;

import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;
import org.springframework.context.annotation.Bean;

/**
 * FileClient 专属配置：注册 multipart/form-data 编码器。
 * ⚠️ 刻意不加 @Configuration —— 否则会被 @SpringBootApplication 组件扫描成全局配置，
 * 导致 SpringFormEncoder 被应用到所有 Feign 客户端（影响普通 JSON 请求）。
 */
public class FileClientConfig {

    @Bean
    public Encoder multipartEncoder() {
        return new SpringFormEncoder();
    }
}
