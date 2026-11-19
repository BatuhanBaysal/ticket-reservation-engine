package com.ticket.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class ExpiryPropertiesTest {

    @Test
    void runsEveryThirtySecondsByDefault() {
        ExpiryProperties properties = bind(Map.of());

        assertThat(properties).isEqualTo(new ExpiryProperties(true, Duration.ofSeconds(30)));
    }

    @Test
    void bindsTheConfiguredValues() {
        Map<String, String> values = Map.of(
            "inventory.hold.expiry.enabled", "false",
            "inventory.hold.expiry.interval", "5m");

        ExpiryProperties properties = bind(values);

        assertThat(properties).isEqualTo(new ExpiryProperties(false, Duration.ofMinutes(5)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "PT-1S"})
    void rejectsAnIntervalOfZeroOrLess(String value) {
        Duration interval = Duration.parse(value);

        assertThatThrownBy(() -> new ExpiryProperties(true, interval))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("interval must be positive");
    }

    @Test
    void rejectsAMissingInterval() {
        assertThatThrownBy(() -> new ExpiryProperties(true, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("interval");
    }

    private static ExpiryProperties bind(Map<String, String> values) {
        Binder binder = new Binder(new MapConfigurationPropertySource(values));
        return binder.bindOrCreate("inventory.hold.expiry", ExpiryProperties.class);
    }
}
