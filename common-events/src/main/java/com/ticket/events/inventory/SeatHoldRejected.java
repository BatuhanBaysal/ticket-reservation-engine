package com.ticket.events.inventory;

import com.ticket.events.Require;

import java.util.UUID;

public record SeatHoldRejected(UUID orderId, String eventId, String seatId, String reason) {

    public static final String SEAT_UNAVAILABLE = "SEAT_UNAVAILABLE";
    public static final String SEAT_NOT_FOUND = "SEAT_NOT_FOUND";

    public SeatHoldRejected {
        Require.notNull(orderId, "orderId");
        Require.notBlank(eventId, "eventId");
        Require.notBlank(seatId, "seatId");
        Require.notBlank(reason, "reason");
    }
}
