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
- Next.js target scaffold: pending.
- Node reference parity map: in progress.
- Node retirement: blocked by design.

Evidence:

- `docs/migration/kotlin-next-playbook.md`
- `docs/migration/parity-scenarios.json`
- `docs/migration/node-retirement-gate.json`
- `docs/migration/structured-api-error-contract.md`
- `docs/test-evidence/migration-foundation.md`
- `docs/architecture/kotlin-spring-foundation.md`
