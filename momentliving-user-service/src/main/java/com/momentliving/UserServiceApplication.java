package com.momentliving;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 用户服务启动类
 * 职责：验证码登录（邮箱/手机）、双 Token 签发与刷新、密码管理、签到、每日积分、个人资料与足迹设置
 */
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.momentliving.mapper")   // ★ 扫描本服务的 Mapper（UserMapper / UserInfoMapper）
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
