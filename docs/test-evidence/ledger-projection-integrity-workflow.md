# Ledger Projection Integrity Workflow Evidence

Date: 2026-06-06

Scope: Phase 5 remaining hardening for synthetic ledger projection drift detection and maker-checker rebuild. This evidence does not introduce real money, real PII, real payment/card networks, Open Banking, or real KYC/provider integrations.

## Implemented Controls

- `V035__ledger_projection_integrity_workflow.sql` adds durable drift run/item and rebuild request/run/item tables.
- `LedgerProjectionIntegrityService` computes expected projection balances from `ledger_postings` using the existing debit-negative, credit-positive ledger sign policy.
- Drift checks are reason-required, audit-appended, and idempotency-key protected.
- Rebuild requests use `operator_approvals` with `LEDGER_PROJECTION_REBUILD` as a high-risk maker-checker business type.
- Rebuild execution requires an approved request, records before/after source and projection hashes, and updates only `account_balance_projections`.
- New ops APIs live under `/api/ops/ledger/projection-*` and return structured domain errors through the existing Spring error handler.
- Ops console manifests `OPS-LEDGER-101`, `OPS-LEDGER-102`, and `OPS-LEDGER-103` declare drift monitor, rebuild request, and rebuild evidence flows.

## Invariant Verdict

Passed for the targeted Phase 5 slice:

- Balanced ledger source rows remain append-only during rebuild.
- `ledger_transactions` and `ledger_postings` row counts are unchanged by rebuild execution.
- Rebuilt balances equal signed posting sums.
- Maker self-approval is rejected.
- Rebuild cannot execute before checker approval.
- Execute replay with the same idempotency key returns the original rebuild run and does not create duplicate run/item rows.
- Closed business-date policy is not bypassed by rebuild because no direct posting is created.

## Commands Run

| Command | Result | Notes |
| --- | --- | --- |
| `scripts/run-core-banking-tests.sh :services:core-banking:compileKotlin` | sandbox failed, escalated pass | Gradle file-lock socket is blocked in sandbox. |
| `scripts/run-core-banking-tests.sh :services:core-banking:compileIntegrationTestKotlin` | pass | Compiled the new `LedgerProjection*` integration tests. |
| `scripts/run-core-banking-tests.sh :services:core-banking:integrationTest --tests '*LedgerProjection*'` | first two runs failed, final rerun pass | Failures were test assertion corrections for account-scoped source posting count and drift-run count. |
| `scripts/run-core-banking-tests.sh :services:core-banking:test` | pass | Full core-banking unit task. |
| `scripts/run-core-banking-tests.sh :services:core-banking:integrationTest` | pass | Full core-banking Testcontainers integration task after adding `V035`. |
| `npm run scripts:typecheck` | pass | Shared TypeScript/script typecheck after API-client changes. |
| `npm run next:ops-console:typecheck` | pass | Ops console TypeScript check. |
| `npm run next:ops-console:build` | pass | Ops console production build. |
| `npm run validate:manifests` | pass | Validated 109 manifests including `OPS-LEDGER-*`. |
| `npm run packages:typecheck` | pass | Screen/form/API/auth package typechecks passed. |
| `npm test` | pass | 169 Node oracle/structural tests passed; Node remains reference-only. |
| `npm run test:e2e -- --grep "projection"` | skipped | Playwright started Next dev servers and skipped the single live API projection test because `BANKING_LAB_E2E_API_BASE_URL` was unset. |

## Not Run

- Live API projection browser execution was not run because no Spring API URL was configured. The Playwright projection grep was run and reported the live API smoke as skipped.

## Residual Risk

- The implementation is row-lock based and suitable for the current synthetic lab scale. Phase 8 should add large-ledger benchmark evidence and partition/archive-aware rebuild notes.
- Live browser proof is wired but remains conditional on a Compose Spring API/Keycloak stack.
