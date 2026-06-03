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

Target-stack Phase 1A now adds:

```text
Spring Boot LedgerController
  -> LedgerCommandService
       -> SERIALIZABLE PostgreSQL transaction
       -> idempotency key advisory lock
       -> account and balance rows SELECT FOR UPDATE
       -> ledger_transactions and ledger_postings append
       -> account_balance_projections update
       -> outbox_events PENDING insert
```

## Supported Commands

- `deposit`
- `withdraw`
- `transfer`
- `reverseTransaction`
- `adjustment`
- `closeBusinessDay`

## Runtime APIs

- `POST /api/ledger/deposits`
- `POST /api/ledger/withdrawals`
- `POST /api/ledger/transfers`
- `POST /api/ledger/reversals`
- `POST /api/ledger/adjustments`
- `POST /api/ops/daily-closings`
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

The Node reference still keeps its command state in memory for oracle parity. The target Spring service now maps the first ledger command semantics onto PostgreSQL, Flyway, idempotency rows, locked balance projections, and durable outbox rows. Full Node retirement remains blocked until non-synthetic passkey operations and final retirement review are complete.
