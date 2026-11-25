package com.ticket.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;

import com.ticket.events.MessageEnvelope;
import com.ticket.events.MessageTypes;
import com.ticket.events.Topics;
import com.ticket.events.inventory.SeatHoldExpired;
import com.ticket.inventory.messaging.outbox.OutboxPoller;
import com.ticket.inventory.messaging.outbox.OutboxWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.kafka.admin.auto-create=true",
        "spring.kafka.producer.properties.max.block.ms=2000",
        "inventory.outbox.poller.send-timeout=2s",
        "inventory.test.broker=outage"})
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class OutboxPollerBrokerOutageTest {

    private static final Duration LONG_WAIT = Duration.ofSeconds(30);

    @Autowired
    private OutboxPoller poller;

    @Autowired
    private OutboxWriter writer;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private KafkaContainer kafka;

    @BeforeEach
    void emptyTheTable() {
        jdbc.sql("DELETE FROM outbox_event").update();
    }

    @AfterEach
    void runTheBrokerAgain() {
        BrokerControl.unpause(kafka);
    }

    @Test
    void keepsTheRowUnpublishedWhileTheBrokerIsPaused() {
        writeRow(UUID.randomUUID());
        BrokerControl.pause(kafka);

        int published = poller.publishBatch();

        Long unpublished = jdbc.sql("SELECT count(*) FROM outbox_event WHERE published_at IS NULL")
            .query(Long.class)
            .single();
        assertSoftly(softly -> {
            softly.assertThat(published).isZero();
            softly.assertThat(unpublished).isEqualTo(1L);
        });
    }

    @Test
    void publishesTheRowAfterTheBrokerRunsAgain() {
        UUID orderId = UUID.randomUUID();
        writeRow(orderId);
        BrokerControl.pause(kafka);
        poller.publishBatch();
        BrokerControl.unpause(kafka);

        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(1))
            .until(() -> poller.publishBatch() > 0);

        String key = orderId.toString();
        List<ConsumerRecord<String, String>> records = TopicReader
            .readUntil(kafka.getBootstrapServers(), Topics.INVENTORY_EVENTS,
                received -> received.stream().anyMatch(record -> key.equals(record.key())), LONG_WAIT)
            .stream()
            .filter(record -> key.equals(record.key()))
            .toList();
        assertThat(records).isNotEmpty();
    }

    private void writeRow(UUID orderId) {
        MessageEnvelope<SeatHoldExpired> envelope = new MessageEnvelope<>(
            UUID.randomUUID(), "c-test", MessageTypes.SEAT_HOLD_EXPIRED,
            Instant.parse("2026-11-25T10:15:30.123Z"), new SeatHoldExpired(orderId, "evt-001", "A-12"));
        new TransactionTemplate(transactionManager).executeWithoutResult(
            status -> writer.write(Topics.INVENTORY_EVENTS, orderId.toString(), envelope));
    }
}
