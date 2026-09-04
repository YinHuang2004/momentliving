package com.momentliving.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RabbitMQConfig {

    // ========== 秒杀订单业务队列相关常量 ==========
    public static final String SECKILL_EXCHANGE = "seckill.exchange";       // 交换机
    public static final String SECKILL_ORDER_QUEUE = "seckill.order.queue"; // 业务队列
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order.create"; // 路由键

    // ========== 死信队列相关常量 ==========
    public static final String SECKILL_DLX_EXCHANGE = "seckill.dlx.exchange";   // 死信交换机
    public static final String SECKILL_DLX_QUEUE = "seckill.dlx.queue";         // 死信队列
    public static final String SECKILL_DLX_ROUTING_KEY = "seckill.dlx";         // 死信路由键

    // ========== 订单超时关单常量（TTL 死信延迟方案，无需安装延迟插件） ==========
    /** 延迟交换机/队列：订单落库后发入，TTL 到期后死信到关单队列 */
    public static final String ORDER_CLOSE_DELAY_EXCHANGE = "order.close.delay.exchange";
    public static final String ORDER_CLOSE_DELAY_QUEUE = "order.close.delay.queue";
    public static final String ORDER_CLOSE_DELAY_ROUTING_KEY = "order.close.delay";
    /** 关单交换机/队列：消费者在此真正执行"待支付单自动取消 + 库存回补" */
    public static final String ORDER_CLOSE_EXCHANGE = "order.close.exchange";
    public static final String ORDER_CLOSE_QUEUE = "order.close.queue";
    public static final String ORDER_CLOSE_ROUTING_KEY = "order.close";

    // ========== 1. 声明秒杀业务交换机（Direct 点对点）==========
    @Bean
    public DirectExchange seckillExchange() {
        // 参数：交换机名称、是否持久化、是否自动删除
        return new DirectExchange(SECKILL_EXCHANGE, true, false);
    }

    // ========== 2. 声明秒杀业务队列（绑定死信交换机）==========
    @Bean
    public Queue seckillOrderQueue() {
        return QueueBuilder.durable(SECKILL_ORDER_QUEUE)  // durable=true：队列持久化
                // 绑定死信交换机：消费失败的消息会转发到死信交换机
                .deadLetterExchange(SECKILL_DLX_EXCHANGE)
                .deadLetterRoutingKey(SECKILL_DLX_ROUTING_KEY)
                .build();
    }

    // ========== 3. 绑定：业务队列 → 业务交换机 ==========
    @Bean
    public Binding seckillOrderBinding() {
        return BindingBuilder
                .bind(seckillOrderQueue())
                .to(seckillExchange())
                .with(SECKILL_ORDER_ROUTING_KEY);
    }

    // ========== 4. 声明死信交换机 ==========
    @Bean
    public DirectExchange seckillDlxExchange() {
        return new DirectExchange(SECKILL_DLX_EXCHANGE, true, false);
    }

    // ========== 5. 声明死信队列 ==========
    @Bean
    public Queue seckillDlxQueue() {
        return QueueBuilder.durable(SECKILL_DLX_QUEUE).build();
    }

    // ========== 6. 绑定：死信队列 → 死信交换机 ==========
    @Bean
    public Binding seckillDlxBinding() {
        return BindingBuilder
                .bind(seckillDlxQueue())
                .to(seckillDlxExchange())
                .with(SECKILL_DLX_ROUTING_KEY);
    }

    // ========== 7. 消息转换器：JSON 序列化 ==========
    // 默认是 Java 序列化（又慢又不通用），改成 JSON
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ========== 8. 延迟关单：TTL 队列（消息到期作为死信投递到关单队列） ==========
    // ⚠️ 队列级 TTL 在声明时固定；修改 momentliving.pay.order-timeout-minutes 后需在控制台删除旧队列再重启
    @Bean
    public Queue orderCloseDelayQueue(com.momentliving.config.PayProperties payProperties) {
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
    public Binding orderCloseDelayBinding(Queue orderCloseDelayQueue, DirectExchange orderCloseDelayExchange) {
        return BindingBuilder.bind(orderCloseDelayQueue)
                .to(orderCloseDelayExchange())
                .with(ORDER_CLOSE_DELAY_ROUTING_KEY);
    }

    // ========== 9. 关单队列（延迟队列的死信落地处） ==========
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
                .to(orderCloseExchange())
                .with(ORDER_CLOSE_ROUTING_KEY);
    }
}