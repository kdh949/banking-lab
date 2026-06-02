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
