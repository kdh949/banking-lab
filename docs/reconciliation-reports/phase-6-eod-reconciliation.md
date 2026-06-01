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
- mismatch type
- internal amount
- external amount
- timeline

## Adjustment Control

Adjustment requests use `RECONCILIATION_ADJUSTMENT` approval.

After checker approval, the ledger posts a balanced `ADJUSTMENT` transaction against `BANK-SUSPENSE` and the target account. The original closed business date is not mutated; the adjustment is posted on an open business date.

## Evidence

`docs/test-evidence/generated/phase-6-fds-aml-reconciliation.json` records the passing checks for ledger validation, unmatched item creation, closed-day rejection, and approved adjustment posting.
