package com.ticket.inventory.messaging.outbox;

import com.ticket.events.Require;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "inventory.outbox.cleanup")
public record OutboxCleanupProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("1h") Duration interval,
    @DefaultValue("7d") Duration retention) {

    public OutboxCleanupProperties {
        requirePositive(interval, "interval");
        requirePositive(retention, "retention");
    }

    private static void requirePositive(Duration value, String name) {
        Require.notNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
