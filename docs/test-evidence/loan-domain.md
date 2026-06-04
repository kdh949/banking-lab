# Loan Domain Evidence

Review date: 2026-06-04

## Scope

Phase 2 adds a synthetic-only loan vertical slice in the target Kotlin/Spring Boot stack:

- loan product catalog and applications;
- deterministic synthetic underwriting from `synthetic*` inputs only;
- maker-checker approval for loan execution;
- loan creation, disbursement, repayment schedule, repayment, overdue accrual, and full prepayment;
- balanced ledger postings for `LOAN_DISBURSEMENT`, `LOAN_REPAYMENT`, and `LOAN_PREPAYMENT`.

No real credit bureau, real KYC, real PII enrichment, external financial network, or real customer money path is used.

## Implemented Target Evidence

- `db/migrations/V022__loan_domain.sql` creates `loan_products`, `loan_applications`, `loans`, `loan_repayment_schedule`, `loan_interest_accruals`, and `loan_payments`.
- `LedgerCommandService` now posts loan disbursement and repayment/prepayment through append-only balanced ledger transactions and durable outbox events.
- `LoanService` exposes application, approval-backed execution, detail, repayment, prepayment, and accrual workflows.
- `StaffAccessService` executes `LOAN_EXECUTION` only after maker-checker approval and supports rejection.
- `screen-manifests/customer-web/CWB-501..504` and `screen-manifests/staff-terminal/LON-101..103` model the reusable command/inquiry/case screens.
- `packages/api-client` and the customer web API-backed smoke panel include the loan API path.

## Commands Run

- `npm run test:core-banking:integration -- --tests lab.banking.core.loan.LoanDomainIntegrationTest`
  - First sandboxed run failed with `java.net.SocketException: Operation not permitted` from Gradle file-lock socket creation.
  - First escalated run failed because the test Flyway path resolved to `services/core-banking/db/migrations`; fixed by walking parent directories to find repository `db/migrations`.
  - Final escalated run passed.
- `npm run packages:typecheck`: passed.
- `npm run next:customer-web:typecheck`: passed.
- `npm run validate:manifests`
  - Initial run failed because `CWB-502` lacked required inquiry `query`; fixed.
  - Second run failed because `LON-101` lacked required case `workflow`; fixed.
  - Final run passed with 81 manifests.
- `npm run test:screen-engine`: passed.

## Invariants Proven By `LoanDomainIntegrationTest`

- Maker cannot self-approve `LOAN_EXECUTION`.
- Approved loan execution creates one active loan and one `LOAN_DISBURSEMENT` ledger transaction.
- Disbursement, repayment, and prepayment ledger transactions have signed posting sum zero.
- Repayment schedule principal sums to original principal and interest is deterministic/non-zero for the test product.
- Customer loan detail is ownership-gated.
- Scheduled repayment decreases outstanding principal and creates one idempotent payment row.
- Overdue accrual is deterministic for a loan/accrual date and replays without duplicate accrual rows.
- Full prepayment closes the loan, clears outstanding principal, removes pending/overdue schedule rows, and replays with the same payment and ledger transaction.
- Account balance projections equal signed posting sums for the customer deposit account, bank suspense, and bank loan asset accounts.
- Customer available balance remains non-negative.
- Durable outbox events exist for loan disbursement, repayment, and prepayment.

## Remaining Risk

This slice proves the first loan product path and full-prepayment path. Future hardening should add partial prepayment schedule-recast cases, richer loan product variants, Temporal-backed long-running review states, and browser E2E against a running Spring API.
