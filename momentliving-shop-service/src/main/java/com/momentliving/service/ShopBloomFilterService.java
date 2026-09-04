package com.momentliving.service;

import com.momentliving.constant.RedisConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 店铺布隆过滤器（双布隆方案）：
 *
 * <p>两个桶 {@code bloom:shop:id:0 / :1} 交替使用，Redis 里一个 active 指针
 * （{@link RedisConstants#BLOOM_FILTER_SHOP_ACTIVE_KEY}）标记当前对外服务的是哪个桶。
 *
 * <ul>
 *   <li><b>读路径</b>（queryById）：只查 active 桶 → false 一定不存在，直接拦截</li>
 *   <li><b>写路径</b>（新增店铺）：双写两个桶 → 重建期间新增的 ID 不会丢</li>
 *   <li><b>重建</b>（每日定时）：只在 backup 桶上 delete + 全量 add，active 桶全程在线无感；
 *       全部完成后单次 set 切换指针（原子），旧桶保留不删（避免切换瞬间读 miss）</li>
 * </ul>
 *
 * 解决了单布隆的三个缺陷：业务真空窗口、重建期间新增 ID 丢失、多实例并发重建（配合分布式锁）。
 */
@Slf4j
@Service
public class ShopBloomFilterService {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 布隆预估容量 */
    private static final long EXPECTED_SIZE = 100_000L;
    /** 误判率 */
    private static final double FPP = 0.01;
    /** 桶数量（双布隆固定 2） */
    private static final int BLOOM_COUNT = 2;
    /** 批量 add 每批数量，防止单次 Redis 命令过大 */
    private static final int BATCH_SIZE = 500;

    /**
     * 读路径：查 active 桶。返回 false 表示 ID 一定不存在。
     */
    public boolean contains(Long id) {
        return getBloom(getActiveIndex()).contains(id);
    }

    /**
     * 写路径：新增店铺后双写两个桶。
     * 双写保证：每日重建期间新增的店铺 ID 也会进入 backup 桶，切换后不会丢。
     */
    public void add(Long id) {
        for (int i = 0; i < BLOOM_COUNT; i++) {
            getBloom(i).add(id);
        }
    }

    /**
     * 启动初始化：把 DB 全量店铺 ID 灌入 active 桶（flag 不存在时默认 0）。
     * 只初始化当前桶，不碰 backup。
     */
    public void initActive(List<Long> shopIds) {
        RBloomFilter<Long> active = getBloom(getActiveIndex());
        for (int i = 0; i < shopIds.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, shopIds.size());
            active.add(shopIds.subList(i, end));
        }
        log.info("布隆初始化完成：active={}, 数量={}", getActiveIndex(), shopIds.size());
    }

    /**
     * 重建 backup 桶并原子切换（核心方法，无业务真空窗口）：
     * <ol>
     *   <li>在 backup 桶上 delete + 全量 add（active 桶全程在线，线上查询无感）</li>
     *   <li>全部 add 完成后，把 active 指针切到 backup（单次 Redis set，原子）</li>
     *   <li>旧桶保留不删（避免切换瞬间并发读 miss；下次重建会覆盖它）</li>
     * </ol>
     */
    public void rebuild(List<Long> shopIds) {
        int active = getActiveIndex();
        int backup = 1 - active;

        RBloomFilter<Long> backupBloom = getBloom(backup);
        backupBloom.delete();
        backupBloom.tryInit(EXPECTED_SIZE, FPP);

        for (int i = 0; i < shopIds.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, shopIds.size());
            backupBloom.add(shopIds.subList(i, end));
        }

        // 原子切换：单次 set，切换前后两个桶都可用
        stringRedisTemplate.opsForValue()
                .set(RedisConstants.BLOOM_FILTER_SHOP_ACTIVE_KEY, String.valueOf(backup));
        log.info("双布隆重建完成并切换：active {} -> {}, 数量={}", active, backup, shopIds.size());
    }

    /**
     * 当前对外服务的桶下标（默认 0）。
     */
    private int getActiveIndex() {
        String flag = stringRedisTemplate.opsForValue()
                .get(RedisConstants.BLOOM_FILTER_SHOP_ACTIVE_KEY);
        return "1".equals(flag) ? 1 : 0;
    }

    private RBloomFilter<Long> getBloom(int idx) {
        RBloomFilter<Long> bloom = redissonClient.getBloomFilter(
                RedisConstants.BLOOM_FILTER_SHOP_KEY_PREFIX + idx);
        // tryInit 只在首次创建时生效；已存在的桶参数不会被覆盖
        bloom.tryInit(EXPECTED_SIZE, FPP);
        return bloom;
    }
}
