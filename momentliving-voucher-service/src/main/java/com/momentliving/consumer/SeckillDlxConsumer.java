package com.momentliving.consumer;

import com.momentliving.config.RabbitMQConfig;
import com.momentliving.entity.VoucherOrder;
import com.momentliving.mapper.VoucherOrderMapper;
import com.momentliving.service.SeckillReservationService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** 业务消费重试耗尽后，以 Lua 原子且幂等地回补 Redis 库存和限购次数。 */
@Slf4j
@Component
public class SeckillDlxConsumer {
    private final SeckillReservationService reservationService;
    private final VoucherOrderMapper orderMapper;

    public SeckillDlxConsumer(SeckillReservationService reservationService,
                              VoucherOrderMapper orderMapper) {
        this.reservationService = reservationService;
        this.orderMapper = orderMapper;
    }

    /** 订单已落库则只需删预占（finish）；未落库说明下单彻底失败，执行 Redis 回补 */
    @RabbitListener(queues = RabbitMQConfig.SECKILL_DLX_QUEUE)
    public void handleDlxMessage(VoucherOrder order, Message message, Channel channel) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            if (orderMapper.selectById(order.getId()) != null) {
                reservationService.finish(order.getVoucherId(), order.getId());
                channel.basicAck(deliveryTag, false);
                return;
            }
            boolean compensated = reservationService.compensate(
                    order.getVoucherId(), order.getUserId(), order.getId());
            channel.basicAck(deliveryTag, false);
            log.warn("死信消息补偿完成，orderId={}, firstCompensation={}", order.getId(), compensated);
        } catch (Exception e) {
            log.error("死信补偿失败，交给容器重试；耗尽后进入人工处理队列，orderId={}", order.getId(), e);
            throw new IllegalStateException("死信补偿失败，orderId=" + order.getId(), e);
        }
    }
}
