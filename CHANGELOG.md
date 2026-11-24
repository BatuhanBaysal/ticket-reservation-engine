# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]
### Added
- `docs/adr/ADR-007-order-api.md`: the order API with the `Idempotency-Key`, the answers and the errors
- `docs/adr/ADR-008-messaging-infrastructure.md`: the outbox, the inbox, the consumers, the topics and the correlation id
- Outbox table `outbox_event` and `OutboxWriter` in `inventory-service`. The expiry job writes a `SeatHoldExpired` event for each expired hold in the same transaction

### Changed
- ADR-005 and the engineering standards record the springdoc check: version 3.2.0 is built on Spring Boot 4.1. The project stays on Spring Boot 4.1.x
- Engineering standards: the package `messaging`, links to ADR-007 and ADR-008 and RFC 9457 for `ProblemDetail`
- The architecture document marks the saga steps by milestone
- Known limitations of the README: reading an order is not authorized, dead-letter topics have no reader and the inbox is not cleaned up

---

## [0.2.0] - 2026-11-22
### Added
- Seat table `seat_inventory` in `inventory-service` with a constraint for every seat state and the expiry index, and the `Seat` entity with a version for optimistic locking
- Seat hold in `inventory-service`: one conditional update decides the holder, the hold duration is configurable, and a repeated hold of the same order returns the existing hold
- Redis admission gate for seat holds in `inventory-service`: a second order is rejected without a database call, the gate can be turned off, and it is skipped when Redis fails or times out
- Seat confirm and release in `inventory-service`: both are safe to repeat, a late confirm succeeds when nobody else holds the seat, and a version conflict is retried up to 3 times before `SeatConflictException`
- Hold expiry job in `inventory-service`: one statement makes overdue holds available and returns them, two runs at the same time expire each hold once, and the job can be turned off
- Concurrency tests of the seat hold: 100 orders hold one seat at the same time, with the Redis gate on and off. Both give one holder. With the gate on one request reaches the database, with the gate off all 100 do
- Race test of seat confirm and the expiry job: 200 rounds with both started together, exactly one wins and no seat is sold with an expiry. A deterministic test makes the version conflict happen and checks the retry
- Repeated request tests: hold, confirm and release change the seat once, also when two requests arrive at the same moment
- Measured results of the concurrency tests in the README

### Changed
- ADR-001 amended: idempotent hold, version rule for raw updates, gate details, retry limit, late confirm and the corrected expiry SQL
- README and architecture document are easier to read on a phone: lists instead of wide tables, a Mermaid diagram and a heading for every part
- Engineering standards: rules for optional dependencies, scheduled jobs, optimistic lock retries, concurrency tests, retry tests, repeat tests and documents on a phone

---

## [0.1.0] - 2026-11-14
### Added
- `docs/adr/ADR-006-message-wire-format.md`: wire format and compatibility rules of messages
- `Topics` and `MessageTypes` in `common-events`: the four topic names of ADR-002 and the 13 message type names
- Inventory messages in `common-events`: three commands and five events as validated records, with the reason codes of `SeatHoldRejected` and `SeatConfirmFailed`
- Payment messages in `common-events`: two commands and three events as validated records, with the outcome codes of `PaymentCancelled`
- JSON codec in `common-events` (Jackson 3): `MessageCodec`, the registry of the 13 message types and the exceptions `UnknownMessageTypeException` and `InvalidMessageException`
- Serialization contract tests: a sample message and a stored JSON file for each of the 13 message types. The tests fail when the wire format changes

---

## [0.0.1] - 2026-11-08
### Added
- Monorepo root files: `.gitignore`, `.editorconfig`, `.gitattributes`, `.env.example`, `LICENSE`
- Root Maven parent POM on Spring Boot 4.1 and Spring Cloud 2025.1
- `docs/ARCHITECTURE.md`, `docs/ENGINEERING_STANDARDS.md` and decision records `ADR-001` to `ADR-005`
- CI workflow, Dependabot configuration, GitHub issue, pull request and milestone templates
- README with guarantees, Status table and Known limitations
- Local infrastructure with Docker Compose: PostgreSQL (three databases), Kafka (KRaft, single broker), Redis
- `common-events` module with the `MessageEnvelope` record
- `inventory-service` skeleton: Spring Boot application with PostgreSQL, Redis, a Liquibase baseline, a health endpoint, a Dockerfile and a Testcontainers smoke test
- `order-service` and `payment-service` skeletons: Spring Boot applications with PostgreSQL, Kafka producer and consumer settings, a Liquibase baseline, a health endpoint, Dockerfiles and Testcontainers smoke tests

[Unreleased]: https://github.com/BatuhanBaysal/ticket-reservation-engine/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/BatuhanBaysal/ticket-reservation-engine/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/BatuhanBaysal/ticket-reservation-engine/compare/v0.0.1...v0.1.0
[0.0.1]: https://github.com/BatuhanBaysal/ticket-reservation-engine/releases/tag/v0.0.1
