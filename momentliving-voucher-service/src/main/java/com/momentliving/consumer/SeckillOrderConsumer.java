package com.momentliving.consumer;

import com.momentliving.config.RabbitMQConfig;
import com.momentliving.constant.RedisConstants;
import com.momentliving.entity.VoucherOrder;
import com.momentliving.service.VoucherOrderService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SeckillOrderConsumer {

    @Resource
    private VoucherOrderService voucherOrderService;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 监听秒杀订单队列
     * @param voucherOrder 订单消息（Jackson 自动反序列化为对象）
     * @param message      RabbitMQ 原生消息对象（用于拿 deliveryTag）
     * @param channel      信道（用于手动 ACK / NACK）
     */
    @RabbitListener(queues = RabbitMQConfig.SECKILL_ORDER_QUEUE, concurrency = "3")
    public void handleSeckillOrder(VoucherOrder voucherOrder, Message message, Channel channel) {
        Long userId = voucherOrder.getUserId();
        Long orderId = voucherOrder.getId();
        // deliveryTag：消息的唯一编号，ACK 时必须传
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            // ========== 1. 获取分布式锁（同一用户串行处理，防止重复消费）==========
            RLock lock = redissonClient.getLock(RedisConstants.LOCK_ORDER_KEY + userId);
            // 尝试获取锁：最多等 5 秒，拿到后 30 秒自动释放
            boolean isLock = lock.tryLock(5, 30, TimeUnit.SECONDS);
            if (!isLock) {
                log.warn("获取分布式锁失败，稍后重试，userId={}", userId);
                // 获取不到锁，NACK 并重新入队，等下次消费
                channel.basicNack(deliveryTag, false, true);
                return;
            }

            try {
                // ========== 2. 执行业务：扣库存 + 创建订单 ==========
                // 注意：不能在这里用 AopContext.currentProxy()——MQ 消费者线程没有 AOP 调用上下文，
                // 直接调用注入的 voucherOrderService（它就是 Spring 代理 Bean），@Transactional 正常生效
                voucherOrderService.createVoucherOrder(voucherOrder);

                // ========== 3. 发送延迟关单消息（TTL 到期死信到 order.close.queue）==========
                // 发送失败不回滚订单：订单已落库，兜底有每分钟的 OrderTimeoutScanTask 会补偿关单
                try {
                    rabbitTemplate.convertAndSend(
                            RabbitMQConfig.ORDER_CLOSE_DELAY_EXCHANGE,
                            RabbitMQConfig.ORDER_CLOSE_DELAY_ROUTING_KEY,
                            String.valueOf(orderId),
                            new CorrelationData(orderId.toString()));
                } catch (Exception ex) {
                    log.error("延迟关单消息发送失败，依赖定时扫描兜底，orderId={}", orderId, ex);
                }

                // ========== 4. 手动 ACK：告诉 RabbitMQ 消息处理成功，可以删了 ==========
                // 参数：deliveryTag, multiple=false（只确认当前这一条）
                channel.basicAck(deliveryTag, false);
                log.info("秒杀订单创建成功，orderId={}, userId={}", orderId, userId);

            } finally {
                // 释放锁（必须在 finally 里，防止异常导致锁不释放）
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }

        } catch (Exception e) {
            log.error("秒杀订单处理异常，orderId={}", orderId, e);
            try {
                // ========== 异常处理：NACK 拒绝消息 ==========
                // 参数：deliveryTag, multiple=false, requeue=false
                // requeue=false：不重新入队，消息进入死信队列（我们配置了死信）
                // 如果 requeue=true：消息重新入队，会被再次消费（配合重试机制）
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ex) {
                log.error("消息 NACK 失败", ex);
            }
        }
    }
}