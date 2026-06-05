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
- Kafka/Redpanda outbox envelope consumption through
  `NotificationKafkaConsumer`, plus a disabled-by-default runtime worker and
  Micrometer counters.
- Retry/failure and dead-letter state transitions.
- Synthetic-only OpenAPI and event contracts.
- TypeScript API client methods for notification event consumption, delivery
  reads, provider failure recording, and delivered-state marking.
- Notification-service route-level authorization filter, signed JWKS JWT
  decoder, dev-only simulator token decoder, and route role policies for event
  consumption, delivery reads, failure recording, and delivered-state marking.

The slice does not claim full Notification Service completion. Customer
preference screens, admin template approval, live Compose notification-consumer
smoke evidence, live notification-service Keycloak smoke evidence, and live
provider integrations are not in scope. Live providers remain prohibited.

## Commands Run

```bash
npm run test:notification-service:unit
npm run test:notification-service:integration
npm run test:notification-service:integration -- --rerun-tasks
npm run test:notification-service:unit -- --rerun-tasks
npm --workspace @banking-lab/api-client run typecheck
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
  PostgreSQL and Redpanda Testcontainers ran
  `NotificationDeliveryIntegrationTest`, `NotificationAuthorizationIntegrationTest`,
  and `NotificationKafkaConsumerIntegrationTest`.
- `npm run test:notification-service:unit -- --rerun-tasks`: pass;
  notification-service Kotlin compiled and ran `NotificationEventConsumerWorkerTest`.
- `npm --workspace @banking-lab/api-client run typecheck`: pass; notification
  service API client methods compile.

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

`NotificationAuthorizationIntegrationTest` verifies:

- missing bearer tokens are rejected with
  `NOTIFICATION_AUTHORIZATION_POLICY_VIOLATION`;
- customer tokens cannot consume notification events or mutate delivery state;
- `NOTIFICATION_SERVICE` tokens can consume domain events and mark synthetic
  deliveries as delivered;
- `AUDITOR` tokens can read masked delivery state but cannot mutate it;
- authorization tests use only dev-enabled simulator tokens, while the runtime
  also supports signed JWKS JWT validation through `banking-lab.security.jwt.*`.

`NotificationKafkaConsumerIntegrationTest` verifies:

- Redpanda-backed outbox envelopes are consumed through Kafka clients and routed
  into the same durable notification inbox path as the REST event API;
- duplicate broker records with the same `outboxEventId` create one inbox row,
  one delivery request, and one pending attempt;
- payment account aliases such as `debitAccountId` are normalized into the
  template payload and masked in the rendered delivery message and stored
  masked payload JSON;
- raw phone numbers are removed from stored masked payload JSON;
- unsupported synthetic event types are committed as skipped records without
  creating notification delivery side effects.

`NotificationEventConsumerWorkerTest` verifies:

- worker configuration is converted into `NotificationKafkaConsumerConfig`;
- batch size, group id, topic, client id, requested actor, default channel, and
  supported event types are passed to the consumer;
- Micrometer counters record polled, processed, duplicate, and created-delivery
  counts;
- disabled workers do not auto-start.

API client typecheck verifies:

- `consumeNotificationEvent`, `getNotificationDelivery`,
  `recordNotificationFailure`, and `markNotificationDelivered` methods are
  available with typed request/response contracts.

## Synthetic Boundary

The migration constrains provider kinds to `SYNTHETIC_SMS_SINK`,
`SYNTHETIC_EMAIL_SINK`, and `SYNTHETIC_PUSH_SINK`. It does not contain real SMS,
email, push, chat, telecom, or external notification provider configuration.
The Kafka consumer rejects outbox envelopes that do not carry `syntheticOnly=true`
in payload or headers before creating delivery side effects.

## Remaining Risk

This is still a partial feature slice. Live Compose evidence for the
notification-service Kafka worker, template maker-checker approval, customer
preference APIs, admin screens, and live notification-service Keycloak smoke
evidence remain future work.
