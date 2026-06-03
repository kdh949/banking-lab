# Migration Log

## 2026-06-02: Migration Foundation Gates

Source plans:

- gstack engineering review plan `20260602-114300`
- gstack engineering review test plan `20260602-114300`
- gstack DX review plan `20260602-115809`

Changes started:

- Node reference runtime retained as executable parity baseline.
- `npm run parity` defined as the reference parity command.
- Structured API error response contract added before Kotlin/Spring controller porting.
- Node retirement gate added and left blocked until Kotlin/Next parity evidence exists.

Current migration status:

- Backend target scaffold: in progress. Gradle/Kotlin files and `/health` source are present, but local Java/Gradle execution is not available in this environment.
- Next.js target scaffold: in progress for `customer-web`; the static shell remains for Node reference parity.
- Node reference parity map: in progress.
- Node retirement: blocked by design.

Evidence:

- `docs/migration/kotlin-next-playbook.md`
- `docs/migration/parity-scenarios.json`
- `docs/migration/node-retirement-gate.json`
- `docs/migration/structured-api-error-contract.md`
- `docs/test-evidence/migration-foundation.md`
- `docs/architecture/kotlin-spring-foundation.md`
- `docs/architecture/next-customer-web-foundation.md`

## 2026-06-02: Phase 0-1A Kotlin Ledger Slice

Changes completed:

- Added Gradle wrapper pinned to Gradle 8.14.3 and a separate `integrationTest` source set.
- Moved the PostgreSQL schema to canonical Flyway `db/migrations/V...` files.
- Added Kotlin/Spring `LedgerCommandService` and APIs for deposit, withdrawal, transfer, reversal, adjustment, and daily closing.
- Added durable idempotency rows, locked balance projections, closed-day rejection, and PENDING outbox rows.
- Added Docker Compose `platform` profile for Redis, Redpanda, Keycloak, Temporal, Prometheus, Grafana, Loki, and Tempo.

Verification:

- Node oracle remains green with `npm test`.
- Kotlin unit tests pass through Docker/JDK.
- PostgreSQL/Flyway/Testcontainers integration tests pass through Docker/JDK with Docker Desktop Testcontainers overrides.
- Node retirement remains blocked until the remaining parity suites and Next.js screens are proven.

## 2026-06-02: Parallel Subagent Integration Slice

Changes completed:

- Added Kotlin workflow state-machine parity for maker-checker, complaint answer approval, FDS release/block, AML closure, and reconciliation adjustment handoff.
- Added Flyway V007 workflow case lifecycle tables for complaint cases, timelines, comments, and reconciliation adjustment requests.
- Hardened Spring ledger reversal API errors to use the documented `LEDGER_REVERSAL_POLICY_VIOLATION` family and added MockMvc/Testcontainers parity coverage.
- Added reusable manifest expansion contracts for inquiry, command, case, and parameter templates plus three parameter screen manifests.
- Added Next.js manifest-rendered shells for staff terminal, complaint portal, ops console, audit console, and FDS/AML console.
- Added repo-scoped Codex skills, subagent profile examples, QA evidence reports, and a parity coverage matrix.

Verification:

- `npm run validate:manifests` validates 26 screen manifests.
- `npm test` and `npm run parity` remain the Node reference gates.
- All six Next channel apps typecheck and build through workspace scripts.
- Kotlin unit and integration tests pass through Docker/JDK; host `./gradlew` remains unavailable because no host Java runtime is installed.
- Node retirement remains blocked until API, workflow durability, Kafka/Temporal, Keycloak, Playwright, security, and observability gates are proven.

## 2026-06-02: Node Retirement MVP Evidence Slice

Changes completed:

- Added maker-checker approval enforcement for Spring reconciliation adjustment postings through `approvalId`.
- Added PostgreSQL audit event evidence for approved adjustment execution.
- Added Spring HTTP integration coverage for every required structured error family.
- Added durable PostgreSQL workflow repository tests for restart-safe workflow state and event history.
- Added durable outbox/inbox state-transition tests for publish, retry, dead-letter, and duplicate consumer replay.
- Added Playwright manifest shell parity for customer, staff, complaint, ops, audit, and FDS/AML Next channels.

Verification:

- Docker/JDK `:services:core-banking:test :services:core-banking:integrationTest --rerun-tasks --no-daemon` passed with a separate project cache.
- Live Spring Boot smoke against isolated PostgreSQL applied Flyway through v007 and returned `/health` with `status=ok` and `syntheticOnly=true`.
- Live structured error smoke returned `LEDGER_RESOURCE_NOT_FOUND` with request ID propagation and `syntheticOnly=true`.
- `npm run validate:manifests` passed for 26 manifests.
- All six Next workspace typecheck commands passed.
- `npm run test:e2e` passed 12 Playwright tests across six manifest shells.

## 2026-06-02: Spring Authorization Simulator Slice

Changes completed:

- Added a Spring HTTP authorization filter for Keycloak/OIDC-shaped simulator Bearer tokens.
- Added route role gates for staff, ops, approval, ledger, and customer API paths.
- Added service-level actor binding so request actors cannot spoof `requestedBy` or `approvedBy` when a token is present.
- Added a customer account detail API with customer ownership enforcement.
- Added denial audit events for token and route-policy failures.

Verification:

- `SecurityAuthorizationIntegrationTest` passes for missing/invalid tokens, route role denial, customer ownership denial, actor-spoof denial, and manager approval execution.

Remaining blockers:

- Node retirement remains blocked.
- Full target parity for all 42 Node scenarios is not complete.
- At this point in the migration log, signed JWT/JWKS validation was not yet proven; the later signed JWT/JWKS authorization slice closes that specific gap.
- At this point in the migration log, real Kafka/Redpanda publish/consume was not yet proven; the following Redpanda outbox delivery slice closes that specific gap.
- Semgrep, Trivy, SBOM, DAST, and observability smoke outputs are still missing.

## 2026-06-02: Redpanda Outbox Delivery Slice

Changes completed:

- Added Kafka client dependencies and Redpanda Testcontainers coverage to the Spring core-banking build.
- Added `KafkaOutboxPublisher` to publish only already-persisted `outbox_events` rows and mark them `PUBLISHED` after broker acknowledgment.
- Added `KafkaInboxConsumer` to consume Redpanda messages and record idempotent `inbox_events` rows.
- Added locked publishable outbox row selection with `FOR UPDATE SKIP LOCKED`.
- Added Redpanda integration coverage for publish-after-durable-insert, duplicate replay idempotency, broker failure retry, and DLQ transition.

Verification:

- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.eventing.RedpandaOutboxDeliveryIntegrationTest` passed.

Remaining blockers:

- Node retirement remains blocked.
- Full target parity for all 42 Node scenarios is not complete.
- At this point in the migration log, signed JWT/JWKS validation was not yet proven; the later signed JWT/JWKS authorization slice closes that specific gap.
- At this point in the migration log, Temporal test-environment evidence was still missing; the later Temporal test environment slice closes that specific gap.
- Semgrep, Trivy, SBOM, DAST, and observability smoke outputs are still missing.

## 2026-06-02: Signed JWT/JWKS Authorization Slice

Changes completed:

- Added a signed RS256 JWT decoder that reads RSA public keys from a JWKS URI and validates signature, issuer, audience, expiry, not-before, roles, and synthetic customer ownership claims.
- Added a composite token decoder so signed JWT/JWKS validation runs before the legacy OIDC-shaped simulator token decoder.
- Added a `banking-lab.security.simulator-tokens-enabled` switch so JWKS tests can prove simulator fallback is disabled.
- Moved customer ownership mismatch rejection into the authorization filter so denial audit events are emitted before domain execution.
- Added JWKS authorization integration coverage for valid signed staff/customer tokens, disabled simulator fallback, invalid signature, expired token, customer ownership denial, and denial audit events.

Verification:

- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.security.JwksAuthorizationIntegrationTest` passed.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.security.SecurityAuthorizationIntegrationTest` passed.

Remaining blockers:

- Node retirement remains blocked.
- Full target parity for all 42 Node scenarios is not complete.
- At this point in the migration log, live Keycloak realm import and MFA-required-action evidence were still missing; the later live Keycloak realm smoke slice closes that specific gap, and the later customer-web Keycloak slice closes only masked account-detail login propagation while WebAuthn and broader channel login remain pending.
- At this point in the migration log, Temporal test-environment evidence was still missing; the later Temporal test environment slice closes that specific gap.
- Semgrep, Trivy, SBOM, DAST, and observability smoke outputs are still missing.

## 2026-06-02: Security Evidence Wrapper Slice

Changes completed:

- Added `npm run security:evidence` through `scripts/run-security-evidence.ts`.
- Added repeatable SCA/SAST/container/SBOM/DAST evidence output paths under `docs/test-evidence/generated`.
- Added DAST profile documentation for local synthetic targets.
- Updated the SBOM profile to point at the wrapper while preserving the direct Trivy command.

Verification:

- `npm run scripts:typecheck` passed.
- `npm run security:evidence` passed under approved network execution.
- Generated evidence recorded `npm audit --audit-level=high` as passed with 0 vulnerabilities.
- Generated evidence recorded Semgrep, Trivy, SBOM, and DAST as skipped because the required tools or live DAST target were unavailable.

Remaining blockers:

- Node retirement remains blocked.
- Full target parity for all 42 Node scenarios is not complete.
- At this point in the migration log, live Keycloak realm import and MFA-required-action evidence were still missing; the later live Keycloak realm smoke slice closes that specific gap, and the later customer-web Keycloak slice closes only masked account-detail login propagation while WebAuthn and broader channel login remain pending.
- At this point in the migration log, Temporal test-environment evidence was still missing; the later Temporal test environment slice closes that specific gap.
- Semgrep, Trivy/Syft SBOM, DAST with a live local target, and observability smoke outputs are still missing.

## 2026-06-02: Temporal Test Environment Slice

Changes completed:

- Added Temporal Java SDK and test-environment dependencies.
- Added `BankingCaseTemporalWorkflow` as a Temporal workflow contract for complaint answer, FDS release/block, AML closure, reconciliation adjustment, account hold, and account release.
- Added approval and rejection signals plus queryable workflow status/checkpoints.
- Added maker-checker self-approval rejection inside the Temporal workflow path.
- Added Temporal test-environment integration coverage for worker registration, workflow start, query, signal, approval completion, rejection without effect, and self-approval failure.

Verification:

- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.temporal.BankingCaseTemporalWorkflowIntegrationTest` passed.

Remaining blockers:

- Node retirement remains blocked.
- Full target parity for all 42 Node scenarios is not complete.
- At this point in the migration log, live Keycloak realm import and MFA-required-action evidence were still missing; the later live Keycloak realm smoke slice closes that specific gap, and the later customer-web Keycloak slice closes only masked account-detail login propagation while WebAuthn and broader channel login remain pending.
- Live Temporal server/worker operations, domain-table workflow ID persistence, and worker observability are not proven.
- Semgrep, Trivy/Syft SBOM, DAST with a live local target, and observability smoke outputs are still missing.

## 2026-06-02: Live Temporal Worker Slice

Changes completed:

- Added `temporal_workflow_id` and `temporal_run_id` columns to complaint, FDS, AML, reconciliation adjustment, and account hold tables through Flyway V010.
- Added `TemporalWorkflowReferenceService` to attach workflow references to the relevant domain table and return `RESOURCE_NOT_FOUND` for missing business references.
- Added a property-gated Spring Boot `BankingCaseTemporalWorker` that connects to a live Temporal server and registers `BankingCaseTemporalWorkflowImpl`.
- Added a Docker Compose `core-banking-temporal-worker` service under the `platform` profile.
- Made Postgres and Temporal host ports overrideable to avoid local stack collisions.
- Added Temporal dynamic config mounting and included Flyway migrations in the Spring Boot container image.
- Added a live Temporal smoke integration test that is skipped by default and runs only when `BANKING_LAB_LIVE_TEMPORAL_TARGET` is set.

Verification:

- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.temporal.TemporalWorkflowReferencePersistenceIntegrationTest --tests lab.banking.core.temporal.BankingCaseTemporalWorkflowIntegrationTest` passed.
- `env BANKING_LAB_POSTGRES_PORT=15432 BANKING_LAB_TEMPORAL_PORT=17233 docker compose --profile platform config` passed.
- `env BANKING_LAB_POSTGRES_PORT=15432 BANKING_LAB_TEMPORAL_PORT=17233 docker compose --profile platform up -d postgres temporal` passed after the Temporal DB driver and dynamic config mount were corrected.
- `env BANKING_LAB_POSTGRES_PORT=15432 BANKING_LAB_TEMPORAL_PORT=17233 docker compose --profile platform up -d --build core-banking-temporal-worker` passed.
- `env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17233 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest` passed.

Remaining blockers:

- Node retirement remains blocked.
- Full target parity for all 42 Node scenarios is not complete.
- At this point in the migration log, live Keycloak realm import and MFA-required-action evidence were still missing; the later live Keycloak realm smoke slice closes that specific gap, and the later customer-web Keycloak slice closes only masked account-detail login propagation while WebAuthn and broader channel login remain pending.
- Worker metrics/tracing, Temporal failure/retry drills, and workflow-observability evidence are still missing.
- Semgrep, Trivy/Syft SBOM, DAST with a live local target, and observability smoke outputs are still missing.

## 2026-06-03: Live Keycloak Realm Smoke Slice

Changes completed:

- Expanded `infra/keycloak/realm-banking-lab.json` with synthetic staff, manager, customer, and MFA-required manager users.
- Added channel client protocol mappers for `core-banking-api` audience and synthetic customer ownership claims.
- Added a TOTP required-action policy and an MFA-required synthetic manager account.
- Made the Keycloak host port overrideable in Docker Compose.
- Added `LiveKeycloakRealmIntegrationTest`, skipped by default unless `BANKING_LAB_LIVE_KEYCLOAK_BASE_URL` is set.

Verification:

- `env BANKING_LAB_KEYCLOAK_PORT=18085 docker compose --profile platform config` passed.
- `env BANKING_LAB_KEYCLOAK_PORT=18085 docker compose --profile platform up -d --force-recreate keycloak` passed.
- `curl -sS -i http://127.0.0.1:18085/realms/banking-lab/.well-known/openid-configuration` returned 200 OK under approved loopback execution.
- The MFA-required synthetic manager direct grant returned `400 invalid_grant` with `Account is not fully set up`.
- `env BANKING_LAB_LIVE_KEYCLOAK_BASE_URL=http://127.0.0.1:18085 scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.security.LiveKeycloakRealmIntegrationTest` passed.

Remaining blockers:

- Node retirement remains blocked.
- Full target parity for all 42 Node scenarios is not complete.
- WebAuthn and interactive Next.js channel login propagation were not proven by this live realm slice; later customer-web Keycloak slices cover masked account detail and transfer retry only.
- Full API-backed channel E2E coverage beyond staff/customer smoke is still pending.
- Semgrep, Trivy/Syft SBOM, DAST with a live local target, and observability smoke outputs are still missing.

## 2026-06-03: API-backed Channel Smoke Slice

Changes completed:

- Added `@banking-lab/api-client` and `@banking-lab/auth-client` TypeScript workspace packages.
- Added API-backed panels to customer, staff, complaint, ops, audit, and FDS/AML channels without adding one-off App Router business screens.
- Added a property-gated Spring `SyntheticDataSeeder` for local synthetic API smoke data across customer/account, complaint, FDS, AML, reconciliation, and audit tables.
- Added a Spring customer transfer API at `POST /api/customer/transfers` that enforces customer ownership before delegating to the ledger service.
- Added a read-only Spring audit events API for audit-console hash-chain review.
- Added a customer-web browser transfer retry smoke that calls the Spring customer transfer API twice with one idempotency key and verifies the replayed `TX-...`.
- Added a customer-web browser insufficient-balance failure smoke that renders the real Spring route's structured `LEDGER_INSUFFICIENT_AVAILABLE_BALANCE` response.
- Added customer transaction history and customer transfer status APIs at `GET /api/customer/transactions` and `GET /api/customer/transfers`.
- Added a customer-web browser history/status smoke that creates a Spring customer transfer, reads the posted transaction from the customer history API, and reads held FDS transfer status from the customer transfer status API.
- Added PostgreSQL `customer_transfer_results` as a durable customer transfer command-result read model for `POSTED`, `HELD`, `FAILED`, and `BLOCKED` outcomes without unsafe postings for held or failed commands.
- Added customer-web browser held/failed status smoke that verifies a high-amount transfer becomes `HELD`, a negative-amount command becomes durable `FAILED`, and both statuses are returned from the Spring customer transfer status API.
- Updated FDS release/block execution so linked customer transfer results move to `POSTED` or `BLOCKED` after maker-checker approval.
- Added a customer complaint entry API at `POST /api/customer/complaints` with customer ownership enforcement, `RECEIVED` complaint case persistence, timeline insertion, audit event append, and bounded SERIALIZABLE retry.
- Added a customer-web browser complaint entry smoke that creates a Spring-backed `CMP-...` case from the customer channel.
- Added Spring integration coverage proving customer transaction history and staff transaction search read the same ledger transaction/posting source and expose the same transaction ID.
- Added a PostgreSQL `audit_hash_chain_lock` row and shared `AuditEventAppender` so concurrent Spring API browser smoke paths append audit events in a stable hash-chain order.
- Added a staff-terminal browser command smoke that requests a customer information change for `SYN-CUS-CMD-001`, proves maker self-approval rejection, approves with a separate manager actor, and verifies the masked updated phone.
- Added a complaint portal browser command smoke that drafts an answer for `CMP-SYN-CMD-001` and approves the generated maker-checker approval through the Spring staff approval API.
- Added a complaint portal browser failure-state smoke that attempts an answer draft for already answered `CMP-SYN-FAIL-001` and renders the real Spring route's structured `WORKFLOW_STATE_VIOLATION` response.
- Added an FDS/AML console browser failure-state smoke that attempts a release request for already released `FDS-SYN-FAIL-001` and renders the real Spring route's structured `WORKFLOW_STATE_VIOLATION` response.
- Added an FDS/AML console browser failure-state smoke that attempts a closure request for already closed `AML-SYN-FAIL-001` and renders the real Spring route's structured `WORKFLOW_STATE_VIOLATION` response.
- Added an FDS/AML console browser command smoke that requests release for `FDS-SYN-CMD-001`, approves the generated maker-checker approval, and verifies a posted ledger transaction.
- Added an FDS/AML console browser command smoke that requests block for `FDS-SYN-BLOCK-CMD-001`, approves the generated maker-checker approval, and verifies no ledger posting is created.
- Added an FDS/AML console browser command smoke that requests AML closure for `AML-SYN-CMD-001`, approves the generated maker-checker approval, and verifies `CLOSED` plus `STR_SIMULATED`.
- Added an ops-console browser command smoke that requests a reconciliation adjustment for `REC-SYN-CMD-001`, approves the generated maker-checker approval, and verifies `ADJUSTED` plus a posted ledger transaction.
- Added an ops-console browser failure-state smoke that attempts an adjustment request for already adjusted `REC-SYN-FAIL-001` and renders the real Spring route's structured `WORKFLOW_STATE_VIOLATION` response.
- Added bounded retry for transient PostgreSQL SERIALIZABLE failures during staff approval execution, FDS decision requests, and AML closure requests so parallel browser command approvals do not leak transient `40001` conflicts as HTTP 500s.
- Added bounded retry for transient PostgreSQL SERIALIZABLE failures during reconciliation adjustment requests so parallel browser command approvals do not leak transient `40001` conflicts as HTTP 500s.
- Added bounded retry for transient PostgreSQL SERIALIZABLE failures during staff masked customer detail so audited inquiry smoke does not leak transient audit hash-chain conflicts as HTTP 500s.
- Added bounded retry for transient PostgreSQL SERIALIZABLE failures during customer transfer commands so parallel browser transfer smoke paths do not leak transient `40001` conflicts as HTTP 500s.
- Added Spring CORS configuration for local Next.js channel origins and allowed security preflight without disabling RBAC/ABAC for real API calls.
- Made the core-banking compose host port configurable.
- Added Playwright coverage that runs API-backed checks only when `BANKING_LAB_E2E_API_BASE_URL` is set.

Verification:

- `npm run packages:typecheck` passed.
- `npm run next:staff-terminal:typecheck` passed.
- `npm run next:customer-web:typecheck` passed.
- `npm run next:staff-terminal:build` passed.
- `npm run next:customer-web:build` passed.
- `npm run next:complaint-portal:typecheck`, `npm run next:ops-console:typecheck`, `npm run next:audit-console:typecheck`, and `npm run next:fds-aml-console:typecheck` passed.
- `npm run next:complaint-portal:build`, `npm run next:ops-console:build`, `npm run next:audit-console:build`, and `npm run next:fds-aml-console:build` passed.
- `npm run test:e2e` passed with 12 tests and 6 API-backed tests skipped because no API URL was configured.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test :services:core-banking:integrationTest` passed after CORS/security changes.
- `env COMPOSE_PROJECT_NAME=banking-lab-api-backed-smoke BANKING_LAB_POSTGRES_PORT=15433 BANKING_LAB_CORE_BANKING_PORT=18082 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres core-banking` passed.
- `curl -fsS http://127.0.0.1:18082/health` returned `status=ok` and `syntheticOnly=true`.
- `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18082 npm run test:e2e` passed all 18 Playwright tests, including Spring-backed account, staff customer, complaint, reconciliation, audit, FDS, and AML read-model checks.
- `env COMPOSE_PROJECT_NAME=banking-lab-api-backed-smoke docker compose --profile platform down -v` cleaned up the isolated smoke stack and volume.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test :services:core-banking:integrationTest` passed after the audit append lock and complaint command smoke changes.
- `env COMPOSE_PROJECT_NAME=banking-lab-api-backed-command-smoke BANKING_LAB_POSTGRES_PORT=15444 BANKING_LAB_CORE_BANKING_PORT=18084 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres core-banking` passed with a fresh seed DB.
- `curl -fsS http://127.0.0.1:18084/health` returned `auditHashChainValid=true` before and after the command smoke.
- `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18084 npm run test:e2e` passed all 19 Playwright tests, including complaint answer draft plus approval execution from the browser.
- `env COMPOSE_PROJECT_NAME=banking-lab-api-backed-command-smoke docker compose --profile platform down -v` cleaned up the isolated command-smoke stack and volume.
- `npm run next:fds-aml-console:typecheck` and `npm run next:fds-aml-console:build` passed after adding the FDS release command panel.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test :services:core-banking:integrationTest` passed after adding FDS command seed data and approval execution retry.
- `env COMPOSE_PROJECT_NAME=banking-lab-fds-command-smoke BANKING_LAB_POSTGRES_PORT=15445 BANKING_LAB_CORE_BANKING_PORT=18086 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres core-banking` passed with a fresh seed DB.
- `curl -fsS http://127.0.0.1:18086/health` returned `auditHashChainValid=true` before and after the FDS release command smoke.
- `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18086 npm run test:e2e` passed all 20 Playwright tests, including complaint answer approval and FDS release approval from the browser.
- `env COMPOSE_PROJECT_NAME=banking-lab-fds-command-smoke docker compose --profile platform down -v` cleaned up the isolated FDS command-smoke stack and volume.
- `npm run packages:typecheck`, `npm run next:fds-aml-console:typecheck`, and `npm run next:fds-aml-console:build` passed after adding the AML closure command panel.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test :services:core-banking:integrationTest` passed after adding AML closure command seed data and retry hardening for FDS/AML command concurrency.
- `env COMPOSE_PROJECT_NAME=banking-lab-aml-command-smoke BANKING_LAB_POSTGRES_PORT=15446 BANKING_LAB_CORE_BANKING_PORT=18087 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres core-banking` passed with a fresh seed DB.
- `curl -fsS http://127.0.0.1:18087/health` returned `auditHashChainValid=true` before and after the FDS/AML command smoke.
- `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18087 npm run test:e2e` passed all 21 Playwright tests, including complaint answer approval, FDS release approval, and AML closure approval from the browser.
- `env COMPOSE_PROJECT_NAME=banking-lab-aml-command-smoke docker compose --profile platform down -v` cleaned up the isolated AML command-smoke stack and volume.
- `npm run packages:typecheck`, `npm run next:fds-aml-console:typecheck`, and `npm run next:fds-aml-console:build` passed after adding the FDS block command panel.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test :services:core-banking:integrationTest` passed after adding FDS block command seed data and staff audited inquiry retry.
- `env COMPOSE_PROJECT_NAME=banking-lab-fds-block-command-smoke BANKING_LAB_POSTGRES_PORT=15447 BANKING_LAB_CORE_BANKING_PORT=18088 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres core-banking` passed with a fresh seed DB.
- `curl -fsS http://127.0.0.1:18088/health` returned `auditHashChainValid=true` before and after the FDS block command smoke.
- `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18088 npm run test:e2e` passed all 22 Playwright tests, including complaint answer approval, FDS release approval, FDS block approval, and AML closure approval from the browser.
- `env COMPOSE_PROJECT_NAME=banking-lab-fds-block-command-smoke docker compose --profile platform down -v` cleaned up the isolated FDS block command-smoke stack and volume.
- `npm run packages:typecheck`, `npm run next:ops-console:typecheck`, and `npm run next:ops-console:build` passed after adding the reconciliation adjustment command panel.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test :services:core-banking:integrationTest` passed after adding reconciliation command seed data and retry hardening for reconciliation adjustment request concurrency.
- `scripts/run-core-banking-tests.sh :services:core-banking:bootJar` passed after the same Spring changes.
- `env COMPOSE_PROJECT_NAME=banking-lab-reconciliation-command-smoke BANKING_LAB_POSTGRES_PORT=15448 BANKING_LAB_CORE_BANKING_PORT=18089 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres core-banking` passed with a fresh seed DB.
- `curl -fsS http://127.0.0.1:18089/health` returned `auditHashChainValid=true` before and after the reconciliation adjustment command smoke.
- `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18089 npm run test:e2e` passed all 23 Playwright tests, including complaint answer approval, FDS release approval, FDS block approval, AML closure approval, and reconciliation adjustment approval from the browser.
- `env COMPOSE_PROJECT_NAME=banking-lab-reconciliation-command-smoke docker compose --profile platform down -v` cleaned up the isolated reconciliation command-smoke stack and volume.
- `npm run packages:typecheck`, `npm run next:staff-terminal:typecheck`, and `npm run next:staff-terminal:build` passed after adding the customer change command panel.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test :services:core-banking:integrationTest` passed after adding the command-only customer seed data and shared-client customer change request type.
- `scripts/run-core-banking-tests.sh :services:core-banking:bootJar` passed after the same Spring seed change.
- `env COMPOSE_PROJECT_NAME=banking-lab-staff-change-command-smoke BANKING_LAB_POSTGRES_PORT=15449 BANKING_LAB_CORE_BANKING_PORT=18090 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres core-banking` passed with a fresh seed DB.
- `curl -fsS http://127.0.0.1:18090/health` returned `auditHashChainValid=true` before and after the staff customer change command smoke.
- The first `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18090 npm run test:e2e` failed because the self-approval probe used a `BRANCH_STAFF` route token and was denied before the maker-checker policy layer; the probe was corrected to use the maker actor with checker role so `MAKER_CHECKER_SELF_APPROVAL_REJECTED` is asserted at the service policy layer.
- The corrected `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18090 npm run test:e2e` passed all 24 Playwright tests, including customer change approval, complaint answer approval, FDS release approval, FDS block approval, AML closure approval, and reconciliation adjustment approval from the browser.
- `env COMPOSE_PROJECT_NAME=banking-lab-staff-change-command-smoke docker compose --profile platform down -v` cleaned up the isolated staff change command-smoke stack and volume.
- `npm run next:complaint-portal:typecheck` and `npm run next:complaint-portal:build` passed after adding the workflow failure-state panel.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test :services:core-banking:integrationTest` passed after adding the real complaint answered-state structured error assertion and command-only failure case seed.
- `scripts/run-core-banking-tests.sh :services:core-banking:bootJar` passed after the same Spring seed change.
- `env COMPOSE_PROJECT_NAME=banking-lab-complaint-failure-smoke BANKING_LAB_POSTGRES_PORT=15450 BANKING_LAB_CORE_BANKING_PORT=18091 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres core-banking` passed with a fresh seed DB.
- `curl -fsS http://127.0.0.1:18091/health` returned `auditHashChainValid=true` before and after the complaint workflow failure-state smoke.
- `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18091 npm run test:e2e` passed all 25 Playwright tests, including customer change approval, complaint answer approval, complaint workflow `WORKFLOW_STATE_VIOLATION`, FDS release approval, FDS block approval, AML closure approval, and reconciliation adjustment approval from the browser.
- `env COMPOSE_PROJECT_NAME=banking-lab-complaint-failure-smoke docker compose --profile platform down -v` cleaned up the isolated complaint failure-smoke stack and volume.
- `npm run next:fds-aml-console:typecheck` and `npm run next:fds-aml-console:build` passed after adding the FDS workflow failure-state panel.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test :services:core-banking:integrationTest` passed after adding the real FDS released-state structured error assertion and command-only failure case seed.
- `scripts/run-core-banking-tests.sh :services:core-banking:bootJar` passed after the same Spring seed change.
- `env COMPOSE_PROJECT_NAME=banking-lab-fds-failure-smoke BANKING_LAB_POSTGRES_PORT=15451 BANKING_LAB_CORE_BANKING_PORT=18092 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres core-banking` passed with a fresh seed DB.
- `curl -fsS http://127.0.0.1:18092/health` returned `auditHashChainValid=true` before and after the FDS workflow failure-state smoke.
- `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18092 npm run test:e2e` passed all 26 Playwright tests, including customer change approval, complaint answer approval, complaint workflow `WORKFLOW_STATE_VIOLATION`, FDS workflow `WORKFLOW_STATE_VIOLATION`, FDS release approval, FDS block approval, AML closure approval, and reconciliation adjustment approval from the browser.
- `env COMPOSE_PROJECT_NAME=banking-lab-fds-failure-smoke docker compose --profile platform down -v` cleaned up the isolated FDS failure-smoke stack and volume.
- `npm run next:fds-aml-console:typecheck` and `npm run next:fds-aml-console:build` passed after adding the AML workflow failure-state panel.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test :services:core-banking:integrationTest` passed after adding the real AML closed-state structured error assertion and command-only failure case seed.
- `scripts/run-core-banking-tests.sh :services:core-banking:bootJar` passed after the same Spring seed change.
- `env COMPOSE_PROJECT_NAME=banking-lab-aml-failure-smoke BANKING_LAB_POSTGRES_PORT=15452 BANKING_LAB_CORE_BANKING_PORT=18093 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres core-banking` passed with a fresh seed DB.
- `curl -fsS http://127.0.0.1:18093/health` returned `auditHashChainValid=true` before and after the AML workflow failure-state smoke.
- `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18093 npm run test:e2e` passed all 27 Playwright tests, including customer change approval, complaint answer approval, complaint workflow `WORKFLOW_STATE_VIOLATION`, FDS workflow `WORKFLOW_STATE_VIOLATION`, AML workflow `WORKFLOW_STATE_VIOLATION`, FDS release approval, FDS block approval, AML closure approval, and reconciliation adjustment approval from the browser.
- `env COMPOSE_PROJECT_NAME=banking-lab-aml-failure-smoke docker compose --profile platform down -v` cleaned up the isolated AML failure-smoke stack and volume.
- `npm run next:ops-console:typecheck` and `npm run next:ops-console:build` passed after adding the reconciliation workflow failure-state panel.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.reconciliation.ReconciliationOpsApiParityIntegrationTest` passed after adding the real reconciliation adjusted-state structured error assertion and command-only failure item seed.
- `scripts/run-core-banking-tests.sh :services:core-banking:bootJar` passed after the same Spring seed change.
- `env BANKING_LAB_POSTGRES_PORT=15453 BANKING_LAB_CORE_BANKING_PORT=18094 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose -p banking-lab-rec-failure-smoke --profile platform up -d --build postgres core-banking` passed with a fresh seed DB.
- `curl -fsS http://127.0.0.1:18094/health` returned `auditHashChainValid=true` before and after the reconciliation workflow failure-state smoke.
- The first `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18094 npm run test:e2e` was blocked by sandbox `listen EPERM` while starting a Next dev server on port 3001; the same command passed under the approved execution path.
- `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18094 npm run test:e2e` passed all 28 Playwright tests, including customer change approval, complaint answer approval, complaint workflow `WORKFLOW_STATE_VIOLATION`, FDS workflow `WORKFLOW_STATE_VIOLATION`, AML workflow `WORKFLOW_STATE_VIOLATION`, reconciliation workflow `WORKFLOW_STATE_VIOLATION`, FDS release approval, FDS block approval, AML closure approval, and reconciliation adjustment approval from the browser.
- `env BANKING_LAB_POSTGRES_PORT=15453 BANKING_LAB_CORE_BANKING_PORT=18094 docker compose -p banking-lab-rec-failure-smoke --profile platform down -v` cleaned up the isolated reconciliation failure-smoke stack and volume.
- `npm run packages:typecheck`, `npm run next:customer-web:typecheck`, and `npm run next:customer-web:build` passed after adding the customer transfer retry/failure panels and shared client method.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.customer.CustomerTransferApiParityIntegrationTest` passed after adding the customer transfer API, idempotent retry assertion, insufficient-balance structured error assertion, and source-account ownership rejection.
- `scripts/run-core-banking-tests.sh :services:core-banking:bootJar` passed after the same Spring API change.
- `env BANKING_LAB_POSTGRES_PORT=15454 BANKING_LAB_CORE_BANKING_PORT=18095 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose -p banking-lab-customer-transfer-smoke --profile platform up -d --build postgres core-banking` passed with a fresh seed DB.
- `curl -fsS http://127.0.0.1:18095/health` returned `auditHashChainValid=true` before and after the customer transfer retry/failure smoke.
- `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18095 npm run test:e2e` passed all 30 Playwright tests, including customer transfer retry replay, customer transfer `LEDGER_INSUFFICIENT_AVAILABLE_BALANCE`, customer change approval, complaint answer approval, complaint workflow `WORKFLOW_STATE_VIOLATION`, FDS workflow `WORKFLOW_STATE_VIOLATION`, AML workflow `WORKFLOW_STATE_VIOLATION`, reconciliation workflow `WORKFLOW_STATE_VIOLATION`, FDS release approval, FDS block approval, AML closure approval, and reconciliation adjustment approval from the browser.
- `env BANKING_LAB_POSTGRES_PORT=15454 BANKING_LAB_CORE_BANKING_PORT=18095 docker compose -p banking-lab-customer-transfer-smoke --profile platform down -v` cleaned up the isolated customer transfer-smoke stack and volume.
- `npm run packages:typecheck`, `npm run next:customer-web:typecheck`, and `npm run next:customer-web:build` passed after adding the customer transaction history/held-status shared-client methods and panel.
- `npm run test:e2e` passed with 12 manifest-shell tests and 19 API-backed tests skipped when no API URL was configured.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.customer.CustomerTransferApiParityIntegrationTest` passed after adding the customer history/status APIs, ledger-source comparison with staff history, held FDS status assertion, and customer transfer SERIALIZABLE retry wrapper.
- `scripts/run-core-banking-tests.sh :services:core-banking:bootJar` passed after the same Spring API change.
- `env BANKING_LAB_POSTGRES_PORT=15455 BANKING_LAB_CORE_BANKING_PORT=18096 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose -p banking-lab-customer-history-smoke --profile migration up -d --build postgres core-banking` passed with a fresh seed DB.
- `curl -fsS http://127.0.0.1:18096/health` returned `status=ok`, `syntheticOnly=true`, and `auditHashChainValid=true`.
- The first `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18096 npm run test:e2e` run passed 29 tests but failed two customer transfer browser smokes with HTTP 500 because parallel customer transfer paths exposed transient PostgreSQL SERIALIZABLE conflicts; the retry wrapper fixed this.
- The corrected `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18096 npm run test:e2e` passed all 31 Playwright tests, including customer transaction history and held FDS status from the browser.
- `env BANKING_LAB_POSTGRES_PORT=15455 BANKING_LAB_CORE_BANKING_PORT=18096 docker compose -p banking-lab-customer-history-smoke --profile migration down -v` cleaned up the isolated customer history-smoke stack and volume.
- `npm run packages:typecheck`, `npm run next:customer-web:typecheck`, and `npm run next:customer-web:build` passed after adding durable customer held/failed status parity to the shared client and browser panel.
- `npm run test:e2e` passed with 12 manifest-shell tests and 20 API-backed tests skipped when no API URL was configured.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.customer.CustomerTransferApiParityIntegrationTest` passed after adding `customer_transfer_results`, held/failed no-posting assertions, and FDS result status updates.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar` passed after the same Spring API change.
- `env BANKING_LAB_POSTGRES_PORT=15456 BANKING_LAB_CORE_BANKING_PORT=18097 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile migration -p banking-lab-customer-failed-status-smoke up --build -d postgres core-banking` passed with a fresh seed DB.
- `curl -fsS http://127.0.0.1:18097/actuator/health` returned `UP`.
- `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18097 npm run test:e2e` passed all 32 Playwright tests, including customer held/failed transfer statuses from the browser.
- `env BANKING_LAB_POSTGRES_PORT=15456 BANKING_LAB_CORE_BANKING_PORT=18097 docker compose --profile migration -p banking-lab-customer-failed-status-smoke down -v` cleaned up the isolated customer failed-status smoke stack and volume.
- `npm run packages:typecheck`, `npm run next:customer-web:typecheck`, and `npm run next:customer-web:build` passed after adding customer complaint entry to the shared client and browser panel.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.complaint.CustomerComplaintEntryApiParityIntegrationTest` passed after adding customer complaint entry validation, ownership, timeline, and audit assertions.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar` passed after adding bounded retry for customer complaint entry.
- `env BANKING_LAB_POSTGRES_PORT=15457 BANKING_LAB_CORE_BANKING_PORT=18098 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile migration -p banking-lab-customer-complaint-smoke up --build -d postgres core-banking` passed with a fresh seed DB.
- `curl -fsS http://127.0.0.1:18098/actuator/health` returned `UP`.
- The first `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18098 npm run test:e2e` run exposed a customer complaint entry HTTP 500 caused by parallel audit hash-chain SERIALIZABLE conflict; the retry wrapper fixed this.
- The corrected `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18098 npm run test:e2e` passed all 33 Playwright tests, including customer complaint entry from the browser.
- `env BANKING_LAB_POSTGRES_PORT=15457 BANKING_LAB_CORE_BANKING_PORT=18098 docker compose --profile migration -p banking-lab-customer-complaint-smoke down -v` cleaned up the isolated customer complaint-smoke stack and volume.
- `npm run test:e2e` passed with 12 manifest-shell tests and 21 API-backed tests skipped when no API URL was configured.

Remaining blockers:

- Node retirement remains blocked.
- Full target parity for all 42 Node scenarios is not complete.
- At this point in the log, this channel smoke used simulator Bearer tokens, not interactive Keycloak browser login; later Keycloak slices close the customer-web API-backed login gap.
- WebAuthn/MFA browser flows are not implemented.
- Customer transfer retry/failure, customer transaction history/held FDS status, customer held/failed status parity, customer complaint entry, customer change approval, complaint answer approval, FDS release approval, FDS block approval, AML closure approval, reconciliation adjustment approval, and complaint/FDS/AML/reconciliation workflow failure-states have browser evidence, but Temporal orchestration and broader workflow/failure/retry transitions from browser channels are still pending.
- Semgrep, Trivy/Syft SBOM, DAST with a live local target, observability smoke, and worker failure drills are still missing.

## 2026-06-03: Customer Complaint Confirmation Parity Slice

Changes completed:

- Added `POST /api/customer/complaints/{caseId}/confirm` in Spring with customer ownership enforcement, `ANSWERED -> CLOSED` state validation, `customer_confirmed_at` persistence, `CLOSED` timeline insertion, customer `COMMAND_EXECUTED` audit append, and bounded SERIALIZABLE retry.
- Extended `ComplaintCaseDto` with customer confirmation timestamp and customer-safe timeline fields so the customer channel can render closure evidence from the Spring read model.
- Added `CustomerComplaintConfirmApiParityIntegrationTest` covering successful closure, invalid-state `WORKFLOW_STATE_VIOLATION`, and customer ownership `AUTHORIZATION_POLICY_VIOLATION`.
- Added shared TypeScript client support plus customer-web browser smoke for confirming seeded `CMP-SYN-CONFIRM-001`.
- Added deterministic synthetic seed data for the answered complaint confirmation smoke.

Verification:

- `npm run packages:typecheck` passed.
- `npm run next:customer-web:typecheck` passed.
- `npm run next:customer-web:build` passed.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.complaint.CustomerComplaintConfirmApiParityIntegrationTest` passed.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test :services:core-banking:integrationTest --tests 'lab.banking.core.complaint.*'` passed.
- `scripts/run-core-banking-tests.sh :services:core-banking:bootJar` passed.
- `npm run test:e2e` passed with 12 manifest-shell tests and 22 API-backed tests skipped when no API URL was configured.
- `env COMPOSE_PROJECT_NAME=banking-lab-complaint-confirm-smoke BANKING_LAB_POSTGRES_PORT=15458 BANKING_LAB_CORE_BANKING_PORT=18099 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres core-banking` passed with a fresh seed DB.
- `curl -fsS http://127.0.0.1:18099/health` returned `status=ok`, `syntheticOnly=true`, and `auditHashChainValid=true` before and after the browser smoke.
- `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18099 npm run test:e2e` passed all 34 Playwright tests, including customer complaint confirmation from the browser.
- `env COMPOSE_PROJECT_NAME=banking-lab-complaint-confirm-smoke docker compose --profile platform down -v` cleaned up the isolated complaint confirmation smoke stack and volume.

Remaining blockers:

- Node retirement remains blocked.
- At this point in the log, the complaint confirmation path still used simulator Bearer tokens in browser smoke, and the then-current interactive Keycloak propagation covered customer-web masked account detail; later Keycloak slices close the current customer-web API-backed login gap.
- WebAuthn/MFA browser flows, observability smoke, worker metrics/failure drills, and non-skipped Semgrep/Trivy/SBOM/DAST evidence remain incomplete.

## 2026-06-03: Customer Web Keycloak Browser Login Propagation Slice

Changes completed:

- Added PKCE verifier/challenge and authorization URL helpers to `@banking-lab/auth-client`.
- Added `POST /api/auth/keycloak-token` in customer-web as a Next BFF token exchange route for the public `customer-web` Keycloak client.
- Added a customer-web browser login smoke that redirects to live Keycloak, exchanges an authorization code, and calls Spring customer account detail with the returned Bearer token.
- Updated the Keycloak realm import to allow the Playwright customer-web port `http://localhost:3001/*`.
- Updated Docker Compose so `core-banking` receives `BANKING_LAB_SECURITY_JWKS_URI`, `BANKING_LAB_SECURITY_ISSUER`, and `BANKING_LAB_SECURITY_AUDIENCE` when simulator tokens are disabled.

Verification:

- `npm run packages:typecheck` passed.
- `npm run next:customer-web:typecheck` passed.
- `npm run scripts:typecheck` passed.
- `npm run next:customer-web:build` passed.
- `npm run test:e2e` passed with 12 manifest-shell tests and 23 API-backed tests skipped when no API or Keycloak URL was configured.
- `env COMPOSE_PROJECT_NAME=banking-lab-keycloak-browser-smoke BANKING_LAB_POSTGRES_PORT=15459 BANKING_LAB_CORE_BANKING_PORT=18101 BANKING_LAB_KEYCLOAK_PORT=18100 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://127.0.0.1:18100/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking` passed with a fresh seed DB.
- `curl -fsS http://127.0.0.1:18100/realms/banking-lab/.well-known/openid-configuration` passed.
- `curl -fsS http://127.0.0.1:18101/health` returned `auditHashChainValid=true` before and after the browser smoke.
- `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18101 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18100 npx playwright test apps/customer-web/e2e/customer-web-parity.spec.ts -g "interactive Keycloak"` passed 1 Playwright test after the Compose JWKS/issuer/audience env pass-through fix.
- `env COMPOSE_PROJECT_NAME=banking-lab-keycloak-browser-smoke docker compose --profile platform down -v` cleaned up the isolated Keycloak browser-smoke stack and volume.

Remaining blockers:

- Node retirement remains blocked.
- At this point in the log, customer-web Keycloak login propagation covered masked account detail; later transfer, complaint, and status propagation slices close the current customer-web API-backed login gap.
- Staff-terminal, complaint-portal, ops-console, audit-console, and FDS/AML-console browser login/role propagation remain pending.
- WebAuthn/MFA browser flows, observability smoke, worker metrics/failure drills, and non-skipped Semgrep/Trivy/SBOM/DAST evidence remain incomplete.

## 2026-06-03: Customer Web Keycloak Transfer Command Propagation Slice

Changes completed:

- Extended the customer-web Keycloak login panel so the access token returned by the Next BFF token exchange route is retained only in browser memory state.
- Added an explicit `Run Keycloak transfer smoke` action that uses the same Keycloak-issued Bearer token to call `POST /api/customer/transfers` twice with one idempotency key.
- Extended the live Keycloak Playwright smoke to assert `Keycloak transfer replayed`, `CWB-OIDC-TRF-...`, `TX-...`, `POSTED`, and `same transaction id` while Spring simulator tokens are disabled.

Verification:

- `npm run packages:typecheck` passed.
- `npm run next:customer-web:typecheck` passed.
- `npm run next:customer-web:build` passed.
- `npm run test:e2e` passed with 12 manifest-shell tests and 23 API-backed tests skipped when no API or Keycloak URL was configured.
- `env COMPOSE_PROJECT_NAME=banking-lab-keycloak-command-smoke BANKING_LAB_POSTGRES_PORT=15460 BANKING_LAB_CORE_BANKING_PORT=18102 BANKING_LAB_KEYCLOAK_PORT=18103 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://127.0.0.1:18103/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking` passed with a fresh seed DB.
- `curl -fsS http://127.0.0.1:18103/realms/banking-lab/.well-known/openid-configuration` passed.
- `curl -fsS http://127.0.0.1:18102/health` returned `auditHashChainValid=true` before and after the browser smoke.
- `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18102 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18103 npx playwright test apps/customer-web/e2e/customer-web-parity.spec.ts -g "interactive Keycloak"` passed 1 Playwright test.
- `env COMPOSE_PROJECT_NAME=banking-lab-keycloak-command-smoke docker compose --profile platform down -v` cleaned up the isolated Keycloak command-smoke stack and volume.

Remaining blockers:

- Node retirement remains blocked.
- At this point in the log, customer-web Keycloak login propagation was proven for masked account detail and transfer retry only.
- At this point in the log, customer-web failure, history/status, held/failed status, complaint entry, and complaint confirmation smokes still used simulator Bearer tokens; the later complaint propagation slice closes complaint entry and confirmation only.
- Staff-terminal, complaint-portal, ops-console, audit-console, and FDS/AML-console browser login/role propagation remain pending.
- WebAuthn/MFA browser flows, observability smoke, worker metrics/failure drills, and non-skipped Semgrep/Trivy/SBOM/DAST evidence remain incomplete.

## 2026-06-03: Customer Web Keycloak Complaint Propagation Slice

Changes completed:

- Extended the customer-web Keycloak login panel with explicit complaint entry and complaint confirmation actions.
- Both actions reuse the Keycloak-issued Bearer token retained only in browser memory state after the Next BFF token exchange.
- Extended the live Keycloak Playwright smoke to assert `Keycloak complaint received`, `ACCOUNT_ACCESS`, `RECEIVED`, `Keycloak complaint closed`, `CMP-SYN-CONFIRM-001`, and `CLOSED` while Spring simulator tokens are disabled.

Verification:

- `npm run next:customer-web:typecheck` passed.
- `npm run next:customer-web:build` passed.
- `npm run test:e2e` passed with 12 manifest-shell tests and 23 API-backed tests skipped when no API or Keycloak URL was configured.
- `env COMPOSE_PROJECT_NAME=banking-lab-keycloak-complaint-smoke BANKING_LAB_POSTGRES_PORT=15461 BANKING_LAB_CORE_BANKING_PORT=18104 BANKING_LAB_KEYCLOAK_PORT=18105 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://127.0.0.1:18105/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking` passed with a fresh seed DB.
- `curl -fsS http://127.0.0.1:18105/realms/banking-lab/.well-known/openid-configuration` passed.
- `curl -fsS http://127.0.0.1:18104/health` returned `auditHashChainValid=true` before and after the browser smoke.
- `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18104 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18105 npx playwright test apps/customer-web/e2e/customer-web-parity.spec.ts -g "interactive Keycloak"` passed 1 Playwright test.
- `env COMPOSE_PROJECT_NAME=banking-lab-keycloak-complaint-smoke docker compose --profile platform down -v` cleaned up the isolated Keycloak complaint-smoke stack and volume.

Remaining blockers:

- Node retirement remains blocked.
- Customer-web Keycloak login propagation is proven for masked account detail, transfer retry, complaint entry, and complaint confirmation only.
- At this point in the log, customer-web transfer failure, history/status, and held/failed status smokes still used simulator Bearer tokens; the later status propagation slice closes this customer-web gap.
- Staff-terminal, complaint-portal, ops-console, audit-console, and FDS/AML-console browser login/role propagation remain pending.
- WebAuthn/MFA browser flows, observability smoke, worker metrics/failure drills, and non-skipped Semgrep/Trivy/SBOM/DAST evidence remain incomplete.

## 2026-06-03: Customer Web Keycloak Transfer Status Propagation Slice

Changes completed:

- Extended the customer-web Keycloak login panel with transfer failure, transaction history/status, held FDS status, and durable held/failed transfer status actions.
- The new actions reuse the Keycloak-issued Bearer token retained only in browser memory state after the Next BFF token exchange.
- Extended the live Keycloak Playwright smoke to assert `Keycloak transfer rejected`, `LEDGER_INSUFFICIENT_AVAILABLE_BALANCE`, `Keycloak history and held status loaded`, `CWB-OIDC-HIST-...`, `FDS-SYN-001`, `HELD`, `Keycloak held and failed statuses loaded`, `CWB-OIDC-HELD-...`, `CWB-OIDC-FAILED-...`, `FAILED`, and `REQUEST_VALIDATION_FAILED` while Spring simulator tokens are disabled.

Verification:

- `npm run next:customer-web:typecheck` passed.
- `npm run next:customer-web:build` passed.
- `npm run test:e2e` passed with 12 manifest-shell tests and 23 API-backed tests skipped when no API or Keycloak URL was configured.
- `env COMPOSE_PROJECT_NAME=banking-lab-keycloak-status-smoke BANKING_LAB_POSTGRES_PORT=15462 BANKING_LAB_CORE_BANKING_PORT=18106 BANKING_LAB_KEYCLOAK_PORT=18107 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://127.0.0.1:18107/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking` passed with a fresh seed DB.
- `curl -fsS http://127.0.0.1:18107/realms/banking-lab/.well-known/openid-configuration` passed.
- `curl -fsS http://127.0.0.1:18106/health` returned `auditHashChainValid=true` before and after the browser smoke.
- `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18106 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18107 npx playwright test apps/customer-web/e2e/customer-web-parity.spec.ts -g "interactive Keycloak"` passed 1 Playwright test.
- `env COMPOSE_PROJECT_NAME=banking-lab-keycloak-status-smoke docker compose --profile platform down -v` cleaned up the isolated Keycloak status-smoke stack and volume.

Remaining blockers:

- Node retirement remains blocked.
- Customer-web Keycloak login propagation is proven for all current API-backed customer paths: masked account detail, transfer retry, transfer failure, transaction history/status, held FDS status, durable held/failed transfer status, complaint entry, and complaint confirmation.
- Staff-terminal, complaint-portal, ops-console, audit-console, and FDS/AML-console browser login/role propagation remain pending.
- WebAuthn/MFA browser flows, observability smoke, worker metrics/failure drills, and non-skipped Semgrep/Trivy/SBOM/DAST evidence remain incomplete.

## 2026-06-03: Staff Terminal Keycloak Branch/Checker Propagation Slice

Changes completed:

- Added a staff-terminal Next BFF token exchange route for the public `staff-terminal` Keycloak client.
- Extended the staff-terminal API-backed panel with live Keycloak branch and checker login actions.
- The branch login uses Authorization Code + PKCE to call Spring staff masked customer detail as `branch01`.
- The checker login uses a second Authorization Code + PKCE flow for `manager01`; the Playwright helper handles Keycloak re-auth screens by restarting login when the first session is still selected.
- Added a Keycloak-backed customer-change command smoke that requests the change as `branch01` and approves it as `manager01`.
- Updated the signed JWKS decoder to use Keycloak `preferred_username` as the command actor subject, falling back to `sub` for non-Keycloak signed JWT tests. This keeps actor binding meaningful for synthetic operator IDs instead of comparing requests to opaque Keycloak UUIDs.
- Added `staff-terminal` web origins to the imported Keycloak realm.

Verification:

- `npm run packages:typecheck` passed.
- `npm run next:staff-terminal:typecheck` passed.
- `npm run next:staff-terminal:build` passed.
- `npm run test:e2e` passed with 12 manifest-shell tests and 24 API-backed tests skipped when no API or Keycloak URL was configured.
- `scripts/run-core-banking-tests.sh :services:core-banking:bootJar` passed after the signed JWT actor-subject change.
- `env COMPOSE_PROJECT_NAME=banking-lab-staff-keycloak-smoke BANKING_LAB_POSTGRES_PORT=15463 BANKING_LAB_CORE_BANKING_PORT=18108 BANKING_LAB_KEYCLOAK_PORT=18109 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://127.0.0.1:18109/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking` passed with a fresh seed DB.
- `curl -fsS http://127.0.0.1:18109/realms/banking-lab/.well-known/openid-configuration` passed.
- `curl -fsS http://127.0.0.1:18108/health` returned `auditHashChainValid=true` before and after the browser smoke.
- A direct branch/manager token diagnostic initially returned `AUTHORIZATION_POLICY_VIOLATION` for actor binding before the `preferred_username` fix, then returned 201 for the branch change request and 200 for manager approval after rebuilding `core-banking`.
- `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18108 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18109 npx playwright test apps/staff-terminal/e2e/staff-terminal-parity.spec.ts -g "interactive Keycloak"` passed 1 Playwright test.

Remaining blockers:

- Node retirement remains blocked.
- Customer-web Keycloak login propagation is proven for all current API-backed customer paths.
- Staff-terminal Keycloak login propagation is proven for masked customer lookup and branch-maker/manager-checker customer-change approval.
- Complaint-portal, ops-console, audit-console, and FDS/AML-console browser login/role propagation remain pending.
- WebAuthn/MFA browser flows, observability smoke, worker metrics/failure drills, and non-skipped Semgrep/Trivy/SBOM/DAST evidence remain incomplete.

## 2026-06-03: Complaint Portal Keycloak Answer Propagation Slice

Changes completed:

- Added a complaint-portal Next BFF token exchange route for the public `complaint-portal` Keycloak client.
- Added the `complaint-portal` Keycloak client and synthetic `complaint01` user with the `COMPLAINT_HANDLER` role to the imported realm.
- Extended the complaint-portal API-backed panel with live Keycloak complaint handler and checker login actions.
- The handler login uses Authorization Code + PKCE to call Spring complaint read-model and answer-draft APIs as `complaint01`.
- The checker login uses a second Authorization Code + PKCE flow for `manager01`.
- Added a Keycloak-backed complaint answer command smoke that drafts an answer as `complaint01` and approves it as `manager01`.
- Added a Keycloak-backed workflow failure-state smoke that renders duplicate answer-draft `WORKFLOW_STATE_VIOLATION` from the real Spring route.

Verification:

- `node -e "const fs=require('fs'); JSON.parse(fs.readFileSync('infra/keycloak/realm-banking-lab.json','utf8')); console.log('realm ok')"` passed.
- `npm run packages:typecheck` passed.
- `npm run next:complaint-portal:typecheck` passed.
- `npm run next:complaint-portal:build` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-complaint-keycloak-smoke BANKING_LAB_POSTGRES_PORT=15464 BANKING_LAB_CORE_BANKING_PORT=18110 BANKING_LAB_KEYCLOAK_PORT=18111 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://127.0.0.1:18111/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking` passed with a fresh seed DB.
- `curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18111/realms/banking-lab/.well-known/openid-configuration` passed.
- `curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18110/health` returned `auditHashChainValid=true`.
- `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18110 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18111 npx playwright test apps/complaint-portal/e2e/complaint-portal-parity.spec.ts -g "interactive Keycloak"` passed 1 Playwright test.

Remaining blockers:

- Node retirement remains blocked.
- Customer-web Keycloak login propagation is proven for all current API-backed customer paths.
- Staff-terminal Keycloak login propagation is proven for masked customer lookup and branch-maker/manager-checker customer-change approval.
- Complaint-portal Keycloak login propagation is proven for answer approval and duplicate answer workflow failure-state rendering.
- Ops-console, audit-console, and FDS/AML-console browser login/role propagation remain pending.
- WebAuthn/MFA browser flows, observability smoke, worker metrics/failure drills, and non-skipped Semgrep/Trivy/SBOM/DAST evidence remain incomplete.

## 2026-06-03: Ops Console Keycloak Reconciliation Propagation Slice

Changes completed:

- Added an ops-console Next BFF token exchange route for the public `ops-console` Keycloak client.
- Added the `ops-console` Keycloak client and synthetic `ops01` user with the `OPS_OPERATOR` role to the imported realm.
- Extended the ops-console API-backed panel with live Keycloak ops operator and checker login actions.
- The operator login uses Authorization Code + PKCE to call Spring reconciliation read-model and adjustment APIs as `ops01`.
- The checker login uses a second Authorization Code + PKCE flow for `manager01`.
- Added a Keycloak-backed reconciliation adjustment command smoke that requests an adjustment as `ops01` and approves it as `manager01`.
- Added a Keycloak-backed workflow failure-state smoke that renders adjusted-item `WORKFLOW_STATE_VIOLATION` from the real Spring route.

Verification:

- `node -e "const fs=require('fs'); JSON.parse(fs.readFileSync('infra/keycloak/realm-banking-lab.json','utf8')); console.log('realm ok')"` passed.
- `npm run packages:typecheck` passed.
- `npm run next:ops-console:typecheck` passed.
- `npm run next:ops-console:build` passed.
- `npm run test:e2e` passed with 12 manifest-shell tests and 26 API-backed tests skipped when no API or Keycloak URL was configured.
- `env COMPOSE_PROJECT_NAME=banking-lab-ops-keycloak-smoke BANKING_LAB_POSTGRES_PORT=15465 BANKING_LAB_CORE_BANKING_PORT=18112 BANKING_LAB_KEYCLOAK_PORT=18113 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://127.0.0.1:18113/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking` passed with a fresh seed DB.
- `curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18113/realms/banking-lab/.well-known/openid-configuration` passed.
- `curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18112/health` returned `auditHashChainValid=true`.
- `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18112 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18113 npx playwright test apps/ops-console/e2e/ops-console-parity.spec.ts -g "interactive Keycloak"` passed 1 Playwright test.
- Post-smoke `curl -fsS http://127.0.0.1:18112/health` returned `auditHashChainValid=true`.

Remaining blockers:

- Node retirement remains blocked.
- Customer-web Keycloak login propagation is proven for all current API-backed customer paths.
- Staff-terminal Keycloak login propagation is proven for masked customer lookup and branch-maker/manager-checker customer-change approval.
- Complaint-portal Keycloak login propagation is proven for answer approval and duplicate answer workflow failure-state rendering.
- Ops-console Keycloak login propagation is proven for reconciliation adjustment and adjusted-item workflow failure-state rendering.
- Audit-console and FDS/AML-console browser login/role propagation remain pending.
- WebAuthn/MFA browser flows, observability smoke, worker metrics/failure drills, and non-skipped Semgrep/Trivy/SBOM/DAST evidence remain incomplete.

## 2026-06-03: Audit Console Keycloak Hash-Chain Propagation Slice

Changes completed:

- Added an audit-console Next BFF token exchange route for the public `audit-console` Keycloak client.
- Added the `audit-console` Keycloak client and synthetic `auditor01` user with the `AUDITOR` role to the imported realm.
- Extended the audit-console API-backed panel with a live Keycloak auditor login action.
- The auditor login uses Authorization Code + PKCE to call Spring audit hash-chain read-model APIs as `auditor01`.
- Added a Keycloak-backed audit read-model smoke that renders hash-chain validity and the seeded `AUD-SYN-SEED-001` event while Spring simulator tokens are disabled.

Verification:

- `node -e "const fs=require('fs'); JSON.parse(fs.readFileSync('infra/keycloak/realm-banking-lab.json','utf8')); console.log('realm ok')"` passed.
- `npm run packages:typecheck` passed.
- `npm run next:audit-console:typecheck` passed.
- `npm run next:audit-console:build` passed.
- `npm run test:e2e` passed with 12 manifest-shell tests and 27 API-backed tests skipped when no API or Keycloak URL was configured.
- `env COMPOSE_PROJECT_NAME=banking-lab-audit-keycloak-smoke BANKING_LAB_POSTGRES_PORT=15466 BANKING_LAB_CORE_BANKING_PORT=18114 BANKING_LAB_KEYCLOAK_PORT=18115 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://127.0.0.1:18115/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking` passed with a fresh seed DB.
- `curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18115/realms/banking-lab/.well-known/openid-configuration` passed.
- `curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18114/health` returned `auditHashChainValid=true`.
- `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18114 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18115 npx playwright test apps/audit-console/e2e/audit-console-parity.spec.ts -g "interactive Keycloak"` passed 1 Playwright test.
- Post-smoke `curl -fsS http://127.0.0.1:18114/health` returned `auditHashChainValid=true`.

Remaining blockers:

- Node retirement remains blocked.
- Customer-web Keycloak login propagation is proven for all current API-backed customer paths.
- Staff-terminal Keycloak login propagation is proven for masked customer lookup and branch-maker/manager-checker customer-change approval.
- Complaint-portal Keycloak login propagation is proven for answer approval and duplicate answer workflow failure-state rendering.
- Ops-console Keycloak login propagation is proven for reconciliation adjustment and adjusted-item workflow failure-state rendering.
- Audit-console Keycloak login propagation is proven for audit hash-chain read-model evidence.
- FDS/AML-console browser login/role propagation remains pending.
- WebAuthn/MFA browser flows, observability smoke, worker metrics/failure drills, and non-skipped Semgrep/Trivy/SBOM/DAST evidence remain incomplete.

## 2026-06-03: FDS/AML Console Keycloak Risk Propagation Slice

Changes completed:

- Added an FDS/AML-console Next BFF token exchange route for the public `fds-aml-console` Keycloak client.
- Added the `fds-aml-console` Keycloak client plus synthetic `risk01` and `compliance01` users to the imported realm.
- Added OIDC-specific synthetic FDS/AML command cases so full API-backed Playwright runs do not reuse the simulator-token command cases.
- Extended the FDS/AML-console API-backed panel with live Keycloak risk reviewer and checker login actions.
- The reviewer login uses Authorization Code + PKCE to call Spring FDS/AML read-model APIs as `risk01`.
- The checker login uses a second Authorization Code + PKCE flow for `compliance01`.
- Added a Keycloak-backed risk approval smoke that requests FDS release, FDS block, and AML closure as `risk01`, then approves them as `compliance01`.
- Added a Keycloak-backed workflow failure-state smoke that renders duplicate FDS/AML `WORKFLOW_STATE_VIOLATION` evidence from the real Spring routes.
- Verified the OIDC-specific FDS/AML command cases coexist with the simulator-token command and failure cases in the full FDS/AML browser spec.

Verification:

- `node -e "const fs=require('fs'); JSON.parse(fs.readFileSync('infra/keycloak/realm-banking-lab.json','utf8')); console.log('realm ok')"` passed.
- `npm run packages:typecheck` passed.
- `npm run next:fds-aml-console:typecheck` passed.
- `npm run next:fds-aml-console:build` passed.
- `npm run test:e2e` passed with 12 manifest-shell tests and 28 API-backed tests skipped when no API or Keycloak URL was configured.
- `env COMPOSE_PROJECT_NAME=banking-lab-risk-keycloak-smoke BANKING_LAB_POSTGRES_PORT=15476 BANKING_LAB_CORE_BANKING_PORT=18124 BANKING_LAB_KEYCLOAK_PORT=18125 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://127.0.0.1:18125/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking` passed with a fresh seed DB.
- `curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18125/realms/banking-lab/.well-known/openid-configuration` passed.
- `curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18124/health` returned `auditHashChainValid=true`.
- `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18124 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18125 npx playwright test apps/fds-aml-console/e2e/fds-aml-console-parity.spec.ts -g "interactive Keycloak"` passed 1 Playwright test.
- Post-smoke `curl -fsS http://127.0.0.1:18124/health` returned `auditHashChainValid=true`.
- `env COMPOSE_PROJECT_NAME=banking-lab-risk-keycloak-smoke BANKING_LAB_POSTGRES_PORT=15476 BANKING_LAB_CORE_BANKING_PORT=18124 BANKING_LAB_KEYCLOAK_PORT=18125 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://127.0.0.1:18125/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking` passed with a fresh seed DB.
- `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18124 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18125 npx playwright test apps/fds-aml-console/e2e/fds-aml-console-parity.spec.ts` passed all 9 FDS/AML Playwright tests with simulator-token paths and live Keycloak paths configured together.
- Post-full-spec `curl -fsS http://127.0.0.1:18124/health` returned `auditHashChainValid=true`.

Remaining blockers:

- Node retirement remains blocked.
- Customer-web Keycloak login propagation is proven for all current API-backed customer paths.
- Staff-terminal Keycloak login propagation is proven for masked customer lookup and branch-maker/manager-checker customer-change approval.
- Complaint-portal Keycloak login propagation is proven for answer approval and duplicate answer workflow failure-state rendering.
- Ops-console Keycloak login propagation is proven for reconciliation adjustment and adjusted-item workflow failure-state rendering.
- Audit-console Keycloak login propagation is proven for audit hash-chain read-model evidence.
- FDS/AML-console Keycloak login propagation is proven for risk read-model, FDS release/block approval, AML closure approval, and duplicate workflow failure-state rendering.
- WebAuthn/MFA browser flows, observability smoke, worker metrics/failure drills, and non-skipped Semgrep/Trivy/SBOM/DAST evidence remain incomplete.

## 2026-06-03: Staff Terminal WebAuthn Required-Action Slice

Changes completed:

- Added the Keycloak `webauthn-register` required action to the imported synthetic realm.
- Added `manager-webauthn01` for browser WebAuthn registration and `manager-webauthn-block01` for direct-grant blocking evidence.
- Extended `LiveKeycloakRealmIntegrationTest` to assert WebAuthn-required synthetic managers are blocked by direct grant with `400 invalid_grant`.
- Added a staff-terminal WebAuthn manager login action that uses the existing Authorization Code + PKCE flow and exchanges the returned code through the Next BFF token route.
- Added a Playwright Chromium virtual-authenticator smoke that completes Keycloak WebAuthn registration and then calls the Spring staff customer detail API with simulator tokens disabled.

Verification:

- `node -e "const fs=require('fs'); JSON.parse(fs.readFileSync('infra/keycloak/realm-banking-lab.json','utf8')); console.log('realm ok')"` passed.
- `npm run packages:typecheck` passed.
- `npm run next:staff-terminal:typecheck` passed.
- `npm run next:staff-terminal:build` passed.
- `npm run test:e2e -- apps/staff-terminal/e2e/staff-terminal-parity.spec.ts` passed with 2 manifest-shell tests and 4 API/Keycloak tests skipped when no API or Keycloak URL was configured.
- `env COMPOSE_PROJECT_NAME=banking-lab-webauthn-smoke BANKING_LAB_POSTGRES_PORT=15477 BANKING_LAB_CORE_BANKING_PORT=18126 BANKING_LAB_KEYCLOAK_PORT=18127 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://localhost:18127/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking` passed with a fresh seed DB.
- `curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://localhost:18127/realms/banking-lab/.well-known/openid-configuration` passed and returned issuer `http://localhost:18127/realms/banking-lab`.
- `curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18126/health` returned `auditHashChainValid=true`.
- `curl -sS -i -X POST http://localhost:18127/realms/banking-lab/protocol/openid-connect/token -H 'Content-Type: application/x-www-form-urlencoded' --data 'grant_type=password&client_id=staff-terminal&username=manager-webauthn-block01&password=manager-webauthn-block01-pass'` returned `400 invalid_grant` with `Account is not fully set up`.
- `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18126 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://localhost:18127 npx playwright test apps/staff-terminal/e2e/staff-terminal-parity.spec.ts -g "WebAuthn"` passed 1 Playwright test.
- `env BANKING_LAB_LIVE_KEYCLOAK_BASE_URL=http://localhost:18127 scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.security.LiveKeycloakRealmIntegrationTest` passed.
- Post-smoke `curl -fsS http://127.0.0.1:18126/health` returned `auditHashChainValid=true`.

Remaining blockers:

- Node retirement remains blocked.
- WebAuthn required-action and local virtual-authenticator browser completion are proven for the synthetic staff-terminal path.
- Production passkey policy, recovery, attestation stance, and non-synthetic operations hardening are not claimed.
- Remaining blockers are full Node reference parity, broader workflow/failure-state channel parity, non-skipped Semgrep/Trivy/SBOM/DAST evidence, observability smoke, worker metrics/failure drills, and final retirement review.

## 2026-06-03: Keycloak Passkey Policy And Recovery Segregation Slice

Changes completed:

- Added explicit WebAuthn policy fields to the imported synthetic Keycloak realm: RP entity `Synthetic Banking Lab`, RP ID `localhost`, ES256 signatures, attestation conveyance `none`, user verification `required`, 60 second creation timeout, and duplicate-authenticator avoidance.
- Added a `PASSKEY_RECOVERY_ADMIN` realm role.
- Added `security-admin01` as a synthetic passkey recovery/compliance/auditor account without customer, branch-staff, or branch-manager roles.
- Added `KeycloakRealmPolicyTest` to lock the committed realm policy, required action, WebAuthn manager account, and recovery role segregation.
- Extended `LiveKeycloakRealmIntegrationTest` to decode the `security-admin01` signed token, assert recovery/compliance/auditor roles, assert the absence of customer/branch roles, and use that token against a Spring staff detail route with simulator tokens disabled.

Verification:

- `node -e "const fs=require('fs'); JSON.parse(fs.readFileSync('infra/keycloak/realm-banking-lab.json','utf8')); console.log('realm ok')"` passed.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test --tests lab.banking.core.security.KeycloakRealmPolicyTest` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-passkey-policy-smoke BANKING_LAB_POSTGRES_PORT=15478 BANKING_LAB_CORE_BANKING_PORT=18128 BANKING_LAB_KEYCLOAK_PORT=18129 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://localhost:18129/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking` passed with a fresh seed DB.
- `curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://localhost:18129/realms/banking-lab/.well-known/openid-configuration` passed and returned issuer `http://localhost:18129/realms/banking-lab`.
- `curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18128/health` returned `auditHashChainValid=true`.
- `curl -sS -i -X POST http://localhost:18129/realms/banking-lab/protocol/openid-connect/token -H 'Content-Type: application/x-www-form-urlencoded' --data 'grant_type=password&client_id=staff-terminal&username=manager-webauthn-block01&password=manager-webauthn-block01-pass'` returned `400 invalid_grant` with `Account is not fully set up`.
- `env BANKING_LAB_LIVE_KEYCLOAK_BASE_URL=http://localhost:18129 scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.security.LiveKeycloakRealmIntegrationTest` passed.
- `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18128 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://localhost:18129 npx playwright test apps/staff-terminal/e2e/staff-terminal-parity.spec.ts -g "WebAuthn"` passed 1 Chromium Playwright test.
- Post-smoke `curl -fsS http://127.0.0.1:18128/health` returned `auditHashChainValid=true`.
- `env COMPOSE_PROJECT_NAME=banking-lab-passkey-policy-smoke docker compose --profile platform down -v` removed the temporary smoke stack.

Remaining blockers:

- Node retirement remains blocked.
- WebAuthn required-action, local virtual-authenticator browser completion, synthetic WebAuthn policy, and recovery role segregation are proven for the local lab path.
- Non-synthetic passkey operations, hardware attestation policy, enterprise recovery runbooks, and production deployment evidence are not claimed.
- Remaining blockers are full Node reference parity, broader workflow/failure-state channel parity, non-skipped Semgrep/Trivy/SBOM/DAST evidence, observability smoke, worker metrics/failure drills, and final retirement review.

## 2026-06-03: Staff Privileged Unmask Browser Slice

Changes completed:

- Added `PiiUnmaskCommand` and `StaffUnmaskResponse` to the shared TypeScript API client.
- Added `unmaskStaffCustomer` to `@banking-lab/api-client` so channel apps can call `POST /api/staff/pii/unmask` through the same typed client surface as other staff commands.
- Extended the staff-terminal API-backed panel with a privileged unmask smoke.
- The simulator-token smoke first attempts branch-staff unmask, renders the structured `AUTHORIZATION_POLICY_VIOLATION`, then executes manager-approved time-boxed unmask and renders `UNMASKED_TIMEBOXED`, `010-0000-1001`, TTL `300`, and `AUD-...`.
- Extended the staff-terminal live Keycloak smoke so the `manager01` signed token executes the same privileged unmask route while Spring simulator-token fallback is disabled.
- Fixed `BankingLabAuthorizationFilter` to add CORS headers to direct authorization-denial responses for allowed local channel origins.
- Added `SecurityAuthorizationIntegrationTest` coverage for CORS-readable auth denial on `POST /api/staff/pii/unmask`.

Verification:

- `npm run packages:typecheck` passed.
- `npm run next:staff-terminal:typecheck` passed.
- `npm run test:e2e -- apps/staff-terminal/e2e/staff-terminal-parity.spec.ts` passed with 2 manifest-shell tests and 5 API/Keycloak tests skipped when no API or Keycloak URL was configured.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.security.SecurityAuthorizationIntegrationTest` passed.
- `scripts/run-core-banking-tests.sh :services:core-banking:bootJar` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-staff-unmask-smoke BANKING_LAB_POSTGRES_PORT=15479 BANKING_LAB_CORE_BANKING_PORT=18130 BANKING_LAB_KEYCLOAK_PORT=18131 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://localhost:18131/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build --force-recreate postgres keycloak core-banking` passed with a fresh seed DB.
- `curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18130/health` returned `auditHashChainValid=true`.
- `env CI=1 BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18130 npx playwright test apps/staff-terminal/e2e/staff-terminal-parity.spec.ts -g "privileged unmask"` passed 1 Playwright test after the auth-deny CORS fix.
- The first sandboxed Playwright run failed with `listen EPERM` and the first elevated run exposed a browser `Failed to fetch` caused by auth-filter denial without CORS headers; the filter fix closed that issue and the test then passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-staff-unmask-smoke BANKING_LAB_POSTGRES_PORT=15479 BANKING_LAB_CORE_BANKING_PORT=18130 BANKING_LAB_KEYCLOAK_PORT=18131 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://localhost:18131/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build --force-recreate postgres keycloak core-banking` passed with simulator-token fallback disabled.
- `curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://localhost:18131/realms/banking-lab/.well-known/openid-configuration` passed and returned issuer `http://localhost:18131/realms/banking-lab`.
- `env CI=1 BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18130 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://localhost:18131 npx playwright test apps/staff-terminal/e2e/staff-terminal-parity.spec.ts -g "interactive Keycloak"` passed 2 Playwright tests, including the staff/checker unmask path and the WebAuthn path.
- Post-smoke `curl -fsS http://127.0.0.1:18130/health` returned `auditHashChainValid=true`.
- `env COMPOSE_PROJECT_NAME=banking-lab-staff-unmask-smoke docker compose --profile platform down -v` removed the temporary smoke stack.

Remaining blockers:

- Node retirement remains blocked.
- Staff privileged unmask browser parity is now proven for both simulator-token and live Keycloak manager-token paths.
- Remaining blockers are full Node reference parity, broader workflow/failure-state channel parity, non-skipped Semgrep/Trivy/SBOM/DAST evidence, observability smoke, worker metrics/failure drills, non-synthetic passkey operations, and final retirement review.

## 2026-06-03: Temporal Worker Metrics And Retry Drill Slice

Changes completed:

- Added `micrometer-registry-prometheus` to the core-banking service.
- Exposed Actuator `health`, `info`, `metrics`, and `prometheus` endpoints in the Spring configuration.
- Added `TemporalWorkerMetrics` for worker running state, start count, failed-start count, and stop count with namespace/task-queue labels.
- Wired `BankingCaseTemporalWorker` lifecycle start/failure/stop paths to those metrics.
- Added `TemporalWorkerMetricsTest` for direct Micrometer registry coverage.
- Added `ObservabilityActuatorIntegrationTest` to prove `/actuator/prometheus` exposes the Temporal worker metrics through Spring Boot with PostgreSQL/Flyway applied.
- Updated Prometheus scrape config to target the Spring `core-banking` service and `core-banking-temporal-worker` service instead of the legacy Node runtime.
- Added a synthetic transient Temporal retry drill to `BankingCaseTemporalWorkflowIntegrationTest`; the first attempt fails before approval, the retry reaches `WAITING_APPROVAL`, and checker approval completes the complaint-answer workflow.

Verification:

- Initial sandboxed `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test --tests lab.banking.core.temporal.TemporalWorkerMetricsTest` failed because Gradle could not create a file-lock coordination socket inside the sandbox; the same command passed under the approved execution path.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test --tests lab.banking.core.temporal.TemporalWorkerMetricsTest` passed.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.temporal.BankingCaseTemporalWorkflowIntegrationTest` initially exposed a DTO property call typo in the new test, then passed after correction.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.observability.ObservabilityActuatorIntegrationTest` initially returned 500 for `/actuator/prometheus` because Spring Boot test observability auto-configuration was disabled; adding `@AutoConfigureObservability` made the runtime-like Prometheus endpoint test pass.

Remaining blockers:

- Node retirement remains blocked.
- Temporal worker metrics and a synthetic Temporal retry drill are now proven.
- Live worker restart drills, trace/log correlation, full Prometheus/Grafana/Loki/Tempo stack smoke, broader workflow/failure-state channel parity, non-skipped Semgrep/Trivy/SBOM/DAST evidence, non-synthetic passkey operations, and final retirement review remain incomplete.

## 2026-06-03: Security Evidence Docker Fallback Slice

Changes completed:

- Extended `scripts/run-security-evidence.ts` so Semgrep, Trivy filesystem scanning, and CycloneDX SBOM generation can run through Docker scanner images when local scanner CLIs are not installed.
- Extended `scripts/run-security-evidence.ts` so ZAP baseline DAST can run through Docker when `BANKING_LAB_DAST_URL` is supplied and host `zap-baseline.py` is not installed.
- Made Semgrep SAST fail on findings with `--error`.
- Made Trivy filesystem scanning fail on HIGH/CRITICAL findings with `--exit-code 1`.
- Scoped the raw balance mutation Semgrep rule away from the central `LedgerCommandService` projection writer, where balance projections are updated only after ledger postings are appended.
- Added `SecurityHeadersFilter` to emit `X-Content-Type-Options`, cross-origin isolation/resource policy headers, and `Cache-Control: no-store`.
- Added safe synthetic `/`, `/robots.txt`, and `/sitemap.xml` endpoints so crawler requests do not disclose runtime errors.
- Added `zap-baseline.conf` to ignore ZAP rule `10049` for intentional no-store banking API responses.
- Updated DAST, SBOM, and regulatory mapping docs for completed SCA/SAST/container/SBOM/DAST evidence.

Verification:

- `npm run scripts:typecheck` passed.
- `npm run security:evidence` first passed with Docker scanner fallback for SCA/SAST/Trivy/SBOM: 4 passed, 0 failed, 1 skipped.
- `semgrep.json` contains 0 results and 0 errors.
- `trivy-fs.json` contains 0 HIGH/CRITICAL vulnerabilities.
- `env COMPOSE_PROJECT_NAME=banking-lab-dast-smoke BANKING_LAB_POSTGRES_PORT=15480 BANKING_LAB_CORE_BANKING_PORT=18132 BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile migration up -d --build postgres core-banking` started a synthetic Spring API DAST target.
- `curl --retry 30 --retry-delay 2 --retry-connrefused -fsS -D - http://127.0.0.1:18132/health` returned `200`, `auditHashChainValid=true`, and the new security headers.
- `env BANKING_LAB_DAST_URL=http://host.docker.internal:18132/health npm run security:evidence` eventually passed after adding security headers, safe crawler endpoints, and the no-store ZAP ignore config: 5 passed, 0 failed, 0 skipped.
- `zap-baseline.log` reports `FAIL-NEW: 0`, `WARN-NEW: 0`, `INFO: 0`, one intentional ignored no-store rule, and 66 PASS checks.
- `env COMPOSE_PROJECT_NAME=banking-lab-dast-smoke docker compose --profile migration down -v` removed the temporary DAST stack.

Remaining blockers:

- Node retirement remains blocked.
- SCA/SAST/container/SBOM/DAST evidence is now non-skipped and passing for the local synthetic slice.
- Full Node reference parity, broader workflow/failure-state channel parity, full Prometheus/Grafana/Loki/Tempo stack smoke, live worker restart drills, non-synthetic passkey operations, and final retirement review remain incomplete.

## 2026-06-03: Observability Stack Smoke Slice

Changes completed:

- Added `docs/test-evidence/observability-stack-smoke.md` as the target-stack runtime evidence record for observability readiness and Prometheus scraping.
- Verified the Docker Compose `platform` profile with isolated ports for PostgreSQL, Temporal, core-banking, core-banking-temporal-worker, Prometheus, Grafana, Loki, Tempo, and Redpanda.
- Confirmed Prometheus scrapes Spring `core-banking`, `core-banking-temporal-worker`, and Redpanda targets without `lastError`.
- Confirmed `banking_lab_temporal_worker_running` reports `1` for `core-banking-temporal-worker:8081` and `0` for the non-worker `core-banking:8081`.
- Updated node-retirement, QA recommendation, gap, and drill docs so observability readiness is no longer treated as missing while trace/log correlation and live worker restart drills remain explicit blockers.

Verification:

- `env COMPOSE_PROJECT_NAME=banking-lab-observability-smoke ... docker compose --profile platform up -d --build postgres temporal core-banking core-banking-temporal-worker prometheus grafana loki tempo` passed after Docker socket approval.
- `env COMPOSE_PROJECT_NAME=banking-lab-observability-smoke BANKING_LAB_REDPANDA_PORT=19092 BANKING_LAB_REDPANDA_ADMIN_PORT=19644 docker compose --profile platform up -d redpanda` passed.
- `curl --retry 12 --retry-delay 5 --retry-all-errors -fsS http://127.0.0.1:18133/health` returned `status=ok`, `syntheticOnly=true`, and `auditHashChainValid=true`.
- `curl --retry 12 --retry-delay 5 --retry-all-errors -fsS http://127.0.0.1:19090/-/ready` returned `Prometheus Server is Ready.`
- `curl --retry 12 --retry-delay 5 --retry-all-errors -fsS http://127.0.0.1:13001/api/health` returned Grafana `database=ok`.
- `curl --retry 12 --retry-delay 5 --retry-all-errors -fsS http://127.0.0.1:13100/ready` and `http://127.0.0.1:13200/ready` returned `ready` after transient `503` warm-up responses.
- `curl --retry 12 --retry-delay 5 --retry-all-errors -fsS http://127.0.0.1:19644/v1/status/ready` returned Redpanda `status=ready`.
- `curl --retry 12 --retry-delay 5 --retry-all-errors -fsS 'http://127.0.0.1:19090/api/v1/query?query=up'` returned `1` for `core-banking:8081`, `core-banking-temporal-worker:8081`, and `redpanda:9644`.
- `curl --retry 12 --retry-delay 5 --retry-all-errors -fsS 'http://127.0.0.1:19090/api/v1/query?query=banking_lab_temporal_worker_running'` returned `1` for `core-banking-temporal-worker:8081`.
- `curl --retry 12 --retry-delay 5 --retry-all-errors -fsS http://127.0.0.1:19090/api/v1/targets` returned `health=up` and empty `lastError` for the Spring service, Temporal worker, and Redpanda scrape targets.

Remaining blockers:

- Node retirement remains blocked.
- Local observability readiness and Prometheus scrape evidence is now proven for the synthetic platform stack.
- Full Node reference parity, broader workflow/failure-state channel parity, live worker restart drills, OpenTelemetry trace/log correlation, non-synthetic passkey operations, and final retirement review remain incomplete.

## 2026-06-03: Temporal Worker Restart Drill Slice

Changes completed:

- Extended `LiveTemporalWorkerSmokeIntegrationTest` with a live restart drill using a unique Temporal task queue.
- The drill starts an SDK worker, starts a complaint-answer workflow, waits for `WAITING_APPROVAL`, shuts down the worker, sends checker approval while no worker is polling, starts a second SDK worker, and verifies completion through Temporal history replay.
- Added `docs/test-evidence/temporal-worker-restart-drill.md`.
- Updated workflow, parity, evidence-gap, failure-drill, QA recommendation, and node-retirement gate docs so worker restart is no longer treated as missing.

Verification:

- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest` passed without live Temporal env, proving compile and env-gated skip behavior.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-restart-smoke BANKING_LAB_POSTGRES_PORT=15482 BANKING_LAB_TEMPORAL_PORT=17235 docker compose --profile platform up -d postgres temporal` passed.
- `env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17235 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal workflow survives worker restart before approval completion'` passed.

Remaining blockers:

- Node retirement remains blocked.
- Live Temporal worker restart continuity is now proven for the synthetic complaint-answer workflow contract.
- Full Node reference parity, broader workflow/failure-state channel parity, Temporal/container worker failure drills, OpenTelemetry trace/log correlation, non-synthetic passkey operations, and final retirement review remain incomplete.

## 2026-06-03: Outbox Worker Failure Drill Slice

Changes completed:

- Extended `RedpandaOutboxDeliveryIntegrationTest` with a crash-before-mark-published drill.
- The drill writes a durable `PENDING` outbox event, simulates a broker-acked Redpanda record without updating the outbox row, replays the event through the normal publisher, and verifies inbox idempotency absorbs the duplicate broker records.
- Added `docs/test-evidence/outbox-worker-failure-drill.md`.
- Updated node-retirement, parity, QA recommendation, gap, and failure-drill docs so outbox crash-before-mark-published recovery is no longer treated as missing.

Verification:

- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.eventing.RedpandaOutboxDeliveryIntegrationTest` passed.

Remaining blockers:

- Node retirement remains blocked.
- Outbox crash-before-mark-published replay/idempotency behavior is now proven for the Redpanda-backed target stack.
- Full Node reference parity, broader workflow/failure-state channel parity, Temporal/container worker failure drills, OpenTelemetry trace/log correlation, non-synthetic passkey operations, and final retirement review remain incomplete.

## 2026-06-03: OpenTelemetry Trace Log Correlation Slice

Changes completed:

- Added Micrometer OpenTelemetry tracing and OTLP exporter dependencies to the Spring core-banking service.
- Added tracing configuration for W3C propagation, full local sampling, and OTLP trace export to Tempo through Docker Compose.
- Added `TraceLogCorrelationFilter` so Spring access logs include request ID, trace ID, span ID, path, status, and `syntheticOnly=true` without logging PII or tokens.
- Extended `ObservabilityActuatorIntegrationTest` to assert trace/span/request correlation appears in the access log.
- Added `docs/test-evidence/opentelemetry-trace-log-correlation.md`.

Verification:

- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.observability.ObservabilityActuatorIntegrationTest` passed.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-otel-smoke ... docker compose --profile platform up -d --build postgres tempo core-banking` passed.
- `curl --retry 30 --retry-delay 2 --retry-connrefused -fsS -H 'x-request-id: REQ-OTEL-CORRELATION-001' http://127.0.0.1:18134/health` returned `status=ok`, `syntheticOnly=true`, `auditHashChainValid=true`, and `migrationTarget=kotlin-spring-boot`.
- `env COMPOSE_PROJECT_NAME=banking-lab-otel-smoke docker compose logs --no-color core-banking | rg 'REQ-OTEL-CORRELATION-001|observability\.access'` found trace ID `2e139b9fee0dc5d1f2c1e782257bc1a5`.
- `curl -v --retry 3 --retry-delay 1 --retry-all-errors -fsS http://127.0.0.1:13201/api/traces/2e139b9fee0dc5d1f2c1e782257bc1a5` returned `200` with `service.name=banking-lab-core-banking`, `synthetic.only=true`, and span `http get /health`.
- `env COMPOSE_PROJECT_NAME=banking-lab-otel-smoke docker compose --profile platform down -v` removed the temporary stack.

Remaining blockers:

- Node retirement remains blocked.
- Basic Spring HTTP trace/log correlation is now proven for the target stack.
- Full Node reference parity, broader workflow/failure-state channel parity, Temporal/container worker failure drills, workflow-specific trace/log coverage, non-synthetic passkey operations, and final retirement review remain incomplete.

## 2026-06-03: Temporal Container Worker Restart Drill Slice

Changes completed:

- Extended `LiveTemporalWorkerSmokeIntegrationTest` with a live Compose container restart drill for `core-banking-temporal-worker`.
- Made the Compose worker task queue overrideable with `BANKING_LAB_TEMPORAL_TASK_QUEUE` so isolated live drills can keep the worker and workflow on the same queue.
- Added `docs/test-evidence/temporal-container-worker-restart-drill.md`.
- Updated node-retirement, parity, QA recommendation, gap, workflow, and failure-drill docs so the actual Compose worker container restart path is no longer treated as missing.

Verification:

- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal workflow survives Compose worker container restart before approval completion'` passed without live Temporal env, proving compile and env-gated skip behavior.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-container-drill BANKING_LAB_POSTGRES_PORT=15485 BANKING_LAB_TEMPORAL_PORT=17236 BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-container-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform up -d --build postgres temporal core-banking-temporal-worker` passed.
- First live drill attempt timed out because the test-managed `docker compose up` restart did not pass `BANKING_LAB_TEMPORAL_TASK_QUEUE`, causing the restarted worker to poll the default `banking-case-workflows` queue; the harness was fixed to pass the task queue env through `docker compose kill` and `up`.
- `env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17236 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-container-drill BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-container-drill scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal workflow survives Compose worker container restart before approval completion'` passed after the fix.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-container-drill docker compose ps --all` showed PostgreSQL, Temporal, and the restarted worker container running after the drill.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-container-drill docker compose logs --no-color core-banking-temporal-worker` showed the restarted worker polling `banking-case-workflows-container-drill`.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-container-drill docker compose --profile platform down -v` removed the temporary stack.

Remaining blockers:

- Node retirement remains blocked.
- Live Compose worker container restart continuity is now proven for the synthetic complaint-answer workflow contract.
- Full Node reference parity, broader workflow/failure-state channel parity, broader workflow/container failure drills beyond the complaint-answer restart path, workflow-specific trace/log coverage, non-synthetic passkey operations, and final retirement review remain incomplete.

## 2026-06-03: Temporal Workflow Trace Log Correlation Slice

Changes completed:

- Added `TemporalWorkflowTraceLogger` to wrap the Spring-managed Temporal workflow implementation with replay-guarded signal/completion trace logging.
- Updated `BankingCaseTemporalWorker` to register the workflow through a factory so the worker can use the tracing wrapper.
- Added `TemporalWorkflowTraceLogIntegrationTest` to prove workflow signal and completion logs carry trace/span IDs, workflow metadata, case type, control effect, and `syntheticOnly=true`.
- Added `docs/test-evidence/temporal-workflow-trace-log-correlation.md`.
- Updated node-retirement, parity, QA recommendation, gap, workflow, drill, and regulatory mapping docs so complaint-answer workflow trace/log correlation is no longer treated as missing.

Verification:

- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.observability.TemporalWorkflowTraceLogIntegrationTest` passed.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar` passed.
- Initial sandboxed `env COMPOSE_PROJECT_NAME=banking-lab-workflow-trace-smoke ... docker compose --profile platform up -d --build postgres temporal tempo core-banking-temporal-worker` failed because Docker socket access was denied; the same command passed under the approved execution path.
- `env COMPOSE_PROJECT_NAME=banking-lab-workflow-trace-smoke ... docker compose --profile platform up -d --build postgres temporal tempo core-banking-temporal-worker` passed with tracing enabled and task queue `banking-case-workflows-trace-smoke`.
- `env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17237 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-trace-smoke scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal worker executes approval workflow from server task queue'` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-workflow-trace-smoke docker compose logs --no-color core-banking-temporal-worker | rg 'observability\\.workflow|banking-case-live-smoke|COMPLAINT-LIVE-TEMPORAL-SMOKE'` found signal trace ID `2ab8abd5494470b58a407f3b3f33c3b8` and completion trace ID `4d49bc27a89ee990416a549ddfa845f1`.
- `curl --retry 12 --retry-delay 2 --retry-all-errors -fsS http://127.0.0.1:13202/api/traces/4d49bc27a89ee990416a549ddfa845f1` returned `200` with `service.name=banking-lab-core-banking-temporal-worker`, `synthetic.only=true`, span name `banking-lab.temporal.workflow.completed`, and `banking.control_effect=CUSTOMER_ANSWER_VISIBLE`.
- `curl --retry 12 --retry-delay 2 --retry-all-errors -fsS http://127.0.0.1:13202/api/traces/2ab8abd5494470b58a407f3b3f33c3b8` returned `200` with `service.name=banking-lab-core-banking-temporal-worker`, span name `banking-lab.temporal.workflow.signal`, and `banking.control_effect=SIGNAL_ACCEPTED`.
- `env COMPOSE_PROJECT_NAME=banking-lab-workflow-trace-smoke docker compose --profile platform down -v` removed the temporary stack.

Remaining blockers:

- Node retirement remains blocked.
- Temporal complaint-answer workflow signal/completion trace/log correlation is now proven for the target worker.
- Full Node reference parity, broader workflow/failure-state channel parity, broader workflow/container failure drills beyond the complaint-answer restart path, workflow trace/log coverage for remaining workflow types/transitions, non-synthetic passkey operations, and final retirement review remain incomplete.

## 2026-06-03: Temporal Workflow Trace Log All-Case Expansion

Changes completed:

- Extended `TemporalWorkflowTraceLogger` so signal trace logs/spans include a workflow business reference ID, not only the workflow ID.
- Expanded `TemporalWorkflowTraceLogIntegrationTest` to cover signal and completion trace/log correlation for `COMPLAINT_ANSWER`, `FDS_RELEASE`, `FDS_BLOCK`, `AML_CLOSURE`, `RECONCILIATION_ADJUSTMENT`, `ACCOUNT_HOLD`, and `ACCOUNT_RELEASE`.
- Added integration coverage for AML closure rejection trace/log correlation with `finalStatus=REJECTED` and `controlEffect=NO_EFFECT`.
- Extended `LiveTemporalWorkerSmokeIntegrationTest` with an env-gated live Compose worker smoke that executes all current approval workflow case types from the server task queue.
- Updated node-retirement, parity, QA recommendation, gap, workflow, drill, and regulatory mapping docs so all-current-case Temporal signal/completion trace/log correlation is no longer treated as missing.

Verification:

- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.observability.TemporalWorkflowTraceLogIntegrationTest` passed.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest --tests lab.banking.core.observability.TemporalWorkflowTraceLogIntegrationTest` passed; live Temporal tests skipped without the live env and the observability integration test ran.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-workflow-all-trace-smoke ... docker compose --profile platform up -d --build postgres temporal tempo core-banking-temporal-worker` passed with tracing enabled and task queue `banking-case-workflows-all-trace-smoke`.
- `env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17238 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-all-trace-smoke scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal worker executes all approval workflow case types from server task queue'` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-workflow-all-trace-smoke docker compose logs --no-color core-banking-temporal-worker | rg 'observability\\.workflow|LIVE-TRACE'` found signal and completion trace IDs for all seven case types.
- `curl -fsS http://127.0.0.1:13203/api/traces/{traceId}` returned `200` for all seven signal traces and all seven completion traces with `service.name=banking-lab-core-banking-temporal-worker`, `synthetic.only=true`, workflow metadata, case type, business reference ID, and control effect attributes.
- A non-escalated compact curl loop against the forwarded Tempo port failed with `curl: (7) Failed to connect to 127.0.0.1 port 13203`; direct approved `curl` commands against the same local Tempo endpoint passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-workflow-all-trace-smoke docker compose --profile platform down -v` removed the temporary stack.

Remaining blockers:

- Node retirement remains blocked.
- Temporal signal/completion trace/log correlation is now proven for all current workflow case types.
- Full Node reference parity, broader workflow/failure-state channel parity, broader workflow/container failure drills beyond the complaint-answer restart path, live workflow rejection/failure-transition trace coverage, non-synthetic passkey operations, and final retirement review remain incomplete.

## 2026-06-03: Temporal Rejection And Failure Trace Log Slice

Changes completed:

- Updated `TemporalWorkflowTraceLogger` so failed workflow spans include `banking.error_type`; Temporal `ApplicationFailure.type` is preserved instead of being flattened to the generic exception class.
- Expanded `TemporalWorkflowTraceLogIntegrationTest` to prove FDS self-approval failure logs carry trace/span IDs, `finalStatus=FAILED`, `controlEffect=NO_EFFECT`, and `errorType=MAKER_CHECKER_SELF_APPROVAL_REJECTED`.
- Extended `LiveTemporalWorkerSmokeIntegrationTest` with an env-gated live worker smoke for AML rejection and FDS self-approval failure transitions.
- Updated node-retirement, parity, QA recommendation, gap, workflow, drill, and regulatory mapping docs so live Temporal rejection/failure transition trace/log correlation is no longer treated as missing.

Verification:

- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest --tests lab.banking.core.observability.TemporalWorkflowTraceLogIntegrationTest` passed.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-workflow-transition-trace-smoke ... docker compose --profile platform up -d --build postgres temporal tempo core-banking-temporal-worker` passed with tracing enabled and task queue `banking-case-workflows-transition-trace-smoke`.
- `env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17239 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-transition-trace-smoke scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal worker executes rejection and failure transitions from server task queue'` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-workflow-transition-trace-smoke docker compose logs --no-color core-banking-temporal-worker | rg 'observability\\.workflow|LIVE-TRACE-AML_REJECT-001|LIVE-TRACE-FDS_SELF_APPROVAL-001'` found rejection signal trace `cc0bb522adfe3e1c7dc57437d9903b0d`, rejection completion trace `55c37e0a68cc077e005d2b945446349c`, self-approval signal trace `1371b87e45d8c9acea56e32aba64d610`, and failed workflow trace `9ba170bdb6881d0246477a0c603fd8cf`.
- `curl --retry 12 --retry-delay 2 --retry-all-errors -v -fsS http://127.0.0.1:13204/ready` returned `ready` after Tempo warm-up.
- `curl -fsS http://127.0.0.1:13204/api/traces/{traceId}` returned `200` for all four transition traces with `service.name=banking-lab-core-banking-temporal-worker`, `synthetic.only=true`, workflow metadata, business reference ID, case type, control effect, and `banking.error_type`; the failed span carried `banking.error_type=MAKER_CHECKER_SELF_APPROVAL_REJECTED`.
- `env COMPOSE_PROJECT_NAME=banking-lab-workflow-transition-trace-smoke docker compose --profile platform down -v` removed the temporary stack.

Remaining blockers:

- Node retirement remains blocked.
- Live Temporal rejection/self-approval failure trace/log correlation is now proven.
- Full Node reference parity, remaining workflow/failure-state channel parity, broader workflow/container failure drills beyond the complaint-answer restart path, Loki/alert/dashboard observability validation, non-synthetic passkey operations, and final retirement review remain incomplete.

## 2026-06-03: Loki Workflow Failure Alert Dashboard Slice

Changes completed:

- Added Promtail to the Docker Compose `platform` profile to ship Docker logs from `banking-lab*` Compose projects into Loki.
- Added Loki ruler configuration and the `TemporalWorkflowFailed` alert rule for `observability.workflow event=failed` logs from `core-banking-temporal-worker`.
- Added Grafana provisioning for Prometheus, Loki, and Tempo datasources plus a `Temporal Workflow Observability` dashboard with a workflow-failure Loki logs panel.
- Updated node-retirement, QA recommendation, gap, parity, failure-drill, and regulatory mapping docs so Loki ingestion, local alert evaluation, and dashboard provisioning are no longer treated as missing.

Verification:

- `docker compose --profile platform config` passed after adding Promtail, Loki rules, and Grafana provisioning mounts.
- `node -e "JSON.parse(require('fs').readFileSync('infra/observability/grafana/dashboards/temporal-workflow-observability.json','utf8')); console.log('grafana dashboard json valid')"` passed.
- `ruby -e "require 'yaml'; %w[infra/observability/promtail/promtail.yml infra/observability/loki/loki.yml infra/observability/loki/rules/fake/temporal-workflow-alerts.yml infra/observability/grafana/provisioning/datasources/datasources.yml infra/observability/grafana/provisioning/dashboards/dashboards.yml infra/observability/prometheus/prometheus.yml].each { |path| YAML.load_file(path) }; puts 'yaml valid'"` passed.
- Initial live Grafana provisioning rejected dashboard UID `banking-lab-temporal-workflow-observability` because it exceeded Grafana's 40-character UID limit; shortening it to `temporal-workflow-observability` fixed provisioning.
- `env COMPOSE_PROJECT_NAME=banking-lab-loki-workflow-smoke ... docker compose --profile platform up -d --build postgres temporal tempo loki promtail grafana core-banking-temporal-worker` passed with task queue `banking-case-workflows-loki-smoke`.
- `curl --retry 12 --retry-delay 5 --retry-all-errors -fsS http://127.0.0.1:13101/ready`, `curl --retry 12 --retry-delay 5 --retry-all-errors -fsS http://127.0.0.1:13002/api/health`, and `curl --retry 12 --retry-delay 5 --retry-all-errors -fsS http://127.0.0.1:13205/ready` passed after Loki/Tempo warm-up `503` responses.
- `curl -fsS -u admin:admin http://127.0.0.1:13002/api/datasources/uid/Loki` returned datasource UID `Loki`.
- `curl -fsS -u admin:admin http://127.0.0.1:13002/api/dashboards/uid/temporal-workflow-observability` returned the provisioned `Temporal Workflow Observability` dashboard with a Loki panel query for `observability.workflow event=failed`.
- `curl -fsS http://127.0.0.1:13101/loki/api/v1/rules` returned alert `TemporalWorkflowFailed`.
- `env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17240 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-loki-smoke scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal worker executes rejection and failure transitions from server task queue'` passed.
- Loki `query_range` returned the failed workflow log for `businessReferenceId=LIVE-TRACE-FDS_SELF_APPROVAL-001`, `finalStatus=FAILED`, `errorType=MAKER_CHECKER_SELF_APPROVAL_REJECTED`, `traceId=07794e172089f781cc02721c2d55fa93`, and `syntheticOnly=true`.
- Loki `count_over_time({compose_service="core-banking-temporal-worker"} |= "observability.workflow" |= "event=failed" [5m]) > 0` returned value `1`.
- `curl --retry 10 --retry-delay 5 --retry-all-errors -fsS http://127.0.0.1:13101/prometheus/api/v1/alerts` returned `TemporalWorkflowFailed` in `firing` state with `severity=warning`, `control=temporal-workflow`, and `synthetic_only=true`.
- `env COMPOSE_PROJECT_NAME=banking-lab-loki-workflow-smoke docker compose --profile platform down -v` removed the temporary stack.

Remaining blockers:

- Node retirement remains blocked.
- Loki ingestion, local alert evaluation, and Grafana dashboard validation are now proven for the Temporal workflow self-approval failure path.
- Full Node reference parity, remaining workflow/failure-state channel parity, broader workflow/container failure drills beyond the complaint-answer restart path, non-synthetic passkey operations, and final retirement review remain incomplete.

## 2026-06-03: FDS Temporal Container Worker Restart Drill Slice

Changes completed:

- Extended `LiveTemporalWorkerSmokeIntegrationTest` so the Compose worker container restart drill is shared across workflow case types.
- Added the live FDS release restart test for `core-banking-temporal-worker`.
- Preserved `BANKING_LAB_TRACING_ENABLED`, `BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED`, and `BANKING_LAB_OTLP_TRACES_ENDPOINT` on test-managed Compose worker restarts, alongside the task queue, so a restarted worker keeps the same isolated drill configuration.
- Updated Temporal restart, evidence gap, failure drill, parity, QA recommendation, workflow, regulatory, and node-retirement docs to record complaint-answer plus FDS release Compose restart coverage while keeping the retirement gate blocked.

Verification:

- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest'` passed without live Temporal env, proving compile and env-gated skip behavior.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-fds-container-drill BANKING_LAB_POSTGRES_PORT=15490 BANKING_LAB_TEMPORAL_PORT=17241 BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-fds-container-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform up -d --build postgres temporal core-banking-temporal-worker` passed.
- First FDS live drill passed, but the restarted worker emitted a Tempo host lookup error because the test-managed restart did not preserve disabled tracing export settings; the harness was fixed to preserve the tracing env values.
- `env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17241 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-fds-container-drill BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-fds-container-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live FDS release workflow survives Compose worker container restart before approval completion'` passed after the fix.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-fds-container-drill docker compose ps --all` showed PostgreSQL, Temporal, and the restarted worker container running after the drill.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-fds-container-drill docker compose logs --tail=260 core-banking-temporal-worker` showed the restarted worker polling `banking-case-workflows-fds-container-drill`, the FDS approval signal, and completion with `caseType=FDS_RELEASE`, `businessReferenceId=FDS-LIVE-TEMPORAL-CONTAINER-RESTART`, `finalStatus=COMPLETED`, `controlEffect=LEDGER_TRANSFER_HANDOFF`, and `syntheticOnly=true`.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-fds-container-drill docker compose --profile platform down -v` removed the temporary stack.

Remaining blockers:

- Node retirement remains blocked.
- Live Compose worker container restart continuity is now proven for the synthetic complaint-answer and FDS release workflow contracts.
- Full Node reference parity, remaining workflow/failure-state channel parity, broader workflow/container failure drills beyond the complaint-answer and FDS release restart paths, non-synthetic passkey operations, and final retirement review remain incomplete.

## 2026-06-03: AML And Reconciliation Temporal Container Restart Drill Slice

Changes completed:

- Extended `LiveTemporalWorkerSmokeIntegrationTest` with live Compose worker container restart drills for `AML_CLOSURE` and `RECONCILIATION_ADJUSTMENT`.
- Reused the existing restart harness so both new paths kill `core-banking-temporal-worker` at `WAITING_APPROVAL`, accept a checker approval signal while the worker container is down, restart the worker on the same task queue, and verify completion from Temporal history.
- Updated Temporal restart, evidence gap, failure drill, parity, QA recommendation, workflow, regulatory, OpenTelemetry/observability, and node-retirement docs to record the then-current complaint-answer, FDS release, AML closure, and reconciliation adjustment Compose restart coverage while keeping the retirement gate blocked.

Verification:

- Initial sandboxed `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest'` failed before test execution because Gradle could not create its local file-lock socket; the same command passed under the approved execution path.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest'` passed without live Temporal env, proving compile and env-gated skip behavior.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-ops-container-drill BANKING_LAB_POSTGRES_PORT=15491 BANKING_LAB_TEMPORAL_PORT=17242 BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-ops-container-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform up -d --build postgres temporal core-banking-temporal-worker` passed.
- `env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17242 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-ops-container-drill BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-ops-container-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live AML closure workflow survives Compose worker container restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live reconciliation adjustment workflow survives Compose worker container restart before approval completion'` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-ops-container-drill docker compose ps --all` showed PostgreSQL, Temporal, and the restarted worker container running after the drill.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-ops-container-drill docker compose logs --tail=320 core-banking-temporal-worker` showed the restarted worker polling `banking-case-workflows-ops-container-drill`, `RECONCILIATION_ADJUSTMENT` completion with `businessReferenceId=REC-LIVE-TEMPORAL-CONTAINER-RESTART`, `finalStatus=COMPLETED`, `controlEffect=BALANCED_ADJUSTMENT_HANDOFF`, and `syntheticOnly=true`, plus `AML_CLOSURE` completion with `businessReferenceId=AML-LIVE-TEMPORAL-CONTAINER-RESTART`, `finalStatus=COMPLETED`, `controlEffect=STR_SIMULATION_CLOSURE`, and `syntheticOnly=true`.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-ops-container-drill docker compose --profile platform down -v` removed the temporary stack.

Remaining blockers:

- Node retirement remains blocked.
- Live Compose worker container restart continuity was proven for the synthetic complaint-answer, FDS release, AML closure, and reconciliation adjustment workflow contracts in this slice. The later FDS block/account hold/account release slice supersedes this remaining-restart gap.
- Full Node reference parity, remaining workflow/failure-state channel parity, host/database crash shapes and broader process-failure variants, non-synthetic passkey operations, and final retirement review remain incomplete.

## 2026-06-03: FDS Block And Account Hold Release Temporal Container Restart Drill Slice

Changes completed:

- Extended `LiveTemporalWorkerSmokeIntegrationTest` with live Compose worker container restart drills for `FDS_BLOCK`, `ACCOUNT_HOLD`, and `ACCOUNT_RELEASE`.
- Reused the existing restart harness so all three paths kill `core-banking-temporal-worker` at `WAITING_APPROVAL`, accept a checker approval signal while the worker container is down, restart the worker on the same task queue, and verify completion from Temporal history.
- Updated Temporal restart, evidence gap, failure drill, parity, QA recommendation, workflow, regulatory, OpenTelemetry/observability, and node-retirement docs to record live Compose restart coverage for all current Temporal case types while keeping the retirement gate blocked.

Verification:

- Initial sandboxed `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest'` failed before test execution because Gradle could not create its local file-lock socket; the same command passed under the approved execution path.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest'` passed without live Temporal env, proving compile and env-gated skip behavior.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-hold-container-drill BANKING_LAB_POSTGRES_PORT=15492 BANKING_LAB_TEMPORAL_PORT=17243 BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-hold-container-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform up -d --build postgres temporal core-banking-temporal-worker` passed.
- `env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17243 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-hold-container-drill BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-hold-container-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live FDS block workflow survives Compose worker container restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live account hold workflow survives Compose worker container restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live account release workflow survives Compose worker container restart before approval completion'` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-hold-container-drill docker compose ps --all` showed PostgreSQL, Temporal, and the restarted worker container running after the drill on task queue `banking-case-workflows-hold-container-drill`.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-hold-container-drill docker compose logs --tail=420 core-banking-temporal-worker` showed the restarted worker polling `banking-case-workflows-hold-container-drill`, `ACCOUNT_HOLD` completion with `businessReferenceId=HOLD-LIVE-TEMPORAL-CONTAINER-RESTART`, `controlEffect=AVAILABLE_BALANCE_HOLD`, and `syntheticOnly=true`, `ACCOUNT_RELEASE` completion with `businessReferenceId=RELEASE-LIVE-TEMPORAL-CONTAINER-RESTART`, `controlEffect=HOLD_RELEASE_HANDOFF`, and `syntheticOnly=true`, plus `FDS_BLOCK` completion with `businessReferenceId=FDS-BLOCK-LIVE-TEMPORAL-CONTAINER-RESTART`, `controlEffect=NO_LEDGER_POSTING`, and `syntheticOnly=true`.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-hold-container-drill docker compose --profile platform down -v` removed the temporary stack.

Remaining blockers:

- Node retirement remains blocked.
- Live Compose worker container restart continuity is now proven for all current synthetic Temporal workflow contracts: complaint answer, FDS release, FDS block, AML closure, reconciliation adjustment, account hold, and account release.
- Full Node reference parity, remaining workflow/failure-state channel parity, host/database crash shapes and broader process-failure variants, non-synthetic passkey operations, and final retirement review remain incomplete.

## 2026-06-03: Temporal Server Container Restart Drill Slice

Changes completed:

- Extended `LiveTemporalWorkerSmokeIntegrationTest` with a representative live Compose `temporal` server container restart drill for `COMPLAINT_ANSWER`.
- Added a Temporal health wait that requires the restarted server to return `SERVING` before checker approval is sent.
- Preserved `BANKING_LAB_POSTGRES_PORT` and `BANKING_LAB_TEMPORAL_PORT` through test-managed Compose restarts so isolated drill stacks keep their assigned host ports.
- Added `docs/test-evidence/temporal-server-restart-drill.md` and updated failure-drill, evidence-gap, workflow, parity, regulatory, observability, QA recommendation, and node-retirement docs while keeping the retirement gate blocked.

Verification:

- Initial sandboxed `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest'` failed before test execution because Gradle could not create its local file-lock socket; the same command passed under the approved execution path.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest'` passed without live Temporal env, proving compile and env-gated skip behavior.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-server-restart-drill BANKING_LAB_POSTGRES_PORT=15493 BANKING_LAB_TEMPORAL_PORT=17244 BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-server-restart-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform up -d --build postgres temporal core-banking-temporal-worker` passed.
- The first live server restart attempt failed because the test-managed `docker compose up -d --no-deps temporal` did not preserve `BANKING_LAB_TEMPORAL_PORT`; the restarted server exposed default host port `7233` instead of isolated port `17244`. The harness was fixed to preserve `BANKING_LAB_TEMPORAL_PORT` and `BANKING_LAB_POSTGRES_PORT`.
- `env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17244 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-server-restart-drill BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-server-restart-drill BANKING_LAB_POSTGRES_PORT=15493 BANKING_LAB_TEMPORAL_PORT=17244 BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal workflow survives Compose Temporal server restart before approval completion'` passed after the fix.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-server-restart-drill docker compose ps --all` showed PostgreSQL healthy, the worker running, and the restarted Temporal server exposed on `0.0.0.0:17244->7233`.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-server-restart-drill docker compose logs --tail=260 core-banking-temporal-worker` showed transient `UNAVAILABLE` poller failures while Temporal was down, then approval signal and completion with `businessReferenceId=COMPLAINT-LIVE-TEMPORAL-SERVER-RESTART`, `finalStatus=COMPLETED`, `controlEffect=CUSTOMER_ANSWER_VISIBLE`, and `syntheticOnly=true`.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-server-restart-drill docker compose logs --tail=220 temporal` showed Temporal restarting, registering the default namespace, reloading task-queue state, and serving the isolated task queue.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-server-restart-drill docker compose --profile platform down -v` removed the temporary stack.

Remaining blockers:

- Node retirement remains blocked.
- Live Compose worker container restart continuity is proven for all current synthetic Temporal workflow contracts, and a representative Temporal server process restart is proven for complaint answer while PostgreSQL remains healthy.
- Full Node reference parity, remaining workflow/failure-state channel parity, host/database crash shapes, broader process-failure variants, non-synthetic passkey operations, and final retirement review remain incomplete.

## 2026-06-03: Temporal PostgreSQL Restart Drill Slice

Changes completed:

- Extended `LiveTemporalWorkerSmokeIntegrationTest` with a representative live Compose `postgres` crash/restart drill for `COMPLAINT_ANSWER`.
- Added `waitForPostgresService`, which verifies PostgreSQL recovery through `docker compose exec -T postgres pg_isready -U banking_lab -d banking_lab` before Temporal health and workflow status are queried.
- Refactored the Compose command helper so timeout handling happens before process output is read.
- Added `docs/test-evidence/temporal-postgres-restart-drill.md` and updated failure-drill, evidence-gap, workflow, parity, regulatory, observability, QA recommendation, and node-retirement docs while keeping the retirement gate blocked.

Verification:

- Initial sandboxed `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest'` failed before test execution because Gradle could not create its local file-lock socket; the same command passed under the approved execution path.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest'` passed without live Temporal env, proving compile and env-gated skip behavior.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-postgres-restart-drill BANKING_LAB_POSTGRES_PORT=15494 BANKING_LAB_TEMPORAL_PORT=17245 BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-postgres-restart-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform up -d --build postgres temporal core-banking-temporal-worker` passed.
- `env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17245 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-postgres-restart-drill BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-postgres-restart-drill BANKING_LAB_POSTGRES_PORT=15494 BANKING_LAB_TEMPORAL_PORT=17245 BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal workflow survives Compose PostgreSQL restart before approval completion'` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-postgres-restart-drill docker compose ps --all` showed PostgreSQL healthy on `0.0.0.0:15494->5432`, Temporal running on `0.0.0.0:17245->7233`, and the worker running after the drill.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-postgres-restart-drill docker compose logs --no-color postgres | rg 'unexpected postmaster exit|database system was interrupted|automatic recovery|database system is ready|Skipping initialization|redo done'` showed the forced postmaster exit, existing data-directory reuse, automatic recovery, redo completion, and return to accepting connections.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-postgres-restart-drill docker compose logs --no-color temporal | rg 'no usable database connection|database connection lost'` showed transient Temporal persistence errors while PostgreSQL was down or recovering.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-postgres-restart-drill docker compose logs --no-color core-banking-temporal-worker | rg 'POSTGRES-RESTART|observability.workflow event=(signal|completed)|banking-case-workflows-postgres-restart-drill|Started CoreBanking'` showed the approval signal and completion with `businessReferenceId=COMPLAINT-LIVE-TEMPORAL-POSTGRES-RESTART`, `finalStatus=COMPLETED`, `controlEffect=CUSTOMER_ANSWER_VISIBLE`, and `syntheticOnly=true`.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-postgres-restart-drill docker compose --profile platform down -v` removed the temporary stack.

Remaining blockers:

- Node retirement remains blocked.
- Live Compose worker container restart continuity is proven for all current synthetic Temporal workflow contracts, a representative Temporal server process restart is proven for complaint answer, and a representative PostgreSQL process restart with automatic recovery is proven for complaint answer.
- Full Node reference parity, remaining workflow/failure-state channel parity, host crash shapes, broader database/process-failure variants, non-synthetic passkey operations, and final retirement review remain incomplete.

## 2026-06-03: Audit Masking Target Parity Slice

Changes completed:

- Added `AuditMaskingParityIntegrationTest` to mirror `tests/audit.test.mjs` against the Kotlin/Spring target stack.
- Covered reason-required staff customer search before audit append, PostgreSQL append-only audit hash-chain continuity, default masked customer PII, and masked account numbers.
- Updated the parity scenario map, parity coverage matrix, evidence gap report, and retirement gate evidence list while keeping Node retirement blocked.

Verification:

- Initial sandboxed `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.audit.AuditMaskingParityIntegrationTest'` failed before test execution because Gradle could not create its local file-lock socket.
- The same command passed under the approved execution path.

Remaining blockers:

- `tests/audit.test.mjs` now has direct target-stack parity coverage, but Node retirement remains blocked.
- Full Node reference parity, remaining workflow/failure-state channel parity, host crash shapes, broader database/process-failure variants, non-synthetic passkey operations, and final retirement review remain incomplete.

## 2026-06-03: Target Screen Manifest Parity Slice

Changes completed:

- Added `packages/screen-engine/test/manifest-parity.test.ts` to mirror `tests/manifest.test.mjs` against the TypeScript target screen-engine package.
- Added `npm run test:screen-engine` and wired it into `npm run parity`.
- Covered six-channel manifest coverage, high-risk staff maker-checker metadata, staff PII masking/reason policy, and ledger/audit/outbox source-of-truth schema table declarations.
- Updated the parity scenario map, migration foundation evidence, parity coverage matrix, evidence gap report, QA recommendation, and retirement gate evidence list while keeping Node retirement blocked.

Verification:

- `npm run test:screen-engine` passed 4/4 target screen-engine manifest parity tests.

Remaining blockers:

- `tests/manifest.test.mjs` now has direct target-stack parity coverage, but Node retirement remains blocked.
- Full Node reference parity, remaining workflow/failure-state channel parity, host crash shapes, broader database/process-failure variants, non-synthetic passkey operations, and final retirement review remain incomplete.

## 2026-06-03: Staff Terminal Target Parity Slice

Changes completed:

- Strengthened `StaffAccessApiParityIntegrationTest` for the four `tests/staffTerminal.test.mjs` oracle scenarios.
- Added no-side-effect assertions for missing-reason staff detail, unauthorized PII unmask, account/transaction inquiry denial, missing-reason customer-change request, and maker self-approval rejection.
- Updated the parity scenario map, parity coverage matrix, evidence gap report, and Phase 3 staff terminal evidence while keeping Node retirement blocked.

Verification:

- Initial sandboxed `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.staff.StaffAccessApiParityIntegrationTest'` failed before test execution because Gradle could not create its local file-lock socket.
- The same command passed under the approved execution path.

Remaining blockers:

- `tests/staffTerminal.test.mjs` now has direct target-stack parity coverage, but Node retirement remains blocked.
- Full Node reference parity, remaining workflow/failure-state channel parity, host crash shapes, broader database/process-failure variants, non-synthetic passkey operations, and final retirement review remain incomplete.

## 2026-06-03: Maker-Checker Target Parity Slice

Changes completed:

- Strengthened `MakerCheckerParityTest`, `PersistentApprovalServiceIntegrationTest`, and `ApprovalApiParityIntegrationTest` for the two `tests/makerChecker.test.mjs` oracle scenarios.
- Added no-side-effect assertions for missing high-risk approval reason and maker self-approval rejection before valid checker approval.
- Updated the parity scenario map, parity coverage matrix, and evidence gap report while keeping Node retirement blocked.

Verification:

- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test --tests 'lab.banking.core.approval.MakerCheckerParityTest'` passed.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.approval.PersistentApprovalServiceIntegrationTest' --tests 'lab.banking.core.approval.ApprovalApiParityIntegrationTest'` passed.

Remaining blockers:

- `tests/makerChecker.test.mjs` now has direct target-stack parity coverage, but Node retirement remains blocked.
- Full Node reference parity, remaining workflow/failure-state channel parity, host crash shapes, broader database/process-failure variants, non-synthetic passkey operations, and final retirement review remain incomplete.

## 2026-06-03: Runtime API Target Parity Slice

Changes completed:

- Strengthened `LedgerRuntimeApiParityIntegrationTest` for the four `tests/runtime.test.mjs` oracle scenarios.
- Added Spring API coverage for `/health`, reason-required staff customer search, customer transfer idempotency, withdrawal plus reversal invariants, and duplicate-reversal structured errors.
- Enabled authorization in the runtime parity test and added PostgreSQL side-effect assertions for audit events, customer transfer results, ledger transactions, and projected balances.
- Updated the parity scenario map, parity coverage matrix, and evidence gap report while keeping Node retirement blocked.

Verification:

- Initial sandboxed `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.ledger.api.LedgerRuntimeApiParityIntegrationTest'` failed before test execution because Gradle could not create its local file-lock socket.
- The same command passed under the approved execution path.

Remaining blockers:

- `tests/runtime.test.mjs` now has direct target-stack parity coverage, but Node retirement remains blocked.
- Full Node reference parity, remaining workflow/failure-state channel parity, host crash shapes, broader database/process-failure variants, non-synthetic passkey operations, and final retirement review remain incomplete.

## 2026-06-03: Ledger Domain Target Parity Slice

Changes completed:

- Strengthened `LedgerInvariantsTest` for the five `tests/ledger.test.mjs` oracle scenarios.
- Added target coverage for balanced internal transfer postings, balance projection from postings, reversal restoring projected balances, unbalanced transaction rejection, and malformed posting rejection.
- Mapped durable idempotency replay to the existing PostgreSQL-backed `LedgerCommandServiceIntegrationTest`.
- Updated the parity scenario map, parity coverage matrix, and evidence gap report while keeping Node retirement blocked.

Verification:

- Initial sandboxed `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test --tests 'lab.banking.core.ledger.domain.LedgerInvariantsTest'` failed before test execution because Gradle could not create its local file-lock socket.
- The same unit test command passed under the approved execution path.
- Initial sandboxed `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.ledger.application.LedgerCommandServiceIntegrationTest'` failed before test execution for the same Gradle file-lock socket reason.
- The same integration test command passed under the approved execution path.

Remaining blockers:

- `tests/ledger.test.mjs` now has direct target-stack parity coverage, but Node retirement remains blocked.
- Full Node reference parity, remaining workflow/failure-state channel parity, host crash shapes, broader database/process-failure variants, non-synthetic passkey operations, and final retirement review remain incomplete.

## 2026-06-03: Ledger Core Command Target Parity Slice

Changes completed:

- Strengthened `LedgerCommandServiceIntegrationTest` for the seven `tests/ledgerCore.test.mjs` oracle scenarios.
- Added PostgreSQL side-effect assertions for failed withdrawal, duplicate reversal, and closed-day direct posting rejection.
- Changed durable idempotency replay coverage to use an internal transfer command so both accounts prove the same ledger source of truth is replayed once.
- Updated the parity scenario map, parity coverage matrix, and evidence gap report while keeping Node retirement blocked.

Verification:

- Initial sandboxed `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.ledger.application.LedgerCommandServiceIntegrationTest'` failed before test execution because Gradle could not create its local file-lock socket.
- The same integration test command passed under the approved execution path.
- `npm test` passed.
- `npm run node:retirement-gate` passed and kept the Node reference retirement gate blocked.
- Initial sandboxed `npm run parity` failed because the sandbox blocked local `127.0.0.1` listener creation for Node reference runtime tests.
- The same parity command passed under the approved execution path and regenerated the evidence pack with no content diff.

Remaining blockers:

- `tests/ledgerCore.test.mjs` now has direct target-stack parity coverage, but Node retirement remains blocked.
- Full Node reference parity, remaining workflow/failure-state channel parity, host crash shapes, broader database/process-failure variants, non-synthetic passkey operations, and final retirement review remain incomplete.

## 2026-06-03: Customer Web Target Parity Slice

Changes completed:

- Added customer self-service `ACCOUNT_VIEW` audit append to Spring customer account detail.
- Added `CustomerAccountApiParityIntegrationTest` for masked customer account detail, audit metadata, raw account-number exclusion from audit payload, and ownership denial before account-view audit.
- Mapped the five `tests/customerWeb.test.mjs` oracle scenarios to target Spring, Next.js, and screen-engine tests, while keeping the Node mock-login route as a legacy oracle only.
- Updated the parity scenario map, parity coverage matrix, customer-web architecture/evidence notes, and evidence gap report while keeping Node retirement blocked.

Verification:

- Initial sandboxed `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.customer.CustomerAccountApiParityIntegrationTest'` failed before test execution because Gradle could not create its local file-lock socket.
- The same customer account integration test command passed under the approved execution path.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.customer.CustomerAccountApiParityIntegrationTest' --tests 'lab.banking.core.customer.CustomerTransferApiParityIntegrationTest' --tests 'lab.banking.core.complaint.CustomerComplaintEntryApiParityIntegrationTest' --tests 'lab.banking.core.complaint.CustomerComplaintConfirmApiParityIntegrationTest'` passed under the approved execution path.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.security.SecurityAuthorizationIntegrationTest' --tests 'lab.banking.core.security.JwksAuthorizationIntegrationTest'` passed under the approved execution path.
- `npm test` passed.
- `npm run node:retirement-gate` passed and kept the Node reference retirement gate blocked.
- Initial sandboxed `npm run parity` failed because the sandbox blocked local `127.0.0.1` listener creation for Node reference runtime tests.
- The same parity command passed under the approved execution path and regenerated the evidence pack with no content diff.

Remaining blockers:

- `tests/customerWeb.test.mjs` now has direct target-stack parity coverage, but Node retirement remains blocked.
- Full Node reference parity, remaining workflow/failure-state channel parity, host crash shapes, broader database/process-failure variants, non-synthetic passkey operations, and final retirement review remain incomplete.

## 2026-06-03: Complaint Workflow Target Parity Slice

Changes completed:

- Added Spring customer complaint list API with customer ownership checks and self-service `COMPLAINT_VIEW` audit that omits complaint descriptions.
- Strengthened `CustomerComplaintEntryApiParityIntegrationTest` for shared customer/staff case visibility, SLA/timeline parity, and masked audit payload evidence.
- Added explicit screen-engine complaint manifest coverage for `CMP-201`, `CMP-101`, and `CMP-102`.
- Updated the parity scenario map, parity coverage matrix, phase-5 complaint docs, architecture notes, and evidence gap report while keeping Node retirement blocked.

Verification:

- Initial sandboxed `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.complaint.CustomerComplaintEntryApiParityIntegrationTest'` failed before test execution because Gradle could not create its local file-lock socket.
- The same customer complaint entry integration test command passed under the approved execution path.
- `npm run packages:typecheck` passed.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test --tests 'lab.banking.core.complaint.ComplaintWorkflowParityTest'` passed under the approved execution path.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.complaint.CustomerComplaintEntryApiParityIntegrationTest' --tests 'lab.banking.core.complaint.ComplaintCaseApiParityIntegrationTest' --tests 'lab.banking.core.complaint.CustomerComplaintConfirmApiParityIntegrationTest'` passed under the approved execution path.
- `npm run test:screen-engine` passed.
- `npm test` passed.
- `npm run node:retirement-gate` passed and kept the Node reference retirement gate blocked.
- Initial sandboxed `npm run parity` failed because the sandbox blocked local `127.0.0.1` listener creation for Node reference runtime tests.
- The same parity command passed under the approved execution path and regenerated the evidence pack with no content diff.

Remaining blockers:

- `tests/complaintWorkflow.test.mjs` now has direct target-stack parity coverage, but Node retirement remains blocked.
- The remaining direct in-progress Node suite is `tests/fdsAmlReconciliation.test.mjs`; host crash shapes, broader database/process-failure variants, non-synthetic passkey operations, and final retirement review remain incomplete.

## 2026-06-03: FDS AML Reconciliation Target Parity Slice

Changes completed:

- Added Spring FDS assignment API so customer-created held FDS cases can move into `INVESTIGATING` before release/block requests.
- Added synthetic customer transfer risk flags for `newDevice` and `firstTimeBeneficiary`, with FDS alert and idempotency command-hash coverage.
- Strengthened `FdsCaseApiParityIntegrationTest` to create FDS cases through `/api/customer/transfers`, assign them, prove no ledger posting before approval, reject self-approval, post exactly one release transfer after checker approval, and keep block decisions posting-free.
- Updated the phase 6 evidence generator to validate the structured `LEDGER_CLOSED_DAY_IMMUTABLE` error contract for closed-day posting rejection.
- Updated the parity scenario map, node retirement gate metadata, parity coverage matrix, FDS/AML/reconciliation architecture/evidence docs, QA recommendation, and evidence gap report while keeping Node retirement blocked.

Verification:

- Initial sandboxed `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.fds.FdsCaseApiParityIntegrationTest'` failed before test execution because Gradle could not create its local file-lock socket.
- The same FDS integration test command passed under the approved execution path.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test --tests 'lab.banking.core.fds.FdsAmlReconciliationWorkflowParityTest'` passed under the approved execution path.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.fds.FdsCaseApiParityIntegrationTest' --tests 'lab.banking.core.aml.AmlCaseApiParityIntegrationTest' --tests 'lab.banking.core.reconciliation.ReconciliationOpsApiParityIntegrationTest'` passed under the approved execution path.
- `npm run packages:typecheck` passed.
- `npm run evidence:phase6` passed and regenerated `docs/test-evidence/generated/phase-6-fds-aml-reconciliation.json` with all checks passing.

Remaining blockers:

- All direct Node reference suites are now target-backed, but Node retirement remains blocked.
- Remaining blockers are broader workflow/failure-state channel parity, host crash shapes, broader database/process-failure variants, non-synthetic passkey operations, and final retirement review.

## 2026-06-03: API-backed Channel Parity Gate Closure

Changes completed:

- Moved staff privileged unmask onto the bounded SERIALIZABLE staff-access retry path so parallel API-backed channel smoke does not leak transient audit hash-chain conflicts as HTTP 500s.
- Re-ran the targeted staff-terminal privileged unmask smoke against a fresh Spring/PostgreSQL Compose stack.
- Re-ran the full API-backed six-channel Playwright suite against the same fresh Spring API.
- Updated the Node retirement gate, QA recommendation, API-backed channel evidence, evidence gap report, parity coverage matrix, and frontend channel notes to close the `api-backed-channel-parity` gate while keeping Node retirement blocked.
- Added a QA evidence regression assertion so the channel gate cannot drift back to `in-progress` without a test failure.

Verification:

- Initial sandboxed `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18132 npm run test:e2e` failed before test execution because the sandbox blocked local Next.js listener creation on port `3001`.
- The approved full API-backed Playwright run initially exposed a staff-terminal privileged unmask HTTP 500 under parallel channel load, while a direct branch denial plus manager unmask API call still returned expected 403/200 responses.
- `scripts/run-core-banking-tests.sh :services:core-banking:bootJar` passed under the approved execution path after the retry fix.
- `env COMPOSE_PROJECT_NAME=banking-lab-channel-gate-smoke BANKING_LAB_POSTGRES_PORT=15480 BANKING_LAB_CORE_BANKING_PORT=18132 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres core-banking` passed with a fresh seed DB.
- `curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18132/health` returned `status=ok`, `syntheticOnly=true`, and `auditHashChainValid=true`.
- `env CI=1 BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18132 npx playwright test apps/staff-terminal/e2e/staff-terminal-parity.spec.ts -g "privileged unmask"` passed 1 Chromium Playwright test.
- `env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18132 npm run test:e2e` passed 35 tests with 7 Keycloak-dependent tests skipped because no Keycloak URL was configured for this simulator-token run.
- Post-smoke `curl -fsS http://127.0.0.1:18132/health` returned `auditHashChainValid=true`.

Remaining blockers:

- The current API-backed channel parity gate is now closed, but Node retirement remains blocked.
- Remaining blockers are host crash shapes, broader database/process-failure variants, non-synthetic passkey operations, evidence-refresh completion, and final retirement review.

## 2026-06-03: Structured Error Real Route Coverage Refresh

Changes completed:

- Replaced the structured-error contract integration test's non-ledger probe assertions with real Spring routes for reason-required staff detail, unauthorized staff unmask, maker-checker self-approval, complaint request validation, staff customer not-found, and complaint workflow state violation.
- Kept `INTERNAL_RUNTIME_ERROR` on the `api-error-parity` probe only, because exposing a production-like endpoint that intentionally throws runtime errors would be unsafe.
- Fixed staff customer lookup so missing synthetic customers map to structured `RESOURCE_NOT_FOUND` instead of leaking `EmptyResultDataAccessException` as HTTP 500.
- Updated the structured-error evidence report, QA recommendation, evidence gap report, and parity coverage matrix to reflect real route coverage for nine required error families.

Verification:

- Initial approved `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.api.StructuredApiErrorContractIntegrationTest` failed because the new real not-found route exposed a staff customer lookup 500.
- The same focused integration test failed a second time because the synthetic answered-complaint fixture used incomplete `answer_json`, which correctly avoided weakening production code with a fake runtime route.
- After mapping staff missing-customer lookup to `WorkflowErrors.notFound` and narrowing the answered-complaint fixture, the same focused integration test passed under the approved execution path.

Remaining blockers:

- Structured error response-shape coverage now uses real Spring routes for all required families except profile-only `INTERNAL_RUNTIME_ERROR`.
- Node retirement remains blocked by host crash shapes, broader database/process-failure variants, non-synthetic passkey operations, evidence-refresh completion, and final retirement review.

## 2026-06-03: All-Current-Case Temporal Server And PostgreSQL Restart Drills

Changes completed:

- Extended `LiveTemporalWorkerSmokeIntegrationTest` with Compose `temporal` server restart drills for `FDS_RELEASE`, `FDS_BLOCK`, `AML_CLOSURE`, `RECONCILIATION_ADJUSTMENT`, `ACCOUNT_HOLD`, and `ACCOUNT_RELEASE`, matching the existing complaint-answer drill.
- Extended the same test class with Compose `postgres` restart drills for the same six additional current workflow case types.
- Updated Temporal server/PostgreSQL restart evidence, failure drill notes, evidence gap report, parity matrix, QA recommendation, and node-retirement gate wording to record all-current-case server/database restart coverage while keeping the retirement gate blocked.

Verification:

- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest` passed without live Temporal env, proving compile and env-gated skip behavior.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-broader-restart-drill BANKING_LAB_POSTGRES_PORT=15495 BANKING_LAB_TEMPORAL_PORT=17246 BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-broader-restart-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform up -d --build postgres temporal core-banking-temporal-worker` passed.
- `env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17246 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-broader-restart-drill BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-broader-restart-drill BANKING_LAB_POSTGRES_PORT=15495 BANKING_LAB_TEMPORAL_PORT=17246 BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal workflow survives Compose Temporal server restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live FDS release workflow survives Compose Temporal server restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live FDS block workflow survives Compose Temporal server restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live AML closure workflow survives Compose Temporal server restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live reconciliation adjustment workflow survives Compose Temporal server restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live account hold workflow survives Compose Temporal server restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live account release workflow survives Compose Temporal server restart before approval completion'` passed.
- `env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17246 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-broader-restart-drill BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-broader-restart-drill BANKING_LAB_POSTGRES_PORT=15495 BANKING_LAB_TEMPORAL_PORT=17246 BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal workflow survives Compose PostgreSQL restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live FDS release workflow survives Compose PostgreSQL restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live FDS block workflow survives Compose PostgreSQL restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live AML closure workflow survives Compose PostgreSQL restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live reconciliation adjustment workflow survives Compose PostgreSQL restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live account hold workflow survives Compose PostgreSQL restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live account release workflow survives Compose PostgreSQL restart before approval completion'` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-broader-restart-drill docker compose ps --all` showed PostgreSQL healthy on `0.0.0.0:15495->5432`, Temporal running on `0.0.0.0:17246->7233`, and the worker running after both drill batches.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-broader-restart-drill docker compose logs --no-color --tail=260 core-banking-temporal-worker` showed transient Temporal poller failures during server restarts, PostgreSQL connection validation failures during database restarts, and successful `observability.workflow event=completed` logs for server/postgres restart business references across the current case types.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-broader-restart-drill docker compose logs --no-color --tail=180 postgres` showed forced postmaster exits, existing data-directory reuse, automatic recovery, redo completion, and return to accepting connections during the PostgreSQL restart batch.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-broader-restart-drill docker compose --profile platform down -v` removed the temporary stack and volume.

Remaining blockers:

- Node retirement remains blocked.
- Live Compose Temporal server and PostgreSQL restart continuity is now proven for all current synthetic Temporal workflow contracts.
- Host crash shapes, API/outbox deployed process-failure variants, non-synthetic passkey operations, evidence-refresh completion, and final retirement review remain incomplete.

## 2026-06-03: Live Compose Outbox Worker Restart-Before-Publish Drill

Changes completed:

- Added `LiveOutboxWorkerSmokeIntegrationTest` as an env-gated live Compose drill for the deployed `core-banking-outbox-worker`.
- Split Redpanda Compose listeners so Docker-internal clients use `redpanda:9092` while host-side drill clients use `127.0.0.1:${BANKING_LAB_REDPANDA_PORT}`.
- Updated outbox failure drill evidence, the evidence gap report, parity matrix, QA recommendation, observability note, and node-retirement gate wording.

Verification:

- `docker compose --profile platform config` passed and rendered Redpanda internal/external listeners plus the outbox worker service.
- Initial sandboxed `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.eventing.LiveOutboxWorkerSmokeIntegrationTest` failed before Gradle startup because the sandbox blocked Gradle's local file-lock socket.
- The approved env-gated compile/skip run of the same integration test passed.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-outbox-worker-drill BANKING_LAB_POSTGRES_PORT=15496 BANKING_LAB_REDPANDA_PORT=19096 BANKING_LAB_REDPANDA_ADMIN_PORT=19696 BANKING_LAB_OUTBOX_TOPIC=banking.lab.outbox-worker-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform up -d --build postgres redpanda core-banking-outbox-worker` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-outbox-worker-drill BANKING_LAB_POSTGRES_PORT=15496 BANKING_LAB_REDPANDA_PORT=19096 BANKING_LAB_REDPANDA_ADMIN_PORT=19696 BANKING_LAB_OUTBOX_TOPIC=banking.lab.outbox-worker-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform up -d --build --no-deps core-banking-outbox-worker` passed after regenerating the boot jar.
- `env BANKING_LAB_LIVE_OUTBOX_COMPOSE_PROJECT=banking-lab-outbox-worker-drill BANKING_LAB_POSTGRES_PORT=15496 BANKING_LAB_REDPANDA_PORT=19096 BANKING_LAB_REDPANDA_ADMIN_PORT=19696 BANKING_LAB_OUTBOX_TOPIC=banking.lab.outbox-worker-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.eventing.LiveOutboxWorkerSmokeIntegrationTest.live outbox worker publishes pending event after Compose worker container restart'` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-outbox-worker-drill BANKING_LAB_POSTGRES_PORT=15496 BANKING_LAB_REDPANDA_PORT=19096 BANKING_LAB_REDPANDA_ADMIN_PORT=19696 BANKING_LAB_OUTBOX_TOPIC=banking.lab.outbox-worker-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform down` removed the temporary containers and network.

Result:

- The live test killed the outbox worker, inserted a synthetic durable `PENDING` outbox row, restarted the worker, observed the row become `PUBLISHED` with `published_at`, and consumed the corresponding Redpanda record through the host-side listener.

Remaining blockers:

- Node retirement remains blocked.
- The deployed outbox worker restart-before-publish path is now proven.
- API process crash after durable ledger/outbox commit, deployed post-broker-ack outbox worker crash, outbox tracing, host crash shapes, non-synthetic passkey operations, evidence-refresh completion, and final retirement review remain incomplete.

## 2026-06-03: Live Compose Outbox Post-Ack Crash Replay Drill

Changes completed:

- Added synthetic-only post-broker-ack fault injection to `KafkaOutboxPublisher` through `OutboxWorkerProperties`, `OutboxEventModels`, and `application.yml`.
- Passed the fault injection settings into the Docker Compose `core-banking-outbox-worker` service without enabling any default crash behavior.
- Extended `LiveOutboxWorkerSmokeIntegrationTest` to halt the deployed worker after broker acknowledgement and before marking the outbox row `PUBLISHED`, then restart the worker and verify replay.
- Updated outbox failure drill evidence, the failure-drill gap notes, evidence gap report, parity matrix, QA recommendation, observability note, and node-retirement gate wording while keeping Node retirement blocked.

Verification:

- Initial sandboxed `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test --tests lab.banking.core.eventing.OutboxWorkerRunnerTest` failed before test execution because the sandbox blocked Gradle's local file-lock socket.
- The approved focused `OutboxWorkerRunnerTest` run passed and verified the configured fault event ID and exit code are propagated to the publisher config.
- Initial approved env-gated `LiveOutboxWorkerSmokeIntegrationTest` compile/skip run failed on a missing return in the new helper; after the helper fix, the same command passed.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-outbox-postack-drill BANKING_LAB_POSTGRES_PORT=15497 BANKING_LAB_REDPANDA_PORT=19097 BANKING_LAB_REDPANDA_ADMIN_PORT=19697 BANKING_LAB_OUTBOX_TOPIC=banking.lab.outbox-postack-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform up -d --build postgres redpanda core-banking-outbox-worker` passed.
- `env BANKING_LAB_LIVE_OUTBOX_COMPOSE_PROJECT=banking-lab-outbox-postack-drill BANKING_LAB_POSTGRES_PORT=15497 BANKING_LAB_REDPANDA_PORT=19097 BANKING_LAB_REDPANDA_ADMIN_PORT=19697 BANKING_LAB_OUTBOX_TOPIC=banking.lab.outbox-postack-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.eventing.LiveOutboxWorkerSmokeIntegrationTest` passed against the live Compose PostgreSQL and Redpanda stack.
- `env COMPOSE_PROJECT_NAME=banking-lab-outbox-postack-drill BANKING_LAB_POSTGRES_PORT=15497 BANKING_LAB_REDPANDA_PORT=19097 BANKING_LAB_REDPANDA_ADMIN_PORT=19697 BANKING_LAB_OUTBOX_TOPIC=banking.lab.outbox-postack-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform down` removed the temporary containers and network.

Result:

- The live post-ack drill inserted a synthetic durable `PENDING` outbox row, started `core-banking-outbox-worker` with `BANKING_LAB_OUTBOX_FAULT_CRASH_AFTER_ACK_EVENT_ID` set to that row, and observed the worker container exit with code `88` after broker acknowledgement.
- After the forced process halt, the outbox row remained `PENDING` with `published_at` null and one Redpanda record was visible for the event.
- Restarting the worker without the fault replayed the still-pending row, marked it `PUBLISHED`, set `published_at`, and produced a second Redpanda record with the same `outboxEventId`.

Remaining blockers:

- Node retirement remains blocked.
- The deployed outbox worker post-broker-ack crash/replay path is now proven for the current synthetic Compose drill.
- API process crash after durable ledger/outbox commit, outbox tracing, host crash shapes, non-synthetic passkey operations, evidence-refresh completion, and final retirement review remain incomplete.

## 2026-06-03: Live Compose API Post-Commit Crash Replay Drill

Changes completed:

- Added synthetic-only `CustomerTransferFaultProperties` configuration for halting `core-banking` after a customer transfer durable commit and before the HTTP response.
- Wired the fault switch through `CustomerTransferController`, `application.yml`, and Docker Compose without enabling any default crash behavior.
- Added `CustomerTransferFaultPropertiesTest` and env-gated `LiveCustomerTransferApiCrashIntegrationTest`.
- Added `docs/test-evidence/api-process-crash-drill.md` and updated the node-retirement gate, failure-drill notes, parity matrix, evidence gap report, QA recommendation, and related evidence conclusions while keeping Node retirement blocked.

Verification:

- Initial sandboxed Gradle runs failed before test execution because the sandbox blocked Gradle's local file-lock socket.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test --tests lab.banking.core.customer.CustomerTransferFaultPropertiesTest` passed after approval.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.customer.LiveCustomerTransferApiCrashIntegrationTest` passed without live API env, proving compile and env-gated skip behavior.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-api-crash-drill BANKING_LAB_POSTGRES_PORT=15499 BANKING_LAB_CORE_BANKING_PORT=18139 BANKING_LAB_SYNTHETIC_SEED_ENABLED=true BANKING_LAB_SECURITY_ENABLED=false BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform up -d --build postgres core-banking` passed after Docker approval.
- The first approved live API crash integration run exposed an uncaught `java.io.IOException` during health polling while the container was being recreated; after widening the polling catch to `Exception`, the same live drill passed.
- `env BANKING_LAB_LIVE_API_CRASH_COMPOSE_PROJECT=banking-lab-api-crash-drill BANKING_LAB_POSTGRES_PORT=15499 BANKING_LAB_CORE_BANKING_PORT=18139 BANKING_LAB_SYNTHETIC_SEED_ENABLED=true BANKING_LAB_SECURITY_ENABLED=false BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.customer.LiveCustomerTransferApiCrashIntegrationTest` passed against the live Compose PostgreSQL and Spring API stack.
- `env COMPOSE_PROJECT_NAME=banking-lab-api-crash-drill BANKING_LAB_POSTGRES_PORT=15499 BANKING_LAB_CORE_BANKING_PORT=18139 BANKING_LAB_SYNTHETIC_SEED_ENABLED=true BANKING_LAB_SECURITY_ENABLED=false BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform down -v` removed the temporary stack and volume.

Result:

- The live drill configured a one-use idempotency-key fault, posted a synthetic customer transfer, and observed `core-banking` exit with code `89` after the durable commit.
- PostgreSQL contained exactly one `ledger_transactions` row, one `customer_transfer_results` row with status `POSTED`, and one `outbox_events` row with status `PENDING` for the idempotency key.
- Source and destination balance projections moved exactly once.
- Restarting `core-banking` without the fault and retrying the same request returned HTTP 200 with `replayed=true`, the same transaction ID, unchanged row counts, and unchanged balances after replay.

Remaining blockers:

- Node retirement remains blocked.
- The API process crash after durable ledger/outbox commit path is now proven for the current synthetic Compose customer-transfer drill.
- Host crash shapes, outbox tracing, non-synthetic passkey operations, evidence-refresh completion, and final retirement review remain incomplete.

## 2026-06-03: Outbox Worker Trace Log Correlation Drill

Changes completed:

- Added `OutboxWorkerTraceLogger` to create Micrometer tracing spans for outbox worker batch and batch-failure events.
- Wired `OutboxWorkerRunner` to record trace/log correlation after each non-empty outbox publish batch and on scheduled batch failures.
- Added `OutboxWorkerTraceLogIntegrationTest` for deterministic captured-log verification.
- Extended `LiveOutboxWorkerSmokeIntegrationTest` with an env-gated live Compose outbox trace-log drill that verifies a published outbox event ID appears in worker logs with trace/span IDs.
- Added `docs/test-evidence/outbox-trace-log-correlation.md` and updated evidence/gate docs while keeping Node retirement blocked.

Verification:

- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test --tests lab.banking.core.eventing.OutboxWorkerRunnerTest` passed.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.observability.OutboxWorkerTraceLogIntegrationTest` passed.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.eventing.LiveOutboxWorkerSmokeIntegrationTest` passed without live outbox env, proving compile and env-gated skip behavior.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-outbox-trace-drill BANKING_LAB_POSTGRES_PORT=15500 BANKING_LAB_REDPANDA_PORT=19100 BANKING_LAB_REDPANDA_ADMIN_PORT=19700 BANKING_LAB_OUTBOX_TOPIC=banking.lab.outbox-trace-drill BANKING_LAB_TRACING_ENABLED=true BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform up -d --build postgres redpanda core-banking-outbox-worker` passed.
- `env BANKING_LAB_LIVE_OUTBOX_COMPOSE_PROJECT=banking-lab-outbox-trace-drill BANKING_LAB_POSTGRES_PORT=15500 BANKING_LAB_REDPANDA_PORT=19100 BANKING_LAB_REDPANDA_ADMIN_PORT=19700 BANKING_LAB_OUTBOX_TOPIC=banking.lab.outbox-trace-drill BANKING_LAB_TRACING_ENABLED=true BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.eventing.LiveOutboxWorkerSmokeIntegrationTest.live outbox worker batch logs carry trace and span ids after publish'` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-outbox-trace-drill docker compose logs --no-color --tail=220 core-banking-outbox-worker` showed an `observability.outbox.worker event=batch` log line with a synthetic outbox event ID, `traceId=c6493c63802a9c590d3c1c697db53c15`, and `spanId=8840ed5ce947119a`.
- `env COMPOSE_PROJECT_NAME=banking-lab-outbox-trace-drill BANKING_LAB_POSTGRES_PORT=15500 BANKING_LAB_REDPANDA_PORT=19100 BANKING_LAB_REDPANDA_ADMIN_PORT=19700 BANKING_LAB_OUTBOX_TOPIC=banking.lab.outbox-trace-drill BANKING_LAB_TRACING_ENABLED=true BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform down -v` removed the temporary stack and volumes.

Result:

- Outbox worker batches now create tracing spans tagged with topic, client ID, attempted/published/failed/dead-lettered counts, and synthetic-only scope.
- Captured integration logs prove 32-character lowercase hex trace IDs and 16-character lowercase hex span IDs.
- The live Compose drill proved the deployed worker logs the published outbox event ID with trace/span correlation after durable publish to Redpanda.

Remaining blockers:

- Node retirement remains blocked.
- Outbox worker trace/log correlation is now proven for the current synthetic target-stack outbox publish path.
- Host crash shapes, non-synthetic passkey operations, evidence-refresh completion, and final retirement review remain incomplete.

## 2026-06-03: Platform Host-Crash-Shaped Temporal And Outbox Drills

Changes completed:

- Extended `LiveTemporalWorkerSmokeIntegrationTest` with a live platform host-crash-shaped drill that starts all current Temporal workflow case types, waits for `WAITING_APPROVAL`, kills `core-banking-temporal-worker`, `temporal`, and `postgres`, restarts them on the same task queue and volumes, then verifies checker approval completion from Temporal history.
- Extended `LiveOutboxWorkerSmokeIntegrationTest` with a live platform host-crash-shaped drill that creates a durable `PENDING` outbox row, kills the eventing platform services, restarts PostgreSQL, Redpanda, and `core-banking-outbox-worker`, then verifies the row becomes `PUBLISHED` and a Redpanda record is observable.
- Added `docs/test-evidence/temporal-platform-host-crash-drill.md` and `docs/test-evidence/outbox-platform-host-crash-drill.md`.
- Updated evidence, parity, failure-drill, architecture, and node-retirement gate docs while keeping Node retirement blocked.

Verification:

- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live all Temporal workflows survive Compose platform host crash restart before approval completion'` passed without live Temporal env, proving compile and env-gated skip behavior.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-host-crash-drill BANKING_LAB_POSTGRES_PORT=15510 BANKING_LAB_TEMPORAL_PORT=17260 BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-host-crash-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform up -d --build postgres temporal core-banking-temporal-worker` passed.
- `env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17260 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-host-crash-drill BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-host-crash-drill BANKING_LAB_POSTGRES_PORT=15510 BANKING_LAB_TEMPORAL_PORT=17260 BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live all Temporal workflows survive Compose platform host crash restart before approval completion'` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-host-crash-drill docker compose ps --all` showed PostgreSQL healthy and Temporal plus the restarted worker running after the drill.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-host-crash-drill docker compose logs --no-color --tail=360 core-banking-temporal-worker` showed transient `UNAVAILABLE` poller failures during the outage and `COMPLETED` workflow logs for complaint answer, FDS release, FDS block, AML closure, reconciliation adjustment, account hold, and account release.
- `env COMPOSE_PROJECT_NAME=banking-lab-temporal-host-crash-drill BANKING_LAB_POSTGRES_PORT=15510 BANKING_LAB_TEMPORAL_PORT=17260 BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-host-crash-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform down -v` removed the temporary Temporal drill stack.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.eventing.LiveOutboxWorkerSmokeIntegrationTest.live outbox worker publishes pending event after Compose platform host crash restart'` passed without live outbox env, proving compile and env-gated skip behavior.
- `env COMPOSE_PROJECT_NAME=banking-lab-outbox-host-crash-drill BANKING_LAB_POSTGRES_PORT=15511 BANKING_LAB_REDPANDA_PORT=19110 BANKING_LAB_REDPANDA_ADMIN_PORT=19710 BANKING_LAB_OUTBOX_TOPIC=banking.lab.outbox-host-crash-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform up -d --build postgres redpanda core-banking-outbox-worker` passed.
- `env BANKING_LAB_LIVE_OUTBOX_COMPOSE_PROJECT=banking-lab-outbox-host-crash-drill BANKING_LAB_POSTGRES_PORT=15511 BANKING_LAB_REDPANDA_PORT=19110 BANKING_LAB_REDPANDA_ADMIN_PORT=19710 BANKING_LAB_OUTBOX_TOPIC=banking.lab.outbox-host-crash-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.eventing.LiveOutboxWorkerSmokeIntegrationTest.live outbox worker publishes pending event after Compose platform host crash restart'` passed.
- `env COMPOSE_PROJECT_NAME=banking-lab-outbox-host-crash-drill docker compose ps --all` showed PostgreSQL healthy, Redpanda running, and the restarted outbox worker running.
- `env COMPOSE_PROJECT_NAME=banking-lab-outbox-host-crash-drill docker compose logs --no-color --tail=260 core-banking-outbox-worker` showed `observability.outbox.worker event=batch ... attempted=1 published=1 failed=0 deadLettered=0` for `OBX-HOST-CRASH-9CB5A46E-5BFB-4F62-9676-6EE04776D6E6`.
- `env COMPOSE_PROJECT_NAME=banking-lab-outbox-host-crash-drill docker compose logs --no-color --tail=180 redpanda` showed recovery from an existing data directory and Kafka listener startup after restart.
- `env COMPOSE_PROJECT_NAME=banking-lab-outbox-host-crash-drill BANKING_LAB_POSTGRES_PORT=15511 BANKING_LAB_REDPANDA_PORT=19110 BANKING_LAB_REDPANDA_ADMIN_PORT=19710 BANKING_LAB_OUTBOX_TOPIC=banking.lab.outbox-host-crash-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform down -v` removed the temporary outbox drill stack.

Result:

- The Temporal platform drill proved all current synthetic Temporal workflow case types survive a simultaneous PostgreSQL, Temporal server, and worker outage before checker approval and complete after restart.
- The outbox platform drill proved a durable pending event survives PostgreSQL, Redpanda, and worker outage before publish, then publishes once after restart with `PUBLISHED` state and a Redpanda record.

Remaining blockers:

- Node retirement remains blocked.
- Current target-stack host-crash-shaped evidence is now proven for the synthetic Temporal workflow and outbox eventing paths.
- Non-synthetic passkey operations, evidence-refresh completion, and final retirement review remain incomplete.

## 2026-06-03: Non-Synthetic Passkey Retirement Guard

Changes completed:

- Added an explicit `non-synthetic-passkey-operations` required gate to `docs/migration/node-retirement-gate.json` with status `pending`.
- Added `docs/test-evidence/passkey-non-synthetic-operations.md` to separate the existing Chromium CDP virtual-authenticator WebAuthn smoke from the still-missing real platform/hardware passkey evidence.
- Extended `scripts/check-node-retirement-gate.ts` so a future pass claim requires `docs/test-evidence/generated/passkey-non-synthetic-evidence.json` with simulator tokens disabled, no Playwright/CDP virtual authenticator, a real platform or hardware-security-key authenticator, Spring signed-token acceptance, redaction confirmation, and commands actually run.
- Updated QA evidence tests and recommendation/gap docs while keeping Node retirement blocked.

Verification:

- `npm run node:retirement-gate` should report blocked and list `non-synthetic-passkey-operations`, `evidence-refresh`, and `retirement-review` as incomplete until real passkey evidence is captured.
- `node --test tests/qaEvidenceCodexPlan.test.mjs tests/migrationFoundation.test.mjs` covers the explicit passkey gate and evidence boundary.

Result:

- The project now fails closed if the passkey gate is marked passed without a non-virtual passkey evidence artifact.
- Existing Keycloak/WebAuthn virtual-authenticator evidence remains valid for local required-action smoke, but it is not accepted as non-synthetic passkey operations evidence.

Remaining blockers:

- Node retirement remains blocked.
- Non-synthetic passkey operations still require a manual or real-browser run with a platform authenticator or hardware security key.
- Evidence-refresh completion and final retirement review remain incomplete.

## 2026-06-03: Non-Synthetic Passkey Evidence Recorder

Changes completed:

- Added `scripts/record-passkey-non-synthetic-evidence.ts` to create `docs/test-evidence/generated/passkey-non-synthetic-evidence.json` after a real manual passkey run.
- Added `npm run passkey:evidence:record`.
- The recorder requires explicit confirmations that a real platform/hardware passkey was used, Playwright/CDP virtual WebAuthn was not used, simulator tokens were disabled, Keycloak required action completed, Spring accepted the signed token, evidence is synthetic-only, and redaction is confirmed.
- The recorder validates a redacted staff panel snapshot for `Keycloak WebAuthn manager loaded`, `manager-webauthn01`, `Bearer`, `SYN-CUS-001`, masked phone output, and `AUD-...`, and rejects obvious tokens, cookies, passwords, credential IDs, attestation objects, or unmasked phone output.
- Extended the retirement gate checker to require those staff panel assertions if the passkey gate is ever marked `pass`.
- Added `tests/passkeyEvidenceRecorder.test.mjs` for success, virtual/CDP rejection, and unredacted secret/unmasked PII rejection.

Verification:

- `npm run scripts:typecheck` passed.
- `node --test tests/passkeyEvidenceRecorder.test.mjs tests/qaEvidenceCodexPlan.test.mjs` passed 8 tests.

Result:

- The repo now has a repeatable artifact-generation path for the future real passkey run instead of relying on an unstructured manual note.
- This does not prove non-synthetic passkey operations by itself; the actual platform or hardware authenticator run is still required.

Remaining blockers:

- Node retirement remains blocked.
- Non-synthetic passkey operations still require the real passkey run and generated artifact.
- Evidence-refresh completion and final retirement review remain incomplete.

## 2026-06-03: Target Service Node Module Isolation

Changes completed:

- Moved Node service-shaped oracle modules from `services/*/src/*.mjs` to `legacy-node-reference/services/*/src/*.mjs`.
- Updated `runtime/labApp.mjs` and `tests/ledgerCore.test.mjs` to use the legacy reference path so the executable Node oracle remains intact.
- Added `legacy-node-reference/README.md` to mark that directory as reference-only and not a target service implementation area.
- Added a migration foundation test that asserts target `services/` contains no `.mjs` business modules while the retirement gate remains blocked.
- Updated ADR and regulatory mapping docs to distinguish legacy `LedgerCore` evidence from Spring/PostgreSQL target services.

Verification:

- `node --test tests/migrationFoundation.test.mjs tests/ledgerCore.test.mjs` should pass after the move.
- `npm test`, `npm run node:retirement-gate`, and `npm run parity` should continue to pass before this cleanup is treated as evidence.

Result:

- The target service tree no longer contains Node business modules.
- The Node oracle is still preserved under an explicit legacy reference boundary until the retirement gate is ready.

Remaining blockers:

- Node retirement remains blocked.
- Non-synthetic passkey operations, evidence-refresh completion, and final retirement review remain incomplete.

## 2026-06-03: Admin Console Target Surface

Changes completed:

- Added `admin-console` as a seventh manifest-driven Next target app on port 3007.
- Added `ADM-101` platform-control dashboard and `ADM-201` privileged security-policy parameter manifests with compliance/passkey-recovery roles, reason-required audit metadata, and maker-checker approval metadata.
- Added Spring `GET /api/admin/platform/summary` for synthetic-only platform controls, plus security filter coverage for `/api/admin/**`.
- Added shared API client support, admin Playwright shell/API smoke hooks, Keycloak client redirect/audience config for `admin-console`, and CORS coverage for port 3007.
- Updated screen-engine, scaffold, retirement-boundary, and evidence docs while keeping Node retirement blocked.

Verification:

- `npm run validate:manifests` should validate 28 screen manifests including `admin-console`.
- `npm run next:admin-console:typecheck` should pass.
- `npm --workspace @banking-lab/screen-engine run test` should pass with admin manifest coverage.
- `scripts/run-core-banking-tests.sh :services:core-banking:integrationTest --tests lab.banking.core.admin.AdminPlatformApiParityIntegrationTest` should pass.

Result:

- The target architecture now has an admin UI surface instead of leaving admin as a plan-only artifact.
- Admin live browser API/Keycloak evidence still needs a refreshed environment run before final retirement review.

Remaining blockers:

- Node retirement remains blocked.
- Non-synthetic passkey operations, admin live browser evidence refresh, evidence-refresh completion, and final retirement review remain incomplete.

## 2026-06-03: Node Retirement Boundary Audit

Changes completed:

- Added `scripts/check-retirement-boundary-audit.ts` and `npm run retirement:audit`.
- The audit fails on `.mjs` files outside approved reference/support paths, direct legacy imports through target `services/`, missing target-stack anchors, missing listed gate evidence paths, and parity map drift from the 42 mapped scenarios.
- Fixed the Phase 2 evidence generator to import `LedgerCore` through `legacy-node-reference/services` instead of the target `services/` tree.
- Added `tests/retirementBoundaryAudit.test.mjs` and listed the audit in the evidence-refresh gate.
- Updated QA recommendation and evidence-gap docs while keeping the gate blocked.

Verification:

- `npm run retirement:audit` should report blocked with reference boundary, target anchors, evidence paths, and parity map passing.
- The audit does not mark Node ready; it preserves the current blockers for non-synthetic passkey operations, evidence-refresh completion, and final retirement review.

Result:

- The repo now has an executable Node-independence boundary check for the current retirement scope.
- Target `services/` remains free of Node business modules, and stale imports through the old service path are guarded.

Remaining blockers:

- Node retirement remains blocked.
- Non-synthetic passkey operations, evidence-refresh completion, and final retirement review remain incomplete.
