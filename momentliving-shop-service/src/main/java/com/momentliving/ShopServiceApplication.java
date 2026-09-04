package com.momentliving;

import com.momentliving.api.client.VoucherClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.momentliving.mapper")       // ★ 扫描本服务的 Mapper（ShopMapper / ShopTypeMapper / ReviewMapper）
@EnableScheduling                    // ★ 开启定时任务：BloomRebuildTask 每天凌晨重建布隆过滤器
@EnableFeignClients(basePackageClasses = VoucherClient.class)  // ★ 评价：UserClient 查作者 / VoucherClient 校验订单核销
public class ShopServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShopServiceApplication.class, args);
    }
}
