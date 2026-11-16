package com.ticket.inventory.service;

import com.ticket.events.Require;
import com.ticket.events.inventory.SeatHoldRejected;
import com.ticket.inventory.domain.Seat;
import com.ticket.inventory.domain.SeatStatus;
import com.ticket.inventory.repository.HeldSeat;
import com.ticket.inventory.repository.SeatRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeatService {

    private final SeatRepository seats;
    private final HoldProperties holdProperties;

    public SeatService(SeatRepository seats, HoldProperties holdProperties) {
        this.seats = seats;
        this.holdProperties = holdProperties;
    }

    @Transactional
    public HoldResult hold(UUID orderId, String eventId, String seatId) {
        Require.notNull(orderId, "orderId");
        Require.notBlank(eventId, "eventId");
        Require.notBlank(seatId, "seatId");
        return seats.holdIfAvailable(orderId, eventId, seatId, holdProperties.duration())
            .map(SeatService::held)
            .orElseGet(() -> existingHoldOrRejection(orderId, eventId, seatId));
    }

    private HoldResult existingHoldOrRejection(UUID orderId, String eventId, String seatId) {
        return seats.findByEventIdAndSeatId(eventId, seatId)
            .map(seat -> holdOf(seat, orderId))
            .orElseGet(() -> new HoldResult.Rejected(SeatHoldRejected.SEAT_NOT_FOUND));
    }

    private static HoldResult held(HeldSeat heldSeat) {
        return new HoldResult.Held(heldSeat.price(), heldSeat.holdExpiresAt());
    }

    private static HoldResult holdOf(Seat seat, UUID orderId) {
        boolean heldByTheSameOrder = seat.getStatus() == SeatStatus.HELD && orderId.equals(seat.getHeldByOrderId());
        if (heldByTheSameOrder) {
            return new HoldResult.Held(seat.getPrice(), seat.getHoldExpiresAt());
        }
        return new HoldResult.Rejected(SeatHoldRejected.SEAT_UNAVAILABLE);
    }
}
