package com.momentliving.service;

import com.momentliving.constant.RedisConstants;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

//Redis 中的秒杀预占记录；
@Service
public class SeckillReservationService {
    /** 补偿标记（NX + TTL）的过期时间：30 天，防止重复补偿 */
    private static final long COMPENSATION_MARKER_TTL_SECONDS = 30L * 24 * 60 * 60;
    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = script("lua/seckill.lua");
    private static final DefaultRedisScript<Long> COMPENSATE_SCRIPT = script("lua/seckill_compensate.lua");
    private static final DefaultRedisScript<Long> UPDATE_SCRIPT = script("lua/seckill_update_reservation.lua");

    private final StringRedisTemplate redisTemplate;

    public SeckillReservationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 执行 seckill.lua 五步原子预扣：判库存/判限购/扣库存/计数/写预占（PENDING）；正数为购买序号 slot，负数为拒绝码 */
    public long reserve(Long voucherId, Long userId, int limit, Long orderId) {
        Long result = redisTemplate.execute(
                RESERVE_SCRIPT,
                List.of(
                        RedisConstants.seckillStockKey(voucherId),
                        RedisConstants.seckillCountKey(voucherId),
                        RedisConstants.seckillReservationKey(voucherId)),
                userId.toString(), Integer.toString(limit), orderId.toString(),
                Long.toString(Instant.now().toEpochMilli()));
        if (result == null) {
            throw new IllegalStateException("秒杀预扣脚本没有返回结果");
        }
        return result;
    }

    /**
     * 原子、幂等回补。返回 false 表示此订单以前已经补偿过。
     */
    public boolean compensate(Long voucherId, Long userId, Long orderId) {
        Long result = redisTemplate.execute(
                COMPENSATE_SCRIPT,
                List.of(
                        RedisConstants.seckillStockKey(voucherId),
                        RedisConstants.seckillCountKey(voucherId),
                        RedisConstants.seckillReservationKey(voucherId),
                        RedisConstants.seckillCompensatedKey(voucherId, orderId)),
                userId.toString(), orderId.toString(), Long.toString(COMPENSATION_MARKER_TTL_SECONDS));
        if (result == null) {
            throw new IllegalStateException("秒杀补偿脚本没有返回结果");
        }
        return result == 1L;
    }

    /** 原子更新预占的状态/尝试次数（MQ 发送后回写 CONFIRMED 或保持 PENDING）；记录已被删除时返回 false */
    public boolean updateAttempt(Long voucherId, Long orderId, Reservation reservation,
                                 String state, int attempts) {
        Reservation updated = new Reservation(
                reservation.userId(), reservation.purchaseSlot(), Instant.now().toEpochMilli(), state, attempts);
        Long result = redisTemplate.execute(
                UPDATE_SCRIPT,
                List.of(RedisConstants.seckillReservationKey(voucherId)),
                orderId.toString(), updated.serialize());
        return result != null && result == 1L;
    }

    /** 订单已落库，删除该单的预占记录（终态） */
    public void finish(Long voucherId, Long orderId) {
        redisTemplate.opsForHash().delete(
                RedisConstants.seckillReservationKey(voucherId), orderId.toString());
    }

    public Map<Object, Object> reservations(Long voucherId) {
        return redisTemplate.opsForHash().entries(RedisConstants.seckillReservationKey(voucherId));
    }

    /** 解析预占串 userId|slot|ts|state|attempts，字段数或类型非法时返回 empty */
    public Optional<Reservation> parse(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        String[] fields = value.toString().split("\\|", -1);
        if (fields.length != 5) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Reservation(
                    Long.parseLong(fields[0]),
                    Integer.parseInt(fields[1]),
                    Long.parseLong(fields[2]),
                    fields[3],
                    Integer.parseInt(fields[4])));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    /** 预占记录值对象：与 Lua 写入/对账回写的串格式一一对应 */
    public record Reservation(Long userId, Integer purchaseSlot, long lastAttemptMillis,
                              String state, int attempts) {
        public String serialize() {
            return userId + "|" + purchaseSlot + "|" + lastAttemptMillis + "|" + state + "|" + attempts;
        }
    }

    private static DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }
}
