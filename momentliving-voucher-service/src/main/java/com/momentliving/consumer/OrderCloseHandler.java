package com.momentliving.consumer;

import com.momentliving.constant.OrderStatus;
import com.momentliving.constant.RedisConstants;
import com.momentliving.entity.VoucherOrder;
import com.momentliving.mapper.PaymentMapper;
import com.momentliving.mapper.SeckillVoucherMapper;
import com.momentliving.mapper.VoucherOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * 关单公共逻辑：订单落库成功后已发送 TTL 延迟消息，
 * 本处理器在消息到期（或定时扫描兜底）时执行"待支付单自动取消 + 库存回补"。
 */
@Slf4j
@Component
public class OrderCloseHandler {

    @Resource
    private VoucherOrderMapper voucherOrderMapper;
    @Resource
    private SeckillVoucherMapper seckillVoucherMapper;
    @Resource
    private PaymentMapper paymentMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    /**
     * 关闭一笔待支付订单并回补库存。
     *
     * @return true=本次执行了关单；false=无需处理（不存在/非待支付/锁竞争）
     */
    @Transactional
    public boolean closeUnpaidOrder(Long orderId) {
        // 订单级分布式锁：与定时扫描兜底互斥，防止同一订单并发双回补
        RLock lock = redissonClient.getLock(RedisConstants.LOCK_ORDER_KEY + orderId);
        boolean locked = false;
        try {
            locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("关单锁等待被中断，orderId={}", orderId);
            return false;
        }
        try {
            if (!locked) {
                log.info("关单锁竞争失败，交由下轮兜底扫描处理，orderId={}", orderId);
                return false;
            }
            VoucherOrder order = voucherOrderMapper.selectById(orderId);
            if (order == null || order.getStatus() == null || order.getStatus() != OrderStatus.PENDING_PAY) {
                return false; // 已支付/已关闭等，无需处理
            }
            // 1. 状态 CAS：待支付(0) → 已关闭(4)
            if (voucherOrderMapper.casClose(orderId) <= 0) {
                return false; // 并发场景已被别处流转
            }
            // 2. 回补库存：DB 先加回（下单消费时扣过），再同步 Redis 预扣量
            seckillVoucherMapper.restoreStock(order.getVoucherId());
            try {
                stringRedisTemplate.opsForValue().increment(RedisConstants.SECKILL_STOCK_KEY + order.getVoucherId());
                stringRedisTemplate.opsForHash().increment(RedisConstants.SECKILL_COUNT_KEY + order.getVoucherId(),
                        order.getUserId().toString(), -1);
            } catch (Exception e) {
                // DB 是库存真源，Redis 补偿失败留痕告警即可
                log.error("关单 Redis 库存回补失败，orderId={}, voucherId={}", orderId, order.getVoucherId(), e);
            }
            // 3. 若用户点过支付但没付完，把待支付流水置为支付失败，保持流水与订单一致
            paymentMapper.casMarkFailed(orderId);
            log.info("超时关单完成，orderId={}, voucherId={}", orderId, order.getVoucherId());
            return true;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
