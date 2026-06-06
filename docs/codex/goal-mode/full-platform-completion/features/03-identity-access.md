# Identity & Access

## Goal

Manage synthetic customer, staff, admin, auditor, system, and service identities
with OIDC/JWT, Keycloak configuration, RBAC/ABAC, sessions, MFA/WebAuthn
simulation or live-readiness evidence, trusted devices, and authorization audit.

## Current Code To Inspect

- `services/core-banking/src/main/kotlin/lab/banking/core/security/**`
- `packages/auth-client/src/**`
- `infra/keycloak/**`
- `infra/security/**`
- `apps/*/src/**`
- `docs/test-evidence/hardening-h2-secure-auth.md`
- `docs/test-evidence/keycloak-live-realm-smoke.md`

## Target Folder Placement

Keep API enforcement in `services/core-banking/.../security`. Shared frontend
token/session helpers belong in `packages/auth-client`. Realm configuration and
local setup live under `infra/keycloak`.

## Backend Implementation Plan

- Enforce JWT/JWKS validation by default.
- Keep simulator tokens double opt-in and development-only.
- Implement RBAC/ABAC policies for customer ownership, staff roles, admin roles,
  auditors, FDS/AML reviewers, ops, and service actors.
- Enforce step-up, trusted-device, and session-revocation controls for high-risk
  routes where configured.

## Database / Migration Plan

Persist trusted devices, revoked sessions, security policy parameters, role/menu
parameters, access history, and audit records.

## API / Event / Workflow Contracts

Expose session/status APIs, authorization-denial structured errors, access
history reads, and policy parameter APIs. Emit audit events for denied and
allowed high-risk access.

## Frontend / Screen Manifest Plan

All apps must use the shared auth client. Admin and audit consoles expose policy,
access, and retention screens. Staff terminal menus must be role-aware.

## Security, Audit, Maker-Checker Controls

Authorization changes and security policy changes are high-risk parameter
changes and require maker-checker approval. Privileged PII unmask requires
reason, role policy, and audit.

## Tests And Evidence

Run security integration tests, `npm run security:posture-check`, relevant app
typechecks, live Keycloak smoke where available, and security evidence commands.

## Acceptance Criteria

- Secure defaults are enabled.
- Keycloak/JWKS path is the target path.
- Simulator auth cannot silently bypass policy.
- API and UI access decisions are consistent.
- Audit evidence covers allowed and denied access.

## Explicit Non-Goals

No production identity tenant, no real customer MFA enrollment, and no real
identity-provider dependency outside local/synthetic lab configuration.
