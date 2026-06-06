# Remaining Hardening Status

Review date: 2026-06-06

Branch: `codex/remaining-hardening-phase-0`

Scope: Phase 0 baseline for `docs/codex/remaining-hardening-goals.md`. The Node runtime remains a legacy oracle/reference only. This status covers the current synthetic lab and does not add real money, real PII, real financial-network, real card-network, Open Banking, or real KYC/provider integration.

Working tree note: before Phase 0 edits, the workspace already had modified generated evidence under `docs/test-evidence/generated/` and untracked Codex planning documents under `docs/codex/`. Phase 0 documentation avoids treating those pre-existing generated changes as new implementation evidence.

## Already Implemented

- Target-stack repository shape exists: Gradle includes `core-banking`, `payment-service`, `notification-service`, and `reporting-service`; Next.js channel workspaces and shared packages exist; Docker Compose, Kubernetes, Helm, Argo CD, Keycloak, Temporal, Redpanda, and observability files are structurally present.
- Core banking ledger controls are implemented in Kotlin/Spring Boot with PostgreSQL/Flyway migrations through `V034`, including customers, accounts, postings, balance projections, idempotency, reversal, adjustment, closed-date controls, audit hash chain, maker-checker, workflow state, statement/certificate read models, EOD, product/fee/loan/card slices, operational-security lab controls, AML/FDS governance, and complaint extensions.
- Existing Node `.mjs` tests remain oracle/reference tests and are guarded by retirement/boundary checks. The current baseline rerun passed `npm test` with 167 tests.
- Screen manifest infrastructure is broad and validated: 106 manifests across customer, staff, complaint, ops, audit, FDS/AML, and admin channels.
- Shared TypeScript packages exist for screen rendering, form validation, API client calls, and auth client behavior; package typechecks pass in the current baseline.
- Payment, notification, and reporting bounded contexts have Spring source, Flyway migrations, service-specific scripts, integration tests, OpenAPI/Event contract artifacts for several surfaces, Docker Compose profiles, Kubernetes/Helm resources, and evidence documents.
- Outbox, Redpanda, Temporal, Keycloak/JWKS, security evidence, formal ledger checks, backup/restore, synthetic load, and platform structural validation have existing implementation/evidence from earlier phases.

## Partially Implemented

- Phase 1 CI full-service verification: root scripts already exist for payment, notification, reporting, platform validation, and Compose checks, but `.github/workflows/ci.yml` currently runs only core-banking for backend verification and does not explicitly run payment, notification, reporting, backend-all-gradle, platform, contracts, or profile Compose validation jobs.
- Phase 2 authentication/authorization standardization: secure defaults, signed JWKS validation, simulator-token opt-in, trusted-device/session/step-up policy, and authorization tests exist, but services still rely on custom authorization filters and custom token decoders. Spring Security OAuth2 Resource Server dependencies/configuration and method-level authorization are not yet the canonical runtime path.
- Phase 5 ledger projection drift/rebuild: balance projections and integrity checks exist, and backup/restore evidence checks projection equality, but dedicated drift run tables, rebuild request/run tables, maker-checker rebuild APIs, ops manifests, and `LedgerProjection*` integration tests are missing.
- Phase 3 customer/staff workflow UI hardening: API-backed panels and manifests exist, but customer-web and staff-terminal still rely heavily on single-page panel flows. Route-separated real workflow pages, token lifecycle UX, and full state panels for loading/success/replay/held/validation/auth/unexpected failures need deeper implementation.
- Phase 4 contracts: `contracts/openapi/payment-service.yaml`, `contracts/openapi/notification-service.yaml`, `contracts/asyncapi/banking-lab-events.yaml`, and event schemas exist, but `core-banking` and `reporting-service` OpenAPI contracts are missing and root `contracts:lint`, `contracts:check-client`, and `contracts:check-events` scripts are not present.
- Phase 6 operations/security: observability assets and a synthetic operational-security lab exist, but `security:secrets-check`, `observability:validate`, docs under `docs/operations/`, a general `POST /api/audit/exports` / `GET /api/audit/exports/{exportId}` API, and the requested five operational runbooks are not present.
- Phase 7 bounded-context hardening: payment/notification/reporting implementations are substantial, but their CI and contract gates are not first-class PR jobs yet. Remaining goals should focus on missing verification links rather than duplicating already implemented service features.
- Phase 8 large-ledger operational evidence: local synthetic load and backup/restore drills exist, but deterministic large-ledger dataset generation, ledger query benchmark scripts, generated benchmark JSON, and partition/archive readiness documentation are missing.

## Missing

- CI jobs or matrix entries for `backend-payment-service`, `backend-notification-service`, `backend-reporting-service`, `backend-all-gradle`, `platform-validation`, `contracts-validation`, and `compose-platform-config`.
- `docs/test-evidence/ci-coverage-hardening.md`.
- Spring Security Resource Server configuration classes such as `SecurityConfig`, JWT authentication converter/policy adapter, resource server dependencies, and Spring Security tests for the standard filter chain.
- Dedicated ledger projection drift/rebuild Flyway migrations, services, controllers, ops-console manifests, API-client methods, tests, and evidence.
- Customer-web route split for login/accounts/account detail/transfers/complaints/cards/loans/payments/notifications/security and staff-terminal route/workflow hardening beyond API-backed panels.
- Contract lint/check scripts and complete OpenAPI/AsyncAPI coverage for core-banking, reporting-service, and the shared client/event drift gates.
- Secrets placeholder check script, `security:secrets-check`, production-like default-secret fail-fast guard evidence, operations SLO/observability docs, audit export job API, and requested runbooks.
- Large-ledger generator, partition/archive readiness checker, large dataset smoke script, query benchmark script, and generated evidence files.

## Risks

- Historical evidence documents include many prior passing commands. This Phase 0 status records only commands actually rerun during this baseline.
- Gradle tests require unsandboxed execution on this workstation because Gradle file-lock sockets fail inside the sandbox with `java.net.SocketException: Operation not permitted`.
- Pre-existing generated evidence changes are still dirty and must not be accidentally committed as Phase 0 implementation evidence.
- Existing service-specific OpenAPI/Event artifacts can create a false sense of complete contract governance; root drift-prevention scripts are still absent.
- UI manifests are broad, but route-level user journeys are still not equivalent to a workflow-complete banking channel experience.

## Phase Plan

- Phase 0: Baseline status and coverage-matrix note only. Do not duplicate existing features.
- Phase 1: Add CI jobs for all service tests, platform validation, contracts validation, and Compose profile config; document CI coverage.
- Phase 2: Standardize Spring Security OAuth2 Resource Server while preserving current RBAC/ABAC, step-up, trusted-device, session, simulator-token, structured-error, and denial-audit behavior.
- Phase 5: Add ledger projection drift detection and maker-checker rebuild workflow before broader UI/contract work.
- Phase 3: Harden customer-web and staff-terminal workflows around route-level jobs, selected API results, idempotency replay, held/blocked states, and structured error handling.
- Phase 4: Add contract lint/drift scripts and fill OpenAPI/AsyncAPI coverage gaps.
- Phase 6: Add secrets hygiene, observability validation/runbooks, and audit export evidence.
- Phase 7: Tighten payment/notification/reporting bounded-context CI and contract verification without reimplementing completed service behavior.
- Phase 8: Add deterministic large-ledger operational evidence, benchmark outputs, and partition/archive readiness docs.

## Commands Attempted

| Command | Result | Notes |
| --- | --- | --- |
| `git status --short --branch` | pass | Confirmed pre-existing dirty generated evidence and untracked Codex planning docs before Phase 0 edits. |
| `npm ci` | pass | Installed 43 packages from the lockfile. |
| `npm test` | pass | 167 passed, 0 failed. Confirms Node oracle/reference, retirement, manifest, scaffold, governance, and evidence guards still pass. |
| `npm run validate:manifests` | pass | Validated 106 screen manifests. |
| `npm run packages:typecheck` | pass | `screen-engine`, `form-engine`, `api-client`, and `auth-client` typechecks passed. |
| `npm run scripts:typecheck` | pass | Script TypeScript project compiled with `--noEmit`. |
| `docker compose config` | pass | Default Compose config rendered; default profile expands the legacy/reference web service only. |
| `npm run test:core-banking:unit` | sandbox failed, escalated pass | Sandbox failed on Gradle file-lock socket; approved rerun passed `:services:core-banking:test`. |
| `npm run test:core-banking:integration` | sandbox failed, escalated pass | Sandbox failed on Gradle file-lock socket; approved rerun passed `:services:core-banking:integrationTest`. |

## Commands Not Attempted

- `docker compose --profile platform config`: reserved for Phase 1 CI/platform work.
- `npm run platform:validate`: reserved for Phase 1 CI/platform work.
- `npm run test:payment-service:unit` and `npm run test:payment-service:integration`: reserved for Phase 1 service-CI wiring.
- `npm run test:notification-service:unit` and `npm run test:notification-service:integration`: reserved for Phase 1 service-CI wiring.
- `npm run test:reporting-service:unit` and `npm run test:reporting-service:integration`: reserved for Phase 1 service-CI wiring.
- `npm run next:*:typecheck`, `npm run next:*:build`, and `npm run test:e2e`: not required for Phase 0 documentation baseline; will be run in the UI hardening phase or final verification as scope requires.
- `npm run contracts:lint`, `npm run contracts:check-client`, and `npm run contracts:check-events`: not attempted because these scripts are not defined yet.
- `npm run security:secrets-check`: not attempted because the script is not defined yet.
- `npm run observability:validate`: not attempted because the script is not defined yet.
- `npm run ledger:large-dataset-smoke` and `npm run ledger:query-benchmark`: not attempted because these scripts are not defined yet.
- Live Docker/Kubernetes/DAST/load/backup drills from the final command list were not rerun in Phase 0 because this phase is an inventory baseline, not an evidence refresh.
