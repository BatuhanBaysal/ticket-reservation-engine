package com.ticket.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import com.ticket.events.MessageCodec;
import com.ticket.events.MessageEnvelope;
import com.ticket.events.MessageTypes;
import com.ticket.events.Topics;
import com.ticket.events.inventory.SeatHoldExpired;
import com.ticket.inventory.domain.SeatStatus;
import com.ticket.inventory.repository.SeatRepository;
import com.ticket.inventory.service.SeatService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SeatHoldExpiryOutboxTest {

    private static final String EVENT_ID = "evt-001";
    private static final String SEAT_ID = "A-12";
    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");

    @Autowired
    private SeatService service;

    @Autowired
    private SeatRepository repository;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MessageCodec codec;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void emptyTheTables() {
        jdbc.sql("DELETE FROM seat_inventory").update();
        jdbc.sql("DELETE FROM outbox_event").update();
    }

    @Test
    void writesOneRowForEachExpiredHold() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, "A-1", UUID.randomUUID(), overdue());
        SeatFixtures.insertHeld(jdbc, EVENT_ID, "A-2", UUID.randomUUID(), overdue());
        SeatFixtures.insertHeld(jdbc, EVENT_ID, "A-3", UUID.randomUUID(), overdue());

        service.expireOverdueHolds();

        assertThat(rowCount()).isEqualTo(3L);
    }

    @Test
    void writesTheRowsToTheInventoryEventsTopic() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, "A-1", UUID.randomUUID(), overdue());
        SeatFixtures.insertHeld(jdbc, EVENT_ID, "A-2", UUID.randomUUID(), overdue());

        service.expireOverdueHolds();

        List<String> topics = jdbc.sql("SELECT DISTINCT topic FROM outbox_event").query(String.class).list();
        assertThat(topics).containsExactly(Topics.INVENTORY_EVENTS);
    }

    @Test
    void usesTheOrderIdAsKey() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, overdue());

        service.expireOverdueHolds();

        String key = jdbc.sql("SELECT message_key FROM outbox_event").query(String.class).single();
        assertThat(key).isEqualTo(ORDER_ID.toString());
    }

    @Test
    void writesTheSeatHoldExpiredType() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, overdue());

        service.expireOverdueHolds();

        assertThat(envelopes()).singleElement().satisfies(envelope ->
            assertThat(envelope.type()).isEqualTo(MessageTypes.SEAT_HOLD_EXPIRED));
    }

    @Test
    void writesThePayloadOfTheExpiredHold() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, overdue());

        service.expireOverdueHolds();

        assertThat(envelopes()).singleElement().satisfies(envelope ->
            assertThat(envelope.payload()).isEqualTo(new SeatHoldExpired(ORDER_ID, EVENT_ID, SEAT_ID)));
    }

    @Test
    void givesEveryEventItsOwnCorrelationId() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, "A-1", UUID.randomUUID(), overdue());
        SeatFixtures.insertHeld(jdbc, EVENT_ID, "A-2", UUID.randomUUID(), overdue());

        service.expireOverdueHolds();

        Set<String> correlationIds = envelopes().stream()
            .map(MessageEnvelope::correlationId)
            .collect(Collectors.toSet());
        assertThat(correlationIds).hasSize(2);
    }

    @Test
    void writesNoRowWhenNothingExpired() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, Instant.now().plus(Duration.ofMinutes(5)));

        service.expireOverdueHolds();

        assertThat(rowCount()).isZero();
    }

    @Test
    void writesNoSecondRowOnASecondRun() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, overdue());
        service.expireOverdueHolds();

        service.expireOverdueHolds();

        assertThat(rowCount()).isEqualTo(1L);
    }

    @Test
    void rollsTheSeatAndTheRowBackWhenAnExceptionFollowsTheUpdate() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, overdue());
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Consumer<TransactionStatus> expireThenFail = status -> {
            service.expireOverdueHolds();
            throw new IllegalStateException("boom");
        };

        catchThrowable(() -> transaction.executeWithoutResult(expireThenFail));

        SeatStatus status = repository.findByEventIdAndSeatId(EVENT_ID, SEAT_ID).orElseThrow().getStatus();
        assertSoftly(softly -> {
            softly.assertThat(rowCount()).isZero();
            softly.assertThat(status).isEqualTo(SeatStatus.HELD);
        });
    }

    private Long rowCount() {
        return jdbc.sql("SELECT count(*) FROM outbox_event").query(Long.class).single();
    }

    private List<MessageEnvelope<?>> envelopes() {
        return jdbc.sql("SELECT CAST(payload AS text) FROM outbox_event")
            .query(String.class)
            .list()
            .stream()
            .<MessageEnvelope<?>>map(codec::fromJson)
            .toList();
    }

    private static Instant overdue() {
        return Instant.now().minus(Duration.ofMinutes(1));
    }
}
