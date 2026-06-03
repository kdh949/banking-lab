# Kotlin + Next.js Migration Playbook

## Scope

This playbook implements the 2026-06-02 gstack engineering and DX review plans. The current Node.js `.mjs` runtime remains the executable reference until Kotlin/Spring Boot backend parity and TypeScript/Next.js frontend parity are proven.

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

- `/Users/donghyunkim/.gstack/projects/kdh949-banking-lab/donghyunkim-feature-phase-1-foundation-eng-review-plan-20260602-114300.md`
- `/Users/donghyunkim/.gstack/projects/kdh949-banking-lab/donghyunkim-feature-phase-1-foundation-eng-review-test-plan-20260602-114300.md`
- `/Users/donghyunkim/.gstack/projects/kdh949-banking-lab/donghyunkim-feature-phase-1-foundation-devex-review-plan-20260602-115809.md`
- `/Users/donghyunkim/.gstack/projects/kdh949-banking-lab/tasks-eng-review-20260602-114300.jsonl`
- `/Users/donghyunkim/.gstack/projects/kdh949-banking-lab/tasks-devex-review-20260602-115809.jsonl`

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

Expected result before Kotlin/Next parity exists:

```text
Parity scenario map covers 42 Node reference scenarios.
Validated screen manifests.
Node reference tests passed.
Evidence pack generated.
Node reference runtime remains required.
```

Node retirement gate:

```bash
npm run node:retirement-gate
```

Expected result at this stage:

```text
Node reference retirement gate: blocked
```

The blocked result is correct until Spring Boot, Next.js, parity tests, evidence, and final review gates are complete.

Spring Boot scaffold commands once JDK and Gradle are available:

```bash
docker compose --profile migration up -d postgres
gradle :services:core-banking:test
gradle :services:core-banking:bootRun
curl http://127.0.0.1:8081/health
```

After a Gradle wrapper is committed, replace the raw `gradle` commands with:

```bash
./gradlew :services:core-banking:test
./gradlew :services:core-banking:bootRun
```

Customer-web Next scaffold commands:

```bash
npm run next:customer-web:typecheck
npm run next:customer-web:build
npm audit --omit=dev
```

## Migration Sequence

1. Keep `runtime`, Node oracle helper modules under `legacy-node-reference/packages`, legacy static app shells under `legacy-node-reference/apps`, and current Node tests intact.
2. Add Spring Boot/Kotlin scaffold under `services/core-banking` while preserving the Node reference entrypoint.
3. Wire Flyway to `db/migrations/V001__foundation.sql` and following `V...` migrations; use Testcontainers for integration tests.
4. Port domain behavior in order: ledger, idempotency, audit/masking, maker-checker, workflow, complaint, FDS/AML, reconciliation.
5. Preserve current route semantics first; publish OpenAPI from Spring after route parity stabilizes.
6. Add Next.js app shells that render from existing screen manifests instead of hand-coded business screens.
7. Generate or hand-maintain a typed TypeScript API client only after the OpenAPI contract stabilizes.
8. Run `npm run parity` plus target Kotlin/Next tests side by side until all mapped scenarios pass.
9. Update evidence, ADRs, architecture notes, threat/control mappings, reconciliation reports, and demos.
10. Remove Node only when `docs/migration/node-retirement-gate.json` changes to `ready` with evidence for every gate.

## First Vertical Slice

Backend:

- Spring Boot `/health` returns `status=ok`, `syntheticOnly=true`, and audit hash-chain status.
- Flyway applies the canonical `db/migrations/V...` migrations.
- Structured API error responses match `docs/migration/structured-api-error-contract.md`.

Frontend:

- One Next.js shell renders manifest metadata for `customer-web`.
- No one-off business screen logic is introduced before the shared manifest renderer exists.
- Keep the legacy customer-web shell under `legacy-node-reference/apps/customer-web/public/index.html` until Node reference retirement is approved.

Parity:

- The first Kotlin tests mirror `tests/ledger.test.mjs` and `tests/ledgerCore.test.mjs`.
- The first API tests mirror `tests/runtime.test.mjs`.

## Do Not Delete Yet

Do not remove or rewrite these reference assets until the retirement gate is ready:

- `runtime/server.mjs`
- `runtime/labApp.mjs`
- Node oracle helper modules under `legacy-node-reference/packages`
- static app shells under `legacy-node-reference/apps`
- legacy static UI assets under `legacy-node-reference/ui/public`
- `tests/*.test.mjs`
- evidence scripts under `scripts/generate-*.mjs`

Deletion before parity removes the only executable proof for ledger, idempotency, audit, masking, maker-checker, workflow, reconciliation, and evidence behavior.
