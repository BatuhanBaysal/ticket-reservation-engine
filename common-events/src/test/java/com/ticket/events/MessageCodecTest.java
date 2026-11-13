package com.ticket.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticket.events.inventory.SeatHeld;
import com.ticket.events.payment.PaymentCompleted;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageCodecTest {

    private static final UUID MESSAGE_ID = UUID.fromString("0f8b2c36-5d0e-4c53-9b1a-7e2a5f4d9c10");
    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");
    private static final UUID PAYMENT_REQUEST_ID = UUID.fromString("9a4c1e72-3b58-4d0f-a6e1-8c27d5b3f401");
    private static final Instant OCCURRED_AT = Instant.parse("2026-11-09T10:15:30.123Z");
    private static final Instant HOLD_EXPIRES_AT = Instant.parse("2026-11-09T10:25:30.123Z");

    private static final String MESSAGE_ID_MEMBER = "\"messageId\":\"" + MESSAGE_ID + "\"";
    private static final String CORRELATION_ID_MEMBER = "\"correlationId\":\"c-7f3a91\"";
    private static final String SEAT_HELD_TYPE_MEMBER = "\"type\":\"SeatHeld\"";
    private static final String OCCURRED_AT_MEMBER = "\"occurredAt\":\"2026-11-09T10:15:30.123Z\"";

    private static final String ORDER_ID_FIELD = "\"orderId\":\"" + ORDER_ID + "\"";
    private static final String EVENT_ID_FIELD = "\"eventId\":\"evt-001\"";
    private static final String SEAT_ID_FIELD = "\"seatId\":\"A-12\"";
    private static final String PRICE_FIELD = "\"price\":\"49.9000\"";
    private static final String HOLD_EXPIRES_AT_FIELD = "\"holdExpiresAt\":\"2026-11-09T10:25:30.123Z\"";

    private final MessageCodec codec = new MessageCodec();

    @Test
    void seatHeldEnvelopeSurvivesTheRoundTrip() {
        MessageEnvelope<SeatHeld> envelope = seatHeldEnvelope();

        MessageEnvelope<?> read = codec.fromJson(codec.toJson(envelope));

        assertThat(read).isEqualTo(envelope);
    }

    @Test
    void writesInstantsAsIso8601Text() {
        String json = codec.toJson(seatHeldEnvelope());

        assertThat(json).contains(OCCURRED_AT_MEMBER);
    }

    @Test
    void writesAmountsAsDecimalStrings() {
        String json = codec.toJson(seatHeldEnvelope());

        assertThat(json).contains(PRICE_FIELD);
    }

    @Test
    void ignoresAJsonFieldThatTheRecordDoesNotHave() {
        String json = json(MESSAGE_ID_MEMBER, CORRELATION_ID_MEMBER, SEAT_HELD_TYPE_MEMBER, OCCURRED_AT_MEMBER,
            payloadMember(ORDER_ID_FIELD, EVENT_ID_FIELD, SEAT_ID_FIELD, PRICE_FIELD, HOLD_EXPIRES_AT_FIELD,
                "\"extra\":\"ignored\""));

        MessageEnvelope<?> read = codec.fromJson(json);

        assertThat(read).isEqualTo(seatHeldEnvelope());
    }

    @Test
    void rejectsAnUnknownTypeWhenReading() {
        String json = json(MESSAGE_ID_MEMBER, CORRELATION_ID_MEMBER, "\"type\":\"NoSuchMessage\"",
            OCCURRED_AT_MEMBER, payloadMember());

        assertThatThrownBy(() -> codec.fromJson(json))
            .isInstanceOf(UnknownMessageTypeException.class)
            .hasMessage("Unknown message type: NoSuchMessage");
    }

    @Test
    void rejectsAnUnknownTypeWhenWriting() {
        SeatHeld payload = seatHeldEnvelope().payload();
        MessageEnvelope<SeatHeld> envelope =
            new MessageEnvelope<>(MESSAGE_ID, "c-7f3a91", "NoSuchMessage", OCCURRED_AT, payload);

        assertThatThrownBy(() -> codec.toJson(envelope))
            .isInstanceOf(UnknownMessageTypeException.class);
    }

    @Test
    void rejectsAPayloadThatDoesNotMatchTheType() {
        PaymentCompleted payload = new PaymentCompleted(PAYMENT_REQUEST_ID, ORDER_ID, new BigDecimal("49.90"));
        MessageEnvelope<PaymentCompleted> envelope = MessageEnvelope.of("c-7f3a91", MessageTypes.SEAT_HELD, payload);

        assertThatThrownBy(() -> codec.toJson(envelope))
            .isInstanceOf(InvalidMessageException.class)
            .hasMessageContaining("PaymentCompleted");
    }

    @Test
    void rejectsMalformedJson() {
        String json = "{\"messageId\":";

        assertThatThrownBy(() -> codec.fromJson(json))
            .isInstanceOf(InvalidMessageException.class);
    }

    @Test
    void rejectsAMissingEnvelopeField() {
        String json = json(CORRELATION_ID_MEMBER, SEAT_HELD_TYPE_MEMBER, OCCURRED_AT_MEMBER, seatHeldPayload());

        assertThatThrownBy(() -> codec.fromJson(json))
            .isInstanceOf(InvalidMessageException.class)
            .hasMessageContaining("messageId");
    }

    @Test
    void rejectsAMissingType() {
        String json = json(MESSAGE_ID_MEMBER, CORRELATION_ID_MEMBER, OCCURRED_AT_MEMBER, seatHeldPayload());

        assertThatThrownBy(() -> codec.fromJson(json))
            .isInstanceOf(InvalidMessageException.class)
            .hasMessageContaining("type");
    }

    @Test
    void rejectsAMissingPayload() {
        String json = json(MESSAGE_ID_MEMBER, CORRELATION_ID_MEMBER, SEAT_HELD_TYPE_MEMBER, OCCURRED_AT_MEMBER);

        assertThatThrownBy(() -> codec.fromJson(json))
            .isInstanceOf(InvalidMessageException.class)
            .hasMessageContaining("payload");
    }

    @Test
    void rejectsAMissingPayloadField() {
        String json = json(MESSAGE_ID_MEMBER, CORRELATION_ID_MEMBER, SEAT_HELD_TYPE_MEMBER, OCCURRED_AT_MEMBER,
            payloadMember(ORDER_ID_FIELD, EVENT_ID_FIELD, PRICE_FIELD, HOLD_EXPIRES_AT_FIELD));

        assertThatThrownBy(() -> codec.fromJson(json))
            .isInstanceOf(InvalidMessageException.class)
            .hasMessageContaining("seatId");
    }

    private static MessageEnvelope<SeatHeld> seatHeldEnvelope() {
        SeatHeld payload = new SeatHeld(ORDER_ID, "evt-001", "A-12", new BigDecimal("49.9000"), HOLD_EXPIRES_AT);
        return new MessageEnvelope<>(MESSAGE_ID, "c-7f3a91", MessageTypes.SEAT_HELD, OCCURRED_AT, payload);
    }

    private static String seatHeldPayload() {
        return payloadMember(ORDER_ID_FIELD, EVENT_ID_FIELD, SEAT_ID_FIELD, PRICE_FIELD, HOLD_EXPIRES_AT_FIELD);
    }

    private static String payloadMember(String... fields) {
        return "\"payload\":{" + String.join(",", fields) + "}";
    }

    private static String json(String... members) {
        return "{" + String.join(",", members) + "}";
    }
}
