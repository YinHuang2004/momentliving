package com.momentliving.service;

import com.momentliving.config.RabbitMQConfig;
import com.momentliving.entity.VoucherOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/** 使用 Publisher Confirm + Return 判断本次消息是否被交换机可靠接收并正确路由。 */
@Slf4j
@Service
public class SeckillMqPublisher {
    private final RabbitTemplate rabbitTemplate;

    public SeckillMqPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /** 发送秒杀订单消息并同步等待 Confirm（5s）；NACK/无法路由/异常均返回 false，预占交给对账任务兜底 */
    public boolean publish(VoucherOrder order) {
        CorrelationData correlationData = new CorrelationData(order.getId().toString());
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SECKILL_EXCHANGE,
                    RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY,
                    order,
                    message -> {
                        message.getMessageProperties().setMessageId(order.getId().toString());
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return message;
                    },
                    correlationData);

            CorrelationData.Confirm confirm = correlationData.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck()) {
                log.error("秒杀消息 Confirm NACK，orderId={}, reason={}", order.getId(), confirm.getReason());
                return false;
            }
            if (correlationData.getReturned() != null) {
                log.error("秒杀消息无法路由，orderId={}, reply={}", order.getId(),
                        correlationData.getReturned().getReplyText());
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.error("秒杀消息发送失败，保留 Redis 预占等待恢复任务，orderId={}", order.getId(), e);
            return false;
        }
    }
}
