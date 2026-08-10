# Kotlin + Next.js Migration Playbook

## Scope

This playbook started from the 2026-06-02 gstack engineering and DX review plans. Kotlin/Spring Boot backend parity and TypeScript/Next.js frontend parity are now proven for the current synthetic lab scope, so the Node.js `.mjs` runtime is archived oracle/reference material rather than a target-path runtime dependency.

The migration must preserve these controls:

- double-entry ledger postings and projected balances
- idempotent external commands
- append-only audit hash chain
- masked PII by default
- maker-checker approval for high-risk operations
- workflow state validity for complaint, FDS, AML, and reconciliation cases
- manifest-declared authorization, audit, masking, and approval policies
- synthetic-only simulator boundaries

## Source Plans

The historical planning artifacts are identified by stable review IDs rather than contributor-machine paths. They are provenance only and are not required to build or test the repository.

- `gstack-review:phase-1-foundation-eng-20260602-114300`
- `gstack-review:phase-1-foundation-eng-test-20260602-114300`
- `gstack-review:phase-1-foundation-devex-20260602-115809`
- `gstack-tasks:phase-1-foundation-eng-20260602-114300`
- `gstack-tasks:phase-1-foundation-devex-20260602-115809`

## Local Prerequisites

Reference runtime:

- Node.js 20 or newer
- npm
- Docker, for Compose validation and future Testcontainers support

Target migration stack:

- JDK 21
- Gradle 8.14 or newer, or 9.x
- Kotlin with Spring Boot
- PostgreSQL through Flyway migrations and Testcontainers for integration tests
- TypeScript with Next.js or React
- Playwright for frontend parity flows after the Next shells exist

## First Commands

Reference confidence check:

```bash
npm install
npm run parity
```

Expected current result:

```text
Parity scenario map covers 43 synthetic runtime scenarios.
Validated screen manifests.
Synthetic runtime tests passed.
Evidence pack generated.
Node-based helpers remain isolated under runtime/synthetic-reference.
```

Node retirement gate:

```bash
npm run node:retirement-gate
```

Gate metadata source of truth: `docs/migration/node-retirement-gate.json`.

Expected current result:

```text
Runtime retirement gate: ready
All required retirement gates have passing evidence.
```

Any future material target-stack change should keep this result ready by rerunning parity, evidence, and retirement-gate checks before claiming the change is releasable.

Spring Boot target commands with JDK 21:

```bash
npm run test:core-banking:unit
npm run test:core-banking:integration
npm run test:payment-service:unit
npm run test:notification-service:unit
npm run test:reporting-service:unit
```

Direct Gradle equivalents:

```bash
./gradlew :services:core-banking:test
./gradlew :services:core-banking:integrationTest
./gradlew :services:payment-service:test
./gradlew :services:notification-service:test
./gradlew :services:reporting-service:test
```

Next.js channel commands:

```bash
npm run next:customer-web:typecheck
npm run next:staff-terminal:typecheck
npm run next:complaint-portal:typecheck
npm run next:ops-console:typecheck
npm run next:audit-console:typecheck
npm run next:fds-aml-console:typecheck
npm run next:admin-console:typecheck
npm run packages:typecheck
npm audit --omit=dev
```

## Migration Sequence

1. Keep `runtime/server.mjs`, `runtime/labApp.mjs`, and the synthetic helper modules under `runtime/synthetic-reference` intact for local test/runtime compatibility; do not recreate deleted legacy static app shells.
2. Add Spring Boot/Kotlin scaffold under `services/core-banking` while preserving the local synthetic runtime entrypoint until its tests are replaced.
3. Wire Flyway to `db/migrations/V001__foundation.sql` and following `V...` migrations; use Testcontainers for integration tests.
4. Port domain behavior in order: ledger, idempotency, audit/masking, maker-checker, workflow, complaint, FDS/AML, reconciliation.
5. Preserve current route semantics first; publish OpenAPI from Spring after route parity stabilizes.
6. Keep channel-specific Next.js surfaces target-backed. Customer, complaint, ops, audit, FDS/AML, and admin channels continue to use manifests where appropriate; `staff-terminal` is the iWorks integrated terminal and must not reintroduce staff-terminal manifests.
7. Generate or hand-maintain a typed TypeScript API client only after the OpenAPI contract stabilizes.
8. Run `npm run parity` plus target Kotlin/Next tests side by side so all mapped scenarios stay passing.
9. Update evidence, ADRs, architecture notes, threat/control mappings, reconciliation reports, and demos.
10. Keep synthetic runtime helpers isolated from target `apps/`, `services/`, and `packages/` source unless a fresh deletion plan reruns `npm run node:retirement-gate`, parity, evidence refresh, and boundary audits without losing regression coverage.

## First Vertical Slice

Backend:

- Spring Boot `/health` returns `status=ok`, `syntheticOnly=true`, and audit hash-chain status.
- Flyway applies the canonical `db/migrations/V...` migrations.
- Structured API error responses match `docs/migration/structured-api-error-contract.md`.
- Core, payment, notification, and reporting Spring services are registered Gradle modules.

Frontend:

- Six manifest-oriented Next.js channel apps render manifest metadata and API-backed panels; `staff-terminal` renders the iWorks integrated terminal.
- Shared API/auth/screen/form packages stay the target channel integration path.
- Legacy static shells are removed from both target apps and synthetic runtime helpers. The staff terminal official screen is `apps/staff-terminal/src/components/terminal/IntegratedTerminalApp.tsx`.

Parity:

- The first Kotlin tests mirror `tests/ledger.test.mjs` and `tests/ledgerCore.test.mjs`.
- The first API tests mirror `tests/runtime.test.mjs`.

## Do Not Delete Yet

Do not remove or rewrite these synthetic runtime/test support assets without a dedicated deletion plan and fresh passing evidence:

- `runtime/server.mjs`
- `runtime/labApp.mjs`
- synthetic helper modules under `runtime/synthetic-reference/packages`
- synthetic helper modules under `runtime/synthetic-reference/services`
- `tests/*.test.mjs`
- evidence scripts under `scripts/generate-*.mjs`

The current gate is ready, but these assets still support local regression checks. Deletion should be treated as a separate controlled change, not a routine documentation or feature cleanup.
