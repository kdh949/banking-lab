# Integrated Staff Terminal Boundary Evidence

Date: 2026-06-10

Scope: current `apps/staff-terminal` frontend boundary after replacing the old
route/manifest prototype with the iWorks integrated terminal. This work does
not add real money, real PII, real payment/card networks, Open Banking, or real
KYC/provider integrations.

## Implemented

- `apps/staff-terminal` now exposes `/` and `/api/terminal-status` as the only
  official routes.
- `IntegratedTerminalApp` is the single staff-terminal UI entrypoint.
- The old staff route pages, broad API-backed panel, manifest renderer,
  workflow route helpers, and staff-terminal manifests are removed.
- `/api/terminal-status` returns current server time and client IP for the
  status bar.
- `StaffApiEvidencePanel` adds a bounded Spring API evidence surface for
  reason-required staff customer detail and approval-inbox reads. It requires
  API URL and simulator-token opt-in configuration and does not restore the
  retired staff route set.
- `apps/staff-terminal/e2e/integrated-terminal.spec.ts` validates the iWorks
  shell, module navigation, unavailable-work X modal, lookup modal, digit-only
  operator input, bounded API panel visibility, and source boundary.
- `scripts/check-integrated-terminal-boundary.ts` fails if removed staff
  terminal paths are reintroduced or if the bounded API panel contract is
  weakened.

## Commands Run

| Command | Result | Notes |
| --- | --- | --- |
| `npm run integrated-terminal:boundary-check` | pass | Verified official route/component boundary. |
| `npm run test:staff-terminal:api-e2e-compose` | pass | Verified `StaffApiEvidencePanel` against disposable Spring/PostgreSQL Compose with dev/test simulator-token opt-in. |
| `node --test tests/nextScaffold.test.mjs tests/migrationFoundation.test.mjs tests/retirementBoundaryAudit.test.mjs tests/stackRetirementAreaAudit.test.mjs tests/passkeyEvidencePreflight.test.mjs` | pass | Verified scaffold, migration, retirement, stack, and passkey preflight updates. |
| `npm test` | pass | 178 node-test cases passed after replacing old staff route assumptions. |

## Not Yet Proven In This Evidence Note

- High-risk Spring/Keycloak command execution through the staff-terminal UI. The
  current staff frontend is a terminal prototype with a bounded read-only API
  evidence panel and does not expose the retired API-backed staff route set.
- Browser console verification is tracked separately with the in-app browser
  check for `http://127.0.0.1:3002/`.

## Residual Risk

- Future staff command screens must be added through the shared integrated
  terminal primitives and navigation registry, not by restoring old route pages
  or staff-terminal screen manifests.
