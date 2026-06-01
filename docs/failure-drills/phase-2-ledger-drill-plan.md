# Phase 2 Ledger Failure Drill Plan

## Drill: Concurrent Withdrawal Burst

- Injection: run 120 withdrawal commands of 1,000 minor units against an account funded with 100,000 minor units.
- Expected impact: exactly 100 withdrawals post and 20 reject.
- Invariant: available balance cannot go below zero.
- Evidence: `tests/ledgerCore.test.mjs`.

## Drill: Idempotent Transfer Retry

- Injection: submit the same internal transfer command twice with the same idempotency key.
- Expected impact: second command returns the original transaction.
- Invariant: idempotent request creates at most one transaction.
- Evidence: `tests/ledgerCore.test.mjs`.

## Drill: Closed Business Day Mutation

- Injection: close a business date and submit a deposit command for that date.
- Expected impact: command is rejected and no ledger transaction is appended.
- Invariant: closed day cannot be mutated directly.
- Evidence: `tests/ledgerCore.test.mjs`.
