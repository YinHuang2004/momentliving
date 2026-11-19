package com.momentliving.service;

import com.momentliving.entity.VoucherOrder;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class SeckillMqPublisherTest {

    @Test
    void returnsTrueOnlyAfterBrokerAck() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData data = invocation.getArgument(4);
            data.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).convertAndSend(anyString(), anyString(), any(),
                any(MessagePostProcessor.class), any(CorrelationData.class));

        assertTrue(new SeckillMqPublisher(template).publish(order()));
    }

    @Test
    void returnsFalseOnBrokerNack() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData data = invocation.getArgument(4);
            data.getFuture().complete(new CorrelationData.Confirm(false, "broker unavailable"));
            return null;
        }).when(template).convertAndSend(anyString(), anyString(), any(),
                any(MessagePostProcessor.class), any(CorrelationData.class));

        assertFalse(new SeckillMqPublisher(template).publish(order()));
    }

    private VoucherOrder order() {
        return VoucherOrder.builder().id(100L).userId(10L).voucherId(20L).purchaseSlot(1).build();
    }
}
