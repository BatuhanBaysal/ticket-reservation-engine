package com.ticket.inventory.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SeatTest {

    @Test
    void rejectsConfirmingAnAvailableSeat() {
        Seat seat = Seat.available("evt-001", "A-12", new BigDecimal("49.9000"));

        assertThatThrownBy(seat::confirm)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Only a held seat can be sold");
    }

    @Test
    void rejectsReleasingAnAvailableSeat() {
        Seat seat = Seat.available("evt-001", "A-12", new BigDecimal("49.9000"));

        assertThatThrownBy(seat::release)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Only a held seat can be released");
    }
}
