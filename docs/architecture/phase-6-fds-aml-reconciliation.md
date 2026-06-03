# Phase 6 FDS AML Reconciliation Architecture

Phase 6 adds risk and operations workflows around the existing ledger core.

```text
Customer transfer
  -> FDS rule evaluation
  -> HELD transfer result + FDS case
  -> reviewer assign
  -> release/block request
  -> maker-checker approval
  -> release posts ledger transfer OR block closes without posting

Transfer monitoring
  -> AML rule evaluation
  -> AML case
  -> reviewer assign/comment
  -> closure request
  -> maker-checker approval
  -> STR simulation closure

Ops EOD
  -> ledger invariant check
  -> external file simulation
  -> reconciliation mismatch item
  -> close business date
  -> adjustment request
  -> maker-checker approval
  -> balanced ADJUSTMENT transaction on open day
```

## Runtime APIs

- `POST /api/customer/transfers`
- `GET /api/customer/transfers`
- `GET /api/staff/fds-cases`
- `GET /api/staff/fds-cases/{caseId}`
- `POST /api/staff/fds-cases/{caseId}/assign`
- `POST /api/staff/fds-cases/{caseId}/release-requests`
- `POST /api/staff/fds-cases/{caseId}/block-requests`
- `GET /api/staff/aml-cases`
- `POST /api/staff/aml-cases/{caseId}/assign`
- `POST /api/staff/aml-cases/{caseId}/comments`
- `POST /api/staff/aml-cases/{caseId}/closure-requests`
- `POST /api/ops/daily-closings`
- `GET /api/ops/reconciliation-items`
- `POST /api/ops/reconciliation-items/{itemId}/adjustment-requests`
- `POST /api/ledger/adjustments`

## Invariants

- Customer transfer FDS risk signals are synthetic-only flags such as `newDevice` and `firstTimeBeneficiary`.
- Held FDS transfers do not create ledger postings.
- FDS assignment moves only `HELD` cases into `INVESTIGATING`.
- FDS release posts exactly one ledger transfer after approval.
- FDS block never creates a ledger posting.
- AML case closure requires approval.
- EOD validates ledger invariants before closing.
- Closed business dates reject direct posting.
- Reconciliation correction uses a balanced adjustment transaction, not balance mutation.
