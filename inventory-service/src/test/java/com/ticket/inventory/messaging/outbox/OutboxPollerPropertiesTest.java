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

class OutboxPollerPropertiesTest {

    @Test
    void runsEveryHalfSecondByDefault() {
        OutboxPollerProperties properties = bind(Map.of());

        assertThat(properties)
            .isEqualTo(new OutboxPollerProperties(true, Duration.ofMillis(500), Duration.ofSeconds(10)));
    }

    @Test
    void bindsTheConfiguredValues() {
        Map<String, String> values = Map.of(
            "inventory.outbox.poller.enabled", "false",
            "inventory.outbox.poller.interval", "2s",
            "inventory.outbox.poller.send-timeout", "3s");

        OutboxPollerProperties properties = bind(values);

        assertThat(properties)
            .isEqualTo(new OutboxPollerProperties(false, Duration.ofSeconds(2), Duration.ofSeconds(3)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "PT-1S"})
    void rejectsAnIntervalOfZeroOrLess(String value) {
        Duration interval = Duration.parse(value);

        assertThatThrownBy(() -> new OutboxPollerProperties(true, interval, Duration.ofSeconds(10)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("interval must be positive");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "PT-1S"})
    void rejectsASendTimeoutOfZeroOrLess(String value) {
        Duration sendTimeout = Duration.parse(value);

        assertThatThrownBy(() -> new OutboxPollerProperties(true, Duration.ofMillis(500), sendTimeout))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("sendTimeout must be positive");
    }

    @Test
    void rejectsAMissingInterval() {
        assertThatThrownBy(() -> new OutboxPollerProperties(true, null, Duration.ofSeconds(10)))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("interval");
    }

    @Test
    void rejectsAMissingSendTimeout() {
        assertThatThrownBy(() -> new OutboxPollerProperties(true, Duration.ofMillis(500), null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("sendTimeout");
    }

    private static OutboxPollerProperties bind(Map<String, String> values) {
        Binder binder = new Binder(new MapConfigurationPropertySource(values));
        return binder.bindOrCreate("inventory.outbox.poller", OutboxPollerProperties.class);
    }
}
