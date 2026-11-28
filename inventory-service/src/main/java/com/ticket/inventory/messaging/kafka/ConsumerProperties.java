package com.ticket.inventory.messaging.kafka;

import com.ticket.events.Require;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "inventory.consumer")
public record ConsumerProperties(@DefaultValue("1s") Duration retryInterval) {

    public ConsumerProperties {
        Require.notNull(retryInterval, "retryInterval");
        if (retryInterval.isZero() || retryInterval.isNegative()) {
            throw new IllegalArgumentException("retryInterval must be positive");
        }
    }
}
