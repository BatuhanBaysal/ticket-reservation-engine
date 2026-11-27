package com.ticket.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import com.ticket.events.MessageCodec;
import com.ticket.events.MessageEnvelope;
import com.ticket.events.MessageTypes;
import com.ticket.events.Topics;
import com.ticket.events.inventory.ConfirmSeatCommand;
import com.ticket.events.inventory.HoldSeatCommand;
import com.ticket.events.inventory.ReleaseSeatCommand;
import com.ticket.events.inventory.SeatConfirmFailed;
import com.ticket.events.inventory.SeatConfirmed;
import com.ticket.events.inventory.SeatHeld;
import com.ticket.events.inventory.SeatHoldRejected;
import com.ticket.events.payment.PaymentCompleted;
import com.ticket.inventory.domain.Seat;
import com.ticket.inventory.domain.SeatStatus;
import com.ticket.inventory.repository.SeatRepository;
import com.ticket.inventory.service.SeatCommandHandler;
import com.ticket.inventory.service.SeatService;
import com.ticket.inventory.service.UnsupportedMessageException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, ConflictInjection.class})
class SeatCommandHandlerTest {

    private static final String EVENT_ID = "evt-001";
    private static final String SEAT_ID = "A-12";
    private static final String CORRELATION_ID = "c-7f3a91";
    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");
    private static final UUID OTHER_ORDER_ID = UUID.fromString("c3e1f6a8-2b94-4d57-9a0e-6f8d1b7c4e23");
    private static final String KEY = RedisFixtures.holdKey(EVENT_ID, SEAT_ID);

    @Autowired
    private SeatCommandHandler handler;

    @Autowired
    private SeatService seatService;

    @Autowired
    private SeatRepository repository;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MessageCodec codec;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private ConflictInjector injector;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void emptyTheTablesTheGateAndTheInjector() {
        jdbc.sql("DELETE FROM seat_inventory").update();
        jdbc.sql("DELETE FROM outbox_event").update();
        jdbc.sql("DELETE FROM processed_message").update();
        RedisFixtures.clearHoldKeys(redis);
        injector.reset();
    }

    @Test
    void writesSeatHeldForAnAvailableSeat() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);

        handler.handle(holdCommand(ORDER_ID));

        assertThat(events()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(MessageTypes.SEAT_HELD);
            assertThat(event.payload()).isInstanceOf(SeatHeld.class);
        });
    }

    @Test
    void marksTheSeatAsHeldByTheOrder() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);

        handler.handle(holdCommand(ORDER_ID));

        Seat seat = seat(SEAT_ID);
        assertSoftly(softly -> {
            softly.assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
            softly.assertThat(seat.getHeldByOrderId()).isEqualTo(ORDER_ID);
        });
    }

    @Test
    void carriesThePriceAndTheExpiryInSeatHeld() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);

        handler.handle(holdCommand(ORDER_ID));

        assertThat(events()).singleElement().satisfies(event -> {
            SeatHeld held = (SeatHeld) event.payload();
            assertThat(held.price()).isEqualByComparingTo("49.9000");
            assertThat(held.holdExpiresAt()).isAfter(Instant.now());
        });
    }

    @Test
    void writesSeatHoldRejectedWhenAnotherOrderHoldsTheSeat() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, OTHER_ORDER_ID, inFiveMinutes());

        handler.handle(holdCommand(ORDER_ID));

        assertThat(events()).singleElement().satisfies(event ->
            assertThat(event.payload()).isEqualTo(
                new SeatHoldRejected(ORDER_ID, EVENT_ID, SEAT_ID, SeatHoldRejected.SEAT_UNAVAILABLE)));
    }

    @Test
    void writesSeatHoldRejectedForAnUnknownSeat() {
        handler.handle(holdCommand(ORDER_ID));

        assertThat(events()).singleElement().satisfies(event ->
            assertThat(event.payload()).isEqualTo(
                new SeatHoldRejected(ORDER_ID, EVENT_ID, SEAT_ID, SeatHoldRejected.SEAT_NOT_FOUND)));
    }

    @Test
    void rejectsWithoutTouchingTheSeatWhenTheGateRejects() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);
        redis.opsForValue().set(KEY, OTHER_ORDER_ID.toString());

        handler.handle(holdCommand(ORDER_ID));

        Seat seat = seat(SEAT_ID);
        List<MessageEnvelope<?>> events = events();
        assertSoftly(softly -> {
            softly.assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
            softly.assertThat(seat.getVersion()).isZero();
            softly.assertThat(events).hasSize(1);
            softly.assertThat(events.get(0).payload()).isEqualTo(
                new SeatHoldRejected(ORDER_ID, EVENT_ID, SEAT_ID, SeatHoldRejected.SEAT_UNAVAILABLE));
        });
    }

    @Test
    void releasesTheGateKeyWhenTheDatabaseRejectsTheHold() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, OTHER_ORDER_ID, inFiveMinutes());

        handler.handle(holdCommand(ORDER_ID));

        assertThat(redis.hasKey(KEY)).isFalse();
    }

    @Test
    void keepsTheGateKeyWhenTheHoldSucceeds() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);

        handler.handle(holdCommand(ORDER_ID));

        assertThat(redis.opsForValue().get(KEY)).isEqualTo(ORDER_ID.toString());
    }

    @Test
    void releasesTheGateKeyOfARepeatedRejection() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, OTHER_ORDER_ID, inFiveMinutes());
        MessageEnvelope<Object> message = holdCommand(ORDER_ID);
        handler.handle(message);

        handler.handle(message);

        assertThat(redis.hasKey(KEY)).isFalse();
    }

    @Test
    void writesSeatConfirmedForAHeldSeat() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, inFiveMinutes());

        handler.handle(confirmCommand(ORDER_ID));

        assertThat(events()).singleElement().satisfies(event ->
            assertThat(event.payload()).isEqualTo(new SeatConfirmed(ORDER_ID, EVENT_ID, SEAT_ID)));
    }

    @Test
    void sellsTheSeatWhenItIsConfirmed() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, inFiveMinutes());

        handler.handle(confirmCommand(ORDER_ID));

        assertThat(seat(SEAT_ID).getStatus()).isEqualTo(SeatStatus.SOLD);
    }

    @Test
    void writesSeatConfirmFailedWithSeatTaken() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, OTHER_ORDER_ID, inFiveMinutes());

        handler.handle(confirmCommand(ORDER_ID));

        assertThat(events()).singleElement().satisfies(event ->
            assertThat(event.payload()).isEqualTo(
                new SeatConfirmFailed(ORDER_ID, EVENT_ID, SEAT_ID, SeatConfirmFailed.SEAT_TAKEN)));
    }

    @Test
    void writesSeatConfirmFailedWithHoldNotFound() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);

        handler.handle(confirmCommand(ORDER_ID));

        assertThat(events()).singleElement().satisfies(event ->
            assertThat(event.payload()).isEqualTo(
                new SeatConfirmFailed(ORDER_ID, EVENT_ID, SEAT_ID, SeatConfirmFailed.HOLD_NOT_FOUND)));
    }

    @Test
    void releasesTheSeat() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, inFiveMinutes());

        handler.handle(releaseCommand(ORDER_ID));

        assertThat(seat(SEAT_ID).getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void writesNoEventForARelease() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, inFiveMinutes());

        handler.handle(releaseCommand(ORDER_ID));

        assertThat(count("SELECT count(*) FROM outbox_event")).isZero();
    }

    @Test
    void releasesTheGateKeyAfterARelease() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, inFiveMinutes());
        redis.opsForValue().set(KEY, ORDER_ID.toString());

        handler.handle(releaseCommand(ORDER_ID));

        assertThat(redis.hasKey(KEY)).isFalse();
    }

    @Test
    void holdingTheSameMessageTwiceWritesOneEvent() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);
        MessageEnvelope<Object> message = holdCommand(ORDER_ID);
        handler.handle(message);

        handler.handle(message);

        assertThat(count("SELECT count(*) FROM outbox_event")).isEqualTo(1L);
    }

    @Test
    void holdingTheSameMessageTwiceChangesTheSeatOnce() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);
        MessageEnvelope<Object> message = holdCommand(ORDER_ID);
        handler.handle(message);

        handler.handle(message);

        assertThat(seat(SEAT_ID).getVersion()).isEqualTo(1L);
    }

    @Test
    void confirmingTheSameMessageTwiceWritesOneEvent() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, inFiveMinutes());
        MessageEnvelope<Object> message = confirmCommand(ORDER_ID);
        handler.handle(message);

        handler.handle(message);

        assertThat(count("SELECT count(*) FROM outbox_event")).isEqualTo(1L);
    }

    @Test
    void confirmingTheSameMessageTwiceChangesTheSeatOnce() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, inFiveMinutes());
        MessageEnvelope<Object> message = confirmCommand(ORDER_ID);
        handler.handle(message);

        handler.handle(message);

        assertThat(seat(SEAT_ID).getVersion()).isEqualTo(1L);
    }

    @Test
    void releasingTheSameMessageTwiceChangesTheSeatOnce() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, inFiveMinutes());
        MessageEnvelope<Object> message = releaseCommand(ORDER_ID);
        handler.handle(message);

        handler.handle(message);

        assertThat(seat(SEAT_ID).getVersion()).isEqualTo(1L);
    }

    @Test
    void storesOneInboxRowForARepeatedMessage() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);
        MessageEnvelope<Object> message = holdCommand(ORDER_ID);
        handler.handle(message);

        handler.handle(message);

        assertThat(count("SELECT count(*) FROM processed_message")).isEqualTo(1L);
    }

    @Test
    void handlesTwoMessagesWithTheSameContentSeparately() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);

        handler.handle(holdCommand(ORDER_ID));
        handler.handle(holdCommand(ORDER_ID));

        assertThat(count("SELECT count(*) FROM processed_message")).isEqualTo(2L);
    }

    @RepeatedTest(value = 20, name = "round {currentRepetition} of {totalRepetitions}")
    void handlingTheSameMessageAtTheSameMomentHasOneEffect(RepetitionInfo repetition) {
        String seatId = "D-" + repetition.getCurrentRepetition();
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, seatId);
        MessageEnvelope<Object> message =
            envelopeOf(MessageTypes.HOLD_SEAT_COMMAND, new HoldSeatCommand(ORDER_ID, EVENT_ID, seatId));
        Callable<Boolean> handle = () -> {
            handler.handle(message);
            return true;
        };

        ConcurrentRunner.runTogether(List.of(handle, handle));

        Seat seat = seat(seatId);
        assertSoftly(softly -> {
            softly.assertThat(count("SELECT count(*) FROM outbox_event")).isEqualTo(1L);
            softly.assertThat(count("SELECT count(*) FROM processed_message")).isEqualTo(1L);
            softly.assertThat(seat.getVersion()).isEqualTo(1L);
        });
    }

    @Test
    void rollsEverythingBackWhenAnExceptionFollowsAConfirm() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, inFiveMinutes());
        MessageEnvelope<Object> message = confirmCommand(ORDER_ID);

        catchThrowable(() -> handleInAnOuterTransactionThenFail(message));

        assertNothingIsLeftBehind(SeatStatus.HELD);
    }

    @Test
    void rollsEverythingBackWhenAnExceptionFollowsAHold() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);
        MessageEnvelope<Object> message = holdCommand(ORDER_ID);

        catchThrowable(() -> handleInAnOuterTransactionThenFail(message));

        assertNothingIsLeftBehind(SeatStatus.AVAILABLE);
    }

    @Test
    void retriesTheHandlerAfterAVersionConflict() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, overdue());
        injector.runOnceAfterTheNextLoad(seatService::expireOverdueHolds);

        handler.handle(confirmCommand(ORDER_ID));

        assertSoftly(softly -> {
            softly.assertThat(injector.loads()).isEqualTo(2);
            softly.assertThat(count("SELECT count(*) FROM processed_message")).isEqualTo(1L);
        });
    }

    @Test
    void writesTheFailureOfTheRetriedAttempt() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, overdue());
        injector.runOnceAfterTheNextLoad(seatService::expireOverdueHolds);

        handler.handle(confirmCommand(ORDER_ID));

        List<MessageEnvelope<?>> failures = events().stream()
            .filter(event -> MessageTypes.SEAT_CONFIRM_FAILED.equals(event.type()))
            .toList();
        assertThat(failures).singleElement().satisfies(event ->
            assertThat(event.payload()).isEqualTo(
                new SeatConfirmFailed(ORDER_ID, EVENT_ID, SEAT_ID, SeatConfirmFailed.HOLD_NOT_FOUND)));
    }

    @Test
    void keepsTheCorrelationIdOfAHoldInSeatHeld() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);

        handler.handle(holdCommand(ORDER_ID));

        assertThat(events()).singleElement().satisfies(event ->
            assertThat(event.correlationId()).isEqualTo(CORRELATION_ID));
    }

    @Test
    void keepsTheCorrelationIdOfAConfirmInSeatConfirmed() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, inFiveMinutes());

        handler.handle(confirmCommand(ORDER_ID));

        assertThat(events()).singleElement().satisfies(event ->
            assertThat(event.correlationId()).isEqualTo(CORRELATION_ID));
    }

    @Test
    void usesTheOrderIdAsTheKey() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);

        handler.handle(holdCommand(ORDER_ID));

        String key = jdbc.sql("SELECT message_key FROM outbox_event").query(String.class).single();
        assertThat(key).isEqualTo(ORDER_ID.toString());
    }

    @Test
    void writesToTheInventoryEventsTopic() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);

        handler.handle(holdCommand(ORDER_ID));

        String topic = jdbc.sql("SELECT topic FROM outbox_event").query(String.class).single();
        assertThat(topic).isEqualTo(Topics.INVENTORY_EVENTS);
    }

    @Test
    void throwsForAPayloadThatInventoryDoesNotHandle() {
        MessageEnvelope<Object> message = envelopeOf(MessageTypes.PAYMENT_COMPLETED,
            new PaymentCompleted(UUID.randomUUID(), ORDER_ID, new BigDecimal("49.90")));

        assertThatThrownBy(() -> handler.handle(message))
            .isInstanceOf(UnsupportedMessageException.class)
            .hasMessageContaining("PaymentCompleted");
    }

    @Test
    void storesNoInboxRowForAPayloadThatInventoryDoesNotHandle() {
        MessageEnvelope<Object> message = envelopeOf(MessageTypes.PAYMENT_COMPLETED,
            new PaymentCompleted(UUID.randomUUID(), ORDER_ID, new BigDecimal("49.90")));
        catchThrowable(() -> handler.handle(message));

        Long rows = count("SELECT count(*) FROM processed_message");

        assertThat(rows).isZero();
    }

    @Test
    void rejectsAMissingEnvelope() {
        assertThatThrownBy(() -> handler.handle(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("envelope");
    }

    private void handleInAnOuterTransactionThenFail(MessageEnvelope<Object> message) {
        Consumer<TransactionStatus> handleThenFail = status -> {
            handler.handle(message);
            throw new IllegalStateException("boom");
        };
        new TransactionTemplate(transactionManager).executeWithoutResult(handleThenFail);
    }

    private void assertNothingIsLeftBehind(SeatStatus expectedStatus) {
        Seat seat = seat(SEAT_ID);
        assertSoftly(softly -> {
            softly.assertThat(seat.getStatus()).isEqualTo(expectedStatus);
            softly.assertThat(seat.getVersion()).isZero();
            softly.assertThat(count("SELECT count(*) FROM processed_message")).isZero();
            softly.assertThat(count("SELECT count(*) FROM outbox_event")).isZero();
        });
    }

    private List<MessageEnvelope<?>> events() {
        return jdbc.sql("SELECT CAST(payload AS text) FROM outbox_event ORDER BY created_at")
            .query(String.class)
            .list()
            .stream()
            .<MessageEnvelope<?>>map(codec::fromJson)
            .toList();
    }

    private Seat seat(String seatId) {
        return repository.findByEventIdAndSeatId(EVENT_ID, seatId).orElseThrow();
    }

    private Long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private static MessageEnvelope<Object> holdCommand(UUID orderId) {
        return envelopeOf(MessageTypes.HOLD_SEAT_COMMAND, new HoldSeatCommand(orderId, EVENT_ID, SEAT_ID));
    }

    private static MessageEnvelope<Object> confirmCommand(UUID orderId) {
        return envelopeOf(MessageTypes.CONFIRM_SEAT_COMMAND, new ConfirmSeatCommand(orderId, EVENT_ID, SEAT_ID));
    }

    private static MessageEnvelope<Object> releaseCommand(UUID orderId) {
        return envelopeOf(MessageTypes.RELEASE_SEAT_COMMAND, new ReleaseSeatCommand(orderId, EVENT_ID, SEAT_ID));
    }

    private static MessageEnvelope<Object> envelopeOf(String type, Object payload) {
        return new MessageEnvelope<>(UUID.randomUUID(), CORRELATION_ID, type,
            Instant.parse("2026-11-27T10:15:30.123Z"), payload);
    }

    private static Instant inFiveMinutes() {
        return Instant.now().plus(Duration.ofMinutes(5));
    }

    private static Instant overdue() {
        return Instant.now().minus(Duration.ofMinutes(1));
    }
}
