# FDS AML Reconciliation Evidence

## Scope

Phase 6 covers FDS rule evaluation, transaction hold/release/block, AML case simulation, daily closing, external-file reconciliation, unmatched item handling, and maker-checker adjustment approval.

## Commands

```bash
npm test
npm run validate:manifests
npm run evidence:phase6
node --check legacy-node-reference/ui/public/app.js
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test --tests 'lab.banking.core.fds.FdsAmlReconciliationWorkflowParityTest'
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.fds.FdsCaseApiParityIntegrationTest' --tests 'lab.banking.core.aml.AmlCaseApiParityIntegrationTest' --tests 'lab.banking.core.reconciliation.ReconciliationOpsApiParityIntegrationTest'
```

## Evidence

- Automated test file: `tests/fdsAmlReconciliation.test.mjs`
- Target unit test: `services/core-banking/src/test/kotlin/lab/banking/core/fds/FdsAmlReconciliationWorkflowParityTest.kt`
- Target API tests: `services/core-banking/src/integrationTest/kotlin/lab/banking/core/fds/FdsCaseApiParityIntegrationTest.kt`, `services/core-banking/src/integrationTest/kotlin/lab/banking/core/aml/AmlCaseApiParityIntegrationTest.kt`, `services/core-banking/src/integrationTest/kotlin/lab/banking/core/reconciliation/ReconciliationOpsApiParityIntegrationTest.kt`
- Generated evidence: `docs/test-evidence/generated/phase-6-fds-aml-reconciliation.json`
- Reconciliation report: `docs/reconciliation-reports/phase-6-eod-reconciliation.md`
- Manifests: `FDS-201`, `AML-201`, `OPS-201`

## Passed Checks

- High-risk transfer creates an FDS case and does not post before approval.
- Spring customer transfer creates HELD FDS cases with synthetic `newDevice` and `firstTimeBeneficiary` risk alerts.
- Spring FDS assign moves the customer-created case into investigation before release/block requests.
- FDS release requires checker approval and then posts the ledger transfer.
- FDS block updates transfer status without ledger posting.
- High-risk customer transfer generates an AML case.
- AML closure requires approval and records STR simulation output.
- EOD closing validates ledger totals and creates an owned unmatched item.
- Closed business day rejects direct mutation.
- Reconciliation adjustment posts as a balanced `ADJUSTMENT` transaction on the next open day.
