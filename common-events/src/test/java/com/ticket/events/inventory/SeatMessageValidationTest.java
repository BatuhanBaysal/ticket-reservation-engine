package com.ticket.events.inventory;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SeatMessageValidationTest {

    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");
    private static final String EVENT_ID = "evt-001";
    private static final String SEAT_ID = "A-12";

    @FunctionalInterface
    interface SeatMessageFactory {
        Object create(UUID orderId, String eventId, String seatId);
    }

    static Stream<Arguments> messages() {
        return Stream.of(
            message("HoldSeatCommand", HoldSeatCommand::new),
            message("ConfirmSeatCommand", ConfirmSeatCommand::new),
            message("ReleaseSeatCommand", ReleaseSeatCommand::new),
            message("SeatHeld", (orderId, eventId, seatId) -> new SeatHeld(
                orderId, eventId, seatId, new BigDecimal("49.90"), Instant.parse("2026-11-09T10:25:30Z"))),
            message("SeatHoldRejected", (orderId, eventId, seatId) -> new SeatHoldRejected(
                orderId, eventId, seatId, SeatHoldRejected.SEAT_UNAVAILABLE)),
            message("SeatConfirmed", SeatConfirmed::new),
            message("SeatConfirmFailed", (orderId, eventId, seatId) -> new SeatConfirmFailed(
                orderId, eventId, seatId, SeatConfirmFailed.SEAT_TAKEN)),
            message("SeatHoldExpired", SeatHoldExpired::new));
    }

    private static Arguments message(String name, SeatMessageFactory factory) {
        return Arguments.of(Named.of(name, factory));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("messages")
    void rejectsNullOrderId(SeatMessageFactory factory) {
        assertThatThrownBy(() -> factory.create(null, EVENT_ID, SEAT_ID))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("orderId");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("messages")
    void rejectsNullEventId(SeatMessageFactory factory) {
        assertThatThrownBy(() -> factory.create(ORDER_ID, null, SEAT_ID))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("eventId");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("messages")
    void rejectsBlankEventId(SeatMessageFactory factory) {
        assertThatThrownBy(() -> factory.create(ORDER_ID, " ", SEAT_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("eventId must not be blank");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("messages")
    void rejectsNullSeatId(SeatMessageFactory factory) {
        assertThatThrownBy(() -> factory.create(ORDER_ID, EVENT_ID, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("seatId");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("messages")
    void rejectsBlankSeatId(SeatMessageFactory factory) {
        assertThatThrownBy(() -> factory.create(ORDER_ID, EVENT_ID, " "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("seatId must not be blank");
    }
}
