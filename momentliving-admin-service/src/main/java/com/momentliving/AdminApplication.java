package com.momentliving;

import com.momentliving.api.client.ShopClient;
import com.momentliving.api.client.UserClient;
import com.momentliving.api.client.VoucherClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 管理端服务启动类（平台管理员：入驻审核等运营职能）
 * 商家端已拆分至 merchant-service（/merchant/**，登录/核销/工作台/入驻申请）
 */
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.momentliving.mapper")
@EnableFeignClients(basePackageClasses = {ShopClient.class, UserClient.class, VoucherClient.class})
public class AdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
