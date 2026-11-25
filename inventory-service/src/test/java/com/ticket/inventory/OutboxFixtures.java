package com.ticket.inventory;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

final class OutboxFixtures {

    private OutboxFixtures() {
    }

    static void insert(
        JdbcClient jdbc,
        UUID id,
        String topic,
        String key,
        String payload,
        Instant createdAt,
        Instant publishedAt) {
        jdbc.sql("""
                INSERT INTO outbox_event (id, topic, message_key, payload, created_at, published_at)
                VALUES (:id, :topic, :key, CAST(:payload AS jsonb),
                        CAST(:createdAt AS timestamptz), CAST(:publishedAt AS timestamptz))
                """)
            .param("id", id)
            .param("topic", topic)
            .param("key", key)
            .param("payload", payload)
            .param("createdAt", utc(createdAt))
            .param("publishedAt", utc(publishedAt))
            .update();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
