package com.ticket.events.inventory;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ReasonedMessagesTest {

    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");

    @FunctionalInterface
    interface ReasonedMessageFactory {
        Object create(String reason);
    }

    static Stream<Arguments> messages() {
        return Stream.of(
            message("SeatHoldRejected", reason -> new SeatHoldRejected(ORDER_ID, "evt-001", "A-12", reason)),
            message("SeatConfirmFailed", reason -> new SeatConfirmFailed(ORDER_ID, "evt-001", "A-12", reason)));
    }

    private static Arguments message(String name, ReasonedMessageFactory factory) {
        return Arguments.of(Named.of(name, factory));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("messages")
    void rejectsANullReason(ReasonedMessageFactory factory) {
        assertThatThrownBy(() -> factory.create(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("reason");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("messages")
    void rejectsABlankReason(ReasonedMessageFactory factory) {
        assertThatThrownBy(() -> factory.create(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("reason must not be blank");
    }
}
