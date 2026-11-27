package com.ticket.inventory.service;

import com.ticket.events.MessageEnvelope;
import com.ticket.events.MessageTypes;
import com.ticket.events.Require;
import com.ticket.events.Topics;
import com.ticket.events.inventory.ConfirmSeatCommand;
import com.ticket.events.inventory.HoldSeatCommand;
import com.ticket.events.inventory.ReleaseSeatCommand;
import com.ticket.events.inventory.SeatConfirmFailed;
import com.ticket.events.inventory.SeatConfirmed;
import com.ticket.events.inventory.SeatHeld;
import com.ticket.events.inventory.SeatHoldRejected;
import com.ticket.inventory.messaging.inbox.InboxGuard;
import com.ticket.inventory.messaging.outbox.OutboxWriter;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SeatCommandHandler {

    static final String CONSUMER = "inventory-service";

    private final SeatSteps steps;
    private final TransactionAttempts attempts;
    private final HoldGate gate;
    private final InboxGuard inbox;
    private final OutboxWriter outbox;

    public SeatCommandHandler(
        SeatSteps steps,
        TransactionAttempts attempts,
        HoldGate gate,
        InboxGuard inbox,
        OutboxWriter outbox) {
        this.steps = steps;
        this.attempts = attempts;
        this.gate = gate;
        this.inbox = inbox;
        this.outbox = outbox;
    }

    public void handle(MessageEnvelope<?> envelope) {
        Require.notNull(envelope, "envelope");
        switch (envelope.payload()) {
            case HoldSeatCommand command -> hold(envelope, command);
            case ConfirmSeatCommand command -> confirm(envelope, command);
            case ReleaseSeatCommand command -> release(envelope, command);
            default -> throw new UnsupportedMessageException(envelope.type());
        }
    }

    private void hold(MessageEnvelope<?> envelope, HoldSeatCommand command) {
        GateDecision decision = gate.admit(command.orderId(), command.eventId(), command.seatId());
        boolean granted = false;
        try {
            granted = isGranted(attempts.run(() -> holdOnce(envelope, command, decision)));
        } finally {
            if (!granted) {
                gate.release(command.orderId(), command.eventId(), command.seatId());
            }
        }
    }

    private Optional<HoldResult> holdOnce(MessageEnvelope<?> envelope, HoldSeatCommand command, GateDecision decision) {
        if (!inbox.firstDelivery(envelope.messageId(), CONSUMER)) {
            return Optional.empty();
        }
        HoldResult result = decision == GateDecision.REJECT
            ? new HoldResult.Rejected(SeatHoldRejected.SEAT_UNAVAILABLE)
            : steps.hold(command.orderId(), command.eventId(), command.seatId());
        writeHoldEvent(envelope, command, result);
        return Optional.of(result);
    }

    private void writeHoldEvent(MessageEnvelope<?> envelope, HoldSeatCommand command, HoldResult result) {
        switch (result) {
            case HoldResult.Held held -> emit(envelope, command.orderId(), MessageTypes.SEAT_HELD,
                new SeatHeld(command.orderId(), command.eventId(), command.seatId(),
                    held.price(), held.holdExpiresAt()));
            case HoldResult.Rejected rejected -> emit(envelope, command.orderId(), MessageTypes.SEAT_HOLD_REJECTED,
                new SeatHoldRejected(command.orderId(), command.eventId(), command.seatId(), rejected.reason()));
        }
    }

    private void confirm(MessageEnvelope<?> envelope, ConfirmSeatCommand command) {
        attempts.run(() -> confirmOnce(envelope, command));
    }

    private boolean confirmOnce(MessageEnvelope<?> envelope, ConfirmSeatCommand command) {
        if (!inbox.firstDelivery(envelope.messageId(), CONSUMER)) {
            return false;
        }
        ConfirmResult result = steps.confirm(command.orderId(), command.eventId(), command.seatId());
        if (result instanceof ConfirmResult.Failed failed) {
            emit(envelope, command.orderId(), MessageTypes.SEAT_CONFIRM_FAILED,
                new SeatConfirmFailed(command.orderId(), command.eventId(), command.seatId(), failed.reason()));
        } else {
            emit(envelope, command.orderId(), MessageTypes.SEAT_CONFIRMED,
                new SeatConfirmed(command.orderId(), command.eventId(), command.seatId()));
        }
        return true;
    }

    private void release(MessageEnvelope<?> envelope, ReleaseSeatCommand command) {
        attempts.run(() -> releaseOnce(envelope, command));
        gate.release(command.orderId(), command.eventId(), command.seatId());
    }

    private boolean releaseOnce(MessageEnvelope<?> envelope, ReleaseSeatCommand command) {
        if (!inbox.firstDelivery(envelope.messageId(), CONSUMER)) {
            return false;
        }
        steps.release(command.orderId(), command.eventId(), command.seatId());
        return true;
    }

    private void emit(MessageEnvelope<?> command, UUID orderId, String type, Object payload) {
        MessageEnvelope<Object> event = MessageEnvelope.of(command.correlationId(), type, payload);
        outbox.write(Topics.INVENTORY_EVENTS, orderId.toString(), event);
    }

    private static boolean isGranted(Optional<HoldResult> result) {
        return result.filter(HoldResult.Held.class::isInstance).isPresent();
    }
}
