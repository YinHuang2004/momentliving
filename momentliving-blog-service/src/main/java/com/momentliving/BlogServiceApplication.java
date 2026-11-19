package com.momentliving;

import com.momentliving.api.client.UserClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/** 探店博客服务：笔记发布（买过才可发）、点赞/收藏/评论、关注与 Feed 流 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackageClasses = UserClient.class)
@MapperScan("com.momentliving.mapper")
public class BlogServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BlogServiceApplication.class, args);
    }
}
