package com.momentliving.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.momentliving.constant.RedisConstants;
import com.momentliving.entity.SeckillVoucher;
import com.momentliving.entity.VoucherOrder;
import com.momentliving.mapper.SeckillVoucherMapper;
import com.momentliving.mapper.VoucherOrderMapper;
import com.momentliving.service.SeckillMqPublisher;
import com.momentliving.service.SeckillReservationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis 预占恢复任务：查库确认、重投 MQ，并在 key 丢失时按 MySQL 真源重建。
 */
@Slf4j
@Component
public class SeckillReconciliationTask {
    /** PENDING 状态（MQ 发送未确认）的重投节流间隔 */
    private static final long PENDING_RETRY_MILLIS = 10_000;
    /** CONFIRMED 状态（消息已确认但订单久未落库）的重投节流间隔 */
    private static final long CONFIRMED_RETRY_MILLIS = 60_000;

    private final SeckillVoucherMapper voucherMapper;
    private final VoucherOrderMapper orderMapper;
    private final SeckillReservationService reservationService;
    private final SeckillMqPublisher mqPublisher;
    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    public SeckillReconciliationTask(SeckillVoucherMapper voucherMapper,
                                     VoucherOrderMapper orderMapper,
                                     SeckillReservationService reservationService,
                                     SeckillMqPublisher mqPublisher,
                                     StringRedisTemplate redisTemplate,
                                     JdbcTemplate jdbcTemplate) {
        this.voucherMapper = voucherMapper;
        this.orderMapper = orderMapper;
        this.reservationService = reservationService;
        this.mqPublisher = mqPublisher;
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 每 10s 扫描结束时间在 1 天内的秒杀券，逐个对账预占与库存/限购 key */
    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    public void recoverAndReconcile() {
        List<SeckillVoucher> vouchers = voucherMapper.selectList(new LambdaQueryWrapper<SeckillVoucher>()
                .gt(SeckillVoucher::getEndTime, LocalDateTime.now().minusDays(1)));
        for (SeckillVoucher voucher : vouchers) {
            try {
                reconcileVoucher(voucher);
            } catch (Exception e) {
                log.error("秒杀库存对账失败，voucherId={}", voucher.getVoucherId(), e);
            }
        }
    }

    /** 逐条核对预占：订单已落库则删预占；未落库按 PENDING 10s / CONFIRMED 60s 节流重投 MQ */
    private void reconcileVoucher(SeckillVoucher voucher) {
        Long voucherId = voucher.getVoucherId();
        Map<Object, Object> reservations = reservationService.reservations(voucherId);
        Map<String, Long> pendingByUser = new HashMap<>();
        int pending = 0;
        long now = System.currentTimeMillis();

        for (Map.Entry<Object, Object> entry : reservations.entrySet()) {
            Long orderId;
            try {
                orderId = Long.valueOf(entry.getKey().toString());
            } catch (NumberFormatException e) {
                log.error("非法秒杀预占 orderId，voucherId={}, value={}", voucherId, entry.getKey());
                continue;
            }
            SeckillReservationService.Reservation reservation = reservationService.parse(entry.getValue())
                    .orElse(null);
            if (reservation == null) {
                log.error("非法秒杀预占记录，voucherId={}, orderId={}, value={}",
                        voucherId, orderId, entry.getValue());
                continue;
            }

            VoucherOrder persisted = orderMapper.selectById(orderId);
            if (persisted != null) {
                reservationService.finish(voucherId, orderId);
                continue;
            }

            pending++;
            pendingByUser.merge(reservation.userId().toString(), 1L, Long::sum);
            long retryAfter = "CONFIRMED".equals(reservation.state())
                    ? CONFIRMED_RETRY_MILLIS : PENDING_RETRY_MILLIS;
            if (now - reservation.lastAttemptMillis() < retryAfter) {
                continue;
            }

            VoucherOrder order = VoucherOrder.builder()
                    .id(orderId)
                    .userId(reservation.userId())
                    .voucherId(voucherId)
                    .purchaseSlot(reservation.purchaseSlot())
                    .payType(0)
                    .status(0)
                    .createTime(LocalDateTime.now())
                    .build();
            boolean confirmed = mqPublisher.publish(order);
            int attempts = reservation.attempts() == Integer.MAX_VALUE
                    ? Integer.MAX_VALUE : reservation.attempts() + 1;
            reservationService.updateAttempt(voucherId, orderId, reservation,
                    confirmed ? "CONFIRMED" : "PENDING", attempts);
            log.warn("重投秒杀消息，orderId={}, confirmed={}, attempts={}", orderId, confirmed, attempts);
        }

        rebuildOrCheckStock(voucher, pending);
        rebuildCountIfMissing(voucherId, pendingByUser);
    }

    /** 库存 key 丢失时按「DB 库存 - 在途预占数」重建，存在但不一致则告警不修正 */
    private void rebuildOrCheckStock(SeckillVoucher voucher, int pending) {
        Long voucherId = voucher.getVoucherId();
        int expectedStock = Math.max(0, voucher.getStock() - pending);
        String stockKey = RedisConstants.seckillStockKey(voucherId);
        String redisStock = redisTemplate.opsForValue().get(stockKey);
        if (redisStock == null) {
            redisTemplate.opsForValue().setIfAbsent(stockKey, Integer.toString(expectedStock));
            log.warn("重建缺失秒杀库存 key，voucherId={}, stock={}", voucherId, expectedStock);
        } else if (Integer.parseInt(redisStock) != expectedStock) {
            log.error("秒杀库存对账不一致，voucherId={}, redisStock={}, expectedStock={}, pending={}",
                    voucherId, redisStock, expectedStock, pending);
        }
    }

    /** 限购计数 key 丢失时按 MySQL 真源（订单表按用户计数 + 在途预占）重建 */
    private void rebuildCountIfMissing(Long voucherId, Map<String, Long> pendingByUser) {
        String countKey = RedisConstants.seckillCountKey(voucherId);
        if (!Boolean.FALSE.equals(redisTemplate.hasKey(countKey))) {
            return;
        }
        Map<String, Long> counts = new HashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select user_id, count(*) cnt from voucher_order " +
                        "where voucher_id=? and purchase_slot is not null and status<>4 group by user_id",
                voucherId);
        rows.forEach(row -> counts.put(row.get("user_id").toString(),
                ((Number) row.get("cnt")).longValue()));
        pendingByUser.forEach((user, count) -> counts.merge(user, count, Long::sum));
        if (counts.isEmpty()) {
            // 没有任何购买/预占记录：限购 key 由秒杀 Lua 脚本按需创建，缺失是正常空状态，
            // 不重建也不告警，否则本任务会每 10 秒重复打一次 WARN
            return;
        }
        redisTemplate.opsForHash().putAll(countKey, counts);
        log.warn("重建缺失秒杀限购 key，voucherId={}, users={}", voucherId, counts.size());
    }
}
