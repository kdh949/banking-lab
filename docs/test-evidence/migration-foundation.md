# Migration Foundation Test Evidence

## Acceptance Checks

- Node reference runtime is retained until parity gates pass.
- Current 42 Node reference scenarios are mapped to Kotlin/Spring and Next/Playwright target tests.
- Parity command runs Node reference tests, manifest validation, and evidence pack generation.
- API failures expose structured error fields for invariant, policy, cause, fix, request ID, and documentation.
- Spring Boot/Kotlin scaffold declares `/health`, structured error DTOs, PostgreSQL, Flyway, and Testcontainers dependencies.
- Customer-web Next scaffold renders from existing manifests and preserves the static Node reference shell.
- Next dependency lock resolves `postcss` to the fixed override version.
- Node retirement gate remains blocked until Kotlin/Next parity evidence exists.

## Commands

```bash
npm test
npm run parity
npm run node:retirement-gate
node --test tests/springScaffold.test.mjs
npm run next:customer-web:typecheck
npm run next:customer-web:build
npm audit --omit=dev
java -version
gradle -v
```

## Evidence Artifacts

- `docs/migration/kotlin-next-playbook.md`
- `docs/migration/parity-scenarios.json`
- `docs/migration/structured-api-error-contract.md`
- `docs/migration/node-retirement-gate.json`
- `scripts/run-parity-checks.mjs`
- `scripts/check-node-retirement-gate.mjs`
- `docs/architecture/kotlin-spring-foundation.md`
- `services/core-banking/build.gradle.kts`
- `services/core-banking/src/main/kotlin/lab/banking/core/api/HealthController.kt`
- `docs/architecture/next-customer-web-foundation.md`
- `apps/customer-web/src/app/page.tsx`
- `apps/customer-web/src/lib/manifestLoader.ts`

## Remaining Risk

- Kotlin/Spring Boot scaffold exists but was not executed locally because this environment has no Java runtime and no Gradle CLI.
- Next.js scaffold currently covers `customer-web`; the remaining app shells and Playwright parity flows are pending.
- Durable approval execution remains a target-stack gap until PostgreSQL-backed execution state exists.
