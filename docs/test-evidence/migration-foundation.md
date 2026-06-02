# Migration Foundation Test Evidence

## Acceptance Checks

- Node reference runtime is retained until parity gates pass.
- Current 42 Node reference scenarios are mapped to Kotlin/Spring and Next/Playwright target tests.
- Parity command runs Node reference tests, manifest validation, and evidence pack generation.
- API failures expose structured error fields for invariant, policy, cause, fix, request ID, and documentation.
- Node retirement gate remains blocked until Kotlin/Next parity evidence exists.

## Commands

```bash
npm test
npm run parity
npm run node:retirement-gate
```

## Evidence Artifacts

- `docs/migration/kotlin-next-playbook.md`
- `docs/migration/parity-scenarios.json`
- `docs/migration/structured-api-error-contract.md`
- `docs/migration/node-retirement-gate.json`
- `scripts/run-parity-checks.mjs`
- `scripts/check-node-retirement-gate.mjs`

## Remaining Risk

- Kotlin/Spring Boot scaffold is not complete yet.
- Next.js scaffold is not complete yet.
- Durable approval execution remains a target-stack gap until PostgreSQL-backed execution state exists.
