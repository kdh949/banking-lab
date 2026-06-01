# ADR 0004: Customer Web Ledger Source

## Status

Accepted

## Context

Phase 4 requires customer web account views, transaction history, transfers, transfer results, and complaint entry. The prompt requires customer and staff transaction history to read the same source of truth.

## Decision

Customer web APIs read from `LedgerCore` for account balances and transaction history. Customer transfers return idempotent transfer result records:

- `POSTED`: ledger transaction was appended through `LedgerCore`.
- `HELD`: transfer was routed to FDS review and no ledger posting was appended.
- `FAILED`: business validation failed and no ledger posting was appended.

Staff and customer transaction history both call `ledgerCore.listTransactions(accountId)`.

## Consequences

Positive:

- Customer and staff channels share one ledger source.
- Transfer retry can return the original result without double posting.
- Held and failed states are visible to the customer.

Tradeoffs:

- Transfer result records are in-memory until persistence is added.
- FDS review is represented as a hold case only; release/block workflow is planned for Phase 6.

## Follow-up

- Persist transfer results and FDS cases.
- Add customer authentication/session binding to account ownership checks.
- Add transfer confirmation receipts and downloadable proof.
