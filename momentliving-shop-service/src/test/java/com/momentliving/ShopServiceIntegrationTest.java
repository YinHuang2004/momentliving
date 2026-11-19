package com.momentliving;

import com.momentliving.service.ShopService;
import com.momentliving.vo.ShopVO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 集成测试（需要完整基础设施：Nacos + Redis + MySQL + ES 全部在线）。
 *
 * ⚠️ 为什么默认 @Disabled：
 *   1. @SpringBootTest 会启动完整 Spring 容器（连 Nacos 配置中心、注册中心、Redis、MySQL、ES、Redisson），
 *      任何一个没启动，容器起不来，测试直接失败；
 *   2. mvn package 默认会执行 mvn test——如果集成测试没被禁用，打包就要求"必须先启动全套环境"，
 *      CI/打包体验很差。所以约定：单元测试（Mockito）保持常开，集成测试手动开启。
 *
 * 使用方式：把 @Disabled 注释掉 → 启动 Nacos/Redis/MySQL/ES → IDEA 里右键 Run 即可。
 */
@SpringBootTest
@Disabled("需要本地启动 Nacos/Redis/MySQL/ES 全套环境后手动开启")
class ShopServiceIntegrationTest {

    @Resource
    private ShopService shopService;

    @Test
    @DisplayName("容器能正常启动（装配检查：Mapper/Redis/Redisson/Feign 全部就绪）")
    void contextLoads() {
        // 能跑到这里，说明整个容器装配没有问题——集成测试的"冒烟测试"
    }

    @Test
    @DisplayName("queryById 全链路：布隆 → 缓存 → DB 真实走通")
    void queryById_realChain() {
        // 前提：数据库里有 id=1 的店铺；第一次调用走 DB 重建缓存，第二次走缓存命中
        ShopVO first = shopService.queryById(1L);
        System.out.println("第一次查询（应走 DB 重建）：" + first);

        ShopVO second = shopService.queryById(1L);
        System.out.println("第二次查询（应走缓存命中）：" + second);

        // 两次结果一致（同一店铺数据）
        org.junit.jupiter.api.Assertions.assertEquals(first.getName(), second.getName());
    }
}
