package com.momentliving.utils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
/** 带逻辑过期时间的 Redis 缓存包装对象:缓存真实数据与过期时间,用于逻辑过期方案 */
public class RedisData <T>{
    private LocalDateTime expireTime;
    private T data;
}
