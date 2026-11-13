package com.ticket.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class MessageCodec {

    private final JsonMapper mapper;

    public MessageCodec() {
        this.mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .withConfigOverride(BigDecimal.class,
                override -> override.setFormat(JsonFormat.Value.forShape(JsonFormat.Shape.STRING)))
            .build();
    }

    public String toJson(MessageEnvelope<?> envelope) {
        Require.notNull(envelope, "envelope");
        Class<?> payloadClass = MessageTypeRegistry.payloadClassOf(envelope.type());
        if (!payloadClass.isInstance(envelope.payload())) {
            throw new InvalidMessageException("Payload " + envelope.payload().getClass().getSimpleName()
                + " does not match the type " + envelope.type());
        }
        try {
            return mapper.writeValueAsString(envelope);
        } catch (JacksonException e) {
            throw new InvalidMessageException("Message could not be written: " + e.getMessage(), e);
        }
    }

    public MessageEnvelope<Object> fromJson(String json) {
        Require.notNull(json, "json");
        try {
            RawEnvelope raw = mapper.readValue(json, RawEnvelope.class);
            requirePresent(raw.messageId(), "messageId");
            requirePresent(raw.correlationId(), "correlationId");
            requirePresent(raw.type(), "type");
            requirePresent(raw.occurredAt(), "occurredAt");
            requirePresent(raw.payload(), "payload");
            Class<?> payloadClass = MessageTypeRegistry.payloadClassOf(raw.type());
            Object payload = mapper.treeToValue(raw.payload(), payloadClass);
            requirePresent(payload, "payload");
            return new MessageEnvelope<>(raw.messageId(), raw.correlationId(), raw.type(), raw.occurredAt(), payload);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new InvalidMessageException("Message could not be read: " + e.getMessage(), e);
        }
    }

    private static void requirePresent(Object value, String name) {
        if (value == null) {
            throw new InvalidMessageException("Message could not be read: " + name + " is missing");
        }
    }

    record RawEnvelope(UUID messageId, String correlationId, String type, Instant occurredAt, JsonNode payload) {
    }
}
