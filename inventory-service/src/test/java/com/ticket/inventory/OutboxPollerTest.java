package com.ticket.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import com.ticket.events.MessageCodec;
import com.ticket.events.MessageEnvelope;
import com.ticket.events.MessageTypes;
import com.ticket.events.Topics;
import com.ticket.events.inventory.SeatHoldExpired;
import com.ticket.inventory.messaging.outbox.OutboxPoller;
import com.ticket.inventory.messaging.outbox.OutboxWriter;
import com.ticket.inventory.service.SeatService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.kafka.admin.auto-create=true",
        "spring.kafka.producer.properties.max.block.ms=2000",
        "inventory.outbox.poller.send-timeout=2s"})
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class OutboxPollerTest {

    private static final String EVENT_ID = "evt-001";
    private static final String INVALID_TOPIC = "invalid topic";
    private static final Duration LONG_WAIT = Duration.ofSeconds(30);
    private static final Duration QUIET_WAIT = Duration.ofSeconds(3);

    @Autowired
    private OutboxPoller poller;

    @Autowired
    private OutboxWriter writer;

    @Autowired
    private SeatService seatService;

    @Autowired
    private MessageCodec codec;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private KafkaContainer kafka;

    private TransactionTemplate transaction;

    @BeforeEach
    void emptyTheTables() {
        jdbc.sql("DELETE FROM outbox_event").update();
        jdbc.sql("DELETE FROM seat_inventory").update();
        transaction = new TransactionTemplate(transactionManager);
    }

    @Test
    void sendsTheRowToTheInventoryEventsTopic() {
        UUID orderId = writeNewRow();

        poller.publishBatch();

        assertThat(recordsOf(orderId, 1)).hasSize(1);
    }

    @Test
    void sendsTheEnvelopeAsTheValue() {
        UUID orderId = UUID.randomUUID();
        MessageEnvelope<SeatHoldExpired> envelope = envelopeOf(orderId, "A-12");
        writeRow(envelope, orderId);
        poller.publishBatch();

        MessageEnvelope<?> received = codec.fromJson(recordsOf(orderId, 1).get(0).value());

        assertThat(received).isEqualTo(envelope);
    }

    @Test
    void marksTheRowAsPublished() {
        writeNewRow();

        poller.publishBatch();

        assertThat(count("SELECT count(*) FROM outbox_event WHERE published_at IS NOT NULL")).isEqualTo(1L);
    }

    @Test
    void returnsTheNumberOfPublishedRows() {
        writeRows(3);

        int published = poller.publishBatch();

        assertThat(published).isEqualTo(3);
    }

    @Test
    void publishesNothingWhenThereAreNoRows() {
        int published = poller.publishBatch();

        assertThat(published).isZero();
    }

    @Test
    void ignoresRowsThatArePublishedAlready() {
        UUID orderId = UUID.randomUUID();
        String payload = codec.toJson(envelopeOf(orderId, "A-12"));
        OutboxFixtures.insert(jdbc, orderId, Topics.INVENTORY_EVENTS, orderId.toString(), payload,
            Instant.now().minus(Duration.ofMinutes(5)), Instant.now().minus(Duration.ofMinutes(4)));

        int published = poller.publishBatch();

        assertThat(published).isZero();
    }

    @Test
    void publishesAtMostOneHundredRowsInOneRun() {
        writeRows(120);

        int published = poller.publishBatch();

        assertSoftly(softly -> {
            softly.assertThat(published).isEqualTo(100);
            softly.assertThat(count("SELECT count(*) FROM outbox_event WHERE published_at IS NULL")).isEqualTo(20L);
        });
    }

    @Test
    void keepsTheOrderOfOneKey() {
        UUID orderId = UUID.randomUUID();
        List<String> seatIds = List.of("S-1", "S-2", "S-3", "S-4", "S-5");
        seatIds.forEach(seatId -> writeRow(envelopeOf(orderId, seatId), orderId));
        poller.publishBatch();

        List<String> received = recordsOf(orderId, seatIds.size()).stream().map(this::seatIdOf).toList();

        assertThat(received).containsExactlyElementsOf(seatIds);
    }

    @Test
    void twoPollersAtTheSameTimePublishEachRowOnce() {
        List<UUID> orderIds = writeRows(200);
        Callable<Integer> run = poller::publishBatch;

        List<Integer> published = ConcurrentRunner.runTogether(List.of(run, run));

        Set<String> keys = orderIds.stream().map(UUID::toString).collect(Collectors.toSet());
        Map<String, Long> perKey = TopicReader
            .readFor(kafka.getBootstrapServers(), Topics.INVENTORY_EVENTS, Duration.ofSeconds(5))
            .stream()
            .filter(record -> keys.contains(record.key()))
            .collect(Collectors.groupingBy(ConsumerRecord::key, Collectors.counting()));
        assertSoftly(softly -> {
            softly.assertThat(published.stream().mapToInt(Integer::intValue).sum()).isEqualTo(200);
            softly.assertThat(perKey).hasSize(200);
            softly.assertThat(perKey.values()).allMatch(count -> count == 1L);
        });
    }

    @Test
    void publishesNoMessageWhenAnExceptionFollowsTheExpiry() {
        UUID orderId = UUID.randomUUID();
        SeatFixtures.insertHeld(jdbc, EVENT_ID, "A-12", orderId, Instant.now().minus(Duration.ofMinutes(1)));
        Consumer<TransactionStatus> expireThenFail = status -> {
            seatService.expireOverdueHolds();
            throw new IllegalStateException("boom");
        };
        catchThrowable(() -> transaction.executeWithoutResult(expireThenFail));

        int published = poller.publishBatch();

        List<ConsumerRecord<String, String>> records = TopicReader
            .readFor(kafka.getBootstrapServers(), Topics.INVENTORY_EVENTS, QUIET_WAIT)
            .stream()
            .filter(record -> orderId.toString().equals(record.key()))
            .toList();
        assertSoftly(softly -> {
            softly.assertThat(published).isZero();
            softly.assertThat(records).isEmpty();
        });
    }

    @Test
    void commitsTheRowsSentBeforeAFailedSend() {
        RowsAroundAFailure rows = insertRowsAroundAFailure();

        int published = poller.publishBatch();

        Boolean firstIsPublished = jdbc
            .sql("SELECT published_at IS NOT NULL FROM outbox_event WHERE id = :id")
            .param("id", rows.first())
            .query(Boolean.class)
            .single();
        assertSoftly(softly -> {
            softly.assertThat(published).isEqualTo(1);
            softly.assertThat(firstIsPublished).isTrue();
        });
    }

    @Test
    void keepsTheFailedRowAndTheLaterRowsUnpublished() {
        RowsAroundAFailure rows = insertRowsAroundAFailure();

        poller.publishBatch();

        List<UUID> unpublished = jdbc
            .sql("SELECT id FROM outbox_event WHERE published_at IS NULL ORDER BY created_at")
            .query(UUID.class)
            .list();
        assertThat(unpublished).containsExactly(rows.failing(), rows.last());
    }

    private RowsAroundAFailure insertRowsAroundAFailure() {
        UUID first = UUID.randomUUID();
        UUID failing = UUID.randomUUID();
        UUID last = UUID.randomUUID();
        insertRow(first, Topics.INVENTORY_EVENTS, Instant.now().minus(Duration.ofMinutes(3)));
        insertRow(failing, INVALID_TOPIC, Instant.now().minus(Duration.ofMinutes(2)));
        insertRow(last, Topics.INVENTORY_EVENTS, Instant.now().minus(Duration.ofMinutes(1)));
        return new RowsAroundAFailure(first, failing, last);
    }

    private void insertRow(UUID id, String topic, Instant createdAt) {
        String payload = codec.toJson(envelopeOf(id, "A-12"));
        OutboxFixtures.insert(jdbc, id, topic, id.toString(), payload, createdAt, null);
    }

    private UUID writeNewRow() {
        UUID orderId = UUID.randomUUID();
        writeRow(envelopeOf(orderId, "A-12"), orderId);
        return orderId;
    }

    private void writeRow(MessageEnvelope<SeatHoldExpired> envelope, UUID orderId) {
        transaction.executeWithoutResult(
            status -> writer.write(Topics.INVENTORY_EVENTS, orderId.toString(), envelope));
    }

    private List<UUID> writeRows(int count) {
        List<UUID> orderIds = IntStream.range(0, count).mapToObj(number -> UUID.randomUUID()).toList();
        transaction.executeWithoutResult(status -> orderIds.forEach(orderId ->
            writer.write(Topics.INVENTORY_EVENTS, orderId.toString(), envelopeOf(orderId, "A-12"))));
        return orderIds;
    }

    private List<ConsumerRecord<String, String>> recordsOf(UUID orderId, int count) {
        String key = orderId.toString();
        return TopicReader
            .readUntil(kafka.getBootstrapServers(), Topics.INVENTORY_EVENTS,
                records -> withKey(records, key).size() >= count, LONG_WAIT)
            .stream()
            .filter(record -> key.equals(record.key()))
            .toList();
    }

    private static List<ConsumerRecord<String, String>> withKey(List<ConsumerRecord<String, String>> records, String key) {
        return records.stream().filter(record -> key.equals(record.key())).toList();
    }

    private String seatIdOf(ConsumerRecord<String, String> record) {
        SeatHoldExpired payload = (SeatHoldExpired) codec.fromJson(record.value()).payload();
        return payload.seatId();
    }

    private Long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private static MessageEnvelope<SeatHoldExpired> envelopeOf(UUID orderId, String seatId) {
        SeatHoldExpired payload = new SeatHoldExpired(orderId, EVENT_ID, seatId);
        return new MessageEnvelope<>(UUID.randomUUID(), "c-test", MessageTypes.SEAT_HOLD_EXPIRED,
            Instant.parse("2026-11-25T10:15:30.123Z"), payload);
    }

    private record RowsAroundAFailure(UUID first, UUID failing, UUID last) {
    }
}
