package com.ticket.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MessageEnvelope<T>(
    UUID messageId,
    String correlationId,
    String type,
    Instant occurredAt,
    T payload) {

    public MessageEnvelope {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(payload, "payload");
        if (correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        if (type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
    }

    public static <T> MessageEnvelope<T> of(String correlationId, String type, T payload) {
        return new MessageEnvelope<>(UUID.randomUUID(), correlationId, type, Instant.now(), payload);
    }
}
