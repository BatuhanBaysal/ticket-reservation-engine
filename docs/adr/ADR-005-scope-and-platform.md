# ADR-005: Scope and platform baseline

Status: Accepted (2026-11-01)

## Context

The goal of the project is evidence for four things: no double booking, idempotent payments, consistent state across services, and correct compensation. Time is about 3 months.

The first draft listed 7 modules, a React dashboard, Grafana, JWT and PDF generation. Several of these do not produce evidence for the four goals.

## Decision

### Platform

- Java 21.
- Spring Boot 4.1.x. Boot 3.4 ended OSS support in December 2025. Boot 3.5, the last 3.x line, ended in June 2026. A new project starts on a supported line.
- Spring Cloud 2025.1.x, used only by api-gateway. Release train 2025.1.2 is compatible with Boot 4.1.
- Kafka client, Liquibase, Testcontainers, JUnit and JaCoCo versions come from the Boot BOM.
- Check before adding a dependency: it must support Boot 4.1. Check springdoc when the service skeletons are added (v0.0.1).

### v1.0.0 scope

`common-events`, `inventory-service`, `order-service`, `payment-service`, `api-gateway`.

### Not in v1.0.0

| Item                              | Reason                                                                                              |
|:----------------------------------|:----------------------------------------------------------------------------------------------------|
| `notification-service` (PDF)      | A Kafka consumer that writes PDFs adds no evidence for the four goals.                              |
| `ticket-frontend`, Playwright     | The project is a backend. The end-to-end flow is tested through the API.                            |
| Grafana and Prometheus dashboards | Micrometer metrics and a load test result give the numbers. Dashboards add setup time only.         |
| JWT / authentication              | No auth service exists. The customer id is a request header. Listed in Known limitations.           |
| Redisson                          | ADR-001: Spring Data Redis covers `SET NX PX`.                                                      |
| Resilience4j                      | Gateway rate limiting uses the built-in Redis rate limiter. Payment retry is ADR-004.               |
| Lombok, MapStruct, OpenPDF        | Java records and plain mapping are enough. Fewer annotation processors on a new Boot major version. |

### Local infrastructure

- One PostgreSQL container with three databases (`order_db`, `inventory_db`, `payment_db`). Services do not read each other's database.
- One Kafka broker (KRaft). One Redis.

## Consequences

- Fewer modules. Every module serves one of the four goals.
- If time remains after v1.0.0, a notification consumer and a frontend can be added as new milestones.
- The `bug_report` and `feature_request` templates still list `notification-service` and `ticket-frontend`. They stay until those modules exist or are dropped.
