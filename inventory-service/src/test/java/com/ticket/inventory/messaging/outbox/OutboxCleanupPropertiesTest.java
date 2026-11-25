package com.ticket.inventory.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class OutboxCleanupPropertiesTest {

    @Test
    void runsHourlyAndKeepsSevenDaysByDefault() {
        OutboxCleanupProperties properties = bind(Map.of());

        assertThat(properties)
            .isEqualTo(new OutboxCleanupProperties(true, Duration.ofHours(1), Duration.ofDays(7)));
    }

    @Test
    void bindsTheConfiguredValues() {
        Map<String, String> values = Map.of(
            "inventory.outbox.cleanup.enabled", "false",
            "inventory.outbox.cleanup.interval", "30m",
            "inventory.outbox.cleanup.retention", "3d");

        OutboxCleanupProperties properties = bind(values);

        assertThat(properties)
            .isEqualTo(new OutboxCleanupProperties(false, Duration.ofMinutes(30), Duration.ofDays(3)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "PT-1S"})
    void rejectsAnIntervalOfZeroOrLess(String value) {
        Duration interval = Duration.parse(value);

        assertThatThrownBy(() -> new OutboxCleanupProperties(true, interval, Duration.ofDays(7)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("interval must be positive");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "PT-1S"})
    void rejectsARetentionOfZeroOrLess(String value) {
        Duration retention = Duration.parse(value);

        assertThatThrownBy(() -> new OutboxCleanupProperties(true, Duration.ofHours(1), retention))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("retention must be positive");
    }

    @Test
    void rejectsAMissingInterval() {
        assertThatThrownBy(() -> new OutboxCleanupProperties(true, null, Duration.ofDays(7)))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("interval");
    }

    @Test
    void rejectsAMissingRetention() {
        assertThatThrownBy(() -> new OutboxCleanupProperties(true, Duration.ofHours(1), null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("retention");
    }

    private static OutboxCleanupProperties bind(Map<String, String> values) {
        Binder binder = new Binder(new MapConfigurationPropertySource(values));
        return binder.bindOrCreate("inventory.outbox.cleanup", OutboxCleanupProperties.class);
    }
}
