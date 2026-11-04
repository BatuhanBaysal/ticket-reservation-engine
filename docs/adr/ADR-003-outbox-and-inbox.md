# ADR-003: Transactional outbox and idempotent consumers

Status: Accepted (2026-11-01)

## Context

A service must update its database and publish a Kafka message. Without a shared transaction, a crash between the two leaves them out of sync.

The outbox pattern fixes publishing: the message is written in the same database transaction as the state change. It does not remove duplicates. The poller can publish a row twice if it crashes before marking it. Kafka delivery is at-least-once. Consumers must handle repeats. The first draft had no consumer-side rule.

## Decision

All three services (order, inventory, payment) have an outbox and an inbox.

**Outbox table `outbox_event`:** `id` (uuid), `topic`, `message_key`, `payload` (jsonb), `created_at`, `published_at` (null until sent).

**Poller:** runs every 500 ms.
1. In one transaction, select up to 100 unpublished rows with `FOR UPDATE SKIP LOCKED`, ordered by `created_at`.
2. Send each to Kafka and wait for the ack (`acks=all`, producer idempotence on).
3. Set `published_at`. Commit.

**Inbox table `processed_message`:** `message_id` (uuid, primary key), `consumer`, `processed_at`.

**Consumer handler**, in one database transaction:
1. `INSERT INTO processed_message ... ON CONFLICT DO NOTHING`.
2. 0 rows inserted: the message is a repeat. Skip the business logic.
3. 1 row inserted: run the business logic, write outbox rows, commit.

Offsets are committed manually after the database commit. Auto-commit is off.

**Message envelope:** `messageId`, `correlationId`, `type`, `occurredAt`, `payload`. The correlation id appears in every log line.

## Consequences

- A rolled-back transaction publishes nothing.
- A repeated message has no second effect.
- Latency per hop is up to about 500 ms.
- The outbox table grows. A cleanup job deletes published rows older than 7 days.
- Two extra tables per service.

## Rejected alternatives

- **Debezium (CDC).** Needs Kafka Connect. More moving parts than this project needs.
- **Publish after commit, no outbox.** Loses the message on a crash between commit and send.
