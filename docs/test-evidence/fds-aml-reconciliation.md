# FDS AML Reconciliation Evidence

## Scope

Phase 6 covers FDS rule evaluation, transaction hold/release/block, AML case simulation, daily closing, external-file reconciliation, unmatched item handling, and maker-checker adjustment approval.

## Commands

```bash
npm test
npm run validate:manifests
npm run evidence:phase6
node --check packages/ui/public/app.js
```

## Evidence

- Automated test file: `tests/fdsAmlReconciliation.test.mjs`
- Generated evidence: `docs/test-evidence/generated/phase-6-fds-aml-reconciliation.json`
- Reconciliation report: `docs/reconciliation-reports/phase-6-eod-reconciliation.md`
- Manifests: `FDS-201`, `AML-201`, `OPS-201`

## Passed Checks

- High-risk transfer creates an FDS case and does not post before approval.
- FDS release requires checker approval and then posts the ledger transfer.
- FDS block updates transfer status without ledger posting.
- High-risk customer transfer generates an AML case.
- AML closure requires approval and records STR simulation output.
- EOD closing validates ledger totals and creates an owned unmatched item.
- Closed business day rejects direct mutation.
- Reconciliation adjustment posts as a balanced `ADJUSTMENT` transaction on the next open day.
