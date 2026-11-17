package com.ticket.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticket.events.inventory.SeatHoldRejected;
import com.ticket.inventory.domain.Seat;
import com.ticket.inventory.domain.SeatStatus;
import com.ticket.inventory.repository.SeatRepository;
import com.ticket.inventory.service.HoldResult;
import com.ticket.inventory.service.SeatService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SeatHoldTest {

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
    void holdsAnAvailableSeatAndReturnsItsPriceAndExpiry() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);
        Instant before = Instant.now();

        HoldResult result = service.hold(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(result).isInstanceOfSatisfying(HoldResult.Held.class, held -> {
            assertThat(held.price()).isEqualByComparingTo("49.9000");
            assertThat(held.holdExpiresAt())
                .isBetween(before.plus(Duration.ofMinutes(9)), before.plus(Duration.ofMinutes(11)));
        });
    }

    @Test
    void marksTheSeatAsHeldByTheOrder() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);

        service.hold(ORDER_ID, EVENT_ID, SEAT_ID);

        Seat seat = seat();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(seat.getHeldByOrderId()).isEqualTo(ORDER_ID);
        assertThat(seat.getVersion()).isEqualTo(1L);
    }

    @Test
    void rejectsASeatHeldByAnotherOrder() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, OTHER_ORDER_ID, Instant.now().plus(Duration.ofMinutes(5)));

        HoldResult result = service.hold(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(result).isEqualTo(new HoldResult.Rejected(SeatHoldRejected.SEAT_UNAVAILABLE));
    }

    @Test
    void keepsTheCurrentHolderWhenAnotherOrderIsRejected() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, OTHER_ORDER_ID, Instant.now().plus(Duration.ofMinutes(5)));

        service.hold(ORDER_ID, EVENT_ID, SEAT_ID);

        Seat seat = seat();
        assertThat(seat.getHeldByOrderId()).isEqualTo(OTHER_ORDER_ID);
        assertThat(seat.getVersion()).isZero();
    }

    @Test
    void holdsASeatWhoseHoldHasExpired() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, OTHER_ORDER_ID, Instant.now().minus(Duration.ofMinutes(1)));

        HoldResult result = service.hold(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(result).isInstanceOf(HoldResult.Held.class);
    }

    @Test
    void givesTheExpiredSeatToTheNewOrder() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, OTHER_ORDER_ID, Instant.now().minus(Duration.ofMinutes(1)));

        service.hold(ORDER_ID, EVENT_ID, SEAT_ID);

        Seat seat = seat();
        assertThat(seat.getHeldByOrderId()).isEqualTo(ORDER_ID);
        assertThat(seat.getVersion()).isEqualTo(1L);
    }

    @Test
    void rejectsASeatThatDoesNotExist() {
        HoldResult result = service.hold(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(result).isEqualTo(new HoldResult.Rejected(SeatHoldRejected.SEAT_NOT_FOUND));
    }

    @Test
    void returnsTheExistingHoldWhenTheSameOrderHoldsTwice() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);
        HoldResult first = service.hold(ORDER_ID, EVENT_ID, SEAT_ID);

        HoldResult second = service.hold(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void changesTheSeatOnceWhenTheSameOrderHoldsTwice() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);

        service.hold(ORDER_ID, EVENT_ID, SEAT_ID);
        service.hold(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(seat().getVersion()).isEqualTo(1L);
    }

    @Test
    void rejectsASoldSeat() {
        SeatFixtures.insertSold(jdbc, EVENT_ID, SEAT_ID, OTHER_ORDER_ID);

        HoldResult result = service.hold(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(result).isEqualTo(new HoldResult.Rejected(SeatHoldRejected.SEAT_UNAVAILABLE));
    }

    @Test
    void rejectsASoldSeatEvenForItsBuyer() {
        SeatFixtures.insertSold(jdbc, EVENT_ID, SEAT_ID, ORDER_ID);

        HoldResult result = service.hold(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(result).isEqualTo(new HoldResult.Rejected(SeatHoldRejected.SEAT_UNAVAILABLE));
    }

    private Seat seat() {
        return repository.findByEventIdAndSeatId(EVENT_ID, SEAT_ID).orElseThrow();
    }
}
