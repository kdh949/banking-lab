# Contract Runtime Evidence Boundary

Review date: 2026-06-10

Status: partial. The repository has structural OpenAPI, API-client, Kotlin controller source-to-OpenAPI path/method diffing, payment-service and reporting-service DTO schema parity, AsyncAPI, event-schema gates, and runtime envelope fixture/schema/source-marker validation. It does not yet have PLAN-required full core-banking springdoc/Jackson DTO schema generation.

This evidence is synthetic-only. It does not use real money, real PII, real KYC/AML providers, payment/card networks, or external financial institution APIs.

## Current Passing Boundary

- `npm run contracts:lint` validates checked-in OpenAPI/AsyncAPI structure, structured-error defaults, idempotency markers, reason-required markers, and event-envelope metadata.
- `npm run contracts:check-client` validates checked-in OpenAPI `operationId` values against `packages/api-client/src/index.ts` shared client methods and explicit exemptions.
- `npm run contracts:diff-openapi` validates Kotlin `@RestController` `/api/**` and checked-in `/health` paths/methods against checked-in OpenAPI contracts, generates payment-service and reporting-service DTO schemas from Kotlin data classes, and writes generated source snapshots under `docs/test-evidence/generated/openapi/`.
- `npm run contracts:check-events` validates AsyncAPI schema references plus `syntheticOnly: true` event payload schema constraints.
- `npm run contracts:validate-runtime-events` validates synthetic runtime envelope fixtures against the 17 event JSON Schemas, checks Kafka header/body envelope consistency, verifies producer ack/source markers, and writes `docs/test-evidence/generated/event-envelope-runtime-validation.json`.
- `npm run contracts:runtime-evidence` generates `docs/test-evidence/generated/contract-runtime-evidence.json` and records the boundary between source inspection and missing runtime gates.

## Source-Inspected Runtime Markers

- `PaymentKafkaOutboxPublisher` writes Kafka envelope payload and headers with `syntheticOnly`, `sourceService`, `eventType`, `aggregateId`, and `occurredAt`.
- `ReportingKafkaOutboxPublisher` writes the same envelope payload/header fields for reporting domain events.
- `NotificationKafkaConsumer` rejects missing envelope metadata and non-synthetic events before delivery creation.
- `LedgerCommandService` persists durable synthetic outbox records, but core-banking runtime Kafka envelope validation remains part of the missing gate.

## Explicit Gaps

- Kotlin controller source-to-OpenAPI path/method diffing is wired through `contracts:diff-openapi`, and payment-service and reporting-service DTO schemas are generated from Kotlin data classes. Do not claim payment-service and reporting-service DTO schema parity is equivalent to full core-banking Spring/Jackson/springdoc DTO-level OpenAPI schema parity.
- `contracts:validate-runtime-events` is wired, but it is a deterministic fixture/schema/source-marker gate. Do not claim this fixture/schema/source-marker gate is equivalent to exhaustive live broker producer/consumer coverage.
- Existing live producer/consumer integration tests remain valuable and should continue to be run for Redpanda/Testcontainers behavior such as broker ack, duplicate delivery, and failure/dead-letter transitions.

## Commands

```bash
npm run contracts:lint
npm run contracts:check-client
npm run contracts:diff-openapi
npm run contracts:check-events
npm run contracts:validate-runtime-events
npm run contracts:runtime-evidence
```

Future completion should add the PLAN-required gates without downgrading failures to warnings:

```bash
npm run test:core-banking:integration
npm run test:payment-service:integration
npm run test:notification-service:integration
npm run test:reporting-service:integration
```
