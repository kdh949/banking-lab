# Notification Service Foundation Evidence

Status: partial target-stack progress.

Review date: 2026-06-05.

## Scope

This evidence covers the first synthetic Notification Service slice:

- `services/notification-service` Kotlin/Spring Boot module registration.
- Notification-service Flyway schema for templates, idempotent inbox events,
  delivery requests, delivery attempts, and dead-letter records.
- Event-consumption service logic that creates masked synthetic delivery
  requests from domain events.
- Retry/failure and dead-letter state transitions.
- Synthetic-only OpenAPI and event contracts.

The slice does not claim full Notification Service completion. Runtime
Kafka/Redpanda consumption, customer preference screens, admin template
approval, Keycloak route enforcement, and live provider integrations are not in
scope. Live providers remain prohibited.

## Commands Run

```bash
npm run test:notification-service:unit
npm run test:notification-service:integration
npm run test:notification-service:integration -- --rerun-tasks
npm run test:notification-service:unit -- --rerun-tasks
```

The commands require the same Gradle file-lock socket and Testcontainers access
as other Spring services, so they were run with approved escalation.

An early unit/integration run was started in parallel and produced Kotlin
incremental-cache daemon fallback noise. The first integration run also exposed a
template placeholder mismatch: the seeded payment notification template did not
render the masked account field expected by the test. The template was corrected
to render `{accountNo}` after payload masking, then integration and unit commands
were rerun sequentially with `--rerun-tasks`.

## Passing Tests

- `npm run test:notification-service:integration -- --rerun-tasks`: pass;
  PostgreSQL Testcontainers ran `NotificationDeliveryIntegrationTest`.
- `npm run test:notification-service:unit -- --rerun-tasks`: pass;
  notification-service Kotlin compiled with no unit test sources.

## Integration Coverage

`NotificationDeliveryIntegrationTest` verifies:

- duplicate `sourceEventId` consumption is idempotent and does not duplicate
  delivery requests;
- delivery request and attempt state is PostgreSQL-backed;
- generated messages are masked and do not expose raw phone numbers;
- provider kind is a synthetic sink such as `SYNTHETIC_SMS_SINK`;
- retry failures are durable and move to `DEAD_LETTER` at threshold;
- dead-letter deliveries cannot be marked delivered later;
- pending deliveries can be marked `DELIVERED` through a synthetic provider sink.

## Synthetic Boundary

The migration constrains provider kinds to `SYNTHETIC_SMS_SINK`,
`SYNTHETIC_EMAIL_SINK`, and `SYNTHETIC_PUSH_SINK`. It does not contain real SMS,
email, push, chat, telecom, or external notification provider configuration.

## Remaining Risk

This is a foundation slice. Runtime Kafka consumption from core-banking Outbox,
template maker-checker approval, customer preference APIs, admin screens, and
Keycloak route enforcement remain future work.
