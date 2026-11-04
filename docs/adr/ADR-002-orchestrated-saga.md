# ADR-002: Orchestrated saga in order-service

Status: Accepted (2026-11-01)

## Context

The first draft called order-service an orchestrator. In the flow, inventory reacted to `order.created` and payment reacted to `seat.reserved`. That is choreography. Only the compensation used a command. Mixing both makes the flow hard to read and hard to test.

## Decision

order-service is the only orchestrator. It sends commands and reads events. inventory-service and payment-service never call each other and never read each other's topics.

### Order states

`PENDING` → `AWAITING_PAYMENT` → `CONFIRMED`

`CANCELLED` is reachable from `PENDING` and `AWAITING_PAYMENT`. It carries a reason: `SEAT_UNAVAILABLE`, `PAYMENT_DECLINED`, `PAYMENT_UNRESOLVED`, `HOLD_EXPIRED`, `SEAT_LOST`.

`CONFIRMED` and `CANCELLED` are final.

### Steps

| #  | order-service receives                                     | order-service does                                                                    |
|:---|:-----------------------------------------------------------|:--------------------------------------------------------------------------------------|
| 1  | `POST /orders`                                             | Create order `PENDING`. Send `HoldSeatCommand`.                                       |
| 2  | `SeatHeld`                                                 | Set `AWAITING_PAYMENT`. Send `ProcessPaymentCommand` (`paymentRequestId` = order id). |
| 2a | `SeatHoldRejected`                                         | Set `CANCELLED(SEAT_UNAVAILABLE)`.                                                    |
| 3  | `PaymentCompleted`                                         | Send `ConfirmSeatCommand`.                                                            |
| 3a | `PaymentDeclined`                                          | Send `ReleaseSeatCommand`. Set `CANCELLED(PAYMENT_DECLINED)`.                         |
| 3b | No payment reply by `payment_deadline` (order-service job) | Send the same `ProcessPaymentCommand` again. After 3 attempts, see ADR-004.           |
| 4  | `SeatConfirmed`                                            | Set `CONFIRMED`.                                                                      |
| 4a | `SeatConfirmFailed` (seat taken after hold expiry)         | Send `CancelPaymentCommand` (refund). Set `CANCELLED(SEAT_LOST)`.                     |
| 5  | `SeatHoldExpired` while `AWAITING_PAYMENT`                 | Send `CancelPaymentCommand`. Set `CANCELLED(HOLD_EXPIRED)`.                           |

Rule for late messages: `PaymentCompleted` for an order that is not `AWAITING_PAYMENT` triggers `CancelPaymentCommand`. A paid order is never left without a seat.

### Messaging

| Topic                | Producer  | Consumer  | Messages                                                                                |
|:---------------------|:----------|:----------|:----------------------------------------------------------------------------------------|
| `inventory.commands` | order     | inventory | `HoldSeatCommand`, `ConfirmSeatCommand`, `ReleaseSeatCommand`                           |
| `inventory.events`   | inventory | order     | `SeatHeld`, `SeatHoldRejected`, `SeatConfirmed`, `SeatConfirmFailed`, `SeatHoldExpired` |
| `payment.commands`   | order     | payment   | `ProcessPaymentCommand`, `CancelPaymentCommand`                                         |
| `payment.events`     | payment   | order     | `PaymentCompleted`, `PaymentDeclined`, `PaymentCancelled`                               |

Message key is the order id, so messages for one order stay in order within a partition. Failed messages go to `<topic>.dlt` after 3 retries.

## Consequences

- The whole flow is readable in one class and testable as one state machine.
- order-service is the coordination point. Its state lives in PostgreSQL, so a restart resumes the saga.
- Every handler must be idempotent (ADR-003).
- One more hop per step than choreography. Accepted.
