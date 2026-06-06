# Fee & Charge

## Goal

Calculate, waive, post, refund, and audit synthetic fees for transfers, cards,
ATM-like actions, loans, accounts, and operations.

## Current Code To Inspect

- `services/core-banking/src/main/kotlin/lab/banking/core/product/**`
- `screen-manifests/staff-terminal/FEE-*.json`
- `screen-manifests/ops-console/OPS-403.fee-posting-batch.json`
- `db/migrations/**`
- `docs/test-evidence/api-backed-channel-smoke.md`

## Target Folder Placement

Keep fee policies and ledger posting in `services/core-banking/.../product` or a
dedicated `core/fee` package if split internally.

## Backend Implementation Plan

- Implement fee policies, policy versions, exemptions, waivers, posting batches,
  targeted refunds, and fee reason codes.
- Post fees through ledger.
- Model fee waiver and refund as approved staff workflows.

## Database / Migration Plan

Use fee policies, fee policy versions, fee waiver requests, fee posting batches,
fee posting items, refund references, approvals, and ledger references.

## API / Event / Workflow Contracts

Expose fee inquiry, waiver request, waiver approval/rejection, policy change,
batch posting, and refund APIs. Emit fee charged, fee waived, fee refunded, and
fee policy changed events.

## Frontend / Screen Manifest Plan

Use `FEE-101`, `FEE-102`, `FEE-103`, and `OPS-403`. Customer web may show fee
line items in transaction history and statements.

## Security, Audit, Maker-Checker Controls

Fee waivers, refunds, and policy changes require maker-checker and audit.
Routine batch posting requires service identity and audit.

## Tests And Evidence

Run fee policy integration tests, ledger posting tests, staff-terminal typecheck,
manifest validation, and statement tests for fee line visibility.

## Acceptance Criteria

- Fees are calculated from durable policy versions.
- Fee postings are balanced ledger transactions.
- Waivers/refunds are approved and audited.
- Policy changes are versioned.

## Explicit Non-Goals

No real merchant fee settlement, real ATM network fee, or production pricing
engine.
