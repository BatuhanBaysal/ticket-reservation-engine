package com.ticket.inventory.repository;

import java.util.UUID;

public record ExpiredHold(UUID orderId, String eventId, String seatId) {
}
