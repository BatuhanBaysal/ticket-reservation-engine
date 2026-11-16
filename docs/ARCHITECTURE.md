# Architecture

Status: the message contracts, the seat table and the seat rules describe code that exists. Everything else is **Planned**, except where a decision record is linked.

## Services

| Module              | Responsibility                                                                   | Data                             |
|:--------------------|:---------------------------------------------------------------------------------|:---------------------------------|
| `common-events`     | Message envelope, event and command records (Java `record`) and the JSON codec.  | none                             |
| `inventory-service` | Seat state, holds, expiry. [ADR-001](adr/ADR-001-seat-hold.md)                   | PostgreSQL `inventory_db`, Redis |
| `order-service`     | Order API, saga orchestration. [ADR-002](adr/ADR-002-orchestrated-saga.md)       | PostgreSQL `order_db`            |
| `payment-service`   | Simulated payments, idempotent by key. [ADR-004](adr/ADR-004-payment-timeout.md) | PostgreSQL `payment_db`          |
| `api-gateway`       | Routing, rate limit, correlation id, internal secret.                            | Redis                            |

Every service that publishes messages has an outbox and an inbox ([ADR-003](adr/ADR-003-outbox-and-inbox.md)).

## Flow

```mermaid
sequenceDiagram
    participant C as Client
    participant O as order-service
    participant I as inventory-service
    participant P as payment-service
    C->>O: POST /orders (Idempotency-Key)
    O->>I: HoldSeatCommand
    I-->>O: SeatHeld
    O->>P: ProcessPaymentCommand
    P-->>O: PaymentCompleted
    O->>I: ConfirmSeatCommand
    I-->>O: SeatConfirmed
    Note over O: order CONFIRMED
```

Compensation paths (details in ADR-002 and ADR-004):

| Trigger                                  | Result                                                        |
|:-----------------------------------------|:--------------------------------------------------------------|
| `SeatHoldRejected`                       | `CANCELLED(SEAT_UNAVAILABLE)`                                 |
| `PaymentDeclined`                        | release seat, `CANCELLED(PAYMENT_DECLINED)`                   |
| `SeatHoldExpired` while awaiting payment | cancel payment, `CANCELLED(HOLD_EXPIRED)`                     |
| `SeatConfirmFailed`                      | refund, `CANCELLED(SEAT_LOST)`                                |
| No payment reply after 3 attempts        | release seat, cancel payment, `CANCELLED(PAYMENT_UNRESOLVED)` |

## State machines

```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> AWAITING_PAYMENT: SeatHeld
    PENDING --> CANCELLED: SeatHoldRejected
    AWAITING_PAYMENT --> CONFIRMED: SeatConfirmed
    AWAITING_PAYMENT --> CANCELLED: declined / expired / lost / unresolved
    CONFIRMED --> [*]
    CANCELLED --> [*]
```

```mermaid
stateDiagram-v2
    [*] --> AVAILABLE
    AVAILABLE --> HELD: hold
    HELD --> AVAILABLE: release / expiry
    HELD --> SOLD: confirm
```

## Message contracts

Every message is the payload of the envelope (ADR-003) and is written as JSON ([ADR-006](adr/ADR-006-message-wire-format.md)). The records live in `common-events`, the topic names in `Topics` and the type names in `MessageTypes`.

### Inventory messages

Topic `inventory.commands`, sent by order-service, read by inventory-service:

| Message              | Fields                         |
|:---------------------|:-------------------------------|
| `HoldSeatCommand`    | `orderId`, `eventId`, `seatId` |
| `ConfirmSeatCommand` | `orderId`, `eventId`, `seatId` |
| `ReleaseSeatCommand` | `orderId`, `eventId`, `seatId` |

Topic `inventory.events`, sent by inventory-service, read by order-service:

| Message             | Fields                                                   |
|:--------------------|:---------------------------------------------------------|
| `SeatHeld`          | `orderId`, `eventId`, `seatId`, `price`, `holdExpiresAt` |
| `SeatHoldRejected`  | `orderId`, `eventId`, `seatId`, `reason`                 |
| `SeatConfirmed`     | `orderId`, `eventId`, `seatId`                           |
| `SeatConfirmFailed` | `orderId`, `eventId`, `seatId`, `reason`                 |
| `SeatHoldExpired`   | `orderId`, `eventId`, `seatId`                           |

Field types: `orderId` is a UUID. `eventId`, `seatId` and `reason` are text. `price` is a decimal amount greater than zero. `holdExpiresAt` is a UTC timestamp. No field may be null and text fields must not be blank.

`SeatHeld` carries the price because order-service needs the amount for `ProcessPaymentCommand` (ADR-002, step 2).

Reason codes are text, not enums, so a new code does not break an old consumer:

| Message             | Codes                                |
|:--------------------|:-------------------------------------|
| `SeatHoldRejected`  | `SEAT_UNAVAILABLE`, `SEAT_NOT_FOUND` |
| `SeatConfirmFailed` | `HOLD_NOT_FOUND`, `SEAT_TAKEN`       |

### Payment messages

Topic `payment.commands`, sent by order-service, read by payment-service:

| Message                 | Fields                                  |
|:------------------------|:----------------------------------------|
| `ProcessPaymentCommand` | `paymentRequestId`, `orderId`, `amount` |
| `CancelPaymentCommand`  | `paymentRequestId`, `orderId`           |

Topic `payment.events`, sent by payment-service, read by order-service:

| Message            | Fields                                   |
|:-------------------|:-----------------------------------------|
| `PaymentCompleted` | `paymentRequestId`, `orderId`, `amount`  |
| `PaymentDeclined`  | `paymentRequestId`, `orderId`, `reason`  |
| `PaymentCancelled` | `paymentRequestId`, `orderId`, `outcome` |

Field types: `paymentRequestId` and `orderId` are UUIDs. `amount` is a decimal amount greater than zero. `reason` and `outcome` are text. No field may be null and text fields must not be blank.

In the saga, `paymentRequestId` equals the order id (ADR-002, step 2). payment-service stores and looks up requests by `paymentRequestId` only (ADR-004).

`outcome` of `PaymentCancelled` is a text code, not an enum:

| Code        | Meaning                                                     |
|:------------|:------------------------------------------------------------|
| `REFUNDED`  | The payment had been charged and was refunded               |
| `CANCELLED` | No charge existed. A later charge with this key is rejected |

### Codec

`MessageCodec` in `common-events` writes an envelope as JSON and reads it back. On reading it takes the payload class from `type`. Instants are ISO-8601 text and amounts are decimal strings. Fields the record does not have are ignored.

| Case                                                                                                | Result                        |
|:----------------------------------------------------------------------------------------------------|:------------------------------|
| `type` is not registered                                                                            | `UnknownMessageTypeException` |
| Malformed JSON, a missing envelope or payload field, or a value that breaks the rules of its record | `InvalidMessageException`     |
| On writing, the payload class does not match `type`                                                 | `InvalidMessageException`     |

### Contract tests

Every message type has a sample message in the tests of `common-events` and a stored JSON file in `src/test/resources/contracts/<Type>.json`. The tests check three things: the codec writes the stored JSON, it reads the stored JSON back into the sample, and a round trip returns the same message. A change of a field name or format fails these tests, and the stored file changes in the same pull request ([ADR-006](adr/ADR-006-message-wire-format.md)).

## Data model

Money columns use `NUMERIC(19, 4)` and `BigDecimal` in code. Timestamps are `timestamptz`.

### Overview

Only `SEAT_INVENTORY` exists today. The other tables are **Planned**.

```mermaid
erDiagram
    ORDERS {
        uuid id PK
        text idempotency_key UK
        text customer_id
        text event_id
        text seat_id
        numeric amount
        text status
        text cancel_reason
        timestamptz payment_deadline
        int payment_attempts
        bigint version
    }
    SEAT_INVENTORY {
        uuid id PK
        text event_id
        text seat_id
        text status
        uuid held_by_order_id
        timestamptz hold_expires_at
        numeric price
        bigint version
    }
    PAYMENT {
        uuid id PK
        uuid payment_request_id UK
        uuid order_id
        numeric amount
        text status
    }
    OUTBOX_EVENT {
        uuid id PK
        text topic
        text message_key
        jsonb payload
        timestamptz published_at
    }
    PROCESSED_MESSAGE {
        uuid message_id PK
        text consumer
        timestamptz processed_at
    }
```

`OUTBOX_EVENT` and `PROCESSED_MESSAGE` exist in each of the three databases. The three databases are separate. The diagram shows them together for reading only.

### Indexes

The reason for each index is recorded next to its Liquibase changeset.

- `seat_inventory (hold_expires_at) WHERE status = 'HELD'`: the expiry job reads only held rows.
- `orders (payment_deadline) WHERE status = 'AWAITING_PAYMENT'`: the retry job reads only these rows.
- `outbox_event (created_at) WHERE published_at IS NULL`: the poller reads only unpublished rows.

### Seat table

`seat_inventory` in `inventory_db` ([ADR-001](adr/ADR-001-seat-hold.md)), created by the Liquibase changeset `001-seat-inventory`.

| Column             | Type            | Rule                                   |
|:-------------------|:----------------|:---------------------------------------|
| `id`               | `uuid`          | primary key                            |
| `event_id`         | `varchar(64)`   | not null, `UNIQUE (event_id, seat_id)` |
| `seat_id`          | `varchar(64)`   | not null                               |
| `status`           | `varchar(16)`   | `AVAILABLE`, `HELD` or `SOLD`          |
| `held_by_order_id` | `uuid`          | see below                              |
| `hold_expires_at`  | `timestamptz`   | see below                              |
| `price`            | `numeric(19,4)` | greater than zero                      |
| `version`          | `bigint`        | optimistic lock, starts at 0           |

The status decides which of the two nullable columns are filled. One check constraint enforces it, so the database cannot hold an invalid state. A status that is not one of the three matches none of the rows below and is rejected too.

| Status      | `held_by_order_id` | `hold_expires_at` |
|:------------|:-------------------|:------------------|
| `AVAILABLE` | null               | null              |
| `HELD`      | set                | set               |
| `SOLD`      | set                | null              |

The database clock (`now()`) decides every hold and every expiry. The partial index `idx_seat_inventory_held_expiry` is described under Indexes.

## Seat rules

### Hold

`SeatService.hold` in `inventory-service` returns `Held` (price and expiry) or `Rejected` with the reason `SEAT_UNAVAILABLE` or `SEAT_NOT_FOUND` ([ADR-001](adr/ADR-001-seat-hold.md)). One conditional update in PostgreSQL decides. It uses the database clock, and the hold lasts `inventory.hold.duration` (default 10 minutes).

| Seat when the order asks              | Result                                                     |
|:--------------------------------------|:-----------------------------------------------------------|
| `AVAILABLE`                           | `Held`. The seat becomes `HELD` for the order              |
| `HELD`, hold expired                  | `Held`. The seat passes to the asking order                |
| `HELD` by the same order, not expired | `Held` with the existing price and expiry. Nothing changes |
| `HELD` by another order, not expired  | `Rejected(SEAT_UNAVAILABLE)`                               |
| `SOLD`                                | `Rejected(SEAT_UNAVAILABLE)`                               |
| not found                             | `Rejected(SEAT_NOT_FOUND)`                                 |

A seat that becomes available between the update and the read is reported as `SEAT_UNAVAILABLE`. The order can ask again.

## Decision records

| ADR                                       | Decision                                                           |
|:------------------------------------------|:-------------------------------------------------------------------|
| [001](adr/ADR-001-seat-hold.md)           | PostgreSQL decides who holds a seat. Redis is a gate.              |
| [002](adr/ADR-002-orchestrated-saga.md)   | order-service orchestrates the saga.                               |
| [003](adr/ADR-003-outbox-and-inbox.md)    | Outbox for publishing, inbox for repeats.                          |
| [004](adr/ADR-004-payment-timeout.md)     | A timeout is an unknown outcome. Retry with the same key.          |
| [005](adr/ADR-005-scope-and-platform.md)  | Boot 4.1, five modules, what is out of scope.                      |
| [006](adr/ADR-006-message-wire-format.md) | Messages are JSON text with a `type` field. Fields are only added. |
