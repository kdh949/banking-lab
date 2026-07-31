
# Payment three-way reconciliation and exception aging

## Decision

The reconciliation run compares three independently owned sources:

1. Payment Service instruction state and amount.
2. A read-only Core Banking bill-payment evidence API built from `ledger_transactions` and `ledger_postings`.
3. The immutable external clearing CSV lines introduced by the settlement import foundation.

Payment Service never queries Core Banking ledger tables directly. It reserves an idempotent run in a short local transaction, releases the transaction, calls the Core Banking evidence API, and then finalizes durable results in a second local transaction. A failed evidence call leaves a retryable `FAILED` run; replaying the same idempotency key reclaims the same PRR identifier.

## Run scope

A run is scoped to one immutable external import and its business date. Its candidate set is the union of:

- every external line in the import; and
- every Core Banking `BILL_PAYMENT` ledger transaction on the same business date.

The Payment Service instruction is then looked up for every union member. This supports missing-payment, missing-ledger, and missing-external detection without manufacturing an external source from internal data.

## Mismatch taxonomy

```text
MATCHED
MISSING_PAYMENT
MISSING_LEDGER
MISSING_EXTERNAL
AMOUNT_MISMATCH
STATUS_MISMATCH
DUPLICATE_EXTERNAL
VALUE_DATE_MISMATCH
LATE_SETTLEMENT
```

Precedence is deterministic: duplicate external rows, missing sources, state/balance/currency disagreement, amount disagreement, late value date, ordinary value-date disagreement, then matched.

## Exception operations fields

Every non-matched result persists:

- `owner_id`
- `detected_at`
- `due_at`
- computed `aging_days` and `overdue` on read
- `detected_reason`
- nullable `resolution`, `resolved_at`, and `approval_id`

The nullable resolution and approval columns are intentional handoff points for the next maker-checker adjustment workflow. Detection itself cannot resolve or approve a financial correction.

Run and exception reads require a business reason and create `PRA-*` access-audit rows. Command routes are limited to operations roles; audit and compliance roles are read-only.

All records, evidence, customers, payment instructions, ledger entries, and clearing lines remain synthetic-only.
