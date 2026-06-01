# Phase 1 Failure Drill Plan

## Drill: Duplicate Transfer Retry

- Injection: submit the same transfer payload twice with the same idempotency key.
- Expected impact: one ledger transaction is created; the second response is marked replayed.
- Invariant: idempotent request creates at most one transaction.
- Evidence: `tests/runtime.test.mjs`.

## Drill: Missing Staff Lookup Reason

- Injection: call staff customer search without a reason.
- Expected impact: request is rejected and no customer data is returned.
- Invariant: no sensitive staff access without business reason and audit path.
- Evidence: `tests/runtime.test.mjs`.

## Drill: Maker Self-Approval

- Injection: maker attempts to approve the same high-risk request.
- Expected impact: approval is rejected.
- Invariant: high-risk staff operation requires maker-checker separation.
- Evidence: `tests/makerChecker.test.mjs`.
