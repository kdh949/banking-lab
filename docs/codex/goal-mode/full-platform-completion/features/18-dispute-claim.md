# Dispute / Claim

## Goal

Manage synthetic card chargebacks, transfer disputes, wrong-deposit reports,
complaints, refund requests, investigation, approval, reversal/adjustment, and
customer/staff case visibility.

## Current Code To Inspect

- `services/core-banking/src/main/kotlin/lab/banking/core/complaint/**`
- `services/core-banking/src/main/kotlin/lab/banking/core/card/**`
- `services/core-banking/src/main/kotlin/lab/banking/core/reconciliation/**`
- `screen-manifests/complaint-portal/CMP-*.json`
- `screen-manifests/staff-terminal/CMP-*.json`
- `docs/test-evidence/phase-5-complaint-workflow.md`

## Target Folder Placement

Complaint and dispute case state can live in `services/core-banking/.../complaint`
when it triggers ledger corrections, approvals, and audit. If volume grows, split
case intake to a complaint service while ledger correction stays in core-banking.

## Backend Implementation Plan

- Implement dispute/claim intake, classification, ownership, SLA, attachments
  metadata, timeline, investigation, answer draft, approval, closure, reopen,
  refund/reversal/adjustment, and customer confirmation.
- Connect card disputes and transfer disputes to source transactions.
- Use Temporal-compatible workflow state for long-running cases.

## Database / Migration Plan

Use cases, dispute details, attachments metadata, comments, timeline, approvals,
refund references, ledger correction references, SLA state, and workflow IDs.

## API / Event / Workflow Contracts

Expose customer complaint/dispute APIs and staff case management APIs. Emit case
created, classified, assigned, approved, refunded, answered, closed, and reopened
events.

## Frontend / Screen Manifest Plan

Use complaint portal `CMP-101` through `CMP-108` and staff `CMP-201`,
`CMP-202`. Add card/transfer dispute screens through manifests if missing.

## Security, Audit, Maker-Checker Controls

Customer reads require ownership. Staff reads require reason. Refunds, reversals,
and final answers require maker-checker. Timeline must not expose internal actor
details to customers unless intended.

## Tests And Evidence

Test intake, classification, answer approval, closure, reopen, refund/reversal,
self-approval rejection, unauthorized access, and customer/staff visibility.

## Acceptance Criteria

- Dispute/claim cases are durable and workflow-backed.
- Financial corrections use ledger reversal or adjustment.
- Customer and staff screens are API-backed.
- SLA/timeline/audit evidence exists.

## Explicit Non-Goals

No real card network chargeback, no real complaint intake from the public, and no
production legal/regulatory filing.
