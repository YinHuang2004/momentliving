package com.momentliving.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * MQ 发布可靠性：publisher-confirm + Returns 回调。
 * 依赖连接配置（放 Nacos 共享配置）：
 * <pre>
 * spring:
 *   rabbitmq:
 *     publisher-confirm-type: correlated   # 异步确认，broker 收到后回调
 *     publisher-returns: true              # 路由失败时返回消息
 * </pre>
 *
 * <p>确认失败/路由失败只做日志告警（附带 CorrelationData 的业务单号便于排查）；
 * 兜底靠关单定时扫描任务，避免为每条消息引入复杂的重发状态机。
 */
@Slf4j
@Component
public class RabbitConfirmConfig implements SmartInitializingSingleton {

    private final RabbitTemplate rabbitTemplate;

    public RabbitConfirmConfig(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void afterSingletonsInstantiated() {
        // broker 是否成功接收
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                String bizId = correlationData == null ? "unknown" : correlationData.getId();
                log.error("MQ confirm 失败！消息未到达 broker，bizId={}, cause={}", bizId, cause);
                // TODO 可扩展：写补偿表 / 告警通知
            }
        });
        // 到达 broker 但没有路由到任何队列（如 routing key 打错）
        rabbitTemplate.setReturnsCallback(returned -> log.error(
                "MQ 消息路由失败！exchange={}, routingKey={}, replyText={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
        // mandatory=true：无路由消息不静默丢弃，触发 returns 回调
        rabbitTemplate.setMandatory(true);
    }
}
