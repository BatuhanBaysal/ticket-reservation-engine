package com.ticket.inventory.messaging.inbox;

import com.ticket.events.Require;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InboxGuard {

    private static final String INSERT = """
        INSERT INTO processed_message (message_id, consumer)
        VALUES (:messageId, :consumer)
        ON CONFLICT (message_id) DO NOTHING
        """;

    private final JdbcClient jdbc;

    public InboxGuard(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstDelivery(UUID messageId, String consumer) {
        Require.notNull(messageId, "messageId");
        Require.notBlank(consumer, "consumer");
        int inserted = jdbc.sql(INSERT)
            .param("messageId", messageId)
            .param("consumer", consumer)
            .update();
        return inserted == 1;
    }
}
