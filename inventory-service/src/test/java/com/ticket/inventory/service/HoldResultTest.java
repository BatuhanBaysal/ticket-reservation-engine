package com.ticket.inventory.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HoldResultTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-11-16T10:25:30Z");
    private static final BigDecimal PRICE = new BigDecimal("49.9000");

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void rejectsAHeldResultWithAPriceOfZeroOrLess(String value) {
        BigDecimal price = new BigDecimal(value);

        assertThatThrownBy(() -> new HoldResult.Held(price, EXPIRES_AT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("price must be positive");
    }

    @Test
    void rejectsAHeldResultWithoutAPrice() {
        assertThatThrownBy(() -> new HoldResult.Held(null, EXPIRES_AT))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("price");
    }

    @Test
    void rejectsAHeldResultWithoutAnExpiry() {
        assertThatThrownBy(() -> new HoldResult.Held(PRICE, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("holdExpiresAt");
    }

    @Test
    void rejectsARejectionWithoutAReason() {
        assertThatThrownBy(() -> new HoldResult.Rejected(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("reason");
    }

    @Test
    void rejectsARejectionWithABlankReason() {
        assertThatThrownBy(() -> new HoldResult.Rejected(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("reason must not be blank");
    }
}
