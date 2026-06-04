# EOD Closing Pipeline Evidence

Date: 2026-06-04

This evidence covers the Phase 1B end-of-day closing slice in the Kotlin/Spring target stack. It uses synthetic accounts, synthetic product/fee policies, PostgreSQL/Testcontainers, the existing reconciliation simulator, and no real customer data, real funds, real KYC, real payment network, or external financial institution API.

## Scope

- `V021__eod_closing_steps.sql` adds the `eod_closing_steps` operational read model.
- `POST /api/ops/eod/close` creates an `EOD_CLOSING` maker-checker approval request for `OPS-101`.
- `GET /api/ops/eod/{businessDate}` exposes the OPS-101 monitor read model.
- Approved EOD execution runs the ordered pipeline: interest accrual, interest posting, fee posting, reconciliation, daily closing.
- Each completed/skipped EOD step records durable step state, an outbox event, and an audit event.
- EOD execution now runs after the staff approval transaction commits, avoiding audit hash-chain self-blocking while preserving maker-checker approval.
- `EndOfDayClosingWorkflow` is registered with the Temporal worker as the contract placeholder for the EOD workflow path.

## Commands Run

```bash
npm run test:core-banking:integration -- --tests lab.banking.core.product.DepositProductApiIntegrationTest
npm run test:core-banking:integration -- --tests lab.banking.core.eod.EodClosingPipelineIntegrationTest
```

## Result

- `npm run test:core-banking:integration -- --tests lab.banking.core.product.DepositProductApiIntegrationTest`: pass after rerun outside the sandbox because Gradle file-lock socket creation was blocked.
- First `EodClosingPipelineIntegrationTest` sandbox run failed with the same Gradle socket restriction.
- First escalated EOD run hung while `AuditEventAppender` waited on the audit hash-chain lock. Thread dump showed the EOD subtransaction appending audit inside the still-open staff approval transaction.
- Fix: approve `EOD_CLOSING` inside the staff transaction, return after commit, then execute the multi-step EOD pipeline.
- `npm run test:core-banking:integration -- --tests lab.banking.core.eod.EodClosingPipelineIntegrationTest`: pass after the transaction-boundary fix.

## Invariants Verified

- OPS-101 GET returns five durable EOD step rows.
- Missing close reason returns `POLICY_REASON_REQUIRED` and creates no approval.
- Valid close request creates one pending `EOD_CLOSING` approval.
- Maker self-approval returns `MAKER_CHECKER_SELF_APPROVAL_REJECTED` and does not close the business date.
- Checker approval executes all EOD steps and returns a closed monitor with `ledgerTotalHash`.
- Duplicate close request for an already closed date is idempotent and replays the existing approval/monitor.
- Posting into a closed business date returns `LEDGER_CLOSED_DAY_IMMUTABLE`.
- Interest and fee posting batches are balanced ledger transactions.
- `Σposting=0` for every transaction.
- Customer and bank-suspense balance projections equal signed ledger postings after EOD.
- Customer available balance remains non-negative.
- EOD step events are durable outbox rows.
- EOD step audit events are written for OPS-101.
- A simulated worker restart after the first committed step resumes from the completed step and finishes the remaining steps without duplicate interest accrual.

## Synthetic Boundary

The test seeds only synthetic customers (`CUS-EOD-*`), accounts (`ACC-EOD-*`), products, fees, and operator IDs. Reconciliation uses the existing `MATCHED` simulator mode. No real money, raw PII, real KYC, real payment network, or external financial institution API is used.

## Remaining Risk

The current restart evidence is an integration-level resume simulation over durable EOD step state. A live Temporal worker/container restart drill for `EndOfDayClosingWorkflow` remains a later hardening item once the EOD workflow activities are fully wired to Temporal.
