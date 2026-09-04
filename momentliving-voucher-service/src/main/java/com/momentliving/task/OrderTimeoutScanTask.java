package com.momentliving.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.momentliving.constant.OrderStatus;
import com.momentliving.config.PayProperties;
import com.momentliving.consumer.OrderCloseHandler;
import com.momentliving.entity.VoucherOrder;
import com.momentliving.mapper.VoucherOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时订单兜底扫描：每 60 秒扫一批「创建时间已超时且仍待支付」的订单执行关单。
 *
 * <p>与 MQ TTL 延迟关单互为双保险 —— MQ 消息确认失败、队列积压、服务重启等场景下，
 * 这里的扫描保证"未支付订单最终一定会被关闭并回补库存"，不会永久占库存。
 */
@Slf4j
@Component
public class OrderTimeoutScanTask {

    /** 单轮最多处理条数，避免高峰期一次扫描拖垮 DB */
    private static final int BATCH_LIMIT = 100;

    private final VoucherOrderMapper voucherOrderMapper;
    private final OrderCloseHandler orderCloseHandler;
    private final PayProperties payProperties;
    private final RedissonClient redissonClient;

    public OrderTimeoutScanTask(VoucherOrderMapper voucherOrderMapper,
                                OrderCloseHandler orderCloseHandler,
                                PayProperties payProperties,
                                RedissonClient redissonClient) {
        this.voucherOrderMapper = voucherOrderMapper;
        this.orderCloseHandler = orderCloseHandler;
        this.payProperties = payProperties;
        this.redissonClient = redissonClient;
    }

    @Scheduled(fixedDelay = 60_000)
    public void scanTimeoutOrders() {
        // 多实例部署互斥锁：同一时刻只有一台实例在跑（拿不到锁本轮直接放弃）
        RLock lock = redissonClient.getLock("lock:task:order-timeout-scan");
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 50, java.util.concurrent.TimeUnit.SECONDS);
            if (!locked) {
                return;
            }
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(payProperties.getOrderTimeoutMinutes());
            List<VoucherOrder> timeoutOrders = voucherOrderMapper.selectList(new LambdaQueryWrapper<VoucherOrder>()
                    .eq(VoucherOrder::getStatus, OrderStatus.PENDING_PAY)
                    .lt(VoucherOrder::getCreateTime, threshold)
                    .last("limit " + BATCH_LIMIT));
            if (timeoutOrders.isEmpty()) {
                return;
            }
            int closed = 0;
            for (VoucherOrder order : timeoutOrders) {
                if (orderCloseHandler.closeUnpaidOrder(order.getId())) {
                    closed++;
                }
            }
            log.info("超时订单扫描完成：候选 {} 笔，实际关单 {} 笔", timeoutOrders.size(), closed);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("超时订单扫描任务异常", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
