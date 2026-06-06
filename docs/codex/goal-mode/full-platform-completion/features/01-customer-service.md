# Customer Service

## Goal

Manage synthetic individual and corporate customer profiles, customer numbers,
customer status, contact data, risk references, staff inquiry, customer
self-service visibility, and profile change workflows.

## Current Code To Inspect

- `services/core-banking/src/main/kotlin/lab/banking/core/customer/**`
- `services/core-banking/src/main/kotlin/lab/banking/core/staff/**`
- `screen-manifests/staff-terminal/CST-*.json`
- `screen-manifests/customer-web/CWB-*.json`
- `apps/staff-terminal/src/**`
- `apps/customer-web/src/**`
- `packages/api-client/src/**`
- `db/migrations/**`
- `docs/implementation-coverage-matrix.md`
- `docs/test-evidence/api-backed-channel-smoke.md`

## Target Folder Placement

Keep ledger-coupled customer data and staff controls in
`services/core-banking/src/main/kotlin/lab/banking/core/customer`. If a separate
`services/customer-service` is introduced later, it must own non-ledger customer
master data only and expose contracts to core-banking instead of bypassing audit
or account ownership checks.

## Backend Implementation Plan

- Implement customer create, retrieve, search, status change, contact change,
  address change, and profile snapshot reads.
- Support individual and corporate synthetic customers with explicit customer
  type, synthetic identifier, lifecycle status, risk grade reference, and
  masking policy.
- Route staff-initiated changes through approval where they affect regulated or
  sensitive fields.
- Ensure customer self-service reads are scoped to the authenticated synthetic
  customer.

## Database / Migration Plan

Use durable tables for customers, customer profiles, customer identifiers,
customer contacts, customer profile change requests, and customer access audit
references. Do not rely on in-memory fixtures for target completion.

## API / Event / Workflow Contracts

- Staff inquiry APIs require `reason`.
- Customer self-service APIs require ownership checks.
- Profile changes emit durable Outbox events.
- High-risk customer status changes use maker-checker workflow state.

## Frontend / Screen Manifest Plan

Maintain staff screens `CST-001`, `CST-002`, `CST-003`, `CST-101`, `CST-102`,
`CST-103`, and `CST-104`. Customer-facing profile/account views remain under
`customer-web` manifests and shared API client calls.

## Security, Audit, Maker-Checker Controls

Mask PII by default, require reason for staff reads, audit every search/detail
view, require maker-checker for profile changes and privileged unmask, and reject
self-approval.

## Tests And Evidence

Run `StaffAccessApiParityIntegrationTest`, relevant customer-web typecheck,
`npm run validate:manifests`, `npm run test:screen-engine`, and conditional
Playwright smoke. Update `docs/implementation-coverage-matrix.md` and customer
evidence.

## Acceptance Criteria

- Customer creation and profile read/write are PostgreSQL-backed.
- Staff and customer paths enforce authorization and masking.
- Profile change requests are approved before execution.
- Audit events prove reason-required access.
- Evidence is updated from actual command output.

## Explicit Non-Goals

No real customer PII, no real identity registry, no production CRM integration,
and no external customer master data API.

