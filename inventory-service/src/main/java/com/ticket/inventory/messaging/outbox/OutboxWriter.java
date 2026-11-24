package com.ticket.inventory.messaging.outbox;

import com.ticket.events.MessageCodec;
import com.ticket.events.MessageEnvelope;
import com.ticket.events.Require;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxWriter {

    private static final String INSERT = """
        INSERT INTO outbox_event (id, topic, message_key, payload)
        VALUES (:id, :topic, :messageKey, CAST(:payload AS jsonb))
        """;

    private final JdbcClient jdbc;
    private final MessageCodec codec;

    public OutboxWriter(JdbcClient jdbc, MessageCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void write(String topic, String key, MessageEnvelope<?> envelope) {
        Require.notBlank(topic, "topic");
        Require.notBlank(key, "key");
        Require.notNull(envelope, "envelope");
        jdbc.sql(INSERT)
            .param("id", UUID.randomUUID())
            .param("topic", topic)
            .param("messageKey", key)
            .param("payload", codec.toJson(envelope))
            .update();
    }
}
