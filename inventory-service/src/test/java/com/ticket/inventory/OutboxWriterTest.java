package com.ticket.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.ticket.events.MessageCodec;
import com.ticket.events.MessageEnvelope;
import com.ticket.events.MessageTypes;
import com.ticket.events.Topics;
import com.ticket.events.inventory.SeatHoldExpired;
import com.ticket.inventory.messaging.outbox.OutboxWriter;
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
class OutboxWriterTest {

    private static final UUID MESSAGE_ID = UUID.fromString("0f8b2c36-5d0e-4c53-9b1a-7e2a5f4d9c10");
    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");
    private static final Instant OCCURRED_AT = Instant.parse("2026-11-24T10:15:30.123Z");
    private static final String KEY = ORDER_ID.toString();

    @Autowired
    private OutboxWriter writer;

    @Autowired
    private MessageCodec codec;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;

    @BeforeEach
    void emptyTheTable() {
        jdbc.sql("DELETE FROM outbox_event").update();
        transaction = new TransactionTemplate(transactionManager);
    }

    @Test
    void storesTheTopic() {
        writeInATransaction();

        String topic = jdbc.sql("SELECT topic FROM outbox_event").query(String.class).single();

        assertThat(topic).isEqualTo("inventory.events");
    }

    @Test
    void storesTheKey() {
        writeInATransaction();

        String key = jdbc.sql("SELECT message_key FROM outbox_event").query(String.class).single();

        assertThat(key).isEqualTo(KEY);
    }

    @Test
    void storesTheEnvelopeAsJson() {
        writeInATransaction();
        String json = jdbc.sql("SELECT CAST(payload AS text) FROM outbox_event").query(String.class).single();

        MessageEnvelope<?> stored = codec.fromJson(json);

        assertThat(stored).isEqualTo(envelope());
    }

    @Test
    void leavesTheRowUnpublished() {
        writeInATransaction();

        Long unpublished = count("SELECT count(*) FROM outbox_event WHERE published_at IS NULL");

        assertThat(unpublished).isEqualTo(1L);
    }

    @Test
    void fillsTheCreationTimeByDefault() {
        writeInATransaction();

        Long withCreationTime = count("SELECT count(*) FROM outbox_event WHERE created_at IS NOT NULL");

        assertThat(withCreationTime).isEqualTo(1L);
    }

    @Test
    void failsWithoutATransaction() {
        MessageEnvelope<SeatHoldExpired> envelope = envelope();

        assertThatThrownBy(() -> writer.write(Topics.INVENTORY_EVENTS, KEY, envelope))
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void writesNothingWhenTheTransactionRollsBack() {
        Consumer<TransactionStatus> writeThenFail = status -> {
            writer.write(Topics.INVENTORY_EVENTS, KEY, envelope());
            throw new IllegalStateException("boom");
        };
        catchThrowable(() -> transaction.executeWithoutResult(writeThenFail));

        Long rows = count("SELECT count(*) FROM outbox_event");

        assertThat(rows).isZero();
    }

    @Test
    void rejectsABlankTopic() {
        MessageEnvelope<SeatHoldExpired> envelope = envelope();

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> writer.write(" ", KEY, envelope)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("topic must not be blank");
    }

    @Test
    void rejectsABlankKey() {
        MessageEnvelope<SeatHoldExpired> envelope = envelope();

        assertThatThrownBy(() -> transaction.executeWithoutResult(
            status -> writer.write(Topics.INVENTORY_EVENTS, " ", envelope)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("key must not be blank");
    }

    @Test
    void rejectsAMissingEnvelope() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(
            status -> writer.write(Topics.INVENTORY_EVENTS, KEY, null)))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("envelope");
    }

    @Test
    void createsThePartialIndex() {
        String definition = jdbc.sql("""
                SELECT indexdef FROM pg_indexes
                WHERE tablename = 'outbox_event' AND indexname = 'idx_outbox_event_unpublished'
                """)
            .query(String.class)
            .single();

        assertThat(definition).contains("(created_at)").contains("published_at IS NULL");
    }

    private void writeInATransaction() {
        MessageEnvelope<SeatHoldExpired> envelope = envelope();
        transaction.executeWithoutResult(status -> writer.write(Topics.INVENTORY_EVENTS, KEY, envelope));
    }

    private Long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private static MessageEnvelope<SeatHoldExpired> envelope() {
        SeatHoldExpired payload = new SeatHoldExpired(ORDER_ID, "evt-001", "A-12");
        return new MessageEnvelope<>(MESSAGE_ID, "c-7f3a91", MessageTypes.SEAT_HOLD_EXPIRED, OCCURRED_AT, payload);
    }
}
