package com.ticket.inventory.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class ConsumerPropertiesTest {

    @Test
    void retriesEveryOneSecondByDefault() {
        ConsumerProperties properties = bind(Map.of());

        assertThat(properties).isEqualTo(new ConsumerProperties(Duration.ofSeconds(1)));
    }

    @Test
    void bindsTheConfiguredValue() {
        ConsumerProperties properties = bind(Map.of("inventory.consumer.retry-interval", "100ms"));

        assertThat(properties).isEqualTo(new ConsumerProperties(Duration.ofMillis(100)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "PT-1S"})
    void rejectsARetryIntervalOfZeroOrLess(String value) {
        Duration interval = Duration.parse(value);

        assertThatThrownBy(() -> new ConsumerProperties(interval))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("retryInterval must be positive");
    }

    @Test
    void rejectsAMissingRetryInterval() {
        assertThatThrownBy(() -> new ConsumerProperties(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("retryInterval");
    }

    private static ConsumerProperties bind(Map<String, String> values) {
        Binder binder = new Binder(new MapConfigurationPropertySource(values));
        return binder.bindOrCreate("inventory.consumer", ConsumerProperties.class);
    }
}
