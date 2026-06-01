# Phase 2 Ledger Invariant Baseline

Phase 2 extends the baseline from opening balances to command-generated transactions.

Validated command families:

- Deposit
- Withdrawal
- Internal transfer
- Reversal

Validated invariants:

- `sum(postings by transaction) == 0`
- `balance == sum(postings by account)` through projection
- `available_balance <= ledger_balance`
- `idempotent request creates at most one transaction`
- `closed day cannot be mutated directly`
- `reversal references original transaction`

The full external-file reconciliation workflow remains planned for Phase 6.
