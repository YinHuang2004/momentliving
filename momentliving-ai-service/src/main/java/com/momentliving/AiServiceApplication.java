package com.momentliving;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * AI 助手服务启动类（端口 8089）
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.momentliving.api.client")   // 复用 momentliving-api 中的 Feign 客户端
@MapperScan("com.momentliving.mapper")   // ★ 扫描本服务的 Mapper（ai_* 表）
public class AiServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
