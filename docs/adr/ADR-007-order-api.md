# ADR-007: Order API

Status: Accepted (2026-11-23)

## Context

A client can send the same order request twice, because of a timeout or a retry. The second request must not create a second order.

An order is finished by a saga over Kafka (ADR-002). It takes several steps in three services, so the API cannot answer with the final state.

## Decision

### Create an order

`POST /orders` takes these inputs:

| Input              | Rule                                  |
|:-------------------|:--------------------------------------|
| `Idempotency-Key`  | Header. Required. 1 to 64 characters. |
| `X-Customer-Id`    | Header. Required. 1 to 64 characters. |
| `X-Correlation-Id` | Header. Optional. See ADR-008.        |
| `eventId`          | Body. Required. 1 to 64 characters.   |
| `seatId`           | Body. Required. 1 to 64 characters.   |

The price is not an input. inventory-service returns it in `SeatHeld`.

The answer is `202 Accepted` with the header `Location: /orders/{id}` and the body `{"orderId": "..."}`. The order is `PENDING` and the saga continues without the client.

In one transaction, order-service inserts the order and writes `HoldSeatCommand` to the outbox (ADR-003).

### One order per key

`orders.idempotency_key` is unique. The database decides, as for seat holds (ADR-001).

| Request                                  | Answer                    |
|:-----------------------------------------|:--------------------------|
| New key                                  | `202`, a new order        |
| Known key, same customer, event and seat | `202`, the same order id  |
| Known key, other customer, event or seat | `422`, nothing is created |

A repeated request returns the same status, header and body, whatever the state of the order is now. It writes no second command.

Requests with the same key at the same moment: one insert wins. The others fail on the unique key, roll back, read the stored order in a new transaction and answer as for a known key.

The key is global, not per customer. Clients use UUIDs. When two customers send the same key, the second one gets `422`.

### Read an order

`GET /orders/{id}` answers `200` with `orderId`, `status`, `cancelReason`, `eventId`, `seatId` and `amount`. `cancelReason` is null unless the order is `CANCELLED`. `amount` is null until the order is `AWAITING_PAYMENT`.

An unknown id answers `404`. An id that is not a UUID answers `400`.

### Errors

Errors are `ProblemDetail` (RFC 9457) with the media type `application/problem+json`.

| Status | Cause                                                     |
|:-------|:----------------------------------------------------------|
| `400`  | Missing or invalid header, invalid body, invalid order id |
| `404`  | Unknown order                                             |
| `422`  | Key used before with another request                      |
| `500`  | Unexpected error. The body has no internal details        |

### Seats and events

order-service does not know seats. A seat that does not exist is rejected by inventory-service, and the order ends as `CANCELLED(SEAT_UNAVAILABLE)`, because ADR-002 has one reason for every rejected hold.

### Not in the API

There is no authentication. The customer id is a header that the caller sets. Anyone with an order id can read the order. Order ids are random UUIDs. This is listed in the Known limitations of the README.

There is no cancel endpoint and no list of orders.

## Consequences

- A client that times out sends the same request with the same key and gets the same order.
- The request never waits for the saga. The client reads the result with `GET`.
- The key is stored in the order row. No second store can disagree with it.
- Keys are never deleted. The table grows with the orders.

## Rejected alternatives

- **`201 Created` with the final state.** The request would wait for three services. A timeout would leave the client without an answer, which is the case the key is meant to solve.
- **Key in Redis.** A second source of truth that can disagree with the database.
- **A separate table for keys.** One more table that holds what the order row already holds.
- **`409 Conflict` for a reused key.** The IETF draft for the `Idempotency-Key` header uses `422` for a key that is reused with another payload.
