# Phase 1 Control Mapping

| Control area | Phase 1 evidence |
| --- | --- |
| Ledger integrity | `packages/banking-domain/src/ledger.mjs`, `tests/ledger.test.mjs` |
| Balance projection only | `projectBalances`, `account_balances` migration comments and tests |
| Idempotent external commands | `IdempotencyStore`, transfer runtime test |
| Reversal instead of mutation | `createReversalTransaction`, append-only SQL triggers |
| Staff access audit | `AuditLog`, reason-required staff search API, runtime test |
| PII masked by default | `masking.mjs`, staff customer search response |
| Maker-checker | `ApprovalStore`, `operator_approvals`, tests |
| Screen manifest scaling | `screen-manifests/*`, `packages/screen-engine`, manifest tests |
| Evidence pack | `docs/test-evidence`, `scripts/generate-phase1-evidence.mjs` |

## Explicit Non-Production Boundary

No real banking network, real KYC, real payment processor, real customer PII, or real funds are connected. External providers remain simulators.
