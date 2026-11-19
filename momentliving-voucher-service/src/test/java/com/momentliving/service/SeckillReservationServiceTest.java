package com.momentliving.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SeckillReservationServiceTest {
    private final SeckillReservationService service =
            new SeckillReservationService(mock(StringRedisTemplate.class));

    @Test
    void parsesVersionedReservation() {
        SeckillReservationService.Reservation reservation = service
                .parse("10|2|1700000000000|CONFIRMED|3")
                .orElseThrow();

        assertEquals(10L, reservation.userId());
        assertEquals(2, reservation.purchaseSlot());
        assertEquals("CONFIRMED", reservation.state());
        assertEquals(3, reservation.attempts());
        assertEquals("10|2|1700000000000|CONFIRMED|3", reservation.serialize());
    }

    @Test
    void rejectsMalformedReservation() {
        assertTrue(service.parse("10|bad|record").isEmpty());
        assertTrue(service.parse(null).isEmpty());
    }
}
