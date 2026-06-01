# Phase 6 FDS AML Reconciliation Drill Plan

## Drill 1: Duplicate FDS Release Approval

Expected result:

- Approval cannot be approved twice.
- Release idempotency key prevents duplicate ledger posting.
- Audit trail keeps the first execution.

## Drill 2: FDS Block After Hold

Expected result:

- Transfer result becomes `BLOCKED`.
- Ledger transaction count does not increase.
- FDS case timeline records checker-approved block.

## Drill 3: Closed-Day Posting Attempt

Expected result:

- Direct transfer on the closed business date fails.
- Error states that the business day is closed.
- Ledger invariants remain valid.

## Drill 4: Reconciliation Adjustment

Expected result:

- Unmatched item requires owner and reason.
- Adjustment requires maker-checker approval.
- Adjustment posts as a balanced ledger transaction on an open business date.
