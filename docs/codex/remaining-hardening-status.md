# Remaining Hardening Status

Review date: 2026-06-06

Phase branches: `codex/remaining-hardening-phase-0`, `codex/remaining-hardening-phase-1-ci`, `codex/remaining-hardening-phase-2-security`, `codex/remaining-hardening-phase-5-ledger-projection`, `codex/remaining-hardening-phase-3-workflow-ui`, `codex/remaining-hardening-phase-4-contracts`, `codex/remaining-hardening-phase-6-ops-security`, `codex/remaining-hardening-phase-7-bounded-contexts`

Scope: Phase 0 baseline for `docs/codex/remaining-hardening-goals.md`. The Node runtime remains a legacy oracle/reference only. This status covers the current synthetic lab and does not add real money, real PII, real financial-network, real card-network, Open Banking, or real KYC/provider integration.

Working tree note: before Phase 0 edits, the workspace already had modified generated evidence under `docs/test-evidence/generated/` and untracked Codex planning documents under `docs/codex/`. Phase 0 documentation avoids treating those pre-existing generated changes as new implementation evidence.

Phase 1 update: full-service CI wiring is now implemented in `.github/workflows/ci.yml` for payment, notification, reporting, aggregate Gradle unit tests, platform validation, structural contracts validation, and Docker Compose profile rendering. `docs/test-evidence/ci-coverage-hardening.md` records the PR/manual boundary and fallback policy. Contract validation remains structural until Phase 4 adds dedicated OpenAPI/AsyncAPI drift scripts.

Phase 2 update: Spring Security and OAuth2 Resource Server dependencies/configuration are now present in all four Spring services. Signed JWKS tokens are verified through Spring/Nimbus decoders, route authorization remains in explicit policy layers, simulator tokens require dev/test double opt-in and fail fast in prod-like profiles, and selected high-risk core APIs now have method-level `@PreAuthorize` gates. Evidence is recorded in `docs/test-evidence/spring-security-resource-server-hardening.md`.

Phase 5 update: ledger projection drift/rebuild operations now have Flyway migration `V035`, `LedgerProjectionIntegrityService`, `/api/ops/ledger/projection-*` APIs, reason-required drift checks, approval-gated rebuild requests, idempotent rebuild execution, ops-console manifests `OPS-LEDGER-101` through `OPS-LEDGER-103`, shared API-client methods, targeted Playwright smoke wiring, and `LedgerProjection*` Testcontainers coverage. Evidence is recorded in `docs/test-evidence/ledger-projection-integrity-workflow.md`.

Phase 3 update: customer-web now has route-backed workflow pages for login, accounts, account detail, transfers, complaints, cards, loans, payments, notifications, and security; staff-terminal now has route-backed workflow pages for transaction code, customer, account, approvals, audit, and workflow timeline views. The route pages reuse shared route components, manifest metadata, structured-error/idempotency/FDS/approval state panels, and mark synthetic demo fallbacks explicitly. Evidence is recorded in `docs/test-evidence/phase-3-workflow-route-hardening.md`. PR #48 hosted GitHub Actions were attempted and rerun, but every job was blocked before runner startup by GitHub account billing/spending-limit restrictions; local targeted tests passed and the blocked hosted CI gate is recorded as external availability evidence.

Phase 4 update: OpenAPI/AsyncAPI contract validation is now implemented through `contracts/openapi/core-banking.yaml`, `contracts/openapi/reporting-service.yaml`, standardized structured-error contract metadata on payment/notification contracts, AsyncAPI envelope metadata, reporting event schemas, root `contracts:*` scripts, CI wiring, and `tests/contractHardening.test.mjs`. Evidence is recorded in `docs/test-evidence/contract-validation-hardening.md`. PR #49 hosted GitHub Actions were attempted, but every job was blocked before runner startup by GitHub account billing/spending-limit restrictions; local contract, Node, package, workflow, and service integration tests passed.

Phase 6 update: secrets hygiene, default-secret fail-fast controls, observability metric validation, five operational runbooks, and a controlled audit export workflow are now implemented. `V036__audit_export_jobs.sql` stores audit export jobs/files, export requests are auditor/compliance-only, reason-required, step-up protected, idempotent, maker-checker approved, synthetic-only, and verified not to mutate ledger rows. Evidence is recorded in `docs/test-evidence/phase-6-ops-security.md`; generated Docker-forced security evidence now reports npm audit, Semgrep, Trivy filesystem scan, SBOM, and ZAP baseline DAST passing against a disposable local synthetic target. PR #50 hosted GitHub Actions were attempted, but every job was blocked before runner startup by GitHub account billing/spending-limit restrictions; local targeted and structural tests passed.

Phase 7 update: payment-service and reporting-service Kafka publishers now include persisted event envelope metadata (`sourceService`, `eventType`, `aggregateId`, `occurredAt`, `syntheticOnly`) in record headers and JSON envelope headers, and their Redpanda integration tests assert that metadata. Notification-service now rejects Kafka events missing required envelope metadata before creating delivery side effects. Existing payment/notification/reporting Compose and Keycloak service-token smokes were rerun for this bounded slice. Evidence is recorded in `docs/test-evidence/phase-7-bounded-context-hardening.md`. PR #51 hosted GitHub Actions were attempted, but every job was blocked before runner startup by GitHub account billing/spending-limit restrictions; local bounded-context, contract, secrets, and structural tests passed.

## Already Implemented

- Target-stack repository shape exists: Gradle includes `core-banking`, `payment-service`, `notification-service`, and `reporting-service`; Next.js channel workspaces and shared packages exist; Docker Compose, Kubernetes, Helm, Argo CD, Keycloak, Temporal, Redpanda, and observability files are structurally present.
- Core banking ledger controls are implemented in Kotlin/Spring Boot with PostgreSQL/Flyway migrations through `V035`, including customers, accounts, postings, balance projections, idempotency, reversal, adjustment, closed-date controls, audit hash chain, maker-checker, workflow state, statement/certificate read models, EOD, product/fee/loan/card slices, operational-security lab controls, AML/FDS governance, complaint extensions, and ledger projection drift/rebuild workflow state.
- Existing Node `.mjs` tests remain oracle/reference tests and are guarded by retirement/boundary checks. The current baseline rerun passed `npm test` with 172 tests after Phase 4 contract structural coverage.
- Screen manifest infrastructure is broad and validated: 109 manifests across customer, staff, complaint, ops, audit, FDS/AML, and admin channels.
- Shared TypeScript packages exist for screen rendering, form validation, API client calls, and auth client behavior; package typechecks pass in the current baseline.
- Payment, notification, and reporting bounded contexts have Spring source, Flyway migrations, service-specific scripts, integration tests, OpenAPI/Event contract artifacts for several surfaces, Docker Compose profiles, Kubernetes/Helm resources, and evidence documents.
- Outbox, Redpanda, Temporal, Keycloak/JWKS, security evidence, formal ledger checks, backup/restore, synthetic load, and platform structural validation have existing implementation/evidence from earlier phases.
- Phase 1 CI full-service wiring now connects service-specific backend jobs, aggregate Gradle unit checks, platform structural validation, structural contract checks, and Compose profile config rendering to PR CI.
- Phase 2 Spring Security Resource Server standardization now protects Spring service API routes with stateless Security filter chains, issuer/audience-aware JWT decoding, Keycloak-style role extraction, simulator-token prod-like profile guards, structured auth failures, and selected high-risk method authorization.
- Phase 5 ledger projection drift/rebuild now detects projection drift from `ledger_postings`, stores drift run/item evidence, submits maker-checker rebuild requests through `operator_approvals`, rebuilds only `account_balance_projections`, records before/after source/projection hashes, and exposes ops-console/API-client smoke wiring.
- Phase 3 customer-web and staff-terminal route shells now split core workflow routes out of the single root panel path and render loading/success/replay/held/blocked/validation/auth/unexpected states from shared route components.
- Phase 4 contract gates now lint required OpenAPI files, compare `operationId` values with the shared TypeScript API client, and verify AsyncAPI event schema references plus synthetic-only payload guards.
- Phase 6 operations/security now includes `.env.example`, `docs/security/secrets-management.md`, `security:secrets-check`, production-like default-secret startup guard coverage, Micrometer metrics for ledger command latency/errors, idempotency replays, outbox backlog, authorization denials, and audit append failures, Prometheus/Grafana structural assets, SLO/observability docs, five runbooks, and a general audit export API.
- Phase 7 bounded-context verification now hardens payment/reporting producer envelopes and notification consumer envelope validation against the AsyncAPI backbone metadata.

## Partially Implemented

- Phase 2 authentication/authorization standardization: Resource Server authentication is now the canonical signed-JWT path, but legacy compatibility decoders remain for dev/test fallback and bounded-context authorization-denied audit expansion is not complete.
- Phase 5 ledger projection drift/rebuild: core drift/rebuild workflow is now implemented. Remaining follow-up is broader live-browser/API evidence against a long-running Compose stack and any future partition/archive-aware rebuild optimizations from Phase 8.
- Phase 3 customer/staff workflow UI hardening: route-separated workflow pages and state panels now exist. Remaining follow-up is deeper live API execution from the route pages themselves, since many real command executions still run through the existing API-backed panels and manifest renderer when service URLs are configured.
- Phase 4 contracts: core/payment/notification/reporting OpenAPI contracts and root lint/client/event gates now exist. Remaining follow-up is DTO-level generated contract diffing, springdoc-generated spec comparison, and implementation-level event envelope validation in service integration tests.
- Phase 6 operations/security: the requested secrets, observability, audit export, and runbook slice is implemented for local synthetic evidence. Remaining follow-up is live observability-stack alert evaluation, DAST with a supplied `BANKING_LAB_DAST_URL`, and any future external secret-store adapter design that remains synthetic-only.
- Phase 7 bounded-context hardening: payment/notification/reporting implementations and CI jobs are substantial. This phase adds implementation-level envelope metadata verification and reruns bounded-context Compose/Keycloak smokes; remaining follow-up is generated JSON Schema validation of live Kafka records and any future notification domain-event producer if that bounded context starts publishing events.
- Phase 8 large-ledger operational evidence: local synthetic load and backup/restore drills exist, but deterministic large-ledger dataset generation, ledger query benchmark scripts, generated benchmark JSON, and partition/archive readiness documentation are missing.

## Missing

- A complete method-level authorization annotation audit across every high-risk service method and bounded-context denial audit rows for every payment/notification/reporting auth failure.
- Live API execution evidence for the new customer-web and staff-terminal route pages beyond existing API-backed panels and manifest-renderer smokes.
- DTO-level generated OpenAPI diffing and implementation-level event envelope producer validation remain missing beyond the new root contract gates.
- Large-ledger generator, partition/archive readiness checker, large dataset smoke script, query benchmark script, and generated evidence files.

## Risks

- Historical evidence documents include many prior passing commands. This Phase 0 status records only commands actually rerun during this baseline.
- Gradle tests require unsandboxed execution on this workstation because Gradle file-lock sockets fail inside the sandbox with `java.net.SocketException: Operation not permitted`.
- Pre-existing generated evidence changes are still dirty and must not be accidentally committed as Phase 0 implementation evidence.
- Core-banking OpenAPI now covers broad shared-client and selected server-only operations, but it uses generic response payloads until a generated DTO/schema workflow is added.
- UI manifests and route pages now cover core customer/staff journey structure, but live API-backed route execution is still not equivalent to a fully workflow-complete banking channel experience.
- `npm run security:evidence` depends on network and Docker scanner availability. The first sandbox run failed on registry/Docker access and the first escalated run exposed a Semgrep fixture issue plus a Trivy DB download failure; both were resolved before the Docker-forced final rerun passed all checks, including ZAP DAST against a disposable local synthetic health endpoint.

## Phase Plan

- Phase 0: Baseline status and coverage-matrix note only. Do not duplicate existing features.
- Phase 1: Add CI jobs for all service tests, platform validation, contracts validation, and Compose profile config; document CI coverage.
- Phase 2: Standardize Spring Security OAuth2 Resource Server while preserving current RBAC/ABAC, step-up, trusted-device, session, simulator-token, structured-error, and denial-audit behavior.
- Phase 5: Add ledger projection drift detection and maker-checker rebuild workflow before broader UI/contract work. Completed for the Spring/ops-console/API-client slice; keep broader live evidence/scale proof for Phase 8.
- Phase 3: Harden customer-web and staff-terminal workflows around route-level jobs, selected API results, idempotency replay, held/blocked states, and structured error handling. Completed for route split and static/Playwright state coverage; keep live route execution evidence as a follow-up.
- Phase 4: Add contract lint/drift scripts and fill OpenAPI/AsyncAPI coverage gaps. Completed for root lint/client/event gates and core/reporting contract presence; keep generated DTO/schema diffing as follow-up.
- Phase 6: Add secrets hygiene, observability validation/runbooks, and audit export evidence. Completed for local synthetic evidence; keep live DAST and live alert routing for an environment-enabled follow-up.
- Phase 7: Tighten payment/notification/reporting bounded-context CI and contract verification without reimplementing completed service behavior. Completed for envelope metadata verification, bounded-context Gradle coverage, and existing Compose/Keycloak service-token smoke reruns.
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
| `scripts/run-core-banking-tests.sh :services:core-banking:compileKotlin :services:payment-service:compileKotlin :services:notification-service:compileKotlin :services:reporting-service:compileKotlin` | sandbox failed, escalated pass | Phase 2 compile check for all Spring services; sandbox failed on Gradle file-lock socket, approved rerun passed. |
| `scripts/run-core-banking-tests.sh :services:core-banking:test --tests '*SpringSecurityResourceServerTest'` | pass | Phase 2 unit coverage for JWT claim conversion and prod-like simulator profile guard. |
| `scripts/run-core-banking-tests.sh :services:core-banking:integrationTest --tests '*Security*' --tests '*Authorization*' --tests '*Jwks*'` | first run failed, rerun pass | First run exposed `JwtException` handling and raw simulator timestamp issues; rerun passed after decoder fixes. |
| `scripts/run-core-banking-tests.sh :services:payment-service:integrationTest --tests '*Authorization*' :services:notification-service:integrationTest --tests '*Authorization*' :services:reporting-service:integrationTest` | pass | Phase 2 bounded-context authorization coverage behind Spring Security Resource Server. |
| `npm run scripts:typecheck` | pass | Phase 2 script typecheck. |
| `npm test` | first run failed, rerun pass | First run exposed source-scaffold test drift after route policy extraction; rerun passed after updating the test. |
| `scripts/run-core-banking-tests.sh :services:core-banking:test :services:payment-service:test :services:notification-service:test :services:reporting-service:test` | first run failed, rerun pass | First run exposed MVC-slice default security on health metadata endpoints; rerun passed after test and runtime permit fixes. |
| `scripts/run-core-banking-tests.sh :services:core-banking:integrationTest` | first runs failed, final rerun pass | Exposed and fixed method-security disabled-mode behavior, servlet-only security config, structured method-denial handling, and AML reviewer workflow role alignment. |
| `scripts/run-core-banking-tests.sh :services:payment-service:integrationTest :services:notification-service:integrationTest :services:reporting-service:integrationTest` | pass | Full bounded-context integration tasks passed. |
| `scripts/run-core-banking-tests.sh :services:core-banking:compileKotlin` | sandbox failed, escalated pass | Phase 5 backend compile; sandbox failed on Gradle file-lock socket, approved rerun passed. |
| `scripts/run-core-banking-tests.sh :services:core-banking:compileIntegrationTestKotlin` | pass | Compiled `LedgerProjection*` integration tests after adding the projection integrity workflow. |
| `scripts/run-core-banking-tests.sh :services:core-banking:integrationTest --tests '*LedgerProjection*'` | first two runs failed, final rerun pass | First runs exposed incorrect test expectations for account-scoped posting count and drift-run count; final rerun passed after assertion fixes. |
| `npm run scripts:typecheck` | pass | Phase 5 TypeScript script/client typecheck after API-client projection methods. |
| `npm run next:ops-console:typecheck` | pass | Ops console TypeScript check after projection workflow panel wiring. |
| `npm run next:ops-console:build` | pass | Next.js ops-console production build after `OPS-LEDGER-*` manifest and API panel updates. |
| `npm run validate:manifests` | pass | Validated 109 manifests after adding `OPS-LEDGER-101`, `OPS-LEDGER-102`, and `OPS-LEDGER-103`. |
| `npm run packages:typecheck` | pass | Shared package typechecks passed after API-client projection DTOs/methods. |
| `npm test` | pass | 169 passed after adding structural coverage for the projection API client and manifests. |
| `scripts/run-core-banking-tests.sh :services:core-banking:test` | pass | Full core-banking unit task after Phase 5 changes. |
| `scripts/run-core-banking-tests.sh :services:core-banking:integrationTest` | pass | Full core-banking Testcontainers integration task passed after adding `V035`. |
| `npm run test:e2e -- --grep "projection"` | skipped | Playwright started local Next dev servers and skipped the live API projection workflow smoke because `BANKING_LAB_E2E_API_BASE_URL` was unset. |
| `npm run test:e2e -- apps/ops-console/e2e/ops-console-parity.spec.ts` | pass | Full ops-console Playwright file passed after stabilizing the manifest workflow-label assertion exposed by CI. |
| `npm run test:e2e` | pass | Full local Playwright manifest suite passed with 17 passed and 58 skipped; live API-backed tests remained gated by missing service URLs. |
| `npm run next:customer-web:typecheck` | pass | Phase 3 customer-web route workflow component and route pages typechecked. |
| `npm run next:staff-terminal:typecheck` | pass | Phase 3 staff-terminal route workflow component and route pages typechecked. |
| `npm run next:customer-web:build` | pass | Customer-web production build generated login/account/transfer/complaint/card/loan/payment/notification/security routes. |
| `npm run next:staff-terminal:build` | first run failed, rerun pass | First run exposed a server-only manifest loader imported into the client dashboard through route summaries; rerun passed after moving route summaries into a client-safe module. |
| `npm test` | pass | 170 structural/oracle tests passed after route workflow coverage updates. |
| `npm run test:e2e -- apps/customer-web/e2e/customer-web-parity.spec.ts apps/staff-terminal/e2e/staff-terminal-parity.spec.ts` | first run failed, rerun pass | First run exposed a strict duplicate text assertion after adding the customer route link; rerun passed with 9 passed and 29 skipped. |
| `npm run validate:manifests` | pass | Validated 109 manifests after route UI changes; manifests themselves were not changed in Phase 3. |
| `npm run test:e2e` | pass | Full local Playwright manifest suite passed with 19 passed and 58 skipped; live API-backed tests remained gated by missing service URLs. |
| `gh pr view 48 --json state,mergeStateStatus,statusCheckRollup,headRefName,baseRefName,url` | pass | Confirmed PR #48 was open and mergeable but unstable after hosted CI jobs failed before runner startup. |
| `gh api /repos/kdh949/banking-lab/check-runs/79869938417/annotations` | pass | Confirmed GitHub Actions annotation: hosted jobs were not started because account payments/spending limits blocked runner allocation. |
| `npm run contracts:lint` | pass | Phase 4 contract lint validated 4 OpenAPI files and the AsyncAPI backbone. |
| `npm run contracts:check-client` | pass | Phase 4 client drift gate validated 135 operation IDs against 128 shared API client methods/exemptions. |
| `npm run contracts:check-events` | pass | Phase 4 event gate validated 17 AsyncAPI schema references and synthetic-only payload guards. |
| `npm run scripts:typecheck` | pass | Typechecked the new TypeScript contract scripts. |
| `npm run packages:typecheck` | pass | Shared package typechecks passed after Phase 4 contract gate additions. |
| `npm test` | pass | 172 structural/oracle tests passed after adding contract hardening coverage. |
| `npm run ci:check-workflow` | pass | Confirmed `contracts-validation` runs the new `contracts:*` scripts. |
| `scripts/run-core-banking-tests.sh :services:core-banking:integrationTest :services:payment-service:integrationTest :services:notification-service:integrationTest :services:reporting-service:integrationTest` | sandbox failed, escalated up-to-date pass | Sandbox failed on Gradle file-lock socket; first escalated rerun completed with tasks up-to-date and was not counted as real test execution. |
| `scripts/run-core-banking-tests.sh :services:core-banking:integrationTest :services:payment-service:integrationTest :services:notification-service:integrationTest :services:reporting-service:integrationTest --rerun-tasks` | escalated pass | Phase 4 service integration verification passed with 17 Gradle tasks executed. |
| `gh pr view 49 --json state,mergeStateStatus,mergeable,statusCheckRollup,url,headRefName,baseRefName` | pass | Confirmed PR #49 is mergeable but unstable because hosted CI jobs failed before runner startup. |
| `gh api /repos/kdh949/banking-lab/check-runs/79871013246/annotations` | pass | Confirmed GitHub Actions annotation: hosted jobs were not started because account payments/spending limits blocked runner allocation. |
| `npm run security:secrets-check` | first run failed, rerun pass | Phase 6 scanner initially flagged broad placeholder-like strings; after tightening allowlists and fixture handling, the final rerun passed across 856 files. |
| `scripts/run-core-banking-tests.sh :services:core-banking:test --tests '*SpringSecurityResourceServerTest'` | sandbox failed, escalated pass | Phase 6 default-secret production-like profile guard coverage; sandbox failed on Gradle file-lock socket. |
| `npm run observability:validate` | pass | Validated observability docs/assets/runbooks and the required metric names. |
| `scripts/run-core-banking-tests.sh :services:core-banking:integrationTest --tests '*ObservabilityActuatorIntegrationTest'` | escalated pass | Verified actuator/prometheus exposure for observability metrics after Phase 6 metric wiring. |
| `npm run test:core-banking:integration -- --tests '*AuditExport*'` | first run failed, final rerun pass | First run exposed PostgreSQL `FOR UPDATE` on the nullable side of an outer join; final rerun passed after locking only the export job alias. |
| `npm run contracts:lint` | pass | Contract lint passed after adding audit export OpenAPI operations. |
| `npm run contracts:check-client` | pass | Shared API client covered the new audit export operation IDs. |
| `npm run scripts:typecheck` | pass | Script TypeScript checks passed after observability validator changes. |
| `npm run packages:typecheck` | pass | Shared package typechecks passed after audit export API-client DTOs/methods. |
| `npm run next:audit-console:typecheck` | pass | Audit console typecheck passed after adding the audit export smoke panel. |
| `npm run next:audit-console:build` | pass | Audit console production build passed after adding the audit export UI state block. |
| `npm run analytics:fds-aml:test` | sandbox failed, direct escalated `uv` pass | Sandbox failed on PyPI DNS for `hatchling`; the equivalent `uv --cache-dir .uv-cache run --directory analytics/aml-fds-python --project . --extra dev pytest` escalated rerun passed 10 Python tests after the Semgrep fixture fix. |
| `npm run security:evidence` | sandbox failed, first escalated run failed, escalated rerun pass with DAST skipped | Sandbox lacked npm registry/Docker access. First escalated run passed npm audit and SBOM but failed Semgrep and Trivy DB download. Later rerun passed npm audit, Semgrep, Trivy, and SBOM with DAST skipped because `BANKING_LAB_DAST_URL` was unset. |
| `BANKING_LAB_DAST_URL=http://host.docker.internal:18132/health npm run security:evidence:docker` | escalated pass | Started disposable synthetic Postgres/core-banking DAST containers, confirmed `curl -fsS http://127.0.0.1:18132/health`, then Docker-forced npm audit, Semgrep, Trivy, SBOM, and ZAP baseline all passed. Temporary containers and network were removed afterward. |
| `npm test` | pass | Final Phase 6 structural/oracle suite passed with 172 tests after restoring Docker-forced live DAST generated evidence. |
| `gh pr view 50 --json mergeStateStatus,statusCheckRollup,url` | pass | Confirmed PR #50 is mergeable but unstable because hosted CI jobs failed before runner startup. |
| `gh api /repos/kdh949/banking-lab/check-runs/79873771310/annotations` | pass | Confirmed GitHub Actions annotation: hosted jobs were not started because account payments/spending limits blocked runner allocation. |
| `scripts/run-core-banking-tests.sh :services:payment-service:integrationTest --tests '*PaymentKafkaOutboxPublisherIntegrationTest' :services:notification-service:integrationTest --tests '*NotificationKafkaConsumerIntegrationTest' :services:reporting-service:integrationTest --tests '*ReportingKafkaOutboxPublisherIntegrationTest'` | escalated pass | Phase 7 targeted Redpanda/Testcontainers envelope metadata verification for payment, notification, and reporting bounded contexts. |
| `scripts/run-core-banking-tests.sh :services:payment-service:test :services:payment-service:integrationTest :services:notification-service:test :services:notification-service:integrationTest :services:reporting-service:test :services:reporting-service:integrationTest` | escalated pass | Phase 7 full bounded-context Gradle unit/integration tasks passed. |
| `npm run contracts:lint` | pass | Phase 7 contract lint rerun after envelope metadata hardening. |
| `npm run contracts:check-client` | pass | Phase 7 shared API-client contract drift gate rerun. |
| `npm run contracts:check-events` | pass | Phase 7 AsyncAPI schema-reference and synthetic-only event guard rerun. |
| `npm run test:payment-service:outbox-worker-compose` | escalated pass | Disposable synthetic Compose stack proved payment outbox worker settlement path. |
| `npm run test:payment-service:domain-publisher-compose` | escalated pass | Disposable synthetic Compose stack proved payment domain-event publisher path. |
| `npm run test:payment-service:keycloak-service-token` | escalated pass | Disposable synthetic Keycloak/core/payment stack accepted a `PAYMENT_SERVICE` service token. |
| `npm run test:notification-service:provider-dead-letter-compose` | escalated pass | Disposable synthetic Compose stack proved notification provider retry/dead-letter behavior. |
| `npm run test:notification-service:keycloak-service-token` | escalated pass | Disposable synthetic Keycloak/notification stack accepted a `NOTIFICATION_SERVICE` service token. |
| `npm run test:reporting-service:domain-publisher-compose` | escalated pass | Disposable synthetic Compose stack proved reporting domain-event publisher path. |
| `npm run test:reporting-service:keycloak-service-token` | escalated pass | Disposable synthetic Keycloak/reporting stack accepted a `REPORTING_ANALYST` service token. |
| `npm run security:secrets-check` | pass | Phase 7 secret placeholder scan passed for 857 files. |
| `npm test` | pass | Phase 7 final Node oracle/structural suite passed with 172 tests. |
| `git diff --check -- . ':!docs/test-evidence/generated/*'` | pass | Phase 7 whitespace validation passed while excluding pre-existing generated evidence changes. |
| `gh pr view 51 --json state,mergeStateStatus,mergeable,statusCheckRollup,url,headRefName,baseRefName` | pass | Confirmed PR #51 was mergeable but unstable because hosted CI jobs failed before runner startup. |
| `gh api /repos/kdh949/banking-lab/check-runs/79874770856/annotations` | pass | Confirmed GitHub Actions annotation: hosted jobs were not started because account payments/spending limits blocked runner allocation. |

## Commands Not Attempted

- `docker compose --profile platform config`: reserved for Phase 1 CI/platform work.
- `npm run platform:validate`: reserved for Phase 1 CI/platform work.
- Full unfiltered all-service Gradle unit and integration tasks were rerun during Phase 2 as listed above.
- Full `npm run next:*:typecheck`, full `npm run next:*:build`, and live API-backed Playwright execution: not required for Phase 3 customer/staff route hardening; targeted customer/staff typecheck/build and full local Playwright passed, with live API-backed route tests still gated by missing service URLs.
- PR #48 hosted CI jobs were attempted and rerun but did not execute because GitHub account billing/spending limits blocked runner allocation before any job steps started.
- PR #49 hosted CI jobs were attempted but did not execute because GitHub account billing/spending limits blocked runner allocation before any job steps started.
- PR #50 hosted CI jobs were attempted but did not execute because GitHub account billing/spending limits blocked runner allocation before any job steps started.
- PR #51 hosted CI jobs were attempted but did not execute because GitHub account billing/spending limits blocked runner allocation before any job steps started.
- Live Prometheus/Grafana/Loki/Tempo alert-routing evidence was not rerun in Phase 6; this phase added structural assets and validator coverage.
- `npm run ledger:large-dataset-smoke` and `npm run ledger:query-benchmark`: not attempted because these scripts are not defined yet.
- Live Docker/Kubernetes/DAST/load/backup drills from the final command list were not rerun in Phase 0 because this phase is an inventory baseline, not an evidence refresh.
