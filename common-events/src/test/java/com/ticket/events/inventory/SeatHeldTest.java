package com.ticket.events.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SeatHeldTest {

    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");
    private static final Instant HOLD_EXPIRES_AT = Instant.parse("2026-11-09T10:25:30Z");

    @Test
    void keepsAPositivePrice() {
        SeatHeld held = new SeatHeld(ORDER_ID, "evt-001", "A-12", new BigDecimal("49.90"), HOLD_EXPIRES_AT);

        assertThat(held.price()).isEqualByComparingTo("49.90");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1", "-0.01"})
    void rejectsAPriceOfZeroOrLess(String value) {
        BigDecimal price = new BigDecimal(value);

        assertThatThrownBy(() -> new SeatHeld(ORDER_ID, "evt-001", "A-12", price, HOLD_EXPIRES_AT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("price must be positive");
    }

    @Test
    void rejectsANullPrice() {
        assertThatThrownBy(() -> new SeatHeld(ORDER_ID, "evt-001", "A-12", null, HOLD_EXPIRES_AT))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("price");
    }

    @Test
    void rejectsANullHoldExpiryTime() {
        BigDecimal price = new BigDecimal("49.90");

        assertThatThrownBy(() -> new SeatHeld(ORDER_ID, "evt-001", "A-12", price, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("holdExpiresAt");
    }
}
