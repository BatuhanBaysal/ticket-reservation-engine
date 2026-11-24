package com.ticket.inventory.service;

import com.ticket.events.Require;
import com.ticket.events.inventory.SeatHoldExpired;
import com.ticket.events.inventory.SeatConfirmFailed;
import com.ticket.events.inventory.SeatHoldRejected;
import com.ticket.events.MessageEnvelope;
import com.ticket.events.MessageTypes;
import com.ticket.events.Topics;
import com.ticket.inventory.domain.Seat;
import com.ticket.inventory.domain.SeatStatus;
import com.ticket.inventory.repository.ExpiredHold;
import com.ticket.inventory.repository.HeldSeat;
import com.ticket.inventory.repository.SeatRepository;
import com.ticket.inventory.messaging.outbox.OutboxWriter;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SeatService {

    private static final int MAX_ATTEMPTS = 3;

    private final SeatRepository seats;
    private final HoldProperties holdProperties;
    private final HoldGate gate;
    private final OutboxWriter outbox;
    private final TransactionTemplate transaction;

    public SeatService(
        SeatRepository seats,
        HoldProperties holdProperties,
        HoldGate gate,
        OutboxWriter outbox,
        PlatformTransactionManager transactionManager) {
        this.seats = seats;
        this.holdProperties = holdProperties;
        this.gate = gate;
        this.outbox = outbox;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public HoldResult hold(UUID orderId, String eventId, String seatId) {
        requireRequest(orderId, eventId, seatId);
        if (gate.admit(orderId, eventId, seatId) == GateDecision.REJECT) {
            return new HoldResult.Rejected(SeatHoldRejected.SEAT_UNAVAILABLE);
        }
        HoldResult result = holdInDatabase(orderId, eventId, seatId);
        if (result instanceof HoldResult.Rejected) {
            gate.release(orderId, eventId, seatId);
        }
        return result;
    }

    public ConfirmResult confirm(UUID orderId, String eventId, String seatId) {
        requireRequest(orderId, eventId, seatId);
        return retryingOnConflict(() -> confirmOnce(orderId, eventId, seatId));
    }

    public void release(UUID orderId, String eventId, String seatId) {
        requireRequest(orderId, eventId, seatId);
        retryingOnConflict(() -> releaseOnce(orderId, eventId, seatId));
        gate.release(orderId, eventId, seatId);
    }

    @Transactional
    public List<ExpiredHold> expireOverdueHolds() {
        List<ExpiredHold> expired = seats.expireOverdueHolds();
        expired.forEach(this::writeExpiredEvent);
        return expired;
    }

    private void writeExpiredEvent(ExpiredHold hold) {
        SeatHoldExpired payload = new SeatHoldExpired(hold.orderId(), hold.eventId(), hold.seatId());
        MessageEnvelope<SeatHoldExpired> envelope =
            MessageEnvelope.of(UUID.randomUUID().toString(), MessageTypes.SEAT_HOLD_EXPIRED, payload);
        outbox.write(Topics.INVENTORY_EVENTS, hold.orderId().toString(), envelope);
    }

    private HoldResult holdInDatabase(UUID orderId, String eventId, String seatId) {
        return seats.holdIfAvailable(orderId, eventId, seatId, holdProperties.duration())
            .map(SeatService::held)
            .orElseGet(() -> existingHoldOrRejection(orderId, eventId, seatId));
    }

    private HoldResult existingHoldOrRejection(UUID orderId, String eventId, String seatId) {
        return seats.findByEventIdAndSeatId(eventId, seatId)
            .map(seat -> holdOf(seat, orderId))
            .orElseGet(() -> new HoldResult.Rejected(SeatHoldRejected.SEAT_NOT_FOUND));
    }

    private ConfirmResult confirmOnce(UUID orderId, String eventId, String seatId) {
        return seats.findByEventIdAndSeatId(eventId, seatId)
            .map(seat -> confirmSeat(seat, orderId))
            .orElseGet(() -> new ConfirmResult.Failed(SeatConfirmFailed.HOLD_NOT_FOUND));
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

    private boolean releaseOnce(UUID orderId, String eventId, String seatId) {
        return seats.findByEventIdAndSeatId(eventId, seatId)
            .filter(seat -> seat.getStatus() == SeatStatus.HELD && orderId.equals(seat.getHeldByOrderId()))
            .map(seat -> {
                seat.release();
                seats.saveAndFlush(seat);
                return true;
            })
            .orElse(false);
    }

    private <T> T retryingOnConflict(Supplier<T> work) {
        OptimisticLockingFailureException lastConflict = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return transaction.execute(status -> work.get());
            } catch (OptimisticLockingFailureException e) {
                lastConflict = e;
            }
        }
        throw new SeatConflictException(MAX_ATTEMPTS, lastConflict);
    }

    private static void requireRequest(UUID orderId, String eventId, String seatId) {
        Require.notNull(orderId, "orderId");
        Require.notBlank(eventId, "eventId");
        Require.notBlank(seatId, "seatId");
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
