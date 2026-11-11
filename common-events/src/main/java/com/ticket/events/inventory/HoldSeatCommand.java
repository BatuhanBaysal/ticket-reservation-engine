package com.ticket.events.inventory;

import com.ticket.events.Require;

import java.util.UUID;

public record HoldSeatCommand(UUID orderId, String eventId, String seatId) {

    public HoldSeatCommand {
        Require.notNull(orderId, "orderId");
        Require.notBlank(eventId, "eventId");
        Require.notBlank(seatId, "seatId");
    }
}
