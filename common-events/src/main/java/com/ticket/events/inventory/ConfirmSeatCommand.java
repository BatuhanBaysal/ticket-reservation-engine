package com.ticket.events.inventory;

import com.ticket.events.Require;

import java.util.UUID;

public record ConfirmSeatCommand(UUID orderId, String eventId, String seatId) {

    public ConfirmSeatCommand {
        Require.notNull(orderId, "orderId");
        Require.notBlank(eventId, "eventId");
        Require.notBlank(seatId, "seatId");
    }
}
