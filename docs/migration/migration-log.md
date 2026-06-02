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

Remaining blockers:

- Node retirement remains blocked.
- Full target parity for all 42 Node scenarios is not complete.
- Keycloak/OAuth2/OIDC enforcement is not proven.
- Real Kafka/Redpanda publish/consume is not proven; current eventing evidence is durable state-transition only.
- Semgrep, Trivy, SBOM, DAST, and observability smoke outputs are still missing.
