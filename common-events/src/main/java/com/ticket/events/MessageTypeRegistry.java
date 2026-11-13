package com.ticket.events;

import static java.util.Map.entry;

import com.ticket.events.inventory.ConfirmSeatCommand;
import com.ticket.events.inventory.HoldSeatCommand;
import com.ticket.events.inventory.ReleaseSeatCommand;
import com.ticket.events.inventory.SeatConfirmFailed;
import com.ticket.events.inventory.SeatConfirmed;
import com.ticket.events.inventory.SeatHeld;
import com.ticket.events.inventory.SeatHoldExpired;
import com.ticket.events.inventory.SeatHoldRejected;
import com.ticket.events.payment.CancelPaymentCommand;
import com.ticket.events.payment.PaymentCancelled;
import com.ticket.events.payment.PaymentCompleted;
import com.ticket.events.payment.PaymentDeclined;
import com.ticket.events.payment.ProcessPaymentCommand;
import java.util.Map;
import java.util.Set;

final class MessageTypeRegistry {

    private static final Map<String, Class<?>> PAYLOAD_CLASSES = Map.ofEntries(
        entry(MessageTypes.HOLD_SEAT_COMMAND, HoldSeatCommand.class),
        entry(MessageTypes.CONFIRM_SEAT_COMMAND, ConfirmSeatCommand.class),
        entry(MessageTypes.RELEASE_SEAT_COMMAND, ReleaseSeatCommand.class),
        entry(MessageTypes.SEAT_HELD, SeatHeld.class),
        entry(MessageTypes.SEAT_HOLD_REJECTED, SeatHoldRejected.class),
        entry(MessageTypes.SEAT_CONFIRMED, SeatConfirmed.class),
        entry(MessageTypes.SEAT_CONFIRM_FAILED, SeatConfirmFailed.class),
        entry(MessageTypes.SEAT_HOLD_EXPIRED, SeatHoldExpired.class),
        entry(MessageTypes.PROCESS_PAYMENT_COMMAND, ProcessPaymentCommand.class),
        entry(MessageTypes.CANCEL_PAYMENT_COMMAND, CancelPaymentCommand.class),
        entry(MessageTypes.PAYMENT_COMPLETED, PaymentCompleted.class),
        entry(MessageTypes.PAYMENT_DECLINED, PaymentDeclined.class),
        entry(MessageTypes.PAYMENT_CANCELLED, PaymentCancelled.class));

    private MessageTypeRegistry() {
    }

    static Class<?> payloadClassOf(String type) {
        Require.notNull(type, "type");
        Class<?> payloadClass = PAYLOAD_CLASSES.get(type);
        if (payloadClass == null) {
            throw new UnknownMessageTypeException(type);
        }
        return payloadClass;
    }

    static Set<String> registeredTypes() {
        return PAYLOAD_CLASSES.keySet();
    }
}
