# ADR 0004: Customer Web Ledger Source

## Status

Accepted

## Context

Phase 4 requires customer web account views, transaction history, transfers, transfer results, and complaint entry. The prompt requires customer and staff transaction history to read the same source of truth.

## Decision

The legacy Node customer web APIs read from `legacy-node-reference/services/core-banking/src/ledgerCore.mjs` for account balances and transaction history. Target customer web APIs read through Spring `CustomerAccountService` and `CustomerTransferService` against PostgreSQL-backed ledger projections. Customer transfers return idempotent transfer result records:

- `POSTED`: ledger transaction was appended through the ledger command path.
- `HELD`: transfer was routed to FDS review and no ledger posting was appended.
- `FAILED`: business validation failed and no ledger posting was appended.

Staff and customer transaction history share the same ledger source of truth. In the legacy oracle this is `ledgerCore.listTransactions(accountId)`; in the target implementation it is the Spring/PostgreSQL transaction history path.

## Consequences

Positive:

- Customer and staff channels share one ledger source in both the legacy oracle and the target Spring service.
- Transfer retry can return the original result without double posting.
- Held and failed states are visible to the customer.

Tradeoffs:

- The legacy oracle keeps transfer result records in memory.
- The target service persists transfer results and FDS case state in PostgreSQL.

## Follow-up

- Add customer authentication/session binding to account ownership checks.
- Add transfer confirmation receipts and downloadable proof.
