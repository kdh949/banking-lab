# Phase 2 Ledger Core Architecture

## Command Flow

```text
Runtime API
  -> LedgerCore command
       -> command lock
       -> account/business-date validation
       -> idempotency replay check
       -> double-entry transaction construction
       -> append transaction and postings
       -> invariant validation
       -> projected balances
```

## Supported Commands

- `deposit`
- `withdraw`
- `transfer`
- `reverseTransaction`

## Runtime APIs

- `POST /api/ledger/deposits`
- `POST /api/ledger/withdrawals`
- `POST /api/ledger/transfers`
- `POST /api/ledger/reversals`
- `GET /api/ledger/transactions`
- `GET /api/ledger/balances`

## Invariants

- Posting sum per transaction is zero by currency.
- Balance is projected from postings.
- Available balance is never greater than ledger balance.
- Idempotency key creates at most one ledger transaction.
- Closed business day cannot be mutated directly.
- Reversal transaction references an existing original transaction.
- Reversal cannot be duplicated with a different idempotency key.
- Partial finalization is rejected.

## Current Persistence Boundary

Phase 2 keeps command state in memory while preserving the Phase 1 PostgreSQL schema as the intended persistence contract. The next persistence slice should map the same command service semantics onto SQL transactions.
