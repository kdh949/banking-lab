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
