# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]
### Added
- `docs/adr/ADR-006-message-wire-format.md`: wire format and compatibility rules of messages
- `Topics` and `MessageTypes` in `common-events`: the four topic names of ADR-002 and the 13 message type names
- Inventory messages in `common-events`: three commands and five events as validated records, with the reason codes of `SeatHoldRejected` and `SeatConfirmFailed`

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

[Unreleased]: https://github.com/BatuhanBaysal/ticket-reservation-engine/compare/v0.0.1...HEAD
[0.0.1]: https://github.com/BatuhanBaysal/ticket-reservation-engine/releases/tag/v0.0.1
