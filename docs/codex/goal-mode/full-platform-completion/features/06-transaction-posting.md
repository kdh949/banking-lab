# Transaction Posting

## Goal

Validate, authorize, post, cancel, reverse, and adjust synthetic transactions for
deposit, withdrawal, transfer, fee, interest, loan, card, payment, and
reconciliation flows.

## Current Code To Inspect

- `services/core-banking/src/main/kotlin/lab/banking/core/ledger/application/**`
- `services/core-banking/src/main/kotlin/lab/banking/core/ledger/api/**`
- `services/core-banking/src/main/kotlin/lab/banking/core/eventing/**`
- `db/migrations/**`
- `tests/ledger*.test.mjs`
- `docs/test-evidence/limit-enforcement.md`

## Target Folder Placement

Posting orchestration belongs in `services/core-banking/.../ledger/application`.
Domain-specific callers may live in their bounded contexts, but all final
posting must pass through the ledger command service.

## Backend Implementation Plan

- Validate account state, business date, amount, currency, limits, holds,
  product rules, and idempotency before posting.
- Persist postings, idempotency result, audit event, and Outbox event atomically.
- Model cancellation as pre-posting command rejection where possible.
- Model finalized correction as reversal or balanced adjustment only.

## Database / Migration Plan

Use idempotency tables, ledger transactions/postings, outbox events, correction
request tables, and business-date state. Add unique keys for command identity.

## API / Event / Workflow Contracts

Expose posting command APIs only through structured command contracts. Emit
events after durable transaction commit through Outbox workers.

## Frontend / Screen Manifest Plan

Posting itself is not a standalone user screen. It is invoked by transfer, loan,
card, product, fee, interest, payment, reconciliation, and staff correction
screens.

## Security, Audit, Maker-Checker Controls

Manual posting, correction, adjustment, and reversal commands are high-risk.
Automated routine posting still requires service identity, idempotency, and
audit/outbox evidence.

## Tests And Evidence

Test success, validation failure, idempotent replay, conflicting replay, no
side-effect failure, reversal, adjustment, closed date, and concurrent limit
usage.

## Acceptance Criteria

- All financial features use posting service.
- Failed validations leave no unsafe partial postings.
- Idempotency is durable and tested.
- Outbox and audit evidence exist for successful postings.

## Explicit Non-Goals

No external payment network posting and no mutable transaction correction.
