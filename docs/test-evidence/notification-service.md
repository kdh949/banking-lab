# Notification Service Foundation Evidence

Status: partial target-stack progress.

Review date: 2026-06-06.

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
- Docker Compose platform services for the notification REST API and the
  enabled Redpanda event-consumer worker, both using synthetic-only provider
  settings and an isolated notification Flyway history table with shared-schema
  baseline version `0`.
- Raw Kubernetes and Helm manifests for the notification REST API and the
  Redpanda event-consumer worker, both using the notification-service JWT
  audience, `notification_flyway_schema_history`, and synthetic provider
  disablement.
- Retry/failure and dead-letter state transitions.
- Live Compose provider-sink retry/dead-letter smoke that creates a masked
  pending delivery through the Keycloak-protected API, records retryable and
  terminal synthetic provider failures, verifies the durable dead-letter row,
  and proves delivered-state mutation is rejected after dead-letter.
- Admin template change requests with maker-checker approval/rejection,
  append-only template versioning, and the synthetic `CHAT` sink.
- Durable Temporal-compatible notification template workflow visibility through
  `notification_workflow_instances`, `notification_workflow_events`, template
  change-request workflow references, and API/admin-console timeline display.
- Recipient notification preferences with wildcard and event-specific channel
  filters, reason-required preference reads, access audit rows, masked
  suppression audit rows, and idempotent suppressed replays.
- Customer-owned notification preference self-service with token `customerId`
  scope checks, self-service access audit, and a customer-web `CWB-801`
  manifest/API-backed smoke panel.
- Customer-owned masked notification delivery history self-service with token
  `customerId` scope checks, self-service access audit, and a customer-web
  `CWB-802` manifest/API-backed smoke panel.
- Reason-required masked delivery history listing with status/channel/event
  filters and `NOTIFICATION_DELIVERY_HISTORY_VIEW` audit rows.
- Admin-console screen manifests and API-backed smoke panel wiring for
  notification template approval and recipient preference management.
- Audit-console screen manifest and API-backed smoke panel wiring for masked
  notification delivery history review.
- Synthetic-only OpenAPI and event contracts.
- TypeScript API client methods for notification event consumption, delivery
  reads/history, provider failure recording, delivered-state marking, template
  reads, template change-request approval/rejection, admin preference list/upsert,
  customer-owned preference list/upsert, and customer-owned delivery history.
- Notification-service route-level authorization filter, signed JWKS JWT
  decoder, dev-only simulator token decoder, and route role policies for event
  consumption, delivery reads, failure recording, delivered-state marking, and
  template/preference administration plus CUSTOMER-owned preference and
  delivery-history self-service.
- Live Keycloak client-credentials smoke for the confidential
  `notification-service-api` client, proving a signed `NOTIFICATION_SERVICE`
  service-account token can call the notification event route with simulator
  tokens disabled and create a masked pending synthetic delivery.

The slice does not claim full Notification Service completion. Browser E2E for
live customer/admin/audit notification API paths remains conditional on local
notification-service URLs, and live provider integrations are not in scope. Live
providers remain prohibited.

## Commands Run

```bash
npm run test:notification-service:unit
npm run test:notification-service:integration
npm run test:notification-service:integration -- --rerun-tasks
npm run test:notification-service:unit -- --rerun-tasks
npm --workspace @banking-lab/api-client run typecheck
npm run validate:manifests
npm run next:admin-console:typecheck
npm run next:audit-console:typecheck
npm run next:customer-web:typecheck
npm run packages:typecheck
npm run test:notification-service:integration -- --tests lab.banking.notification.NotificationAuthorizationIntegrationTest --rerun-tasks
npm run test:notification-service:integration -- --tests lab.banking.notification.NotificationTemplateAdminIntegrationTest --tests lab.banking.notification.NotificationAuthorizationIntegrationTest --rerun-tasks
npm run test:notification-service:keycloak-service-token
npm run test:notification-service:provider-dead-letter-compose
npm run test:e2e -- apps/admin-console/e2e/admin-console-parity.spec.ts apps/audit-console/e2e/audit-console-parity.spec.ts
npm run test:e2e -- apps/customer-web/e2e/customer-web-parity.spec.ts
docker compose --profile platform config
npm run k8s:validate
npm run helm:template
npm run security:posture-check
env COMPOSE_PROJECT_NAME=banking-lab-notification-consumer-smoke BANKING_LAB_POSTGRES_PORT=15508 BANKING_LAB_REDPANDA_PORT=19108 BANKING_LAB_REDPANDA_ADMIN_PORT=19608 BANKING_LAB_NOTIFICATION_EVENT_CONSUMER_TOPIC=banking.lab.notification-consumer-smoke BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform up -d --build postgres redpanda notification-event-consumer
env BANKING_LAB_LIVE_NOTIFICATION_COMPOSE_PROJECT=banking-lab-notification-consumer-smoke BANKING_LAB_POSTGRES_PORT=15508 BANKING_LAB_REDPANDA_PORT=19108 BANKING_LAB_REDPANDA_ADMIN_PORT=19608 BANKING_LAB_NOTIFICATION_EVENT_CONSUMER_TOPIC=banking.lab.notification-consumer-smoke BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh :services:notification-service:integrationTest --tests 'lab.banking.notification.LiveNotificationConsumerComposeSmokeIntegrationTest.live notification event consumer writes masked delivery from Compose Redpanda record' --rerun-tasks
env COMPOSE_PROJECT_NAME=banking-lab-notification-consumer-smoke BANKING_LAB_POSTGRES_PORT=15508 BANKING_LAB_REDPANDA_PORT=19108 BANKING_LAB_REDPANDA_ADMIN_PORT=19608 BANKING_LAB_NOTIFICATION_EVENT_CONSUMER_TOPIC=banking.lab.notification-consumer-smoke BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform down -v
npm test
npm run evidence:refresh-check
npm run node:retirement-gate
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
  `NotificationTemplateAdminIntegrationTest`, `NotificationPreferenceIntegrationTest`, and
  `NotificationKafkaConsumerIntegrationTest`.
- `npm run test:notification-service:integration -- --tests lab.banking.notification.NotificationAuthorizationIntegrationTest --rerun-tasks`:
  pass; customer-owned preference self-service accepts matching CUSTOMER
  `customerId` scope, customer-owned delivery history self-service returns only
  the matching customer's masked delivery rows, cross-customer access is
  rejected, and admin preference/history routes stay restricted to operations,
  audit, or compliance roles as modeled.
- `npm run test:notification-service:integration -- --tests lab.banking.notification.NotificationTemplateAdminIntegrationTest --tests lab.banking.notification.NotificationAuthorizationIntegrationTest --rerun-tasks`:
  pass after 2026-06-06 workflow visibility update; PostgreSQL Testcontainers
  verified template change-request workflow instance backreferences, requested
  and terminal workflow events, auditor-readable workflow timeline API output,
  and CUSTOMER denial on the workflow list route. The first sandboxed Gradle run
  failed before tests with `java.net.SocketException: Operation not permitted`;
  the approved escalated rerun passed.
- `npm run test:notification-service:unit -- --rerun-tasks`: pass;
  notification-service Kotlin compiled and ran `NotificationEventConsumerWorkerTest`.
- `npm --workspace @banking-lab/api-client run typecheck`: pass; notification
  service API client methods compile.
- `npm run validate:manifests`: pass; notification admin/audit manifests satisfy
  the shared screen-engine template contract.
- `npm run next:admin-console:typecheck`: pass; admin-console compiles with the
  notification template/preference API-backed panel wiring.
- `npm run next:audit-console:typecheck`: pass; audit-console compiles with the
  notification delivery-history API-backed panel wiring.
- `npm run next:customer-web:typecheck`: pass; customer-web compiles with the
  owned notification preference and delivery-history self-service panel wiring.
- `npm run packages:typecheck`: pass; shared screen/form/auth/api packages compile
  after notification client contract expansion.
- `npm run test:e2e -- apps/admin-console/e2e/admin-console-parity.spec.ts apps/audit-console/e2e/audit-console-parity.spec.ts`:
  pass; 4 manifest-rendering tests passed and 4 API/Keycloak live smokes skipped
  because API/Keycloak E2E base URLs were not configured.
- `npm run test:e2e -- apps/customer-web/e2e/customer-web-parity.spec.ts`: pass;
  customer-web manifest rendering passed and API/Keycloak/payment/notification
  live smokes were skipped because the corresponding E2E base URLs were not
  configured.
- `docker compose --profile platform config`: pass; platform profile renders
  `notification-service` and `notification-event-consumer` with Redpanda
  bootstrap configuration, synthetic provider disablement, and
  `notification_flyway_schema_history`.
- `npm run k8s:validate`: pass; structural validation covers
  `notification-service` and `notification-event-consumer` deployments with
  readiness/liveness probes, Redpanda bootstrap configuration, synthetic
  provider disablement, `notification-service-api`, and
  `notification_flyway_schema_history`.
- `npm run helm:template`: pass; Helm renders the notification REST API
  Deployment/Service and event-consumer Deployment with the same synthetic
  provider, audience, Flyway, and Redpanda consumer controls.
- `npm run security:posture-check`: pass; static posture checks include raw
  Kubernetes and Helm notification API/worker deployment controls.
- Live Compose notification consumer smoke: pass; a disposable Compose project
  started PostgreSQL, Redpanda, and `notification-event-consumer`, produced the
  same synthetic `PaymentLedgerPostingRequested` broker record twice, observed
  one durable inbox row, one masked delivery request, one pending attempt, and a
  worker batch log with `processed=1`, `duplicates=1`, `deliveries=1`, and
  `syntheticOnly=true`; the stack and volumes were removed with
  `docker compose --profile platform down -v`.
- `npm run test:notification-service:keycloak-service-token`: pass; a
  disposable Compose project started PostgreSQL, Redpanda, Keycloak, and
  `notification-service` with `BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false`.
  Keycloak issued a signed client-credentials token for
  `notification-service-api` containing the `NOTIFICATION_SERVICE` realm role
  and `notification-service-api` audience, and the notification API accepted it
  on `POST /api/notifications/events` to create one `PENDING`,
  `syntheticOnly=true` delivery item without exposing the raw phone number.
- `npm run test:notification-service:provider-dead-letter-compose`: pass; a
  disposable Compose project started PostgreSQL, Redpanda, Keycloak, and
  `notification-service` with simulator tokens disabled. The smoke used a signed
  `notification-service-api` client-credentials token to create one masked
  pending delivery, record a retryable synthetic provider failure, record a
  terminal failure at threshold `2`, verify one durable
  `notification_dead_letters` row and two failure/dead-letter attempts, and
  prove a delivered-state command on the dead-letter delivery returns structured
  `NOTIFICATION_STATE_TRANSITION_REJECTED`.
- `npm test`: pass; the Node oracle/static scaffold suite verifies the
  notification-service Dockerfile, Compose API/consumer services, disabled API
  consumer setting, enabled worker setting, and Prometheus scrape targets.

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
- delivery history filters by recipient, event, channel, and status while
  returning masked messages only;
- delivery history reads require actor/reason and append
  `NOTIFICATION_DELIVERY_HISTORY_VIEW` audit rows.

`NotificationAuthorizationIntegrationTest` verifies:

- missing bearer tokens are rejected with
  `NOTIFICATION_AUTHORIZATION_POLICY_VIOLATION`;
- customer tokens cannot consume notification events or mutate delivery state;
- `NOTIFICATION_SERVICE` tokens can consume domain events and mark synthetic
  deliveries as delivered;
- `AUDITOR` tokens can read masked delivery state but cannot mutate it;
- customer tokens cannot list masked delivery history;
- auditor tokens can list masked delivery history with a reason-required query;
- customer delivery-history self-service uses only
  `/api/notifications/customers/{customerId}/deliveries`, accepts matching
  CUSTOMER token `customerId` scope, returns masked messages, records
  `NOTIFICATION_CUSTOMER_DELIVERY_HISTORY_VIEW`, and rejects
  cross-customer/ops calls on that customer route;
- customers cannot create template change requests;
- operations makers can create template change requests but cannot approve them
  through the route policy;
- operations managers can approve pending template changes, while auditors can
  read the activated synthetic template;
- customers and auditors cannot mutate recipient preferences through the route
  policy;
- operations actors can upsert preferences, while auditors can read durable
  preference state with reason-required audit;
- customer preference self-service uses only
  `/api/notifications/customers/{customerId}/preferences`, accepts matching
  CUSTOMER token `customerId` scope, persists preference state with the customer
  actor, records `NOTIFICATION_CUSTOMER_PREFERENCE_VIEW`, and rejects
  cross-customer/ops calls on that customer route;
- authorization tests use only dev-enabled simulator tokens, while the runtime
  also supports signed JWKS JWT validation through `banking-lab.security.jwt.*`.

`NotificationTemplateAdminIntegrationTest` verifies:

- pending template changes are not active and cannot be used for delivery;
- every template change request creates a durable
  `NOTIFICATION_TEMPLATE_CHANGE` workflow instance and `REQUESTED` timeline
  event;
- approval and rejection append terminal workflow events and update workflow
  status without activating rejected templates;
- template approval is blocked when maker and checker are the same actor;
- checker roles are limited to operations, compliance, or notification managers;
- approval activates a higher-version template, retires previous active
  templates for the event/channel, and enables `SYNTHETIC_CHAT_SINK` delivery;
- rejection is terminal and does not activate a template;
- reason, positive version, synthetic-only provider, channel/provider match, and
  raw account/phone/email literal checks are enforced before durable change
  creation.

`NotificationPreferenceIntegrationTest` verifies:

- wildcard disabled preferences suppress delivery creation after inbox
  idempotency is recorded;
- suppressed events write durable audit rows with masked account and phone
  payload values;
- duplicate suppressed `sourceEventId` replays return no deliveries and do not
  duplicate suppression rows;
- event-specific enabled preferences override wildcard opt-out rows;
- preference reads require actor/reason and append
  `NOTIFICATION_PREFERENCE_VIEW` audit rows;
- preference administration requires a reason and rejects `syntheticOnly=false`.

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

`springScaffold.test.mjs` verifies:

- `docker-compose.yml` includes `notification-service` and
  `notification-event-consumer` in the platform profile;
- the REST API container keeps `BANKING_LAB_NOTIFICATION_EVENT_CONSUMER_ENABLED=false`;
- the worker container sets `BANKING_LAB_NOTIFICATION_EVENT_CONSUMER_ENABLED=true`;
- both containers keep `BANKING_LAB_NOTIFICATION_SERVICE_REAL_PROVIDER_ENABLED=false`;
- `package.json` exposes `test:notification-service:keycloak-service-token`,
  and the checked-in smoke script disables simulator tokens, requests a
  Keycloak `client_credentials` token for `notification-service-api`, verifies
  the `NOTIFICATION_SERVICE` role/audience, and calls
  `/api/notifications/events` through Bearer auth;
- the platform profile uses `redpanda:9092` and a dedicated
  `notification_flyway_schema_history` table with Flyway baseline version `0`
  to avoid core-banking Flyway version conflicts on the shared synthetic schema;
- Prometheus scrapes `notification-service:8089` and
  `notification-event-consumer:8089`.

`LiveNotificationConsumerComposeSmokeIntegrationTest` verifies:

- the Compose-managed `notification-event-consumer` consumes from host-side
  Redpanda through the configured topic;
- duplicate broker records with one `outboxEventId` produce one inbox row and
  one delivery request;
- the stored pending attempt is attributed to
  `notification-compose-event-consumer`;
- account and phone values are masked in both rendered message and stored
  payload;
- the worker emits an operational batch log with processed, duplicate, and
  delivery counts.

API client typecheck verifies:

- `consumeNotificationEvent`, `getNotificationDelivery`,
  `recordNotificationFailure`, `markNotificationDelivered`,
  `listNotificationDeliveries`, `listNotificationTemplates`,
  `createNotificationTemplateChangeRequest`,
  `listNotificationTemplateChangeRequests`, `getNotificationTemplateChangeRequest`,
  `approveNotificationTemplateChangeRequest`, and
  `rejectNotificationTemplateChangeRequest`, `listNotificationPreferences`, and
  `upsertNotificationPreference`, `listCustomerNotificationPreferences`, and
  `upsertCustomerNotificationPreference`, and `listCustomerNotificationDeliveries`
  methods are available with typed request/response contracts.

`nextScaffold.test.mjs` verifies:

- admin-console keeps notification template approval and preference management
  manifests under the shared screen-manifest renderer path;
- the admin API-backed panel uses `NEXT_PUBLIC_BANKING_NOTIFICATION_API_BASE_URL`
  and the typed
  `listNotificationTemplates`/`listNotificationTemplateChangeRequests`/
  `listNotificationPreferences` client methods, including workflow status and
  timeline display;
- audit-console keeps the `AUD-301` masked notification delivery-history inquiry
  manifest under the shared renderer path;
- the audit API-backed panel uses `NEXT_PUBLIC_BANKING_NOTIFICATION_API_BASE_URL`
  and the typed `listNotificationDeliveries` client method.
- customer-web keeps the `CWB-801` notification preference self-service command
  manifest under the shared renderer path;
- customer-web keeps the `CWB-802` notification delivery-history self-service
  inquiry manifest under the shared renderer path;
- the customer API-backed panel uses
  `NEXT_PUBLIC_BANKING_NOTIFICATION_API_BASE_URL` and the typed
  `listCustomerNotificationPreferences`/`upsertCustomerNotificationPreference`
  and `listCustomerNotificationDeliveries` client methods.

## Synthetic Boundary

The migration constrains provider kinds to `SYNTHETIC_SMS_SINK`,
`SYNTHETIC_EMAIL_SINK`, `SYNTHETIC_PUSH_SINK`, and `SYNTHETIC_CHAT_SINK`. It
does not contain real SMS, email, push, chat, telecom, or external notification
provider configuration.
The Kafka consumer rejects outbox envelopes that do not carry `syntheticOnly=true`
in payload or headers before creating delivery side effects.
Template administration rejects `syntheticOnly=false`, non-matching provider
kinds, and raw account/phone/email literals in template bodies.
Admin delivery history and admin preference reads require reasons and persist
synthetic access-audit rows. Customer preference and delivery-history
self-service are scoped to the CUSTOMER token `customerId`, record self-service
access audit, and return or store only masked synthetic recipient/channel/event
data. Preference administration rejects `syntheticOnly=false`, stores only
synthetic recipient/channel/event filters, and suppression audit records persist
masked payload JSON.
Template workflow rows are synthetic-only, reference only template change request
IDs, actor IDs, statuses, and business reasons, and do not copy template body
text or delivery payloads into timeline events.
The Docker Compose services explicitly set
`BANKING_LAB_NOTIFICATION_SERVICE_REAL_PROVIDER_ENABLED=false`.
The Keycloak service-token smoke explicitly disables simulator tokens and uses
only the synthetic `notification-service-api` service account to create a masked
pending delivery through the REST event route.
The provider dead-letter Compose smoke also disables simulator tokens and uses
only the synthetic `notification-service-api` service account to drive provider
failure and dead-letter state through the REST delivery endpoints. It verifies
raw phone values are not exposed by live API responses or delivery history.
The raw Kubernetes and Helm deployment manifests also set
`BANKING_LAB_NOTIFICATION_SERVICE_REAL_PROVIDER_ENABLED=false`, use the
service-specific `notification-service-api` audience, and keep the event
consumer on the synthetic `redpanda:9092` domain-events stream.

## Remaining Risk

This is still a partial feature slice. Browser E2E for live notification
customer/admin/audit API paths remains conditional on local service URLs, and
live Kubernetes/Helm rollout of the notification API/worker remains future work.
