package com.momentliving;

import com.momentliving.api.client.UserClient;
import com.momentliving.api.client.VoucherClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 商家端服务启动类
 * 职责：商家登录（merchant 表账号体系）、到店核销、工作台统计、入驻申请
 * 与 admin-service（平台管理员：入驻审核等运营职能）彻底分服务部署
 */
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.momentliving.mapper")
@EnableFeignClients(basePackageClasses = {VoucherClient.class, UserClient.class})
public class MerchantServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MerchantServiceApplication.class, args);
    }
}
