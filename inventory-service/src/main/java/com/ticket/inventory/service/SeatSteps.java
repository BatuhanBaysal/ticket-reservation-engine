package com.ticket.inventory.service;

import com.ticket.events.inventory.SeatConfirmFailed;
import com.ticket.events.inventory.SeatHoldRejected;
import com.ticket.inventory.domain.Seat;
import com.ticket.inventory.domain.SeatStatus;
import com.ticket.inventory.repository.HeldSeat;
import com.ticket.inventory.repository.SeatRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SeatSteps {

    private final SeatRepository seats;
    private final HoldProperties holdProperties;

    public SeatSteps(SeatRepository seats, HoldProperties holdProperties) {
        this.seats = seats;
        this.holdProperties = holdProperties;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public HoldResult hold(UUID orderId, String eventId, String seatId) {
        return seats.holdIfAvailable(orderId, eventId, seatId, holdProperties.duration())
            .map(SeatSteps::held)
            .orElseGet(() -> existingHoldOrRejection(orderId, eventId, seatId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ConfirmResult confirm(UUID orderId, String eventId, String seatId) {
        return seats.findByEventIdAndSeatId(eventId, seatId)
            .map(seat -> confirmSeat(seat, orderId))
            .orElseGet(() -> new ConfirmResult.Failed(SeatConfirmFailed.HOLD_NOT_FOUND));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean release(UUID orderId, String eventId, String seatId) {
        return seats.findByEventIdAndSeatId(eventId, seatId)
            .filter(seat -> seat.getStatus() == SeatStatus.HELD && orderId.equals(seat.getHeldByOrderId()))
            .map(seat -> {
                seat.release();
                seats.saveAndFlush(seat);
                return true;
            })
            .orElse(false);
    }

    private HoldResult existingHoldOrRejection(UUID orderId, String eventId, String seatId) {
        return seats.findByEventIdAndSeatId(eventId, seatId)
            .map(seat -> holdOf(seat, orderId))
            .orElseGet(() -> new HoldResult.Rejected(SeatHoldRejected.SEAT_NOT_FOUND));
    }

    private ConfirmResult confirmSeat(Seat seat, UUID orderId) {
        boolean ownedByTheOrder = orderId.equals(seat.getHeldByOrderId());
        return switch (seat.getStatus()) {
            case AVAILABLE -> new ConfirmResult.Failed(SeatConfirmFailed.HOLD_NOT_FOUND);
            case HELD -> ownedByTheOrder ? sell(seat) : new ConfirmResult.Failed(SeatConfirmFailed.SEAT_TAKEN);
            case SOLD -> ownedByTheOrder
                ? new ConfirmResult.Confirmed()
                : new ConfirmResult.Failed(SeatConfirmFailed.SEAT_TAKEN);
        };
    }

    private ConfirmResult sell(Seat seat) {
        seat.confirm();
        seats.saveAndFlush(seat);
        return new ConfirmResult.Confirmed();
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
