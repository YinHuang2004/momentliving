package com.momentliving;

import com.momentliving.api.client.UserClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 即时聊天服务：WebSocket 单聊/群聊 + 首条限制 + 博客卡片消息
 */
@EnableDiscoveryClient
@MapperScan("com.momentliving.mapper")
@EnableFeignClients(clients = {UserClient.class})
@SpringBootApplication
public class ChatServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatServiceApplication.class, args);
    }
}
