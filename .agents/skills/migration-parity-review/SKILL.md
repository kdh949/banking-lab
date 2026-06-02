---
name: migration-parity-review
description: Review Kotlin/Spring Boot and TypeScript/Next.js migration work against the Node reference oracle, parity scenario map, structured errors, evidence, and node retirement gate. Use before claiming target-stack parity or removing any Node reference asset.
---

# Migration Parity Review

## Scope

Use before claiming parity, updating retirement status, or changing migration evidence.

Read first:

1. `PLAN.md`
2. `AGENTS.md`
3. `docs/migration/kotlin-next-playbook.md`
4. `docs/migration/parity-scenarios.json`
5. `docs/migration/node-retirement-gate.json`
6. `docs/migration/structured-api-error-contract.md`
7. Existing Node oracle tests under `tests/*.test.mjs`

## Parity Checklist

- Node reference runtime remains intact until the retirement gate is ready.
- All 42 mapped Node reference scenarios are either covered by target tests or explicitly marked pending with reason.
- Kotlin/Spring ledger behavior matches Node oracle for ledger, idempotency, reversal, adjustment, closed day, reconciliation, and structured errors.
- Next.js screens render from manifests, not one-off duplicated business screens.
- Staff/customer/complaint/ops/audit/FDS-AML flows preserve audit, masking, reason, maker-checker, and workflow metadata.
- Evidence files cite commands actually run.
- Retirement gate remains `blocked` unless every required gate has proof.

## Commands

```bash
npm run parity
npm test
npm run validate:manifests
npm run evidence:pack
npm run node:retirement-gate
```

When target stack is available:

```bash
./gradlew :services:core-banking:test
./gradlew :services:core-banking:integrationTest
npm run next:customer-web:typecheck
npm run next:customer-web:build
```

## Output

Lead with blockers. Include:

- Scenario coverage by suite
- Target-stack proof status
- Node retirement gate verdict
- Evidence gaps
- Coordinator-owned changes requested
