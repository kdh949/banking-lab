# Loan Service

## Goal

Manage synthetic loan products, applications, underwriting review, approval,
execution, repayment schedule, accrual, repayment, prepayment, delinquency, and
ledger postings.

## Current Code To Inspect

- `services/core-banking/src/main/kotlin/lab/banking/core/loan/**`
- `screen-manifests/customer-web/CWB-50*.json`
- `screen-manifests/staff-terminal/LON-*.json`
- `db/migrations/**`
- `docs/test-evidence/loan-domain.md`

## Target Folder Placement

Keep loan execution and repayment in `services/core-banking/.../loan` because
they post principal, interest, fees, and reversals to the ledger. Analytics or
credit scoring simulators may live outside core-banking.

## Backend Implementation Plan

- Implement loan product inquiry, application, review, approval, execution,
  repayment schedule generation, interest accrual, repayment, prepayment, and
  delinquency state.
- Require maker-checker for loan execution.
- Post disbursement, interest, fee, repayment, prepayment, and adjustment entries
  through ledger.

## Database / Migration Plan

Use loan products, applications, underwriting decisions, loans, schedules,
payments, accruals, delinquency records, approvals, idempotency keys, and ledger
references.

## API / Event / Workflow Contracts

Expose customer application/detail/repayment/prepayment APIs and staff review,
execution, and accrual APIs. Emit loan application, approval, execution, accrual,
repayment, and delinquency events.

## Frontend / Screen Manifest Plan

Customer screens use `CWB-501` through `CWB-504`. Staff terminal uses `LON-101`,
`LON-102`, and `LON-103`.

## Security, Audit, Maker-Checker Controls

Customer loan actions require ownership. Staff review requires reason. Execution
requires maker-checker and self-approval rejection. Delinquency actions are
audited.

## Tests And Evidence

Run loan domain integration tests, customer-web and staff-terminal typechecks,
ledger invariant tests, and manifest validation.

## Acceptance Criteria

- Loan lifecycle is durable and ledger-backed.
- Execution and repayment postings are balanced.
- Schedules and accruals are test-covered.
- Staff high-risk steps are approved and audited.

## Explicit Non-Goals

No real credit bureau, real underwriting, real collateral, real collections, or
production loan servicing integration.

