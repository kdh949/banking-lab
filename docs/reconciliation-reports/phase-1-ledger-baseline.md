# Phase 1 Ledger Baseline

The Phase 1 reconciliation baseline verifies that seed opening transactions are balanced and balances are projection-only.

Current checks:

- `sum(postings by transaction) == 0`
- `balance == sum(postings by account)`
- `available_balance <= ledger_balance`

The full EOD closing and external-file reconciliation flow is planned for Phase 6.
