# Migration Foundation Test Evidence

## Acceptance Checks

- Node reference runtime is retained only as archived oracle/reference material after parity gates passed.
- Current 43 Node reference scenarios are mapped to Kotlin/Spring and Next/Playwright target tests.
- Parity command runs Node reference tests, manifest validation, target screen-engine manifest parity tests, and evidence pack generation.
- API failures expose structured error fields for invariant, policy, cause, fix, request ID, and documentation.
- Spring Boot/Kotlin services declare `/health`, structured error DTOs, PostgreSQL, Flyway, Testcontainers, security, outbox, workflow, payment, notification, and reporting dependencies.
- Kotlin/Spring ledger commands now persist source-of-truth transactions and postings through PostgreSQL/Flyway.
- Testcontainers verifies idempotency replay/conflict, outbox row insertion, closed-day rejection, reversal, and REPEATABLE READ/SERIALIZABLE concurrent withdrawal safety.
- Seven Next.js channel apps render from existing manifests while static Node reference shells are preserved under `legacy-node-reference/apps`.
- Next dependency lock resolves `postcss` to the fixed override version.
- Node retirement gate is ready for the current synthetic lab scope; rerun the gate after material target-stack changes.

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
./gradlew --version
docker compose config
docker compose --profile migration config
docker compose --profile platform config
docker run --rm -v /Users/donghyunkim/Documents/banking-lab:/workspace -w /workspace gradle:8.14.3-jdk21 ./gradlew :services:core-banking:test --no-daemon
docker run --rm -e TESTCONTAINERS_RYUK_DISABLED=true -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -v /var/run/docker.sock:/var/run/docker.sock -v /Users/donghyunkim/Documents/banking-lab:/workspace -w /workspace gradle:8.14.3-jdk21 ./gradlew :services:core-banking:integrationTest --no-daemon
```

## 2026-06-02 Results

- `npm test`: passed, 54/54 Node reference and structural checks.
- `npm run validate:manifests`: passed, 23 manifests.
- `npm run next:customer-web:typecheck`: passed.
- `npm run node:retirement-gate`: passed with expected `blocked` status.
- `docker compose config`: passed for default profile.
- `docker compose --profile migration config`: passed.
- `docker compose --profile platform config`: passed.
- `java -version`: failed on host because no Java runtime is installed.
- `gradle --version`: failed on host because Gradle CLI is not installed.
- `./gradlew --version`: failed on host because no Java runtime is installed.
- Docker/JDK `:services:core-banking:test`: passed.
- Docker/JDK/Testcontainers `:services:core-banking:integrationTest`: passed with Docker Desktop overrides `TESTCONTAINERS_RYUK_DISABLED=true` and `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`.

## 2026-06-03 Target Manifest Parity Results

- `npm run test:screen-engine`: passed, 4/4 target TypeScript screen-engine manifest parity tests.
- `npm run parity`: passed under the approved execution path after the sandbox blocked local Node reference HTTP server `listen(127.0.0.1)`. The parity runner now includes `npm run test:screen-engine`.

## 2026-06-06 Current Gate Check

- `npm run node:retirement-gate`: passed with `ready` status.
- Current parity inventory covers 43 mapped Node reference scenarios, all target-backed.
- The Node runtime remains preserved as archived oracle/reference material, not as a target-path dependency.

## Evidence Artifacts

- `docs/migration/kotlin-next-playbook.md`
- `docs/migration/parity-scenarios.json`
- `docs/migration/structured-api-error-contract.md`
- `docs/migration/node-retirement-gate.json`
- `scripts/run-parity-checks.ts`
- `scripts/check-node-retirement-gate.ts`
- `packages/screen-engine/test/manifest-parity.test.ts`
- `docs/architecture/kotlin-spring-foundation.md`
- `services/core-banking/build.gradle.kts`
- `services/core-banking/src/main/kotlin/lab/banking/core/api/HealthController.kt`
- `services/core-banking/src/main/kotlin/lab/banking/core/ledger/application/LedgerCommandService.kt`
- `services/core-banking/src/integrationTest/kotlin/lab/banking/core/ledger/application/LedgerCommandServiceIntegrationTest.kt`
- `db/migrations/V001__foundation.sql`
- `db/migrations/V002__ledger_constraints.sql`
- `db/migrations/V003__audit_approval_workflow.sql`
- `db/migrations/V004__outbox_inbox.sql`
- `db/migrations/V005__fds_aml_reconciliation.sql`
- `docs/architecture/next-customer-web-foundation.md`
- `apps/customer-web/src/app/page.tsx`
- `apps/customer-web/src/lib/manifestLoader.ts`

## Remaining Risk

- Historical 2026-06-02 host-level Java/Gradle failures are no longer the only verification path; current evidence uses JDK 21 and Docker/Testcontainers paths where needed.
- Node is retired only from target-path dependency for the current synthetic lab scope. Keep the archived oracle/reference boundary until a separate deletion plan preserves regression coverage.
- Broader future command paths should continue to reuse bounded retry or operator-visible failure handling for serialization conflicts.
- Live platform hardening still needs ingress-controller traffic, real TLS termination evidence, Argo CD controller sync health, canary promotion, and multi-node storage behavior before any production-like deployment claim.
