package com.ticket.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import com.ticket.inventory.domain.Seat;
import com.ticket.inventory.domain.SeatStatus;
import com.ticket.inventory.repository.SeatRepository;
import com.ticket.inventory.service.ConfirmResult;
import com.ticket.inventory.service.HoldResult;
import com.ticket.inventory.service.SeatService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
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
class SeatRepeatedRequestsTest {

    private static final int ROUNDS = 20;
    private static final String EVENT_ID = "evt-001";
    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");

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

    @RepeatedTest(value = ROUNDS, name = "round {currentRepetition} of {totalRepetitions}")
    void holdingTwiceAtTheSameTimeGivesOneHold(RepetitionInfo repetition) {
        String seatId = "H-" + repetition.getCurrentRepetition();
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, seatId);
        Callable<HoldResult> hold = () -> service.hold(ORDER_ID, EVENT_ID, seatId);

        List<HoldResult> results = ConcurrentRunner.runTogether(List.of(hold, hold));

        Seat seat = seat(seatId);
        assertSoftly(softly -> {
            softly.assertThat(results).hasSize(2).allMatch(HoldResult.Held.class::isInstance);
            softly.assertThat(results.get(0)).isEqualTo(results.get(1));
            softly.assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
            softly.assertThat(seat.getHeldByOrderId()).isEqualTo(ORDER_ID);
            softly.assertThat(seat.getVersion()).isEqualTo(1L);
        });
    }

    @RepeatedTest(value = ROUNDS, name = "round {currentRepetition} of {totalRepetitions}")
    void confirmingTwiceAtTheSameTimeSellsTheSeatOnce(RepetitionInfo repetition) {
        String seatId = "C-" + repetition.getCurrentRepetition();
        SeatFixtures.insertHeld(jdbc, EVENT_ID, seatId, ORDER_ID, Instant.now().plus(Duration.ofMinutes(5)));
        Callable<ConfirmResult> confirm = () -> service.confirm(ORDER_ID, EVENT_ID, seatId);

        List<ConfirmResult> results = ConcurrentRunner.runTogether(List.of(confirm, confirm));

        Seat seat = seat(seatId);
        assertSoftly(softly -> {
            softly.assertThat(results).containsExactly(new ConfirmResult.Confirmed(), new ConfirmResult.Confirmed());
            softly.assertThat(seat.getStatus()).isEqualTo(SeatStatus.SOLD);
            softly.assertThat(seat.getHoldExpiresAt()).isNull();
            softly.assertThat(seat.getVersion()).isEqualTo(1L);
        });
    }

    @RepeatedTest(value = ROUNDS, name = "round {currentRepetition} of {totalRepetitions}")
    void releasingTwiceAtTheSameTimeReleasesTheSeatOnce(RepetitionInfo repetition) {
        String seatId = "R-" + repetition.getCurrentRepetition();
        SeatFixtures.insertHeld(jdbc, EVENT_ID, seatId, ORDER_ID, Instant.now().plus(Duration.ofMinutes(5)));
        Callable<Boolean> release = () -> {
            service.release(ORDER_ID, EVENT_ID, seatId);
            return true;
        };

        ConcurrentRunner.runTogether(List.of(release, release));

        Seat seat = seat(seatId);
        assertSoftly(softly -> {
            softly.assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
            softly.assertThat(seat.getHeldByOrderId()).isNull();
            softly.assertThat(seat.getVersion()).isEqualTo(1L);
        });
    }

    @Test
    void repeatedRequestsChangeTheSeatOncePerStep() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, "L-1");

        List<Long> versions = versionsAfterEachRepeatedStep("L-1");

        assertThat(versions).containsExactly(1L, 2L, 2L);
    }

    private List<Long> versionsAfterEachRepeatedStep(String seatId) {
        service.hold(ORDER_ID, EVENT_ID, seatId);
        service.hold(ORDER_ID, EVENT_ID, seatId);
        Long afterTheHolds = seat(seatId).getVersion();
        service.confirm(ORDER_ID, EVENT_ID, seatId);
        service.confirm(ORDER_ID, EVENT_ID, seatId);
        Long afterTheConfirms = seat(seatId).getVersion();
        service.release(ORDER_ID, EVENT_ID, seatId);
        service.release(ORDER_ID, EVENT_ID, seatId);
        Long afterTheReleases = seat(seatId).getVersion();
        return List.of(afterTheHolds, afterTheConfirms, afterTheReleases);
    }

    private Seat seat(String seatId) {
        return repository.findByEventIdAndSeatId(EVENT_ID, seatId).orElseThrow();
    }
}
