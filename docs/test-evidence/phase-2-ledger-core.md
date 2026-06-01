# Phase 2 Ledger Core Test Evidence

## Acceptance Checks

- Deposit and withdrawal create balanced ledger transactions.
- Withdrawal cannot exceed available balance.
- Internal transfer changes both accounts from the same ledger source of truth.
- Idempotency retry returns the original transaction and does not duplicate postings.
- Reversal references the original transaction and cannot be duplicated.
- Concurrent withdrawals serialize and cannot overdraw.
- Closed business day rejects direct posting commands.
- Runtime withdrawal and reversal endpoints preserve invariants.

## Commands

```bash
npm test
npm run evidence:phase2
```

Generated machine-readable evidence is written to `docs/test-evidence/generated/phase-2-ledger-core.json`.
