package com.momentliving.task;

import com.momentliving.constant.RedisConstants;
import com.momentliving.mapper.ShopMapper;
import com.momentliving.service.ShopService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * 项目启动自动预热店铺逻辑过期缓存
 * 作用：给逻辑过期方案做热点数据预热，防止启动瞬间大量请求缓存未命中击穿数据库
 */
@Component
@Slf4j
public class ShopCachePreLoadTask implements ApplicationRunner {

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private ShopService shopService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 1. 查询数据库所有店铺ID
        List<Long> allShopIdList = shopMapper.selectAllIds();

        // 2. 循环逐个存入Redis，逻辑过期时间30分钟（单位：秒）
        long expireSeconds = RedisConstants.CACHE_SHOP_TTL * 60L;
        for (Long id : allShopIdList) {
            shopService.saveShop2Redis(id, expireSeconds);
        }
        // 打印日志，确认预热完成
        log.info("店铺缓存预热成功，共预热:{}条店铺",allShopIdList);

    }
}