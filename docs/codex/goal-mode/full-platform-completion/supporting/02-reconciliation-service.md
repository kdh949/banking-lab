# Reconciliation Service

## Goal

Model synthetic reconciliation for ledger projections, external simulator files,
payment/card/transfer mismatches, unmatched item review, adjustment approval, and
closed-date controls.

## Current Code To Inspect

- `services/core-banking/src/main/kotlin/lab/banking/core/reconciliation/**`
- `services/core-banking/src/main/kotlin/lab/banking/core/eod/**`
- `screen-manifests/ops-console/OPS-201.reconciliation-items.json`
- `screen-manifests/ops-console/OPS-202.reconciliation-adjustment.json`
- `screen-manifests/ops-console/OPS-301.reconciliation-parameters.json`
- `docs/test-evidence/fds-aml-reconciliation.md`
- `docs/reconciliation-reports/**`

## Target Folder Placement

Keep reconciliation logic that posts adjustments in `services/core-banking`.
External simulator feeds can live in `services/external-simulators` or
`services/payment-service` depending on the source.

## Implementation Plan

- Generate synthetic expected/actual reconciliation records.
- Detect unmatched, duplicate, stale, and amount-mismatch items.
- Route adjustment requests through maker-checker.
- Post corrections as balanced ledger adjustments only.
- Integrate with daily closing and closed-business-date guard.

## Tests And Evidence

Test unmatched item creation, parameter changes, adjustment approval,
self-approval rejection, closed-date rejection, balanced posting, and ops-console
visibility. Update reconciliation reports and evidence after commands run.

## Acceptance Criteria

- Reconciliation mismatch handling is durable.
- Corrections never edit balances directly.
- Adjustments are approved, balanced, and auditable.
- Ops screens are API-backed.

## Explicit Non-Goals

No real clearing network, real bank statement import, or production accounting
reconciliation.
