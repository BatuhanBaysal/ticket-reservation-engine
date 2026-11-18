package com.ticket.inventory.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ConfirmResultTest {

    @Test
    void rejectsAFailureWithoutAReason() {
        assertThatThrownBy(() -> new ConfirmResult.Failed(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("reason");
    }

    @Test
    void rejectsAFailureWithABlankReason() {
        assertThatThrownBy(() -> new ConfirmResult.Failed(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("reason must not be blank");
    }
}
