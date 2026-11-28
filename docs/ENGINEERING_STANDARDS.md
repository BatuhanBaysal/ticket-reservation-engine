# Engineering Standards

Rules every module follows. A rule is **Planned** until the first module applies it.

## Backend

- Packages: `controller`, `service`, `repository`, `domain` and `messaging` (Kafka consumers, outbox, inbox and topic settings). No business logic in controllers.
- Input validation with Bean Validation. One global exception handler that returns `ProblemDetail` (RFC 9457).
- REST rules of order-service: [ADR-007](adr/ADR-007-order-api.md).
- Config profiles: `local`, `test`. Secrets come from environment variables.
- Actuator health endpoints. OpenAPI through springdoc 3.2.0, the version built on Boot 4.1 ([ADR-005](adr/ADR-005-scope-and-platform.md)). A newer springdoc line needs a newer Boot line.
- One multi-stage Dockerfile per service.
- Correlation id in every log line (`correlationId` from the message envelope or request header).
- Money: `NUMERIC(19, 4)` in the database, `BigDecimal` in code.
- An optional dependency such as Redis never fails a request. The failure is logged as a warning and the request continues without it.
- A scheduled job has an `enabled` setting and an `interval` setting. The `test` profile turns jobs off, so tests decide when a job runs.
- A Kafka listener has an `enabled` setting. The `test` profile turns listeners off, and a test that needs one turns it on.

## Database

- All schema changes go through Liquibase. No manual tables.
- Constraints: unique, foreign key and check constraints where the rule is a data rule.
- Each index has a one-line reason next to its changeset (the `comment` of the changeset).
- Timestamps: `timestamptz` in the database, `Instant` in code.
- An operation that can lose an optimistic lock runs in a new transaction on each attempt, with at most 3 attempts.
- A step that changes data requires an open transaction and does not retry. The caller opens the transaction and the attempts wrap all of it. A message handler runs the inbox insert, the change and the outbox write in one transaction.
- ER diagram: [ARCHITECTURE.md](ARCHITECTURE.md).

## Messaging

- Envelope, outbox, inbox: [ADR-003](adr/ADR-003-outbox-and-inbox.md).
- Wire format and compatibility rules: [ADR-006](adr/ADR-006-message-wire-format.md).
- Code, poller, consumers, topics and correlation id: [ADR-008](adr/ADR-008-messaging-infrastructure.md).
- Services write and read messages with `MessageCodec` only. They do not configure their own JSON mapper for messages.
- Every message type has a sample message and a stored JSON file in the tests of `common-events`. A change of the wire format changes the file in the same pull request.
- Producer: `acks=all`, idempotence on. Consumer: manual commit after the database commit.
- Retry 3 times, then `<topic>.dlt`. Unknown and invalid messages go there without a retry.

## Required tests

1. 100 concurrent holds on one seat, Redis gate on: 1 `SeatHeld`, 99 `SeatHoldRejected` (v0.2.0).
2. Same as 1, Redis gate off: 1 `SeatHeld`, 99 `SeatHoldRejected` (v0.2.0).
3. Confirm and expiry race on one seat: exactly one wins, no `SOLD` seat with an expiry (v0.2.0).
4. Same `Idempotency-Key` sent twice: 1 order, same response (v0.3.0).
5. Same message delivered twice: 1 business effect, for hold, charge and state change (v0.2.0 to v0.4.0).
6. Outbox, exception after the state change and before the commit: no message published (v0.3.0).
7. Outbox, broker down and then up: message published after recovery (v0.3.0).
8. `LOST_RESPONSE` payment: order `CONFIRMED`, 1 charge (v0.4.0).
9. No payment reply on 3 attempts: `CANCELLED(PAYMENT_UNRESOLVED)`, no charge remains (v0.4.0).
10. Saga, happy path and 4 compensation paths: final order and seat states match ARCHITECTURE.md (v0.4.0).
11. Serialization of each message type: round trip equals original (v0.1.0).
12. One API flow through the gateway: order `CONFIRMED` (v0.5.0).

- Unit tests follow Arrange-Act-Assert. Phases are separated by a blank line, without phase comments. One behavior per test.
- Integration tests use Testcontainers: real PostgreSQL, Redis and Kafka.
- A concurrency test starts all threads on a latch, so they begin together. It repeats for 20 rounds, each on a new seat, and checks the database state as well as the results.
- A retry path is not left to timing. A test runs the competing action between the load and the save, so the conflict always happens.
- A repeat test sends the same request twice, in sequence and at the same moment, and checks that the state and the version changed once.
- A handler test sends the same message twice, with the same message id, in sequence and at the same moment. It checks one effect, one event and one inbox row.
- A test that proves a gate or a cache counts the calls that reach the database.
- A test of a broker outage pauses the Kafka container with Docker, so its port does not change. A test that needs Kafka has its own container and its own context.
- JaCoCo reports coverage. There is no percentage gate. The tests above are the quality bar.

## Documentation language

- English. Short, plain sentences.
- Present tense only for what exists. Everything else is labeled **Planned**.
- Say why and what resulted. Not "Redis gate added" but "Redis gate rejects losers without a database call; measured in v0.6.0".
- Numbers and evidence: latency, test count, coverage, load test result.
- No promotional adjectives.
- README keeps a Status table and a Known limitations section. Update the Status table at the end of each milestone.
- Keep documents readable on a phone: tables have at most three columns, diagrams are Mermaid and code lines are short.

## Git workflow

- Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`, `build:`, `ci:`, `chore:`).
- Every change starts from an issue and ends in a PR that closes it (`Closes #n`).
- Branch names: `<type>/<short-description>`, for example `feat/inventory-service-skeleton`.
- Issue and PR titles use the same Conventional Commits prefix. The PR copies the issue's labels and milestone.
- PR description: what changed, why, how it was tested.
- `main` is protected by a ruleset: no direct pushes, no force pushes, no deletion. Changes go through pull requests.
- Merge method: rebase and merge by default. Squash and merge for Dependabot pull requests and for pull requests with fix-up commits.
- One tag and one release per milestone.
- The Maven version is the next release with the `-SNAPSHOT` suffix, for example `0.1.0-SNAPSHOT` during `v0.1.0`. The tag marks the release. The first pull request of the next milestone raises the version.
