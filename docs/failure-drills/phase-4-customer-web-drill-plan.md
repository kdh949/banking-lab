# Phase 4 Customer Web Failure Drill Plan

## Drill: Customer Transfer Retry

- Injection: submit the same transfer twice with the same idempotency key.
- Expected impact: second response returns the first result and no duplicate ledger transaction is appended.
- Evidence: `tests/customerWeb.test.mjs`.

## Drill: FDS-Held Transfer

- Injection: submit a transfer at or above the synthetic FDS threshold.
- Expected impact: result is `HELD`, FDS case is created, no ledger posting occurs.
- Evidence: `tests/customerWeb.test.mjs`.

## Drill: Failed Transfer

- Injection: submit an invalid amount.
- Expected impact: result is `FAILED`, no ledger posting occurs.
- Evidence: `tests/customerWeb.test.mjs`.
