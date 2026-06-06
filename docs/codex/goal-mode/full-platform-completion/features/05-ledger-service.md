# Ledger Service

## Goal

Provide the authoritative double-entry ledger for all synthetic money movement:
transactions, postings, currencies, business dates, reversals, adjustments,
closing guards, projected balances, and invariants.

## Current Code To Inspect

- `services/core-banking/src/main/kotlin/lab/banking/core/ledger/**`
- `db/migrations/**`
- `formal/**`
- `contracts/events/ledger-transaction-posted.schema.json`
- `docs/test-evidence/phase-2-ledger-core.md`
- `docs/test-evidence/formal-ledger-verification.md`

## Target Folder Placement

Keep the ledger in `services/core-banking/.../ledger`. It is the center of the
modular monolith and must not be bypassed by Payment Service, Card Service, Loan
Service, Fee & Charge, Interest Engine, or Transfer Service.

## Backend Implementation Plan

- Implement append-only ledger transactions and postings.
- Enforce balanced postings per transaction and currency.
- Support deposit, withdrawal, internal transfer, reversal, adjustment, fee,
  interest, loan, card, reconciliation, and payment posting types.
- Enforce closed business date guard.
- Project balances from postings.
- Record idempotency and Outbox events in the same durable transaction.

## Database / Migration Plan

Use tables for ledger transactions, ledger postings, idempotency keys, balance
projections, business dates, daily closings, and outbox events. Add constraints
where PostgreSQL can enforce integrity directly.

## API / Event / Workflow Contracts

Ledger command APIs must be idempotent and return structured errors. Events must
include transaction identity, posting summary, business date, source command, and
synthetic-only metadata.

## Frontend / Screen Manifest Plan

Ledger is surfaced through account history, transaction detail, certificates,
staff transaction inquiry, audit screens, ops closing screens, and reconciliation
screens.

## Security, Audit, Maker-Checker Controls

Ledger reads by staff require reason. Manual adjustments and corrections require
maker-checker. Source rows are never updated or deleted after finalization.

## Tests And Evidence

Run ledger unit and integration tests, formal ledger verification, statement
read-model tests, reconciliation tests, and backup/restore drills.

## Acceptance Criteria

- Ledger invariants are enforced in code, DB tests, and formal checks.
- Every financial feature posts through the ledger.
- Reversal and adjustment are balanced and auditable.
- Closed dates reject direct posting.

## Explicit Non-Goals

No real money, no real settlement ledger, and no direct balance mutation.
