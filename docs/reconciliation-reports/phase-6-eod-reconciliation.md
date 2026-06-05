# Phase 6 EOD Reconciliation Report

## Reconciliation Model

The reconciliation engine compares internal posted transfer entries with a synthetic external institution file.

Internal source:

- `ledger_transactions`
- `ledger_postings`
- balance projection from postings

External source:

- `simulateExternalInstitutionFile`
- synthetic `OPENBANKING-SIM` entries only

## Mismatch Handling

Unmatched or mismatched entries become reconciliation items with:

- status `OPEN`
- owner `ops01`
- mismatch type (`AMOUNT_MISMATCH`, `MISSING_EXTERNAL`, `UNEXPECTED_EXTERNAL`, `DUPLICATE_EXTERNAL`, `STALE_EXTERNAL`, or `STATUS_MISMATCH`)
- internal amount
- external amount
- synthetic feed file id
- detected reason
- timeline

The synthetic external file simulator supports matched, amount mismatch, missing external,
external-only, duplicate external, stale external, and status-mismatch modes. These modes
are stored as durable reconciliation item metadata and surfaced through the shared API
client for staff and operations screens.

## Adjustment Control

Adjustment requests use `RECONCILIATION_ADJUSTMENT` approval.

After checker approval, the ledger posts a balanced `ADJUSTMENT` transaction against `BANK-SUSPENSE` and the target account. The original closed business date is not mutated; the adjustment is posted on an open business date.

## Evidence

`docs/test-evidence/generated/phase-6-fds-aml-reconciliation.json` records the passing checks for ledger validation, unmatched item creation, closed-day rejection, and approved adjustment posting. `ReconciliationOpsApiParityIntegrationTest` also covers the target Spring/PostgreSQL mismatch taxonomy for duplicate, stale, external-only, and missing-external simulator feeds.
