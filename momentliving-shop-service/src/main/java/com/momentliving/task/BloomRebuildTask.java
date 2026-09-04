package com.momentliving.task;

import com.momentliving.mapper.ShopMapper;
import com.momentliving.service.ShopBloomFilterService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class BloomRebuildTask {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private ShopBloomFilterService shopBloomFilterService;

    private static final String REBUILD_LOCK_KEY = "bloom:shop:rebuild:lock";

    // 每天凌晨两点执行重建布隆过滤器任务。
    // 双布隆方案：重建只在 backup 桶上进行，active 桶全程对外服务，无业务真空窗口；
    // 重建完成后原子切换 active 指针，旧桶保留，下次重建覆盖。
    // 多实例并发：分布式锁保证同一时间只有一台实例执行重建。
    @Scheduled(cron = "0 0 2 * * ?")
    public void rebuildShopBloomFilter() {
        RLock lock = redissonClient.getLock(REBUILD_LOCK_KEY);
        boolean tryLock = false;
        try {
            // 分布式锁，防止多实例同时重建，锁超时10分钟
            tryLock = lock.tryLock(0, 10, TimeUnit.MINUTES);
            if (!tryLock) {
                log.info("重建店铺布隆过滤器：其他实例正在执行，本实例放弃执行");
                return;
            }
            log.info("开始重建店铺布隆过滤器（双布隆）");

            // 查询 DB 全量店铺 ID（重建的权威数据源）
            List<Long> shopIds = shopMapper.selectAllIds();
            log.info("查询到有效店铺数量：{}", shopIds.size());

            // 在 backup 桶上重建并原子切换（active 桶全程在线，线上无感）
            shopBloomFilterService.rebuild(shopIds);
        } catch (Exception e) {
            log.error("重建店铺布隆过滤器异常", e);
            // 双布隆下即使异常，active 桶仍在服务，不影响线上；下个周期会重试
        } finally {
            if (tryLock) {
                lock.unlock();
            }
        }
    }
}
