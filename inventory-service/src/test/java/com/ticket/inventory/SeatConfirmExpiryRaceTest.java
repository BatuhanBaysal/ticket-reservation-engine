package com.ticket.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import com.ticket.events.inventory.SeatConfirmFailed;
import com.ticket.inventory.domain.Seat;
import com.ticket.inventory.domain.SeatStatus;
import com.ticket.inventory.repository.ExpiredHold;
import com.ticket.inventory.repository.SeatRepository;
import com.ticket.inventory.service.ConfirmResult;
import com.ticket.inventory.service.SeatService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, ConflictInjection.class})
class SeatConfirmExpiryRaceTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeatConfirmExpiryRaceTest.class);
    private static final int ROUNDS = 200;
    private static final String EVENT_ID = "evt-001";
    private static final String SEAT_ID = "A-12";
    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");
    private static final ConfirmResult HOLD_NOT_FOUND = new ConfirmResult.Failed(SeatConfirmFailed.HOLD_NOT_FOUND);

    @Autowired
    private SeatService service;

    @Autowired
    private SeatRepository repository;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ConflictInjector injector;

    @BeforeEach
    void emptyTheTableAndTheInjector() {
        jdbc.sql("DELETE FROM seat_inventory").update();
        injector.reset();
    }

    @Test
    void exactlyOneSideWinsInEveryRound() {
        List<Round> rounds = playRounds();

        List<Round> withoutExactlyOneWinner = rounds.stream().filter(round -> !round.hasExactlyOneWinner()).toList();

        assertThat(withoutExactlyOneWinner).isEmpty();
    }

    @Test
    void leavesNoSeatSoldWithAnExpiryAndNoSeatHeld() {
        playRounds();

        Long soldWithExpiry = jdbc
            .sql("SELECT count(*) FROM seat_inventory WHERE status = 'SOLD' AND hold_expires_at IS NOT NULL")
            .query(Long.class)
            .single();
        Long held = jdbc.sql("SELECT count(*) FROM seat_inventory WHERE status = 'HELD'")
            .query(Long.class)
            .single();

        assertSoftly(softly -> {
            softly.assertThat(soldWithExpiry).isZero();
            softly.assertThat(held).isZero();
        });
    }

    @Test
    void returnsHoldNotFoundWhenTheJobWinsTheVersionConflict() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, overdue());
        injector.runOnceAfterTheNextLoad(service::expireOverdueHolds);

        ConfirmResult result = service.confirm(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(result).isEqualTo(HOLD_NOT_FOUND);
    }

    @Test
    void leavesTheSeatAvailableWhenTheJobWinsTheVersionConflict() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, overdue());
        injector.runOnceAfterTheNextLoad(service::expireOverdueHolds);

        service.confirm(ORDER_ID, EVENT_ID, SEAT_ID);

        Seat seat = seat();
        assertSoftly(softly -> {
            softly.assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
            softly.assertThat(seat.getHeldByOrderId()).isNull();
            softly.assertThat(seat.getVersion()).isEqualTo(1L);
        });
    }

    @Test
    void retriesTheConfirmOnceAfterTheVersionConflict() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, overdue());
        injector.runOnceAfterTheNextLoad(service::expireOverdueHolds);

        service.confirm(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(injector.loads()).isEqualTo(2);
    }

    @Test
    void reportsTheHoldAsExpiredWhenTheJobWinsTheVersionConflict() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, overdue());
        AtomicReference<List<ExpiredHold>> expiredByTheJob = new AtomicReference<>();
        injector.runOnceAfterTheNextLoad(() -> expiredByTheJob.set(service.expireOverdueHolds()));

        service.confirm(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(expiredByTheJob.get()).containsExactly(new ExpiredHold(ORDER_ID, EVENT_ID, SEAT_ID));
    }

    private List<Round> playRounds() {
        List<Round> rounds = IntStream.range(0, ROUNDS).mapToObj(this::playRound).toList();
        long confirmWins = rounds.stream().filter(Round::confirmWon).count();
        long jobWins = rounds.stream().filter(Round::expiryWon).count();
        LOGGER.info("Confirm won {} of {} rounds and the expiry job won {}", confirmWins, ROUNDS, jobWins);
        return rounds;
    }

    private Round playRound(int number) {
        String seatId = "R-" + number;
        UUID orderId = UUID.randomUUID();
        SeatFixtures.insertHeld(jdbc, EVENT_ID, seatId, orderId, overdue());
        AtomicReference<ConfirmResult> confirmResult = new AtomicReference<>();
        AtomicReference<List<ExpiredHold>> expiredHolds = new AtomicReference<>();

        ConcurrentRunner.runTogether(List.of(
            confirming(orderId, seatId, confirmResult),
            expiring(expiredHolds)));

        Seat seat = repository.findByEventIdAndSeatId(EVENT_ID, seatId).orElseThrow();
        boolean expired = expiredHolds.get().contains(new ExpiredHold(orderId, EVENT_ID, seatId));
        return new Round(confirmResult.get(), expired, seat.getStatus(), seat.getHoldExpiresAt());
    }

    private Callable<Object> confirming(UUID orderId, String seatId, AtomicReference<ConfirmResult> target) {
        return () -> {
            target.set(service.confirm(orderId, EVENT_ID, seatId));
            return null;
        };
    }

    private Callable<Object> expiring(AtomicReference<List<ExpiredHold>> target) {
        return () -> {
            target.set(service.expireOverdueHolds());
            return null;
        };
    }

    private Seat seat() {
        return repository.findByEventIdAndSeatId(EVENT_ID, SEAT_ID).orElseThrow();
    }

    private static Instant overdue() {
        return Instant.now().minus(Duration.ofMinutes(1));
    }

    private record Round(ConfirmResult confirm, boolean expired, SeatStatus status, Instant holdExpiresAt) {

        boolean confirmWon() {
            return confirm instanceof ConfirmResult.Confirmed
                && !expired
                && status == SeatStatus.SOLD
                && holdExpiresAt == null;
        }

        boolean expiryWon() {
            return HOLD_NOT_FOUND.equals(confirm)
                && expired
                && status == SeatStatus.AVAILABLE
                && holdExpiresAt == null;
        }

        boolean hasExactlyOneWinner() {
            return confirmWon() ^ expiryWon();
        }
    }
}
