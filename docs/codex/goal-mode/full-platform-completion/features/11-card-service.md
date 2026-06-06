# Card Service

## Goal

Manage synthetic debit/credit card lifecycle: issue, activation, 3DS simulation,
authorization, hold, capture, cancel, reversal, lost-card status, card limits,
and dispute hooks.

## Current Code To Inspect

- `services/core-banking/src/main/kotlin/lab/banking/core/card/**`
- `screen-manifests/customer-web/CWB-60*.json`
- `db/migrations/**`
- `docs/test-evidence/card-domain.md`
- `packages/api-client/src/**`

## Target Folder Placement

Keep card ledger-impacting behavior in `services/core-banking/.../card` because
captures and reversals post to ledger. Network simulation can later move to
`services/external-simulators`.

## Backend Implementation Plan

- Implement card issue, status, activation, lost/stolen report, 3DS simulation,
  authorization hold, capture, cancel, reversal, and limit enforcement.
- Separate authorization hold from capture posting.
- Ensure reversal references original capture or authorization where relevant.

## Database / Migration Plan

Use cards, card limits, card usage counters, card authorizations, card captures,
3DS simulations, lost-card events, and ledger references.

## API / Event / Workflow Contracts

Expose customer card APIs and staff/admin card inquiry where needed. Emit card
issued, authorization held, capture posted, cancellation, reversal, and loss
events through Outbox.

## Frontend / Screen Manifest Plan

Use customer-web `CWB-601` through `CWB-606`. Add staff or back-office screens
for card disputes and manual review only through manifests.

## Security, Audit, Maker-Checker Controls

Customer card actions require ownership. Staff card access requires reason.
High-risk manual reversals or limit increases require maker-checker.

## Tests And Evidence

Run card domain integration tests, customer-web typecheck, manifest validation,
screen engine tests, and ledger invariant checks for captures/reversals.

## Acceptance Criteria

- Card lifecycle is durable and API-backed.
- Capture creates balanced ledger postings.
- Reversal and cancellation are auditable.
- No real card network is used.

## Explicit Non-Goals

No real card issuing, PAN handling, PCI production scope, acquirer network, or
payment processor integration.

