package com.ticket.events.inventory;

import com.ticket.events.Require;

import java.util.UUID;

public record SeatConfirmed(UUID orderId, String eventId, String seatId) {

    public SeatConfirmed {
        Require.notNull(orderId, "orderId");
        Require.notBlank(eventId, "eventId");
        Require.notBlank(seatId, "seatId");
    }
}
