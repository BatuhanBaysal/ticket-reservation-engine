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
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

final class MessageSamples {

    private static final UUID MESSAGE_ID = UUID.fromString("0f8b2c36-5d0e-4c53-9b1a-7e2a5f4d9c10");
    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");
    private static final UUID PAYMENT_REQUEST_ID = UUID.fromString("9a4c1e72-3b58-4d0f-a6e1-8c27d5b3f401");
    private static final Instant OCCURRED_AT = Instant.parse("2026-11-09T10:15:30.123Z");
    private static final Instant HOLD_EXPIRES_AT = Instant.parse("2026-11-09T10:25:30.123Z");
    private static final String CORRELATION_ID = "c-7f3a91";
    private static final String EVENT_ID = "evt-001";
    private static final String SEAT_ID = "A-12";
    private static final BigDecimal AMOUNT = new BigDecimal("49.9000");

    private static final Map<String, MessageEnvelope<Object>> SAMPLES = Map.ofEntries(
        sample(MessageTypes.HOLD_SEAT_COMMAND, new HoldSeatCommand(ORDER_ID, EVENT_ID, SEAT_ID)),
        sample(MessageTypes.CONFIRM_SEAT_COMMAND, new ConfirmSeatCommand(ORDER_ID, EVENT_ID, SEAT_ID)),
        sample(MessageTypes.RELEASE_SEAT_COMMAND, new ReleaseSeatCommand(ORDER_ID, EVENT_ID, SEAT_ID)),
        sample(MessageTypes.SEAT_HELD, new SeatHeld(ORDER_ID, EVENT_ID, SEAT_ID, AMOUNT, HOLD_EXPIRES_AT)),
        sample(MessageTypes.SEAT_HOLD_REJECTED,
            new SeatHoldRejected(ORDER_ID, EVENT_ID, SEAT_ID, SeatHoldRejected.SEAT_UNAVAILABLE)),
        sample(MessageTypes.SEAT_CONFIRMED, new SeatConfirmed(ORDER_ID, EVENT_ID, SEAT_ID)),
        sample(MessageTypes.SEAT_CONFIRM_FAILED,
            new SeatConfirmFailed(ORDER_ID, EVENT_ID, SEAT_ID, SeatConfirmFailed.SEAT_TAKEN)),
        sample(MessageTypes.SEAT_HOLD_EXPIRED, new SeatHoldExpired(ORDER_ID, EVENT_ID, SEAT_ID)),
        sample(MessageTypes.PROCESS_PAYMENT_COMMAND,
            new ProcessPaymentCommand(PAYMENT_REQUEST_ID, ORDER_ID, AMOUNT)),
        sample(MessageTypes.CANCEL_PAYMENT_COMMAND, new CancelPaymentCommand(PAYMENT_REQUEST_ID, ORDER_ID)),
        sample(MessageTypes.PAYMENT_COMPLETED, new PaymentCompleted(PAYMENT_REQUEST_ID, ORDER_ID, AMOUNT)),
        sample(MessageTypes.PAYMENT_DECLINED,
            new PaymentDeclined(PAYMENT_REQUEST_ID, ORDER_ID, "CARD_DECLINED")),
        sample(MessageTypes.PAYMENT_CANCELLED,
            new PaymentCancelled(PAYMENT_REQUEST_ID, ORDER_ID, PaymentCancelled.REFUNDED)));

    private MessageSamples() {
    }

    static Map<String, MessageEnvelope<Object>> byType() {
        return SAMPLES;
    }

    static boolean hasStoredJson(String type) {
        return MessageSamples.class.getResource(resourcePath(type)) != null;
    }

    static String storedJson(String type) {
        try (InputStream stream = MessageSamples.class.getResourceAsStream(resourcePath(type))) {
            if (stream == null) {
                throw new IllegalStateException("Missing contract file " + resourcePath(type));
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map.Entry<String, MessageEnvelope<Object>> sample(String type, Object payload) {
        return entry(type, new MessageEnvelope<>(MESSAGE_ID, CORRELATION_ID, type, OCCURRED_AT, payload));
    }

    private static String resourcePath(String type) {
        return "/contracts/" + type + ".json";
    }
}
