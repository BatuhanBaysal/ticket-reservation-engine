package com.ticket.inventory.messaging.outbox;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxCleanup {

    private static final String DELETE_OLD_PUBLISHED = """
        DELETE FROM outbox_event
        WHERE published_at IS NOT NULL
          AND published_at < now() - make_interval(secs => :retentionSeconds)
        """;

    private final JdbcClient jdbc;
    private final OutboxCleanupProperties properties;

    public OutboxCleanup(JdbcClient jdbc, OutboxCleanupProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Transactional
    public int deleteOldPublishedRows() {
        return jdbc.sql(DELETE_OLD_PUBLISHED)
            .param("retentionSeconds", properties.retention().toMillis() / 1000.0)
            .update();
    }
}
