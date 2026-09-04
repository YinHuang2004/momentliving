package com.momentliving.consumer;

import com.momentliving.config.RabbitMQConfig;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.IOException;

/**
 * 超时关单消费者：监听延迟队列的死信投递。
 * 消息体为下单成功后由秒杀消费者写入的 orderId 字符串。
 */
@Slf4j
@Component
public class OrderCloseConsumer {

    @Resource
    private OrderCloseHandler orderCloseHandler;

    @RabbitListener(queues = RabbitMQConfig.ORDER_CLOSE_QUEUE, concurrency = "2")
    public void handleOrderClose(String orderIdStr, Message message, Channel channel) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            Long orderId = Long.valueOf(orderIdStr);
            boolean closed = orderCloseHandler.closeUnpaidOrder(orderId);
            channel.basicAck(deliveryTag, false);
            log.debug("关单消息处理完毕，orderId={}, closed={}", orderId, closed);
        } catch (Exception e) {
            log.error("关单消息处理异常，orderIdStr={}, NACK 进入重试判断", orderIdStr, e);
            try {
                // 不 requeue：消息进本地日志人工排查；关单还有每分钟的扫描兜底，不会丢单
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ex) {
                log.error("NACK 失败", ex);
            }
        }
    }
}
