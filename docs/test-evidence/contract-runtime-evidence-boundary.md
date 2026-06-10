# Contract Runtime Evidence Boundary

Review date: 2026-06-10

Status: partial. The repository has structural OpenAPI, API-client, AsyncAPI, and event-schema gates. It does not yet have PLAN-required generated OpenAPI DTO diffing or runtime event-envelope validation gates.

This evidence is synthetic-only. It does not use real money, real PII, real KYC/AML providers, payment/card networks, or external financial institution APIs.

## Current Passing Boundary

- `npm run contracts:lint` validates checked-in OpenAPI/AsyncAPI structure, structured-error defaults, idempotency markers, reason-required markers, and event-envelope metadata.
- `npm run contracts:check-client` validates checked-in OpenAPI `operationId` values against `packages/api-client/src/index.ts` shared client methods and explicit exemptions.
- `npm run contracts:check-events` validates AsyncAPI schema references plus `syntheticOnly: true` event payload schema constraints.
- `npm run contracts:runtime-evidence` generates `docs/test-evidence/generated/contract-runtime-evidence.json` and records the boundary between source inspection and missing runtime gates.

## Source-Inspected Runtime Markers

- `PaymentKafkaOutboxPublisher` writes Kafka envelope payload and headers with `syntheticOnly`, `sourceService`, `eventType`, `aggregateId`, and `occurredAt`.
- `ReportingKafkaOutboxPublisher` writes the same envelope payload/header fields for reporting domain events.
- `NotificationKafkaConsumer` rejects missing envelope metadata and non-synthetic events before delivery creation.
- `LedgerCommandService` persists durable synthetic outbox records, but core-banking runtime Kafka envelope validation remains part of the missing gate.

## Explicit Gaps

- `contracts:diff-openapi` is not wired. Do not claim generated Spring/Jackson/springdoc DTO-level OpenAPI diffing has passed.
- `contracts:validate-runtime-events` is not wired. Do not claim source inspection or structural AsyncAPI validation is equivalent to a runtime producer/consumer envelope validation integration test.
- Existing live producer/consumer integration tests are valuable, but this boundary evidence does not certify that every produced Kafka payload is validated against the checked-in AsyncAPI/event JSON Schemas at runtime.

## Commands

```bash
npm run contracts:lint
npm run contracts:check-client
npm run contracts:check-events
npm run contracts:runtime-evidence
```

Future completion should add the PLAN-required gates without downgrading failures to warnings:

```bash
npm run contracts:diff-openapi
npm run contracts:validate-runtime-events
```
