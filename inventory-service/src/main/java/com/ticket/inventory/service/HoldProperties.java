package com.ticket.inventory.service;

import com.ticket.events.Require;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "inventory.hold")
public record HoldProperties(Duration duration) {

    public HoldProperties {
        Require.notNull(duration, "duration");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("duration must be positive");
        }
    }
}
