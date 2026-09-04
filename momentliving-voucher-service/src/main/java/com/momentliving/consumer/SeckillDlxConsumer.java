package com.momentliving.consumer;

import com.momentliving.config.RabbitMQConfig;
import com.momentliving.constant.RedisConstants;
import com.momentliving.entity.VoucherOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

@Slf4j
@Component
public class SeckillDlxConsumer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 监听死信队列：处理失败的订单消息，做 Redis 补偿
     */
    @RabbitListener(queues = RabbitMQConfig.SECKILL_DLX_QUEUE)
    public void handleDlxMessage(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        Long orderId = voucherOrder.getId();

        log.warn("收到死信消息，开始 Redis 补偿，orderId={}, voucherId={}, userId={}",
                orderId, voucherId, userId);

        try {
            // 1. Redis 库存 +1（加回扣掉的库存）
            String stockKey = RedisConstants.SECKILL_STOCK_KEY + voucherId;
            stringRedisTemplate.opsForValue().increment(stockKey);

            // 2. 把用户下单次数 -1（减回扣掉的次数）
            String countKey = RedisConstants.SECKILL_COUNT_KEY + voucherId;
            stringRedisTemplate.opsForHash().increment(countKey, userId.toString(),-1);

            log.info("Redis 补偿完成，orderId={}", orderId);
        } catch (Exception e) {
            log.error("Redis 补偿失败，orderId={}", orderId, e);
            // 这里可以加告警：比如发邮件、发短信，人工介入
        }
    }
}