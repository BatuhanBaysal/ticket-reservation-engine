package com.ticket.events.inventory;

import com.ticket.events.Require;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SeatHeld(
    UUID orderId,
    String eventId,
    String seatId,
    BigDecimal price,
    Instant holdExpiresAt) {

    public SeatHeld {
        Require.notNull(orderId, "orderId");
        Require.notBlank(eventId, "eventId");
        Require.notBlank(seatId, "seatId");
        Require.positive(price, "price");
        Require.notNull(holdExpiresAt, "holdExpiresAt");
    }
}
