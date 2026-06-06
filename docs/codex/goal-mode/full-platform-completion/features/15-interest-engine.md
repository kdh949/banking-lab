# Interest Engine

## Goal

Calculate, accrue, post, reverse, and evidence synthetic deposit interest, loan
interest, and overdue interest using product terms and ledger postings.

## Current Code To Inspect

- `services/core-banking/src/main/kotlin/lab/banking/core/product/**`
- `services/core-banking/src/main/kotlin/lab/banking/core/loan/**`
- `screen-manifests/ops-console/OPS-401.interest-accrual-run.json`
- `screen-manifests/ops-console/OPS-402.interest-posting-batch.json`
- `db/migrations/**`
- `docs/implementation-coverage-matrix.md`

## Target Folder Placement

Deposit interest logic belongs in `core/product`; loan interest logic may live in
`core/loan` while shared calculation utilities can be in `core/product` or
`core/common`. Posting must go through ledger.

## Backend Implementation Plan

- Implement accrual calculation by product/account/loan, rate version, day-count
  convention, business date, and eligibility.
- Persist accrual results and posting batches.
- Post interest payments/charges through ledger.
- Support reversals or adjustments for corrected accruals.

## Database / Migration Plan

Use interest accruals, interest posting batches, batch items, rate versions,
loan accruals, overdue interest records, and ledger references.

## API / Event / Workflow Contracts

Expose ops accrual run, posting batch, preview, execution, and history APIs.
Emit interest accrued and interest posted events.

## Frontend / Screen Manifest Plan

Use `OPS-401` and `OPS-402`. Statements show interest line items. Admin/product
screens show rate versions and scheduled changes.

## Security, Audit, Maker-Checker Controls

Routine batch posting may be service/ops controlled. Manual corrections require
maker-checker, audit, and balanced adjustment transactions.

## Tests And Evidence

Test accrual calculation, versioned rate use, batch idempotency, posting balance,
closed business date rejection, reversal/adjustment, and statement visibility.

## Acceptance Criteria

- Accruals are durable and reproducible.
- Interest posting creates balanced ledger entries.
- Product/loan terms drive calculations.
- Evidence proves idempotent batch behavior.

## Explicit Non-Goals

No real tax reporting, real regulatory interest disclosure, or production
actuarial/pricing integration.
