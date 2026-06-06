# Phase 7 Bounded Context Hardening Evidence

Date: 2026-06-06

Branch: `codex/remaining-hardening-phase-7-bounded-contexts`

Scope: Phase 7 of `docs/codex/remaining-hardening-goals.md` for payment, notification, and reporting bounded-context verification. This work stays inside the synthetic banking lab. It does not add real customer money, real personal data, real payment/card-network integration, Open Banking, real KYC providers, real notification providers, or external financial institution APIs.

## Implemented This Phase

- Hardened payment-service Kafka domain-event envelopes with persisted `occurredAt`, `aggregateId`, `eventType`, `sourceService`, and `syntheticOnly` metadata in both Kafka record headers and JSON envelope headers.
- Hardened reporting-service Kafka domain-event envelopes with the same persisted metadata contract.
- Added payment-service Redpanda integration assertions for full envelope metadata on cancellation, failure, retry, and dead-letter lifecycle events.
- Added reporting-service Redpanda integration assertions for full envelope metadata on generated/exported reporting events.
- Added notification-service consumer envelope metadata validation before delivery side effects are created.
- Updated notification Redpanda and live Compose fixtures to carry full source-service, event-type, aggregate-id, occurred-at, and synthetic-only metadata.
- Added a negative notification integration test proving missing `sourceService` metadata is rejected before inbox or delivery rows are created.

## Already Present And Reused

- Payment-service already had instruction lifecycle, idempotent create/replay, settlement bridge, cancellation maker-checker, outbox retry/dead-letter, Redpanda publisher, ops/staff UI wiring, OpenAPI, AsyncAPI, and Compose smoke scripts.
- Notification-service already had template approval, preference/suppression policy, synthetic provider behavior, retry/dead-letter state, masking, self-service/admin APIs, OpenAPI, AsyncAPI, and Compose/keycloak smoke scripts.
- Reporting-service already had definition catalog, idempotent artifact generation, content SHA-256, export metadata, retention sweep, workflow timeline, outbox publisher, audit/admin UI wiring, OpenAPI, AsyncAPI, and Compose/keycloak smoke scripts.

## Commands Run

| Command | Result | Notes |
| --- | --- | --- |
| `scripts/run-core-banking-tests.sh :services:payment-service:integrationTest --tests '*PaymentKafkaOutboxPublisherIntegrationTest' :services:notification-service:integrationTest --tests '*NotificationKafkaConsumerIntegrationTest' :services:reporting-service:integrationTest --tests '*ReportingKafkaOutboxPublisherIntegrationTest'` | escalated pass | Targeted Redpanda/Testcontainers integration coverage for the changed payment, notification, and reporting envelope paths. |
| `scripts/run-core-banking-tests.sh :services:payment-service:test :services:payment-service:integrationTest :services:notification-service:test :services:notification-service:integrationTest :services:reporting-service:test :services:reporting-service:integrationTest` | escalated pass | Full bounded-context unit/integration Gradle tasks passed; reporting unit task has no test sources. |
| `npm run contracts:lint` | pass | OpenAPI/AsyncAPI structural contract lint passed. |
| `npm run contracts:check-client` | pass | Shared API client operation coverage passed. |
| `npm run contracts:check-events` | pass | AsyncAPI event schema references and synthetic-only payload guards passed. |
| `npm run test:payment-service:outbox-worker-compose` | escalated pass | Disposable synthetic Compose stack proved payment outbox worker settlement path. |
| `npm run test:payment-service:domain-publisher-compose` | escalated pass | Disposable synthetic Compose stack proved payment domain-event publisher path. |
| `npm run test:payment-service:keycloak-service-token` | escalated pass | Disposable synthetic Keycloak/core/payment stack accepted a `PAYMENT_SERVICE` service token for dispatch. |
| `npm run test:notification-service:provider-dead-letter-compose` | escalated pass | Disposable synthetic Compose stack proved notification provider retry/dead-letter behavior. |
| `npm run test:notification-service:keycloak-service-token` | escalated pass | Disposable synthetic Keycloak/notification stack accepted a `NOTIFICATION_SERVICE` service token. |
| `npm run test:reporting-service:domain-publisher-compose` | escalated pass | Disposable synthetic Compose stack proved reporting domain-event publisher path. |
| `npm run test:reporting-service:keycloak-service-token` | escalated pass | Disposable synthetic Keycloak/reporting stack accepted a `REPORTING_ANALYST` service token for catalog, artifact list/export, and retention sweep. |
| `npm run security:secrets-check` | pass | Secret placeholder scan passed for 857 files after Phase 7 changes. |
| `npm test` | pass | Node oracle/structural suite passed with 172 tests. |
| `git diff --check -- . ':!docs/test-evidence/generated/*'` | pass | Whitespace validation passed while excluding pre-existing generated evidence changes. |
| `gh pr view 51 --json state,mergeStateStatus,mergeable,statusCheckRollup,url,headRefName,baseRefName` | pass | PR #51 was mergeable but unstable because hosted CI check runs failed before runner startup. |
| `gh api /repos/kdh949/banking-lab/check-runs/79874851504/annotations` | pass | Confirmed observed PR #51 GitHub Actions annotation: hosted jobs were not started because account payments/spending limits blocked runner allocation. |

## Not Run Yet

- Full platform live runtime, Kubernetes, DAST, and large-ledger drills were not part of Phase 7 and are tracked separately.
- PR #51 hosted GitHub Actions were attempted, but all jobs failed before runner startup due GitHub account billing/spending-limit restrictions. No hosted test steps executed.

## Residual Risk

- Notification-service currently consumes domain events and persists masked delivery state; it does not publish a dedicated notification domain-event outbox in this slice.
- Envelope metadata is structurally enforced in service tests and runtime consumer/publisher paths. JSON Schema validation of live Kafka records against every `contracts/events/*.schema.json` remains a future generated-contract improvement.
