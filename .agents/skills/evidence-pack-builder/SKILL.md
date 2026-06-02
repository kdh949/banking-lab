---
name: evidence-pack-builder
description: Build or review banking-lab evidence packs and milestone evidence without overstating results. Use when updating proof for ledger, audit, maker-checker, manifests, migration parity, failure drills, node retirement gates, or generated evidence reports.
---

# Evidence Pack Builder

## Scope

Use for evidence-oriented work. Evidence must reflect commands actually run.

Read first:

1. `docs/migration/kotlin-next-playbook.md`
2. `docs/migration/node-retirement-gate.json`
3. `docs/migration/parity-scenarios.json`
4. Existing `docs/test-evidence/**`
5. Existing `docs/failure-drills/**`
6. `package.json` scripts

## Evidence Rules

- Do not mark a gate passed without running the command or citing already-existing evidence with date/path.
- Keep Node retirement blocked until every gate in `docs/migration/node-retirement-gate.json` is genuinely ready.
- Report blocked commands with exact reason: missing dependency, unavailable Docker, failing test, or out-of-scope path.
- Preserve synthetic-only scope.
- Evidence should connect back to controls: ledger, idempotency, audit, masking, maker-checker, workflow, reconciliation, manifests, structured errors.

## Baseline Commands

```bash
npm run parity
npm test
npm run validate:manifests
npm run evidence:pack
npm run node:retirement-gate
```

Target-stack commands when available:

```bash
./gradlew :services:core-banking:test
./gradlew :services:core-banking:integrationTest
npm run next:customer-web:typecheck
npm run next:customer-web:build
```

## Output

Use this format:

- Changed files
- Commands run
- Passing tests
- Failing/skipped tests with reason
- Domain invariants affected
- Security/control impact
- Remaining risk
- Next smallest safe task
