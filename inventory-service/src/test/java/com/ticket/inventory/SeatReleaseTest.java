package com.ticket.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.ticket.inventory.domain.Seat;
import com.ticket.inventory.domain.SeatStatus;
import com.ticket.inventory.repository.SeatRepository;
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
class SeatReleaseTest {

    private static final String EVENT_ID = "evt-001";
    private static final String SEAT_ID = "A-12";
    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");
    private static final UUID OTHER_ORDER_ID = UUID.fromString("c3e1f6a8-2b94-4d57-9a0e-6f8d1b7c4e23");
    private static final String KEY = RedisFixtures.holdKey(EVENT_ID, SEAT_ID);

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
    void makesAHeldSeatAvailable() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, Instant.now().plus(Duration.ofMinutes(5)));

        service.release(ORDER_ID, EVENT_ID, SEAT_ID);

        Seat seat = seat();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seat.getHeldByOrderId()).isNull();
        assertThat(seat.getHoldExpiresAt()).isNull();
    }

    @Test
    void raisesTheVersionWhenAHeldSeatIsReleased() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, Instant.now().plus(Duration.ofMinutes(5)));

        service.release(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(seat().getVersion()).isEqualTo(1L);
    }

    @Test
    void ignoresASeatHeldByAnotherOrder() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, OTHER_ORDER_ID, Instant.now().plus(Duration.ofMinutes(5)));

        service.release(ORDER_ID, EVENT_ID, SEAT_ID);

        Seat seat = seat();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(seat.getHeldByOrderId()).isEqualTo(OTHER_ORDER_ID);
        assertThat(seat.getVersion()).isZero();
    }

    @Test
    void ignoresASoldSeatEvenForItsBuyer() {
        SeatFixtures.insertSold(jdbc, EVENT_ID, SEAT_ID, ORDER_ID);

        service.release(ORDER_ID, EVENT_ID, SEAT_ID);

        Seat seat = seat();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.SOLD);
        assertThat(seat.getVersion()).isZero();
    }

    @Test
    void ignoresAnAvailableSeat() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);

        service.release(ORDER_ID, EVENT_ID, SEAT_ID);

        Seat seat = seat();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seat.getVersion()).isZero();
    }

    @Test
    void ignoresAnUnknownSeat() {
        assertThatCode(() -> service.release(ORDER_ID, EVENT_ID, SEAT_ID)).doesNotThrowAnyException();
    }

    @Test
    void deletesTheRedisKeyOfTheOrder() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);
        service.hold(ORDER_ID, EVENT_ID, SEAT_ID);

        service.release(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(redis.hasKey(KEY)).isFalse();
    }

    @Test
    void keepsTheRedisKeyOfAnotherOrder() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, OTHER_ORDER_ID, Instant.now().plus(Duration.ofMinutes(5)));
        redis.opsForValue().set(KEY, OTHER_ORDER_ID.toString());

        service.release(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(redis.opsForValue().get(KEY)).isEqualTo(OTHER_ORDER_ID.toString());
    }

    @Test
    void changesTheSeatOnceWhenTheOrderReleasesTwice() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, Instant.now().plus(Duration.ofMinutes(5)));

        service.release(ORDER_ID, EVENT_ID, SEAT_ID);
        service.release(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(seat().getVersion()).isEqualTo(1L);
    }

    private Seat seat() {
        return repository.findByEventIdAndSeatId(EVENT_ID, SEAT_ID).orElseThrow();
    }
}
