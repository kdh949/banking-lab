# Account Service

## Goal

Manage account opening, product binding, account state, aliases, holds, closing,
customer ownership, account restrictions, and account lifecycle audit.

## Current Code To Inspect

- `services/core-banking/src/main/kotlin/lab/banking/core/ledger/**`
- `services/core-banking/src/main/kotlin/lab/banking/core/customer/**`
- `services/core-banking/src/main/kotlin/lab/banking/core/staff/**`
- `screen-manifests/staff-terminal/ACC-*.json`
- `screen-manifests/customer-web/CWB-101.account-overview.json`
- `db/migrations/**`
- `docs/test-evidence/phase-2-ledger-core.md`

## Target Folder Placement

Account lifecycle state remains ledger-coupled and belongs in
`services/core-banking/.../ledger` or a dedicated `core/account` package if split
internally. Do not create a separate account service until transactional ledger
invariants can still be preserved.

## Backend Implementation Plan

- Implement account opening after KYC eligibility and product validation.
- Support status transitions: active, dormant, restricted, held, closing, closed.
- Support account aliases and synthetic account numbers.
- Apply account holds/release through approval and workflow state.
- Reject postings to closed or restricted accounts according to policy.

## Database / Migration Plan

Persist accounts, aliases, account status history, account holds, hold requests,
account restrictions, and links to customer, product, ledger postings, and
balance projections.

## API / Event / Workflow Contracts

Expose account open, detail, search, status, hold, release, close, certificate,
and ownership APIs. Emit account-opened, account-status-changed, and hold events
through Outbox.

## Frontend / Screen Manifest Plan

Use `ACC-101`, `ACC-102`, `ACC-103`, `ACC-104`, customer overview/detail, and
statement/certificate screens. Staff reads require reason; customer reads require
ownership.

## Security, Audit, Maker-Checker Controls

Account holds, releases, closures, and restriction changes are high-risk staff
commands. Require reason, role policy, maker-checker, audit, and structured
state-transition errors.

## Tests And Evidence

Test opening, duplicate idempotency key replay, account hold/release, closed
account posting rejection, ownership checks, audit, and integration with ledger
posting commands.

## Acceptance Criteria

- Account lifecycle is PostgreSQL-backed.
- Posting paths enforce account state.
- Staff and customer reads are authorized.
- High-risk account commands are maker-checker protected.

## Explicit Non-Goals

No real account numbers, no real bank account opening, and no external banking
network enrollment.
