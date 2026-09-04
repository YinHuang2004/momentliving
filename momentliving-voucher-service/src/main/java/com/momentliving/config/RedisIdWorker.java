package com.momentliving.config;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 基于 Redis 的分布式 ID 生成器
 * 时间戳 + 序列号方案
 */
@Component
public class RedisIdWorker {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 开始时间戳 (2022-01-01 00:00:00)
     */
    private static final long BEGIN_TIMESTAMP = 1640995200;
    /**
     * 序列化位数
     */
    private static final int COUNT_BITS = 32;
    private static final String ID_PREFIX = "icr:";

    /**
     * 生成分布式 ID
     *
     * @param keyPrefix 业务前缀
     * @return 全局唯一 ID
     */
    public long nextId(String keyPrefix) {
        // 1. 生成时间戳（31位最多69年，所以不要直接使用当前时间作为时间戳，使用时间差来作为时间戳）
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        // 2. 生成序列号
        //获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long count = stringRedisTemplate.opsForValue().increment(ID_PREFIX + keyPrefix + ":" + date);
        // 3. 拼接并返回（符号位默认为0，时间戳31+序列号32，左移32然后低位刷为count，有0则0，有1则1，那么可以使用|来刷，也可以想加（速度慢而已）
        return timestamp << COUNT_BITS | count;
    }
}
