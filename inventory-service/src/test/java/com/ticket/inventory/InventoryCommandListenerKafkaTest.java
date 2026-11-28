package com.ticket.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ticket.events.MessageCodec;
import com.ticket.events.MessageEnvelope;
import com.ticket.events.MessageTypes;
import com.ticket.events.Topics;
import com.ticket.events.inventory.HoldSeatCommand;
import com.ticket.events.inventory.SeatHeld;
import com.ticket.events.payment.PaymentCompleted;
import com.ticket.inventory.messaging.kafka.DeadLetterTopics;
import com.ticket.inventory.service.SeatCommandHandler;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.kafka.admin.auto-create=true",
        "inventory.consumer.enabled=true",
        "inventory.consumer.retry-interval=100ms",
        "inventory.outbox.poller.enabled=true",
        "inventory.outbox.poller.interval=200ms"})
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class InventoryCommandListenerKafkaTest {

    private static final String EVENT_ID = "evt-001";
    private static final String SEAT_ID = "A-12";
    private static final String GROUP = "inventory-service";
    private static final String COMMANDS_DLT = DeadLetterTopics.of(Topics.INVENTORY_COMMANDS);
    private static final Duration LONG_WAIT = Duration.ofSeconds(30);

    @MockitoSpyBean
    private SeatCommandHandler handler;

    @MockitoSpyBean
    private MessageCodec codec;

    @Autowired
    private KafkaTemplate<String, String> template;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private Environment environment;

    @BeforeEach
    void emptyTheSeatsAndTheGate() {
        jdbc.sql("DELETE FROM seat_inventory").update();
        RedisFixtures.clearHoldKeys(redis);
    }

    @Test
    void turnsAHoldCommandIntoSeatHeld() {
        UUID orderId = UUID.randomUUID();
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);
        send(orderId.toString(), codec.toJson(holdCommand(orderId, SEAT_ID, "c-1")));

        List<ConsumerRecord<String, String>> events = recordsOf(Topics.INVENTORY_EVENTS, orderId, 1);

        MessageEnvelope<?> event = codec.fromJson(events.get(0).value());
        assertSoftly(softly -> {
            softly.assertThat(events.get(0).key()).isEqualTo(orderId.toString());
            softly.assertThat(event.type()).isEqualTo(MessageTypes.SEAT_HELD);
            softly.assertThat(event.payload()).isInstanceOf(SeatHeld.class);
        });
    }

    @Test
    void keepsTheCorrelationIdOfTheCommand() {
        UUID orderId = UUID.randomUUID();
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);
        send(orderId.toString(), codec.toJson(holdCommand(orderId, SEAT_ID, "c-corr-1")));

        List<ConsumerRecord<String, String>> events = recordsOf(Topics.INVENTORY_EVENTS, orderId, 1);

        assertThat(codec.fromJson(events.get(0).value()).correlationId()).isEqualTo("c-corr-1");
    }

    @Test
    void producesOneEventForTheSameCommandTwice() {
        UUID orderId = UUID.randomUUID();
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, "A-12");
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, "A-13");
        String repeated = codec.toJson(holdCommand(orderId, "A-12", "c-1"));
        send(orderId.toString(), repeated);
        send(orderId.toString(), repeated);
        send(orderId.toString(), codec.toJson(holdCommand(orderId, "A-13", "c-2")));
        await().atMost(LONG_WAIT).until(() -> eventRows(orderId, "A-13") == 1L);

        Long events = eventRows(orderId, "");

        assertThat(events).isEqualTo(2L);
    }

    @Test
    void sendsAnInvalidMessageToTheDeadLetterTopic() {
        UUID orderId = UUID.randomUUID();
        send(orderId.toString(), "this is not json");

        List<ConsumerRecord<String, String>> deadLetters = recordsOf(COMMANDS_DLT, orderId, 1);

        assertThat(deadLetters).singleElement().satisfies(record ->
            assertThat(record.value()).isEqualTo("this is not json"));
    }

    @Test
    void doesNotRetryAnInvalidMessage() {
        UUID orderId = UUID.randomUUID();
        String value = "this is not json either " + orderId;
        send(orderId.toString(), value);
        recordsOf(COMMANDS_DLT, orderId, 1);

        verify(codec, times(1)).fromJson(value);
    }

    @Test
    void keepsTheKeyAndThePartitionOfADeadLetter() {
        UUID orderId = UUID.randomUUID();
        RecordMetadata sent = send(orderId.toString(), "this is not json");

        List<ConsumerRecord<String, String>> deadLetters = recordsOf(COMMANDS_DLT, orderId, 1);

        assertSoftly(softly -> {
            softly.assertThat(deadLetters.get(0).key()).isEqualTo(orderId.toString());
            softly.assertThat(deadLetters.get(0).partition()).isEqualTo(sent.partition());
        });
    }

    @Test
    void sendsAnUnknownTypeToTheDeadLetterTopicWithoutRetries() {
        UUID orderId = UUID.randomUUID();
        String value = unknownTypeMessage(orderId);
        send(orderId.toString(), value);

        List<ConsumerRecord<String, String>> deadLetters = recordsOf(COMMANDS_DLT, orderId, 1);

        assertThat(deadLetters).hasSize(1);
        verify(codec, times(1)).fromJson(value);
    }

    @Test
    void sendsAnUnsupportedPayloadToTheDeadLetterTopicWithoutRetries() {
        UUID orderId = UUID.randomUUID();
        MessageEnvelope<PaymentCompleted> message = new MessageEnvelope<>(UUID.randomUUID(), "c-1",
            MessageTypes.PAYMENT_COMPLETED, Instant.parse("2026-11-28T10:15:30.123Z"),
            new PaymentCompleted(UUID.randomUUID(), orderId, new BigDecimal("49.90")));
        send(orderId.toString(), codec.toJson(message));

        List<ConsumerRecord<String, String>> deadLetters = recordsOf(COMMANDS_DLT, orderId, 1);

        assertThat(deadLetters).hasSize(1);
        verify(handler, times(1)).handle(withMessageId(message.messageId()));
    }

    @Test
    void retriesAFailingHandlerThreeTimesThenSendsTheRecordToTheDeadLetterTopic() {
        UUID orderId = UUID.randomUUID();
        MessageEnvelope<HoldSeatCommand> command = holdCommand(orderId, SEAT_ID, "c-1");
        doThrow(new IllegalStateException("boom")).when(handler).handle(withMessageId(command.messageId()));
        send(orderId.toString(), codec.toJson(command));
        recordsOf(COMMANDS_DLT, orderId, 1);

        verify(handler, times(4)).handle(withMessageId(command.messageId()));
    }

    @Test
    void doesNotLetAFailingMessageBlockTheNextOne() {
        UUID orderId = UUID.randomUUID();
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);
        send(orderId.toString(), "this is not json");
        send(orderId.toString(), codec.toJson(holdCommand(orderId, SEAT_ID, "c-1")));

        List<ConsumerRecord<String, String>> events = recordsOf(Topics.INVENTORY_EVENTS, orderId, 1);

        assertThat(events).hasSize(1);
    }

    @Test
    void commitsTheOffsetAfterTheHandler() {
        UUID orderId = UUID.randomUUID();
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);
        RecordMetadata sent = send(orderId.toString(), codec.toJson(holdCommand(orderId, SEAT_ID, "c-1")));

        await().atMost(LONG_WAIT).untilAsserted(() ->
            assertThat(committedOffsetOf(sent)).isGreaterThan(sent.offset()));
    }

    @Test
    void commitsTheOffsetOfADeadLetter() {
        UUID orderId = UUID.randomUUID();
        RecordMetadata sent = send(orderId.toString(), "this is not json");
        recordsOf(COMMANDS_DLT, orderId, 1);

        await().atMost(LONG_WAIT).untilAsserted(() ->
            assertThat(committedOffsetOf(sent)).isGreaterThan(sent.offset()));
    }

    @Test
    void logLinesOfAMessageCarryItsCorrelationId() {
        UUID orderId = UUID.randomUUID();
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);
        try (LogCapture logs = LogCapture.of("com.ticket.inventory")) {
            send(orderId.toString(), codec.toJson(holdCommand(orderId, SEAT_ID, "c-log-1")));

            await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(logs.mdcValues("correlationId")).contains("c-log-1"));
        }
    }

    @Test
    void printsTheCorrelationIdInTheLogPattern() {
        String pattern = environment.getProperty("logging.pattern.level");

        assertThat(pattern).contains("%X{correlationId");
    }

    private RecordMetadata send(String key, String value) {
        try {
            return template.send(Topics.INVENTORY_COMMANDS, key, value)
                .get(30, TimeUnit.SECONDS)
                .getRecordMetadata();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("The message was not sent", e);
        }
    }

    private List<ConsumerRecord<String, String>> recordsOf(String topic, UUID orderId, int count) {
        String key = orderId.toString();
        return TopicReader
            .readUntil(kafka.getBootstrapServers(), topic,
                records -> withKey(records, key).size() >= count, LONG_WAIT)
            .stream()
            .filter(record -> key.equals(record.key()))
            .toList();
    }

    private static List<ConsumerRecord<String, String>> withKey(List<ConsumerRecord<String, String>> records, String key) {
        return records.stream().filter(record -> key.equals(record.key())).toList();
    }

    private long committedOffsetOf(RecordMetadata sent) {
        return GroupOffsets.committed(kafka.getBootstrapServers(), GROUP, sent.topic(), sent.partition());
    }

    private Long eventRows(UUID orderId, String seatId) {
        return jdbc.sql("""
                SELECT count(*) FROM outbox_event
                WHERE message_key = :key AND CAST(payload AS text) LIKE :seat
                """)
            .param("key", orderId.toString())
            .param("seat", "%" + seatId + "%")
            .query(Long.class)
            .single();
    }

    private static MessageEnvelope<?> withMessageId(UUID messageId) {
        return argThat(envelope -> messageId.equals(envelope.messageId()));
    }

    private static MessageEnvelope<HoldSeatCommand> holdCommand(UUID orderId, String seatId, String correlationId) {
        return new MessageEnvelope<>(UUID.randomUUID(), correlationId, MessageTypes.HOLD_SEAT_COMMAND,
            Instant.parse("2026-11-28T10:15:30.123Z"), new HoldSeatCommand(orderId, EVENT_ID, seatId));
    }

    private static String unknownTypeMessage(UUID orderId) {
        return "{\"messageId\":\"" + UUID.randomUUID() + "\",\"correlationId\":\"c-1\","
            + "\"type\":\"NoSuchMessage\",\"occurredAt\":\"2026-11-28T10:15:30.123Z\","
            + "\"payload\":{\"orderId\":\"" + orderId + "\"}}";
    }
}
