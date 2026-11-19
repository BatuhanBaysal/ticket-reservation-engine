# ADR-001: Seat holds — the database is the source of truth, Redis is an admission gate

Status: Accepted (2026-11-01)

## Context

A seat has at most one holder. A hold lasts 10 minutes by default.

A distributed lock protects a short critical section. A 10-minute hold is a lease: it must survive restarts and be queryable. The first draft used both a Redis lock and a database status for the same fact. Two sources of truth can disagree.

## Decision

**PostgreSQL decides who holds a seat.**

Table `seat_inventory` in inventory-service:

| Column                | Note                         |
|:----------------------|:-----------------------------|
| `event_id`, `seat_id` | `UNIQUE (event_id, seat_id)` |
| `status`              | `AVAILABLE`, `HELD`, `SOLD`  |
| `held_by_order_id`    | null unless `HELD` or `SOLD` |
| `hold_expires_at`     | null unless `HELD`           |
| `price`               | `NUMERIC(19, 4)`             |
| `version`             | JPA `@Version`               |

**Hold is one conditional UPDATE.** One row returned means held. No row means the order does not get the seat, except when it already holds it (below).

```sql
UPDATE seat_inventory
SET status = 'HELD', held_by_order_id = :orderId,
    hold_expires_at = now() + make_interval(secs => :holdSeconds),
    version = version + 1
WHERE event_id = :eventId AND seat_id = :seatId
  AND (status = 'AVAILABLE' OR (status = 'HELD' AND hold_expires_at < now()))
RETURNING price, hold_expires_at;
```

`holdSeconds` is the configured hold duration (default 10 minutes). Every raw update of this table also sets `version = version + 1`, so the version check of confirm and release sees it.

**A hold is idempotent for the same order.** The update returns no row and the seat is `HELD` by the same order: the hold is still valid, because an expired hold would have matched the update. The service returns the existing price and expiry and changes nothing. Any other state is a rejection: `SEAT_UNAVAILABLE`, or `SEAT_NOT_FOUND` when the seat does not exist.

**Redis is an admission gate.** Before the UPDATE, inventory-service runs `SET hold:{eventId}:{seatId} {orderId} NX` with the hold duration as TTL.
- The key exists with another order's id: reject without touching the database.
- The key exists with the same order's id: continue, so a repeated hold stays idempotent.
- The database rejects the hold: delete the key, only if the value equals `orderId`. One Lua script does the compare and the delete, so they are atomic.
- Redis fails or times out, or the gate is turned off with `inventory.hold.gate.enabled=false`: skip the gate. The UPDATE still allows one holder.
- Redis commands time out after 500 ms, so a down Redis adds a short delay to a hold and never stops it.
- Release deletes the key the same way. Expiry needs no delete, because the key has the same TTL as the hold.

**Confirm and release** load the row and use `@Version`. A concurrent change, such as the expiry job or a new hold, makes the save fail on the version. The operation then starts again in a new transaction and sees the new state. After 3 attempts it gives up with `SeatConflictException` and the seat is unchanged.

A seat that is `HELD` by the order can be confirmed after its hold has expired, as long as no other order holds it. A paid order keeps its seat unless someone else took it.

**Expiry job** runs every 30 seconds:

```sql
WITH overdue AS (
    SELECT id, held_by_order_id
    FROM seat_inventory
    WHERE status = 'HELD' AND hold_expires_at < now()
    FOR UPDATE SKIP LOCKED
)
UPDATE seat_inventory AS seat
SET status = 'AVAILABLE', held_by_order_id = NULL,
    hold_expires_at = NULL, version = seat.version + 1
FROM overdue
WHERE seat.id = overdue.id
RETURNING seat.event_id, seat.seat_id, overdue.held_by_order_id;
```

The `RETURNING` of a plain update returns the new row, where the order id is already null. The order id is read first, in `overdue`. `SKIP LOCKED` lets two runs work at the same time: a hold that one run has locked is skipped by the other, so no hold expires twice.

An expired hold does not block the seat. The hold update takes a seat whose hold has expired. The job makes the state consistent and reports the expiry.

Each returned row writes a `SeatHoldExpired` event to the outbox (ADR-003). Index: `(hold_expires_at) WHERE status = 'HELD'`, because the job reads only held rows.

## Consequences

- Correctness does not depend on Redis. The concurrency test runs with the gate on and off. Both runs must produce 1 holder.
- The gate has one purpose: reject losers without a database round trip. The benefit is measured in v0.6.0 and reported only if the numbers show it.
- A key that survives a failed delete can reject a valid hold for up to the hold duration. This trade-off is accepted. The database still never allows two holders.
- Redisson is not used. Spring Data Redis is enough for `SET NX PX`.

## Rejected alternatives

- **Redisson `RLock` with a 10-minute lease.** The lock expires on its own clock, is not queryable, and does not survive a Redis failover.
- **Database only.** Correct. Kept as the fallback path. Redis is added to measure and explain the trade-off.
