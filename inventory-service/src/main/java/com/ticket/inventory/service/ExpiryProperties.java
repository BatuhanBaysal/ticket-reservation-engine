package com.ticket.inventory.service;

import com.ticket.events.Require;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "inventory.hold.expiry")
public record ExpiryProperties(@DefaultValue("true") boolean enabled, @DefaultValue("30s") Duration interval) {

    public ExpiryProperties {
        Require.notNull(interval, "interval");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("interval must be positive");
        }
    }
}
