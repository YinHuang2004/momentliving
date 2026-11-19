package com.momentliving.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MQ 拓扑声明：秒杀下单队列、两级死信（业务 DLX → 人工补偿队列）、
 * 订单延迟关单（TTL 队列 + 死信转关单队列）
 */
@Configuration
public class RabbitMQConfig {
    public static final String SECKILL_EXCHANGE = "seckill.exchange";
    public static final String SECKILL_ORDER_QUEUE = "seckill.order.queue";
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order.create";

    public static final String SECKILL_DLX_EXCHANGE = "seckill.dlx.exchange";
    public static final String SECKILL_DLX_QUEUE = "seckill.dlx.queue";
    public static final String SECKILL_DLX_ROUTING_KEY = "seckill.dlx";
    public static final String COMPENSATION_FAILED_EXCHANGE = "seckill.compensation.failed.exchange";
    public static final String COMPENSATION_FAILED_QUEUE = "seckill.compensation.failed.queue";
    public static final String COMPENSATION_FAILED_ROUTING_KEY = "seckill.compensation.failed";

    public static final String ORDER_CLOSE_DELAY_EXCHANGE = "order.close.delay.exchange";
    public static final String ORDER_CLOSE_DELAY_QUEUE = "order.close.delay.queue";
    public static final String ORDER_CLOSE_DELAY_ROUTING_KEY = "order.close.delay";
    public static final String ORDER_CLOSE_EXCHANGE = "order.close.exchange";
    public static final String ORDER_CLOSE_QUEUE = "order.close.queue";
    public static final String ORDER_CLOSE_ROUTING_KEY = "order.close";

    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange(SECKILL_EXCHANGE, true, false);
    }

    /** 秒杀订单队列：消费失败reject后死信到业务 DLX（seckill.dlx.queue） */
    @Bean
    public Queue seckillOrderQueue() {
        return QueueBuilder.durable(SECKILL_ORDER_QUEUE)
                .deadLetterExchange(SECKILL_DLX_EXCHANGE)
                .deadLetterRoutingKey(SECKILL_DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding seckillOrderBinding() {
        return BindingBuilder.bind(seckillOrderQueue())
                .to(seckillExchange()).with(SECKILL_ORDER_ROUTING_KEY);
    }

    @Bean
    public DirectExchange seckillDlxExchange() {
        return new DirectExchange(SECKILL_DLX_EXCHANGE, true, false);
    }

    /** 业务死信队列：补偿重试仍耗尽后，二次死信到人工处理队列 */
    @Bean
    public Queue seckillDlxQueue() {
        return QueueBuilder.durable(SECKILL_DLX_QUEUE)
                .deadLetterExchange(COMPENSATION_FAILED_EXCHANGE)
                .deadLetterRoutingKey(COMPENSATION_FAILED_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding seckillDlxBinding() {
        return BindingBuilder.bind(seckillDlxQueue())
                .to(seckillDlxExchange()).with(SECKILL_DLX_ROUTING_KEY);
    }

    @Bean
    public DirectExchange compensationFailedExchange() {
        return new DirectExchange(COMPENSATION_FAILED_EXCHANGE, true, false);
    }

    @Bean
    public Queue compensationFailedQueue() {
        return QueueBuilder.durable(COMPENSATION_FAILED_QUEUE).build();
    }

    @Bean
    public Binding compensationFailedBinding() {
        return BindingBuilder.bind(compensationFailedQueue())
                .to(compensationFailedExchange()).with(COMPENSATION_FAILED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /** 延迟关单队列：TTL 取支付超时分钟数，到期死信转 order.close.queue 触发关单 */
    @Bean
    public Queue orderCloseDelayQueue(PayProperties payProperties) {
        int ttlMillis = payProperties.getOrderTimeoutMinutes() * 60 * 1000;
        return QueueBuilder.durable(ORDER_CLOSE_DELAY_QUEUE)
                .withArgument("x-message-ttl", ttlMillis)
                .deadLetterExchange(ORDER_CLOSE_EXCHANGE)
                .deadLetterRoutingKey(ORDER_CLOSE_ROUTING_KEY)
                .build();
    }

    @Bean
    public DirectExchange orderCloseDelayExchange() {
        return new DirectExchange(ORDER_CLOSE_DELAY_EXCHANGE, true, false);
    }

    @Bean
    public Binding orderCloseDelayBinding(Queue orderCloseDelayQueue,
                                          DirectExchange orderCloseDelayExchange) {
        return BindingBuilder.bind(orderCloseDelayQueue)
                .to(orderCloseDelayExchange).with(ORDER_CLOSE_DELAY_ROUTING_KEY);
    }

    @Bean
    public Queue orderCloseQueue() {
        return QueueBuilder.durable(ORDER_CLOSE_QUEUE).build();
    }

    @Bean
    public DirectExchange orderCloseExchange() {
        return new DirectExchange(ORDER_CLOSE_EXCHANGE, true, false);
    }

    @Bean
    public Binding orderCloseBinding() {
        return BindingBuilder.bind(orderCloseQueue())
                .to(orderCloseExchange()).with(ORDER_CLOSE_ROUTING_KEY);
    }
}
