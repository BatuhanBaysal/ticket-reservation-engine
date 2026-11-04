# ADR-001: Seat holds — the database is the source of truth, Redis is an admission gate

Status: Accepted (2026-11-01)

## Context

A seat has at most one holder. A hold lasts 10 minutes.

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

**Hold is one conditional UPDATE.** Rows affected = 1 means held. Rows affected = 0 means rejected.

```sql
UPDATE seat_inventory
SET status = 'HELD', held_by_order_id = :orderId,
    hold_expires_at = now() + interval '10 minutes'
WHERE event_id = :eventId AND seat_id = :seatId
  AND (status = 'AVAILABLE' OR (status = 'HELD' AND hold_expires_at < now()));
```

**Redis is an admission gate.** Before the UPDATE, inventory-service runs `SET hold:{eventId}:{seatId} {orderId} NX PX 600000`.
- Key exists: reject without touching the database.
- Key set, UPDATE affects 0 rows: delete the key (only if the value equals `orderId`) and reject.
- Redis is down or empty: skip the gate. The UPDATE still allows one holder.
- Release deletes the key (only if the value equals `orderId`). Expiry needs no delete, because the key has the same 10-minute TTL.

**Confirm and release** load the row and use `@Version`. A concurrent confirm and expiry fails on version, and the loser retries or reports failure.

**Expiry job** runs every 30 seconds:

```sql
UPDATE seat_inventory SET status = 'AVAILABLE', held_by_order_id = NULL, hold_expires_at = NULL
WHERE status = 'HELD' AND hold_expires_at < now()
RETURNING event_id, seat_id, held_by_order_id;
```

Each returned row writes a `SeatHoldExpired` event to the outbox (ADR-003). Index: `(hold_expires_at) WHERE status = 'HELD'`, because the job reads only held rows.

## Consequences

- Correctness does not depend on Redis. The concurrency test runs with the gate on and off. Both runs must produce 1 holder.
- The gate has one purpose: reject losers without a database round trip. The benefit is measured in v0.6.0 and reported only if the numbers show it.
- A key that survives a failed delete can reject a valid hold for up to 10 minutes. This trade-off is accepted. The database still never allows two holders.
- Redisson is not used. Spring Data Redis is enough for `SET NX PX`.

## Rejected alternatives

- **Redisson `RLock` with a 10-minute lease.** The lock expires on its own clock, is not queryable, and does not survive a Redis failover.
- **Database only.** Correct. Kept as the fallback path. Redis is added to measure and explain the trade-off.
