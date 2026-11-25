package com.ticket.inventory.messaging.outbox;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPoller {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPoller.class);
    private static final int BATCH_SIZE = 100;

    private static final String SELECT_UNPUBLISHED = """
        SELECT id, topic, message_key, CAST(payload AS text) AS payload
        FROM outbox_event
        WHERE published_at IS NULL
        ORDER BY created_at
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """;

    private static final String MARK_PUBLISHED = """
        UPDATE outbox_event
        SET published_at = clock_timestamp()
        WHERE id IN (:ids)
        """;

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final OutboxPollerProperties properties;

    public OutboxPoller(JdbcClient jdbc, KafkaTemplate<String, String> kafka, OutboxPollerProperties properties) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.properties = properties;
    }

    @Transactional
    public int publishBatch() {
        List<OutboxRow> rows = jdbc.sql(SELECT_UNPUBLISHED)
            .param("limit", BATCH_SIZE)
            .query((resultSet, rowNumber) -> new OutboxRow(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("topic"),
                resultSet.getString("message_key"),
                resultSet.getString("payload")))
            .list();
        List<UUID> sent = sendInOrder(rows);
        if (!sent.isEmpty()) {
            jdbc.sql(MARK_PUBLISHED).param("ids", sent).update();
        }
        return sent.size();
    }

    private List<UUID> sendInOrder(List<OutboxRow> rows) {
        List<UUID> sent = new ArrayList<>();
        for (OutboxRow row : rows) {
            if (!send(row)) {
                break;
            }
            sent.add(row.id());
        }
        return sent;
    }

    private boolean send(OutboxRow row) {
        try {
            kafka.send(row.topic(), row.key(), row.payload())
                .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Sending the outbox row {} was interrupted", row.id());
            return false;
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            LOGGER.warn("The outbox row {} was not sent: {}", row.id(), e.getMessage());
            return false;
        }
    }

    private record OutboxRow(UUID id, String topic, String key, String payload) {
    }
}
