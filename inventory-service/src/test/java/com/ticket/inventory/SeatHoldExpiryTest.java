package com.ticket.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticket.inventory.domain.Seat;
import com.ticket.inventory.domain.SeatStatus;
import com.ticket.inventory.repository.ExpiredHold;
import com.ticket.inventory.repository.SeatRepository;
import com.ticket.inventory.service.SeatService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SeatHoldExpiryTest {

    private static final String EVENT_ID = "evt-001";
    private static final String SEAT_ID = "A-12";
    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");

    @Autowired
    private SeatService service;

    @Autowired
    private SeatRepository repository;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void emptyTheTable() {
        jdbc.sql("DELETE FROM seat_inventory").update();
    }

    @Test
    void returnsTheOverdueHoldItExpired() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, overdue());

        List<ExpiredHold> expired = service.expireOverdueHolds();

        assertThat(expired).containsExactly(new ExpiredHold(ORDER_ID, EVENT_ID, SEAT_ID));
    }

    @Test
    void makesTheOverdueSeatAvailable() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, overdue());

        service.expireOverdueHolds();

        Seat seat = seat();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seat.getHeldByOrderId()).isNull();
        assertThat(seat.getHoldExpiresAt()).isNull();
    }

    @Test
    void raisesTheVersionOfTheExpiredSeat() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, overdue());

        service.expireOverdueHolds();

        assertThat(seat().getVersion()).isEqualTo(1L);
    }

    @Test
    void keepsAHoldThatHasNotExpired() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, Instant.now().plus(Duration.ofMinutes(5)));

        service.expireOverdueHolds();

        Seat seat = seat();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(seat.getHeldByOrderId()).isEqualTo(ORDER_ID);
        assertThat(seat.getVersion()).isZero();
    }

    @Test
    void leavesASoldSeatUntouched() {
        SeatFixtures.insertSold(jdbc, EVENT_ID, SEAT_ID, ORDER_ID);

        service.expireOverdueHolds();

        Seat seat = seat();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.SOLD);
        assertThat(seat.getVersion()).isZero();
    }

    @Test
    void leavesAnAvailableSeatUntouched() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);

        service.expireOverdueHolds();

        Seat seat = seat();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seat.getVersion()).isZero();
    }

    @Test
    void returnsNothingOnASecondRun() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, overdue());
        service.expireOverdueHolds();

        List<ExpiredHold> second = service.expireOverdueHolds();

        assertThat(second).isEmpty();
    }

    @Test
    void expiresEveryOverdueHold() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, "A-1", UUID.randomUUID(), overdue());
        SeatFixtures.insertHeld(jdbc, EVENT_ID, "A-2", UUID.randomUUID(), overdue());
        SeatFixtures.insertHeld(jdbc, EVENT_ID, "A-3", UUID.randomUUID(), overdue());

        List<ExpiredHold> expired = service.expireOverdueHolds();

        assertThat(expired).extracting(ExpiredHold::seatId).containsExactlyInAnyOrder("A-1", "A-2", "A-3");
    }

    @Test
    void twoRunsAtTheSameTimeExpireEachHoldOnce() throws Exception {
        for (int number = 0; number < 50; number++) {
            SeatFixtures.insertHeld(jdbc, EVENT_ID, "S-" + number, UUID.randomUUID(), overdue());
        }
        CountDownLatch start = new CountDownLatch(1);
        Callable<List<ExpiredHold>> run = () -> {
            start.await();
            return service.expireOverdueHolds();
        };
        List<ExpiredHold> expired = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<List<ExpiredHold>> first = executor.submit(run);
            Future<List<ExpiredHold>> second = executor.submit(run);
            start.countDown();
            expired.addAll(first.get(30, TimeUnit.SECONDS));
            expired.addAll(second.get(30, TimeUnit.SECONDS));
        }

        assertThat(expired).hasSize(50).doesNotHaveDuplicates();
    }

    private Seat seat() {
        return repository.findByEventIdAndSeatId(EVENT_ID, SEAT_ID).orElseThrow();
    }

    private static Instant overdue() {
        return Instant.now().minus(Duration.ofMinutes(1));
    }
}
