# Phase 3 Workflow Route Hardening Evidence

Date: 2026-06-06

Scope: Phase 3 customer-web and staff-terminal route workflow hardening for the synthetic banking lab. This work does not add real money, real PII, real payment/card networks, Open Banking, or real KYC/provider integrations.

## Implemented

- Added customer-web route pages for login, account list, account detail, transfer entry, transfer result/status, complaints, complaint detail, cards, card detail, loans, payments, notifications, and security.
- Added staff-terminal route pages for transaction code, customer lookup, account operations, approval inbox, audit events, and workflow timeline.
- Added shared route workflow components that reuse existing manifest metadata and display session, reason, audit, structured-error, idempotency replay, FDS hold, step-up, and approval state panels.
- Added route navigation from the customer root shell and staff terminal dashboard.
- Kept API execution on the target TypeScript/Spring paths already present in shared API-backed panels and manifest renderer; no Node target dependency was introduced.

## State Coverage

- Customer transfer route: `POSTED`, `REPLAYED`, `HELD`, `FAILED`, `BLOCKED`, validation, authorization, and unexpected failure states are visible.
- Customer complaint route: received, waiting approval, closed, and ownership-denied states are visible.
- Staff customer route: reason-required, authorization-denied, masked result, privileged unmask, and unexpected failure states are visible.
- Staff approval route: requested, self-approval blocked, approved, rejected, and executed states are visible.

## Commands Run

| Command | Result | Notes |
| --- | --- | --- |
| `npm run next:customer-web:typecheck` | pass | Customer workflow route component and route wrappers typechecked. |
| `npm run next:staff-terminal:typecheck` | pass | Staff workflow route component and route wrappers typechecked. |
| `npm run next:customer-web:build` | pass | Production build generated the new customer route tree. |
| `npm run next:staff-terminal:build` | first run failed, rerun pass | Initial failure caught a server-only manifest loader imported into a client chunk through route summaries; rerun passed after moving summaries to a client-safe module. |
| `npm test` | pass | 170 structural/oracle tests passed after route structural coverage updates. |
| `npm run test:e2e -- apps/customer-web/e2e/customer-web-parity.spec.ts apps/staff-terminal/e2e/staff-terminal-parity.spec.ts` | first run failed, rerun pass | Initial failure caught a strict duplicate text assertion after adding a customer route link; rerun passed with 9 passed and 29 skipped. |
| `npm run validate:manifests` | pass | Validated 109 manifests; Phase 3 changed route UI, not manifest definitions. |
| `npm run test:e2e` | pass | Full local Playwright manifest suite passed with 19 passed and 58 skipped. |
| `gh pr view 48 --json state,mergeStateStatus,statusCheckRollup,headRefName,baseRefName,url` | pass | PR #48 is open and mergeable but unstable because hosted CI checks failed before runner startup. |
| `gh api /repos/kdh949/banking-lab/check-runs/79869938417/annotations` | pass | GitHub Actions reported that hosted jobs were not started because account payments/spending limits blocked runner allocation. |

## Not Run

- Live route execution against Spring/Keycloak was not run because `BANKING_LAB_E2E_API_BASE_URL`, Keycloak, payment, and notification service URLs were not configured in this local phase run.
- Full all-channel `next:*` typecheck/build matrix was not rerun; the changed customer-web and staff-terminal apps were targeted.
- PR #48 hosted CI jobs did not execute after push/rerun because GitHub account billing/spending limits blocked runner allocation. This is recorded as an external CI availability gate, not a passed or failed test step.

## Residual Risk

- The new route pages provide real workflow route structure and visible state coverage, but deeper command execution still primarily lives in the existing API-backed panels and staff manifest renderer until live route-specific API forms are wired.
- Customer account list still uses existing detail/history client capabilities and a clearly labelled synthetic demo fallback when no live session is configured; a dedicated account-list API remains a future improvement if product scope requires it.
