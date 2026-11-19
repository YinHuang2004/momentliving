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
import com.momentliving.service.SeckillReservationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private RedissonClient redissonClient;
    @Resource
    private SeckillReservationService reservationService;

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
            // 2. 只有秒杀订单才回补秒杀库存和 Redis 限购资格。
            if (order.getPurchaseSlot() != null) {
                seckillVoucherMapper.restoreStock(order.getVoucherId());
                registerRedisCompensationAfterCommit(order);
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

    /** 事务提交后再回补 Redis 预占；无事务上下文则立即执行，失败留给兜底任务重试 */
    private void registerRedisCompensationAfterCommit(VoucherOrder order) {
        Runnable compensation = () -> {
            try {
                reservationService.compensate(order.getVoucherId(), order.getUserId(), order.getId());
            } catch (Exception e) {
                // ClosedOrderCompensationTask 会按 MySQL 已关闭订单继续兜底。
                log.error("关单已提交，Redis 回补失败，orderId={}", order.getId(), e);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            compensation.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                compensation.run();
            }
        });
    }
}
