package com.momentliving;

import com.momentliving.api.client.UserClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackageClasses = UserClient.class)   // ★ 个人主页足迹：UserClient 查足迹可见性设置
@MapperScan("com.momentliving.mapper")   // ★ 扫描本服务的 Mapper（Voucher / SeckillVoucher / VoucherOrder / Payment / VoucherVerify）
@EnableAspectJAutoProxy(exposeProxy = true)
@EnableScheduling                 // ★ 超时订单兜底扫描任务（task/OrderTimeoutScanTask）
public class VoucherServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(VoucherServiceApplication.class, args);
    }
}
