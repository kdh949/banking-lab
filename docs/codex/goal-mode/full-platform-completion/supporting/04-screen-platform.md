# Screen Platform

## Goal

Avoid hand-coding every banking screen by using screen manifests, reusable
templates, shared UI packages, shared API clients, role-aware menus, and
consistent audit/reason/masking behavior.

## Current Code To Inspect

- `packages/screen-engine/src/**`
- `packages/form-engine/src/**`
- `packages/channel-ui/src/**`
- `packages/api-client/src/**`
- `screen-manifests/**`
- `apps/*/src/**`
- `docs/architecture/frontend-channels-manifest-shells.md`
- `docs/architecture/manifest-renderer-v2.md`

## Target Folder Placement

Manifests belong in `screen-manifests/<channel>`. Shared renderer logic belongs
in `packages/screen-engine`, `packages/form-engine`, and `packages/channel-ui`.
Channel-specific shells belong in `apps/<channel>`.

## Implementation Plan

- Keep four required templates: Inquiry, Command, Case, and Parameter.
- Support transaction-code input, role-aware menus, customer context, masked PII,
  reason-required lookup, audit panel, approval inbox, workflow timeline, and
  exception/retry panel.
- Keep screen IDs stable and map them to API client operations.
- Add custom panels only for behavior the manifest renderer cannot express.

## Tests And Evidence

Run `npm run validate:manifests`, `npm run test:screen-engine`,
`npm run packages:typecheck`, channel typechecks, and Playwright smoke for changed
channels.

## Acceptance Criteria

- New screens are manifest-driven unless explicitly justified.
- Screen text and actions fit the template model.
- API-backed screens use shared API client methods.
- Role, reason, masking, audit, and approval states are visible.

## Explicit Non-Goals

No copy-paste screen farm, no marketing landing page as primary app surface, and
no hidden API calls outside shared client conventions.
