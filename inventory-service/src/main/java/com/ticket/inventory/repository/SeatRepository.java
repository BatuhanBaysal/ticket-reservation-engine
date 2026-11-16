package com.ticket.inventory.repository;

import com.ticket.inventory.domain.Seat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, UUID>, SeatHoldOperations {

    Optional<Seat> findByEventIdAndSeatId(String eventId, String seatId);
}
