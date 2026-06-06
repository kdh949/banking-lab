# Admin Console

## Goal

Provide administrative control for platform status, security policy, roles/menus,
parameters, batches, monitoring links, evidence, feature coverage, and controlled
configuration changes.

## Current Code To Inspect

- `services/core-banking/src/main/kotlin/lab/banking/core/admin/**`
- `services/core-banking/src/main/kotlin/lab/banking/core/parameters/**`
- `apps/admin-console/src/**`
- `screen-manifests/admin-console/**`
- `infra/**`
- `docs/test-evidence/parameter-admin-apis.md`

## Target Folder Placement

Admin APIs belong in `services/core-banking/.../admin` and `core/parameters`.
Frontend belongs in `apps/admin-console`. Platform assets remain under `infra`.

## Backend Implementation Plan

- Implement platform control dashboard data, security policy parameters,
  menu/role parameters, system status, batch status, feature coverage references,
  evidence links, and controlled parameter change workflows.
- Keep configuration changes versioned and scheduled where applicable.

## Database / Migration Plan

Use parameter tables, parameter versions, parameter change requests, approvals,
security policy parameters, role/menu parameters, audit retention parameters,
batch status, and evidence references.

## API / Event / Workflow Contracts

Expose admin dashboard, parameter current/history/change, approval, rollback,
system health, and evidence read APIs. Emit parameter changed and admin access
events.

## Frontend / Screen Manifest Plan

Use `ADM-101`, `ADM-201`, and `ADM-301`. Additional parameter screens should use
the Parameter Template instead of bespoke copies.

## Security, Audit, Maker-Checker Controls

Admin actions are high-risk. Require admin/compliance roles, step-up where
configured, maker-checker for parameter changes, and audit for every read/change.

## Tests And Evidence

Run parameter admin integration tests, admin-console typecheck/build, manifest
validation, Keycloak smoke, security posture check, and platform evidence checks.

## Acceptance Criteria

- Admin Console is API-backed.
- Parameter changes are versioned and approved.
- Platform/evidence state is visible.
- Security policy cannot be bypassed through UI.

## Explicit Non-Goals

No real production admin console, no real infrastructure mutation from the lab
UI, and no secret management for production systems.
