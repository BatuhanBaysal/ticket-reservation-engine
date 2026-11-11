package com.ticket.events.inventory;

import com.ticket.events.Require;

import java.util.UUID;

public record SeatConfirmFailed(UUID orderId, String eventId, String  seatId, String reason) {

    public static final String HOLD_NOT_FOUND = "HOLD_NOT_FOUND";
    public static final String SEAT_TAKEN = "SEAT_TAKEN";

    public SeatConfirmFailed {
        Require.notNull(orderId, "orderId");
        Require.notBlank(eventId, "eventId");
        Require.notBlank(seatId, "seatId");
        Require.notBlank(reason, "reason");
    }
}
