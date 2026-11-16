package com.ticket.inventory.repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface SeatHoldOperations {

    Optional<HeldSeat> holdIfAvailable(UUID orderId, String eventId, String seatId, Duration holdDuration);
}
