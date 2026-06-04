# EOD Worker Restart Mid-Pipeline Drill

Date: 2026-06-04

## Purpose

Verify that an approved synthetic end-of-day close can resume after a worker stops mid-pipeline without replaying completed ledger work or mutating ledger source rows.

## Current Executable Drill

`EodClosingPipelineIntegrationTest.approved EOD pipeline resumes after a committed first step` simulates a restart boundary:

1. Seed synthetic customer, account, product, fee, and opening-balance ledger rows.
2. Create and approve an `EOD_CLOSING` maker-checker approval.
3. Initialize `eod_closing_steps`.
4. Execute the first step, interest accrual, and mark `INTEREST_ACCRUAL` as `COMPLETED`.
5. Call `EodClosingService.applyApprovedClose` again with the approved request.
6. Verify the pipeline skips the completed first step, posts interest, posts fees, reconciles, closes the date, and preserves balanced postings and projection equality.

Command:

```bash
npm run test:core-banking:integration -- --tests lab.banking.core.eod.EodClosingPipelineIntegrationTest
```

Result on 2026-06-04: pass after sandbox escalation for Gradle file-lock socket access.

## Controls Verified

- Step state is durable in PostgreSQL.
- Completed steps are not rerun.
- Remaining steps run to completion after resume.
- Ledger postings remain balanced.
- Balance projections still equal signed postings.
- Closed-date posting remains rejected.
- Synthetic-only boundary is preserved.

## Not Yet Claimed

This drill does not claim a live Temporal worker container restart for EOD activities. The Temporal workflow contract is registered, but the EOD pipeline currently runs through Spring service orchestration with durable step state. A live Temporal EOD activity restart drill should be added after EOD activities are fully delegated to Temporal.
