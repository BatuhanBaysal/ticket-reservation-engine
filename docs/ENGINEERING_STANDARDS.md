# Engineering Standards

Rules every module follows. A rule is **Planned** until the first module applies it.

## Backend

- Packages: `controller`, `service`, `repository`, `domain`. No business logic in controllers.
- Input validation with Bean Validation. One global exception handler that returns `ProblemDetail` (RFC 7807).
- Config profiles: `local`, `test`. Secrets come from environment variables.
- Actuator health endpoints. OpenAPI through springdoc (check Boot 4.1 support when the service skeletons are added).
- One multi-stage Dockerfile per service.
- Correlation id in every log line (`correlationId` from the message envelope or request header).
- Money: `NUMERIC(19, 4)` in the database, `BigDecimal` in code.

## Database

- All schema changes go through Liquibase. No manual tables.
- Constraints: unique, foreign key and check constraints where the rule is a data rule.
- Each index has a one-line reason next to its changeset.
- ER diagram: [ARCHITECTURE.md](ARCHITECTURE.md).

## Messaging

- Envelope, outbox, inbox: [ADR-003](adr/ADR-003-outbox-and-inbox.md).
- Producer: `acks=all`, idempotence on. Consumer: manual commit after the database commit.
- Retry 3 times, then `<topic>.dlt`.

## Required tests

| #  | Test                                                | Pass condition                                      | Milestone       |
|:---|:----------------------------------------------------|:----------------------------------------------------|:----------------|
| 1  | 100 concurrent holds on one seat, Redis gate on     | 1 `SeatHeld`, 99 `SeatHoldRejected`                 | v0.2.0          |
| 2  | Same as 1, Redis gate off                           | 1 `SeatHeld`, 99 `SeatHoldRejected`                 | v0.2.0          |
| 3  | Confirm and expiry race on one seat                 | Exactly one wins. No `SOLD` seat with an expiry.    | v0.2.0          |
| 4  | Same `Idempotency-Key` sent twice                   | 1 order, same response                              | v0.3.0          |
| 5  | Same message delivered twice                        | 1 business effect (hold, charge, state change)      | v0.2.0 – v0.4.0 |
| 6  | Outbox: exception after state change, before commit | No message published                                | v0.3.0          |
| 7  | Outbox: broker down, then up                        | Message published after recovery                    | v0.3.0          |
| 8  | `LOST_RESPONSE` payment                             | Order `CONFIRMED`. 1 charge.                        | v0.4.0          |
| 9  | No payment reply on 3 attempts                      | `CANCELLED(PAYMENT_UNRESOLVED)`. No charge remains. | v0.4.0          |
| 10 | Saga: happy path and 4 compensation paths           | Final order and seat states match ARCHITECTURE.md   | v0.4.0          |
| 11 | Serialization of each message type                  | Round trip equals original                          | v0.1.0          |
| 12 | One API flow through the gateway                    | Order `CONFIRMED`                                   | v0.5.0          |

- Unit tests follow Arrange-Act-Assert. Phases are separated by a blank line, without phase comments. One behavior per test.
- Integration tests use Testcontainers: real PostgreSQL, Redis and Kafka.
- JaCoCo reports coverage. There is no percentage gate. The tests above are the quality bar.

## Documentation language

- English. Short, plain sentences.
- Present tense only for what exists. Everything else is labeled **Planned**.
- Say why and what resulted. Not "Redis gate added" but "Redis gate rejects losers without a database call; measured in v0.6.0".
- Numbers and evidence: latency, test count, coverage, load test result.
- No promotional adjectives.
- README keeps a Status table and a Known limitations section. Update the Status table at the end of each milestone.

## Git workflow

- Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`, `build:`, `ci:`, `chore:`).
- Every change starts from an issue and ends in a PR that closes it (`Closes #n`).
- Branch names: `<type>/<short-description>`, for example `feat/inventory-service-skeleton`.
- Issue and PR titles use the same Conventional Commits prefix. The PR copies the issue's labels and milestone.
- PR description: what changed, why, how it was tested.
- `main` is protected by a ruleset: no direct pushes, no force pushes, no deletion. Changes go through pull requests.
- Merge method: rebase and merge by default. Squash and merge for Dependabot pull requests and for pull requests with fix-up commits.
- One tag and one release per milestone.
