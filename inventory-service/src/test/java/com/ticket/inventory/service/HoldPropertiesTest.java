package com.ticket.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HoldPropertiesTest {

    @Test
    void keepsAPositiveDuration() {
        HoldProperties properties = new HoldProperties(Duration.ofMinutes(10));

        assertThat(properties.duration()).isEqualTo(Duration.ofMinutes(10));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "PT-1S"})
    void rejectsADurationOfZeroOrLess(String value) {
        Duration duration = Duration.parse(value);

        assertThatThrownBy(() -> new HoldProperties(duration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("duration must be positive");
    }

    @Test
    void rejectsAMissingDuration() {
        assertThatThrownBy(() -> new HoldProperties(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("duration");
    }
}
