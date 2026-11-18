package com.ticket.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticket.events.inventory.SeatConfirmFailed;
import com.ticket.inventory.domain.Seat;
import com.ticket.inventory.domain.SeatStatus;
import com.ticket.inventory.repository.SeatRepository;
import com.ticket.inventory.service.ConfirmResult;
import com.ticket.inventory.service.SeatService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SeatConfirmTest {

    private static final String EVENT_ID = "evt-001";
    private static final String SEAT_ID = "A-12";
    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");
    private static final UUID OTHER_ORDER_ID = UUID.fromString("c3e1f6a8-2b94-4d57-9a0e-6f8d1b7c4e23");

    @Autowired
    private SeatService service;

    @Autowired
    private SeatRepository repository;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void emptyTheTableAndTheGate() {
        jdbc.sql("DELETE FROM seat_inventory").update();
        RedisFixtures.clearHoldKeys(redis);
    }

    @Test
    void returnsConfirmedForASeatHeldByTheOrder() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, Instant.now().plus(Duration.ofMinutes(5)));

        ConfirmResult result = service.confirm(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(result).isEqualTo(new ConfirmResult.Confirmed());
    }

    @Test
    void marksTheSeatAsSoldWithoutAnExpiry() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, Instant.now().plus(Duration.ofMinutes(5)));

        service.confirm(ORDER_ID, EVENT_ID, SEAT_ID);

        Seat seat = seat();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.SOLD);
        assertThat(seat.getHeldByOrderId()).isEqualTo(ORDER_ID);
        assertThat(seat.getHoldExpiresAt()).isNull();
    }

    @Test
    void raisesTheVersionWhenTheSeatIsSold() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, Instant.now().plus(Duration.ofMinutes(5)));

        service.confirm(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(seat().getVersion()).isEqualTo(1L);
    }

    @Test
    void confirmsAHoldThatHasExpiredWhenNobodyElseHoldsTheSeat() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, Instant.now().minus(Duration.ofMinutes(1)));

        ConfirmResult result = service.confirm(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(result).isEqualTo(new ConfirmResult.Confirmed());
        assertThat(seat().getStatus()).isEqualTo(SeatStatus.SOLD);
    }

    @Test
    void failsWithSeatTakenForASeatHeldByAnotherOrder() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, OTHER_ORDER_ID, Instant.now().plus(Duration.ofMinutes(5)));

        ConfirmResult result = service.confirm(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(result).isEqualTo(new ConfirmResult.Failed(SeatConfirmFailed.SEAT_TAKEN));
    }

    @Test
    void failsWithSeatTakenForASeatSoldToAnotherOrder() {
        SeatFixtures.insertSold(jdbc, EVENT_ID, SEAT_ID, OTHER_ORDER_ID);

        ConfirmResult result = service.confirm(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(result).isEqualTo(new ConfirmResult.Failed(SeatConfirmFailed.SEAT_TAKEN));
    }

    @Test
    void failsWithHoldNotFoundForAnAvailableSeat() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);

        ConfirmResult result = service.confirm(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(result).isEqualTo(new ConfirmResult.Failed(SeatConfirmFailed.HOLD_NOT_FOUND));
    }

    @Test
    void failsWithHoldNotFoundForAnUnknownSeat() {
        ConfirmResult result = service.confirm(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(result).isEqualTo(new ConfirmResult.Failed(SeatConfirmFailed.HOLD_NOT_FOUND));
    }

    @Test
    void returnsConfirmedAgainWhenTheOrderConfirmsTwice() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, Instant.now().plus(Duration.ofMinutes(5)));
        service.confirm(ORDER_ID, EVENT_ID, SEAT_ID);

        ConfirmResult second = service.confirm(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(second).isEqualTo(new ConfirmResult.Confirmed());
    }

    @Test
    void changesTheSeatOnceWhenTheOrderConfirmsTwice() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, Instant.now().plus(Duration.ofMinutes(5)));

        service.confirm(ORDER_ID, EVENT_ID, SEAT_ID);
        service.confirm(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(seat().getVersion()).isEqualTo(1L);
    }

    @Test
    void leavesTheSeatOfAnotherOrderUnchangedWhenTheConfirmFails() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, OTHER_ORDER_ID, Instant.now().plus(Duration.ofMinutes(5)));

        service.confirm(ORDER_ID, EVENT_ID, SEAT_ID);

        Seat seat = seat();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(seat.getHeldByOrderId()).isEqualTo(OTHER_ORDER_ID);
        assertThat(seat.getVersion()).isZero();
    }

    private Seat seat() {
        return repository.findByEventIdAndSeatId(EVENT_ID, SEAT_ID).orElseThrow();
    }
}
