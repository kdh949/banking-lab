# Back Office

## Goal

Provide internal staff operations for inquiry, command, approval, correction,
case handling, reconciliation, FDS/AML decisions, operational retries, and audit
review through a controlled staff terminal.

## Current Code To Inspect

- `services/core-banking/src/main/kotlin/lab/banking/core/staff/**`
- `services/core-banking/src/main/kotlin/lab/banking/core/approval/**`
- `apps/staff-terminal/src/**`
- `screen-manifests/staff-terminal/**`
- `docs/test-evidence/staff-terminal-renderer-e2e.md`
- `docs/test-evidence/api-backed-channel-smoke.md`

## Target Folder Placement

Staff-specific orchestration belongs in `services/core-banking/.../staff` and
`apps/staff-terminal`. Shared approval/audit logic belongs in `core/approval`
and `core/audit`.

## Backend Implementation Plan

- Implement reason-required inquiry for customers, accounts, transactions, KYC,
  complaints, FDS/AML, reconciliation, limits, fees, products, loans, cards, and
  payments.
- Implement command request, approval, rejection, execution, retry, and audit
  read APIs.
- Support transaction-code navigation and workflow timelines.

## Database / Migration Plan

Use approval requests, staff access audit, workflow references, command request
tables, operational retry records, and parameter change request tables.

## API / Event / Workflow Contracts

Expose staff inquiry, command, approval inbox, audit log, workflow timeline, and
exception/retry APIs. Emit staff command requested/approved/executed events.

## Frontend / Screen Manifest Plan

Use manifest-driven staff terminal screens, transaction code input, role-aware
menus, customer context panel, masked PII, approval inbox, audit panel, workflow
timeline, and exception/retry panel.

## Security, Audit, Maker-Checker Controls

Back Office is control-heavy. Every sensitive read needs reason. Every high-risk
command needs maker-checker. Self-approval is rejected. PII is masked by default.

## Tests And Evidence

Run staff access integration tests, staff-terminal typecheck, manifest validation,
screen-engine tests, Playwright smoke, Keycloak smoke, and evidence refresh.

## Acceptance Criteria

- Staff terminal is not just static UI.
- Core inquiry and command screens are API-backed.
- Approval/audit controls are visible and tested.
- Role policy controls menu and command availability.

## Explicit Non-Goals

No real branch operations, real employee accounts, real HR integration, or live
production operations console.

