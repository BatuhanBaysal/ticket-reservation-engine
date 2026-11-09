# ADR-006: Message wire format

Status: Accepted (2026-11-09)

## Context

Services exchange commands and events through Kafka (ADR-002). ADR-003 defines the fields of the envelope but not how it is written to a topic.

Without a written rule, each service could serialize messages its own way. A field added later could break a consumer that is not yet deployed. The message definitions live in one module, `common-events`, so one rule can cover every service.

## Decision

**A Kafka record value is the envelope as one JSON object in UTF-8.** Producers and consumers use Kafka's String serializer and deserializer. The `MessageCodec` in `common-events` converts between the envelope and the string. No service configures its own JSON mapper for messages.

**The Kafka key is the order id** as text (ADR-002).

```json
{
  "messageId": "0f8b2c36-5d0e-4c53-9b1a-7e2a5f4d9c10",
  "correlationId": "c-7f3a91",
  "type": "SeatHeld",
  "occurredAt": "2026-11-09T10:15:30.123Z",
  "payload": {
    "orderId": "5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55",
    "eventId": "evt-001",
    "seatId": "A-12",
    "price": "49.9000",
    "holdExpiresAt": "2026-11-09T10:25:30.123Z"
  }
}
```

**Field rules:**
- `type` equals the name of the payload record. The set of names is defined once in `MessageTypes`. The codec picks the payload class from `type`.
- Property names are camelCase and equal the record component names.
- Ids are UUIDs in lowercase text. `correlationId` is text.
- Timestamps are ISO-8601 text in UTC with a `Z` suffix, never numbers.
- Amounts are JSON strings that hold a decimal number, never floating point numbers. They map to `BigDecimal` and `NUMERIC(19, 4)`.
- Reasons and outcomes are String codes, not Java enums. A new code does not break an old consumer.

**Compatibility rules:**
- A new field is allowed. It is optional: when absent, the consumer reads it as null. Consumers ignore fields they do not know. Consumers are deployed before producers.
- Renaming or removing a field, or changing its type or meaning, is not allowed. Such a change needs a new message type with a new name.
- Every message type has a stored JSON sample in `common-events/src/test/resources/contracts/`. A test compares the codec output with the sample. A change of the wire format fails the build and shows up in the pull request.

**Unknown type.** If `type` is not registered, the codec throws `UnknownMessageTypeException`. The consumer treats the record as a failed message and follows the failure path of the engineering standards.

**Library.** Jackson 3 (`tools.jackson`). The version comes from the Spring Boot BOM. `common-events` has no Spring dependency.

## Consequences

- A message can be read in the Kafka console tools and in logs.
- JSON is larger than a binary format. Accepted at this scale.
- There is no schema registry. The contract is the records plus the stored samples. A consumer outside this repository has no schema file. Accepted: every service is in this repository.
- Every service must use `MessageCodec`.
- An old consumer keeps working when a producer adds a field. An old consumer fails on an unknown type and the record goes to the dead-letter topic.

## Rejected alternatives

- **Spring Kafka `JsonSerializer` with type headers.** It writes Java class names into record headers. Renaming a class or a package breaks every consumer, and the type is not part of the message.
- **Avro or Protobuf with a schema registry.** Checks compatibility automatically, but needs a registry container and code generation. The stored samples give the main benefit: a changed format fails a test.
