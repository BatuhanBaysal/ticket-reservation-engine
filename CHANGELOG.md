# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]
### Added
- Seat table `seat_inventory` in `inventory-service` with a constraint for every seat state and the expiry index, and the `Seat` entity with a version for optimistic locking
- Seat hold in `inventory-service`: one conditional update decides the holder, the hold duration is configurable, and a repeated hold of the same order returns the existing hold
- Redis admission gate for seat holds in `inventory-service`: a second order is rejected without a database call, the gate can be turned off, and it is skipped when Redis fails or times out
- Seat confirm and release in `inventory-service`: both are safe to repeat, a late confirm succeeds when nobody else holds the seat, and a version conflict is retried up to 3 times before `SeatConflictException`
- Hold expiry job in `inventory-service`: one statement makes overdue holds available and returns them, two runs at the same time expire each hold once, and the job can be turned off

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

[Unreleased]: https://github.com/BatuhanBaysal/ticket-reservation-engine/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/BatuhanBaysal/ticket-reservation-engine/compare/v0.0.1...v0.1.0
[0.0.1]: https://github.com/BatuhanBaysal/ticket-reservation-engine/releases/tag/v0.0.1
