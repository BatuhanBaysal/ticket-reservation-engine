package com.ticket.inventory.messaging.outbox;

import com.ticket.events.Require;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "inventory.outbox.poller")
public record OutboxPollerProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("500ms") Duration interval,
    @DefaultValue("10s") Duration sendTimeout) {

    public OutboxPollerProperties {
        requirePositive(interval, "interval");
        requirePositive(sendTimeout, "sendTimeout");
    }

    private static void requirePositive(Duration value, String name) {
        Require.notNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
