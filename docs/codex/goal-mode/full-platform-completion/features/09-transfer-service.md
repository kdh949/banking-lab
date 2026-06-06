# Transfer Service

## Goal

Handle internal transfer, scheduled transfer, held transfer review, cancellation,
status inquiry, idempotency, customer result visibility, staff inquiry, and
integration with ledger posting or payment rails.

## Current Code To Inspect

- `services/core-banking/src/main/kotlin/lab/banking/core/customer/CustomerTransfer*`
- `services/core-banking/src/main/kotlin/lab/banking/core/ledger/**`
- `screen-manifests/customer-web/CWB-201.internal-transfer.json`
- `screen-manifests/customer-web/CWB-202.transfer-result.json`
- `screen-manifests/customer-web/CWB-203.transfer-status.json`
- `screen-manifests/staff-terminal/TRF-*.json`
- `docs/test-evidence/phase-4-customer-web.md`

## Target Folder Placement

Keep internal transfer orchestration in `services/core-banking` because it posts
directly to the ledger. External or interbank simulation can be split later into
`services/payment-service` or `services/external-simulators`.

## Backend Implementation Plan

- Validate source/destination account, ownership, KYC, limits, holds, account
  state, idempotency, and business date.
- Post internal transfers through ledger command service.
- Model held transfers for FDS review without unsafe postings.
- Support scheduled transfer state and execution worker or workflow.
- Expose customer and staff status reads.

## Database / Migration Plan

Use transfer requests/results, scheduled transfer definitions, held-transfer
cases, idempotency keys, ledger references, workflow references, and outbox
events.

## API / Event / Workflow Contracts

Customer transfer APIs return posted, held, failed, canceled, or scheduled
status. Events include transfer requested, posted, held, released, blocked, and
canceled.

## Frontend / Screen Manifest Plan

Customer web owns transfer entry/result/status. Staff terminal owns transfer
inquiry and cancel/reject inquiry screens.

## Security, Audit, Maker-Checker Controls

Customer transfer requires ownership. Staff reads require reason. Held transfer
release/block requires FDS maker-checker or workflow approval.

## Tests And Evidence

Test internal posting, idempotent retry, insufficient balance, limit exceeded,
held transfer no-posting behavior, release/block, scheduled execution, and
customer/staff status consistency.

## Acceptance Criteria

- Internal transfers are ledger-backed and idempotent.
- Held/failed transfers do not create unsafe postings.
- Status is visible in customer and staff channels.
- FDS review paths are audited.

## Explicit Non-Goals

No real interbank transfer network and no real payment rail integration.

