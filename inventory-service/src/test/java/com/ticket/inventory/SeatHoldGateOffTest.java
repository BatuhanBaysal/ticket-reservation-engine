package com.ticket.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticket.events.inventory.SeatHoldRejected;
import com.ticket.inventory.domain.Seat;
import com.ticket.inventory.domain.SeatStatus;
import com.ticket.inventory.repository.HeldSeat;
import com.ticket.inventory.repository.SeatRepository;
import com.ticket.inventory.service.HoldResult;
import com.ticket.inventory.service.SeatService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "inventory.hold.gate.enabled=false")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SeatHoldGateOffTest {

    private static final String EVENT_ID = "evt-001";
    private static final String SEAT_ID = "A-12";
    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");
    private static final UUID OTHER_ORDER_ID = UUID.fromString("c3e1f6a8-2b94-4d57-9a0e-6f8d1b7c4e23");
    private static final HeldSeat HELD_SEAT = new HeldSeat(SeatFixtures.PRICE, Instant.parse("2026-11-17T10:25:30Z"));

    @MockitoBean
    private SeatRepository repository;

    @Autowired
    private SeatService service;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void clearTheKeys() {
        RedisFixtures.clearHoldKeys(redis);
    }

    @Test
    void rejectsTheSecondOrderInTheDatabase() {
        givenTheSeatIsHeldBy(ORDER_ID);
        service.hold(ORDER_ID, EVENT_ID, SEAT_ID);

        HoldResult result = service.hold(OTHER_ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(result).isEqualTo(new HoldResult.Rejected(SeatHoldRejected.SEAT_UNAVAILABLE));
    }

    @Test
    void sendsEveryOrderToTheDatabase() {
        givenTheSeatIsHeldBy(ORDER_ID);
        service.hold(ORDER_ID, EVENT_ID, SEAT_ID);

        service.hold(OTHER_ORDER_ID, EVENT_ID, SEAT_ID);

        verify(repository).holdIfAvailable(eq(ORDER_ID), eq(EVENT_ID), eq(SEAT_ID), any());
        verify(repository).holdIfAvailable(eq(OTHER_ORDER_ID), eq(EVENT_ID), eq(SEAT_ID), any());
    }

    @Test
    void leavesRedisUntouched() {
        givenTheSeatIsHeldBy(ORDER_ID);

        service.hold(ORDER_ID, EVENT_ID, SEAT_ID);
        service.hold(OTHER_ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(redis.keys("hold:*")).isEmpty();
    }

    private void givenTheSeatIsHeldBy(UUID orderId) {
        Seat seat = mock(Seat.class);
        when(seat.getStatus()).thenReturn(SeatStatus.HELD);
        when(seat.getHeldByOrderId()).thenReturn(orderId);
        when(repository.holdIfAvailable(eq(orderId), eq(EVENT_ID), eq(SEAT_ID), any()))
            .thenReturn(Optional.of(HELD_SEAT));
        when(repository.findByEventIdAndSeatId(EVENT_ID, SEAT_ID)).thenReturn(Optional.of(seat));
    }
}
