# ADR-004: A payment timeout is an unknown outcome, not a failure

Status: Accepted (2026-11-01)

## Context

When no payment reply arrives, the charge may or may not have happened. Treating a timeout as `failed` and releasing the seat can leave a customer charged without a seat.

## Decision

**payment-service stores every request.** Table `payment`: `id`, `payment_request_id` (`UNIQUE`), `order_id`, `amount` (`NUMERIC(19, 4)`), `status`, `created_at`, `updated_at`.

Statuses: `CHARGED`, `DECLINED`, `REFUNDED`, `CANCELLED`.

**Same request, same result.** A repeated `ProcessPaymentCommand` with a known `payment_request_id` charges nothing. payment-service publishes the stored result again.

**order-service retries with the same key.**
- When an order becomes `AWAITING_PAYMENT`, it gets `payment_deadline = now() + 30 s`.
- A job in order-service finds orders past the deadline and sends the same `ProcessPaymentCommand` again.
- Maximum 3 attempts. Total wait is about 90 s, inside the 10-minute hold.
- After 3 attempts with no reply: send `ReleaseSeatCommand` and `CancelPaymentCommand`. Set `CANCELLED(PAYMENT_UNRESOLVED)`.

**`CancelPaymentCommand` is idempotent:**

| Stored status                       | Result                                                        |
|:------------------------------------|:--------------------------------------------------------------|
| `CHARGED`                           | Refund. Status `REFUNDED`.                                    |
| none                                | Record `CANCELLED`. A later charge with this key is rejected. |
| `DECLINED`, `REFUNDED`, `CANCELLED` | No change.                                                    |

**Simulator modes** (configuration, not a request field):
- `SUCCESS`: charge and reply.
- `DECLINE`: record `DECLINED` and reply.
- `LOST_RESPONSE`: record `CHARGED`, do not reply on the first attempt. Reply on the next attempt with the stored result.

## Consequences

- Test: with `LOST_RESPONSE`, the order reaches `CONFIRMED` and the charge count is 1.
- Test: with no reply on any attempt, the order is `CANCELLED(PAYMENT_UNRESOLVED)` and no charge remains.
- The "unknown outcome" is detected by the deadline job in order-service. payment-service does not publish it.
- Real payment providers need reconciliation jobs. This project has none. Listed in Known limitations.
