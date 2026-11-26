package com.ticket.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import com.ticket.inventory.domain.Seat;
import com.ticket.inventory.domain.SeatStatus;
import com.ticket.inventory.repository.SeatRepository;
import com.ticket.inventory.service.ConfirmResult;
import com.ticket.inventory.service.HoldResult;
import com.ticket.inventory.service.SeatSteps;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SeatStepsTest {

    private static final String EVENT_ID = "evt-001";
    private static final String SEAT_ID = "A-12";
    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");

    @Autowired
    private SeatSteps steps;

    @Autowired
    private SeatRepository repository;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;

    @BeforeEach
    void emptyTheTable() {
        jdbc.sql("DELETE FROM seat_inventory").update();
        transaction = new TransactionTemplate(transactionManager);
    }

    @Test
    void holdsAnAvailableSeatInsideATransaction() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);

        HoldResult result = transaction.execute(status -> steps.hold(ORDER_ID, EVENT_ID, SEAT_ID));

        assertThat(result).isInstanceOf(HoldResult.Held.class);
    }

    @Test
    void confirmsAHeldSeatInsideATransaction() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, inFiveMinutes());

        ConfirmResult result = transaction.execute(status -> steps.confirm(ORDER_ID, EVENT_ID, SEAT_ID));

        assertThat(result).isEqualTo(new ConfirmResult.Confirmed());
    }

    @Test
    void releasesAHeldSeatInsideATransaction() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, inFiveMinutes());

        Boolean released = transaction.execute(status -> steps.release(ORDER_ID, EVENT_ID, SEAT_ID));

        assertThat(released).isTrue();
    }

    @Test
    void returnsFalseWhenNothingIsReleased() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);

        Boolean released = transaction.execute(status -> steps.release(ORDER_ID, EVENT_ID, SEAT_ID));

        assertThat(released).isFalse();
    }

    @Test
    void theHoldStepFailsWithoutATransaction() {
        assertThatThrownBy(() -> steps.hold(ORDER_ID, EVENT_ID, SEAT_ID))
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void theConfirmStepFailsWithoutATransaction() {
        assertThatThrownBy(() -> steps.confirm(ORDER_ID, EVENT_ID, SEAT_ID))
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void theReleaseStepFailsWithoutATransaction() {
        assertThatThrownBy(() -> steps.release(ORDER_ID, EVENT_ID, SEAT_ID))
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void theHoldStepRollsBackWithTheTransaction() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);
        Consumer<TransactionStatus> holdThenFail = status -> {
            steps.hold(ORDER_ID, EVENT_ID, SEAT_ID);
            throw new IllegalStateException("boom");
        };

        catchThrowable(() -> transaction.executeWithoutResult(holdThenFail));

        assertTheSeatIsUnchanged(SeatStatus.AVAILABLE);
    }

    @Test
    void theConfirmStepRollsBackWithTheTransaction() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, inFiveMinutes());
        Consumer<TransactionStatus> confirmThenFail = status -> {
            steps.confirm(ORDER_ID, EVENT_ID, SEAT_ID);
            throw new IllegalStateException("boom");
        };

        catchThrowable(() -> transaction.executeWithoutResult(confirmThenFail));

        assertTheSeatIsUnchanged(SeatStatus.HELD);
    }

    @Test
    void theReleaseStepRollsBackWithTheTransaction() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, inFiveMinutes());
        Consumer<TransactionStatus> releaseThenFail = status -> {
            steps.release(ORDER_ID, EVENT_ID, SEAT_ID);
            throw new IllegalStateException("boom");
        };

        catchThrowable(() -> transaction.executeWithoutResult(releaseThenFail));

        assertTheSeatIsUnchanged(SeatStatus.HELD);
    }

    private void assertTheSeatIsUnchanged(SeatStatus expectedStatus) {
        Seat seat = repository.findByEventIdAndSeatId(EVENT_ID, SEAT_ID).orElseThrow();
        assertSoftly(softly -> {
            softly.assertThat(seat.getStatus()).isEqualTo(expectedStatus);
            softly.assertThat(seat.getVersion()).isZero();
        });
    }

    private static Instant inFiveMinutes() {
        return Instant.now().plus(Duration.ofMinutes(5));
    }
}
