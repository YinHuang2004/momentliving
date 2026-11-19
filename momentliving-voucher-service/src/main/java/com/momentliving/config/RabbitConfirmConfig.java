package com.momentliving.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/** Return 仅用于兜底日志；逐条消息的 Confirm/Return 由 CorrelationData 判断。 */
@Slf4j
@Component
public class RabbitConfirmConfig implements SmartInitializingSingleton {
    private final RabbitTemplate rabbitTemplate;

    public RabbitConfirmConfig(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /** 单例就绪后开启 mandatory：消息不可路由时经 Return 回调记录兜底日志 */
    @Override
    public void afterSingletonsInstantiated() {
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setReturnsCallback(returned -> log.error(
                "RabbitMQ 消息无法路由，messageId={}, exchange={}, routingKey={}, reply={}",
                returned.getMessage().getMessageProperties().getMessageId(),
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
    }
}
