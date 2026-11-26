package com.ticket.inventory.service;

import com.ticket.events.MessageEnvelope;
import com.ticket.events.MessageTypes;
import com.ticket.events.Require;
import com.ticket.events.Topics;
import com.ticket.events.inventory.SeatHoldExpired;
import com.ticket.events.inventory.SeatHoldRejected;
import com.ticket.inventory.messaging.outbox.OutboxWriter;
import com.ticket.inventory.repository.ExpiredHold;
import com.ticket.inventory.repository.SeatRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeatService {

    private final SeatSteps steps;
    private final TransactionAttempts attempts;
    private final HoldGate gate;
    private final SeatRepository seats;
    private final OutboxWriter outbox;

    public SeatService(
        SeatSteps steps,
        TransactionAttempts attempts,
        HoldGate gate,
        SeatRepository seats,
        OutboxWriter outbox) {
        this.steps = steps;
        this.attempts = attempts;
        this.gate = gate;
        this.seats = seats;
        this.outbox = outbox;
    }

    public HoldResult hold(UUID orderId, String eventId, String seatId) {
        requireRequest(orderId, eventId, seatId);
        if (gate.admit(orderId, eventId, seatId) == GateDecision.REJECT) {
            return new HoldResult.Rejected(SeatHoldRejected.SEAT_UNAVAILABLE);
        }
        HoldResult result = attempts.run(() -> steps.hold(orderId, eventId, seatId));
        if (result instanceof HoldResult.Rejected) {
            gate.release(orderId, eventId, seatId);
        }
        return result;
    }

    public ConfirmResult confirm(UUID orderId, String eventId, String seatId) {
        requireRequest(orderId, eventId, seatId);
        return attempts.run(() -> steps.confirm(orderId, eventId, seatId));
    }

    public void release(UUID orderId, String eventId, String seatId) {
        requireRequest(orderId, eventId, seatId);
        attempts.run(() -> steps.release(orderId, eventId, seatId));
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

    private static void requireRequest(UUID orderId, String eventId, String seatId) {
        Require.notNull(orderId, "orderId");
        Require.notBlank(eventId, "eventId");
        Require.notBlank(seatId, "seatId");
    }
}
