# Phase 4 Contract Validation Hardening Evidence

Date: 2026-06-06

Scope: Phase 4 OpenAPI/AsyncAPI contract validation for the synthetic banking lab. This work does not add real money, real PII, real payment/card networks, Open Banking, real KYC providers, or external financial institution APIs.

## Implemented

- Added `contracts/openapi/core-banking.yaml` for the shared-client-facing core banking, customer, staff, ledger projection, parameter, card, loan, complaint, audit, FDS, AML, and admin API surface.
- Added `contracts/openapi/reporting-service.yaml` for reporting catalog, artifact, export, and retention APIs.
- Standardized OpenAPI metadata across core-banking, payment-service, notification-service, and reporting-service with `x-banking-lab-contract`, structured-error defaults, and `StructuredErrorResponse` components.
- Added root contract gates:
  - `npm run contracts:lint`
  - `npm run contracts:check-client`
  - `npm run contracts:check-events`
- Wired the three contract gates into the existing `contracts-validation` CI job.
- Added AsyncAPI event-envelope metadata requiring `syntheticOnly`, `sourceService`, `eventType`, `aggregateId`, and `occurredAt`.
- Added reporting event schemas for `ReportArtifactGenerated`, `ReportArtifactExported`, and `ReportRetentionSweepCompleted`.
- Added `tests/contractHardening.test.mjs` so `npm test` also verifies the Phase 4 contract gates and key contract anchors.

## Validation Rules

- `contracts:lint` checks required OpenAPI files, operation IDs, structured-error defaults, idempotency-policy markers, reason-required markers, and AsyncAPI envelope metadata.
- `contracts:check-client` compares OpenAPI `operationId` values with `packages/api-client/src/index.ts`; server-only operations must explicitly set `x-api-client-required: false`.
- `contracts:check-events` checks that every JSON event schema is referenced by AsyncAPI and that event payload schemas require `syntheticOnly: true`.

## Commands Run

| Command | Result | Notes |
| --- | --- | --- |
| `npm run contracts:lint` | pass | Validated 4 OpenAPI files and 1 AsyncAPI backbone contract. |
| `npm run contracts:check-client` | pass | Validated 135 OpenAPI operation IDs against 128 shared API client methods/exemptions. |
| `npm run contracts:check-events` | pass | Validated 17 AsyncAPI event schema references and synthetic-only payload guards. |
| `npm run scripts:typecheck` | pass | Typechecked the new TypeScript contract scripts. |
| `npm run packages:typecheck` | pass | Shared package typechecks passed after contract/API-client gate additions. |
| `npm test` | pass | 172 structural/oracle tests passed, including `tests/contractHardening.test.mjs`. |
| `npm run ci:check-workflow` | pass | Confirmed the `contracts-validation` CI job runs the new `contracts:*` gates. |
| `scripts/run-core-banking-tests.sh :services:core-banking:integrationTest :services:payment-service:integrationTest :services:notification-service:integrationTest :services:reporting-service:integrationTest` | sandbox failed, escalated up-to-date pass | Sandbox failed before tests on Gradle file-lock socket; first escalated rerun completed with tasks up-to-date, so it was not counted as a real test execution. |
| `scripts/run-core-banking-tests.sh :services:core-banking:integrationTest :services:payment-service:integrationTest :services:notification-service:integrationTest :services:reporting-service:integrationTest --rerun-tasks` | escalated pass | Real integration rerun passed with 17 tasks executed. |
| `gh pr view 49 --json state,mergeStateStatus,mergeable,statusCheckRollup,url,headRefName,baseRefName` | pass | PR #49 is mergeable but unstable because hosted GitHub Actions jobs failed before runner startup. |
| `gh api /repos/kdh949/banking-lab/check-runs/79871013246/annotations` | pass | GitHub Actions reported that hosted jobs were not started because account payments/spending limits blocked runner allocation. |

## Not Run Yet

- PR #49 hosted CI jobs did not execute because GitHub account billing/spending limits blocked runner allocation before any job steps started. This is recorded as an external CI availability gate, not a passed or failed test step.

## Residual Risk

- Core-banking OpenAPI uses generic response schemas for broad operation coverage in this phase; Kotlin controller source-to-OpenAPI path/method diffing is wired through `contracts:diff-openapi`, while springdoc/Jackson DTO schema generation remains a future improvement.
- AsyncAPI models a shared outbox/envelope contract plus payload schemas. Some services still need implementation-level producer validation against the envelope fields in later bounded-context hardening.

## 2026-06-10 Runtime Boundary Evidence

`docs/test-evidence/contract-runtime-evidence-boundary.md` and
`docs/test-evidence/generated/contract-runtime-evidence.json` record the
current boundary without upgrading it to a runtime pass. They confirm the
structural contract gates and selected source envelope markers are present, and
they record `contracts:diff-openapi` as a passing Kotlin controller source diff
gate while keeping `contracts:validate-runtime-events` as a PLAN-required
missing gate.

## 2026-06-10 OpenAPI Source Diff Gate

- Added `npm run contracts:diff-openapi`, which compares Kotlin
  `@RestController` `/api/**` and checked-in `/health` routes against the
  checked-in OpenAPI files for core-banking, payment-service,
  notification-service, and reporting-service.
- The gate writes deterministic synthetic-only snapshots under
  `docs/test-evidence/generated/openapi/`.
- The gate fails on runtime path/methods missing from OpenAPI, OpenAPI
  path/methods with no controller route, structured error metadata gaps, and
  request/response DTO name mismatches where checked-in OpenAPI declares a DTO
  schema reference.
- This is not a full springdoc/Jackson schema export. Existing broad
  `AnyJsonResponse` coverage remains a documented residual risk until DTO
  schema generation is added.

Current local validation:

| Command | Result | Notes |
| --- | --- | --- |
| `npm run contracts:diff-openapi` | pass | Matched 142 core-banking, 14 payment-service, 16 notification-service, and 6 reporting-service controller operations to checked-in OpenAPI. |
| `npm run contracts:lint` | pass | Revalidated 4 OpenAPI files and 1 AsyncAPI file after adding the source diff gate. |
| `npm run contracts:check-client` | pass | Validated 178 OpenAPI operation IDs against 146 shared API client methods/exemptions. |
| `npm run contracts:check-events` | pass | Revalidated 17 AsyncAPI event schema references. |
| `npm run contracts:runtime-evidence` | pass | Regenerated `docs/test-evidence/generated/contract-runtime-evidence.json` with the OpenAPI source diff boundary. |
