package com.ticket.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageEnvelopeTest {

    private record SamplePayload(String value) {
    }

    @Test
    void createsEnvelopeWithGivenFields() {
        UUID messageId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-11-06T10:00:00Z");
        SamplePayload payload = new SamplePayload("seat-1");

        MessageEnvelope<SamplePayload> envelope =
            new MessageEnvelope<>(messageId, "corr-1", "SeatHeld", occurredAt, payload);

        assertThat(envelope.messageId()).isEqualTo(messageId);
        assertThat(envelope.correlationId()).isEqualTo("corr-1");
        assertThat(envelope.type()).isEqualTo("SeatHeld");
        assertThat(envelope.occurredAt()).isEqualTo(occurredAt);
        assertThat(envelope.payload()).isEqualTo(payload);
    }

    @Test
    void factoryGeneratesMessageIdAndTimestamp() {
        MessageEnvelope<SamplePayload> envelope =
            MessageEnvelope.of("corr-1", "SeatHeld", new SamplePayload("seat-1"));

        assertThat(envelope.messageId()).isNotNull();
        assertThat(envelope.occurredAt()).isNotNull();
    }

    @Test
    void rejectsBlankType() {
        SamplePayload payload = new SamplePayload("seat-1");

        assertThatThrownBy(() -> MessageEnvelope.of("corr-1", " ", payload))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("type must not be blank");
    }
}
