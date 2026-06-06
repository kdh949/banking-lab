# Balance Service

## Goal

Provide current balance, available balance, held amount, projected balance,
certificate balance, and history-backed balance explanations derived from ledger
postings.

## Current Code To Inspect

- `services/core-banking/src/main/kotlin/lab/banking/core/ledger/**`
- `services/core-banking/src/main/kotlin/lab/banking/core/statement/**`
- `screen-manifests/customer-web/CWB-101.account-overview.json`
- `screen-manifests/staff-terminal/ACC-102.account-detail.json`
- `docs/test-evidence/statement-read-models.md`
- `docs/test-evidence/postgres-backup-restore-drill.md`

## Target Folder Placement

Balance projection logic belongs in `services/core-banking/.../ledger` or a
dedicated `core/balance` package backed by ledger postings. It must not become a
separate mutable balance service.

## Backend Implementation Plan

- Project ledger/current balance from postings.
- Derive available balance from ledger balance minus holds, authorizations,
  pending transfers, and restrictions.
- Support as-of balance, certificate balance, and reconciliation comparison.
- Rebuild projections from postings during verification and backup/restore
  drills.

## Database / Migration Plan

Use `account_balance_projections` or equivalent projection tables with rebuild
tests proving equality to signed posting sums. Hold and authorization tables feed
available-balance calculation.

## API / Event / Workflow Contracts

Expose balance reads for customer, staff, statement, certificate, limit, card,
loan, payment, and reconciliation flows. Do not expose write APIs for balances.

## Frontend / Screen Manifest Plan

Show masked account identifiers, current balance, available balance, holds, and
certificate data in customer, staff, statement, and ops screens.

## Security, Audit, Maker-Checker Controls

Customer reads require ownership. Staff reads require reason. Balance
certificates and privileged balance evidence create audit events.

## Tests And Evidence

Run ledger projection tests, statement read-model tests, backup/restore drill,
formal ledger checks, and account/card/transfer integration tests that affect
holds and available balance.

## Acceptance Criteria

- No feature directly mutates balances as truth.
- Projection rebuild equals posting sums.
- Available balance respects holds and authorizations.
- Balance views are authorized and audited.

## Explicit Non-Goals

No real bank balance, no cross-bank balance inquiry, and no mutable balance edit.
