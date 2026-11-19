package com.momentliving.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AI 服务启动类（Spring AI 1.1 + Spring AI Alibaba 1.1.2）。
 *
 * <p>⚠️ 默认只扫描 {@code com.momentliving.ai} 包——刻意避开 momentliving-common 里的
 * MybatisPlusConfig 等配置类：本服务只用 JdbcTemplate 直连 PG，不引入 MyBatis-Plus，
 * 避免把不适配 Boot 3.5 的自动配置扫进来。
 */
@EnableAsync
@ConfigurationPropertiesScan
@SpringBootApplication
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
