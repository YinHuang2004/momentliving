package com.momentliving.service;

import com.momentliving.constant.RedisConstants;
import com.momentliving.entity.Shop;
import com.momentliving.exception.BadRequestException;
import com.momentliving.mapper.ShopMapper;
import com.momentliving.service.impl.ShopServiceImpl;
import com.momentliving.vo.ShopVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ShopServiceImpl.queryById 缓存三分支的纯单元测试。
 *
 * 特点：不起 Spring 容器、不连 Redis/MySQL/ES，全靠 Mockito 打桩，
 * 毫秒级跑完，mvn test / mvn package 时随时可跑（不需要任何基础设施）。
 *
 * 覆盖的三条主路径（对应缓存方案的核心设计）：
 *   1. 布隆过滤器拦截：id 一定不存在 → 直接抛异常，不碰 Redis 和 DB
 *   2. 缓存未命中 + DB 有数据 → 查 DB 重建缓存并返回
 *   3. 缓存未命中 + DB 无数据 → 写空值缓存（防穿透）并抛异常
 */
@ExtendWith(MockitoExtension.class)          // Mockito 5 + JUnit 5 集成：自动初始化 @Mock/@InjectMocks
class ShopServiceImplTest {

    @Mock
    private ShopBloomFilterService shopBloomFilterService;   // 被打桩的外部依赖：布隆过滤器

    @Mock
    private StringRedisTemplate stringRedisTemplate;         // 被打桩的外部依赖：Redis

    @Mock
    private ValueOperations<String, String> valueOps;        // opsForValue() 的返回值，单独 mock

    @Mock
    private ShopMapper shopMapper;                           // 被打桩的外部依赖：MySQL

    @InjectMocks
    private ShopServiceImpl service;                         // 把上面的 mock 注入被测对象（支持 @Resource 字段注入）

    private static final Long SHOP_ID = 1L;
    private static final String CACHE_KEY = RedisConstants.CACHE_SHOP_KEY + SHOP_ID;

    @Test
    @DisplayName("布隆过滤器判定不存在：直接抛异常，不查 Redis、不查 DB（防穿透第一道防线）")
    void queryById_blockedByBloomFilter() {
        // given：布隆说这个 id 一定不存在
        when(shopBloomFilterService.contains(SHOP_ID)).thenReturn(false);

        // when + then：抛"店铺不存在"
        assertThrows(BadRequestException.class, () -> service.queryById(SHOP_ID));

        // then：验证缓存和数据库完全没被碰——这就是布隆拦截的意义
        verifyNoInteractions(stringRedisTemplate);
        verifyNoInteractions(shopMapper);
    }

    @Test
    @DisplayName("缓存未命中 + DB 有数据：查库重建缓存并返回（同步重建路径）")
    void queryById_cacheMiss_rebuildFromDb() {
        Shop shop = new Shop();
        shop.setId(SHOP_ID);
        shop.setName("测试奶茶店");

        // given：布隆放行 → Redis 两次 get 都为空 → 抢到互斥锁 → DB 查到数据
        when(shopBloomFilterService.contains(SHOP_ID)).thenReturn(true);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn(null);                       // queryById 与 buildCacheAndReturn 各查一次
        when(valueOps.setIfAbsent(eq(RedisConstants.LOCK_SHOP_KEY + SHOP_ID),
                anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(shopMapper.selectById(SHOP_ID)).thenReturn(shop);                // buildCacheAndReturn 与 saveShop2Redis 各查一次

        // when
        ShopVO vo = service.queryById(SHOP_ID);

        // then：返回了 DB 数据；且缓存被回写（带物理 TTL）
        assertNotNull(vo);
        assertEquals("测试奶茶店", vo.getName());
        verify(valueOps).set(eq(CACHE_KEY), contains("测试奶茶店"),
                anyLong(), eq(TimeUnit.SECONDS));
        // 锁必须在 finally 里被释放
        verify(stringRedisTemplate).delete(RedisConstants.LOCK_SHOP_KEY + SHOP_ID);
    }

    @Test
    @DisplayName("缓存未命中 + DB 也没有：写空值缓存防穿透，并抛异常")
    void queryById_dbMiss_cacheEmptyValue() {
        // given：布隆放行（说明以前存在/误报）但 DB 里真没有
        when(shopBloomFilterService.contains(SHOP_ID)).thenReturn(true);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn(null);
        when(valueOps.setIfAbsent(eq(RedisConstants.LOCK_SHOP_KEY + SHOP_ID),
                anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(shopMapper.selectById(SHOP_ID)).thenReturn(null);

        // when + then
        assertThrows(BadRequestException.class, () -> service.queryById(SHOP_ID));

        // then：写入了 5 分钟空值缓存——下一次同样的请求会被空值缓存挡住，不再打 DB
        verify(valueOps).set(eq(CACHE_KEY), eq(""), eq(5L), eq(TimeUnit.MINUTES));
    }
}
