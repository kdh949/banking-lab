# Browser QA - Integration Workstation

Date: 2026-06-04

## Scope

In-app browser QA was run against the committed integration workstation implementation and the follow-up CASE renderer fix.

Local targets:

- `http://localhost:3002` staff terminal
- `http://localhost:3001` customer web
- `http://localhost:3003` complaint portal
- `http://localhost:3004` ops console
- `http://localhost:3005` audit console
- `http://localhost:3006` FDS/AML console
- `http://localhost:3007` admin console

## Staff Terminal Browser Checks

The in-app browser opened the staff terminal and verified the visible shell:

- `INZENT Banking` terminal shell
- transaction code launcher
- open terminal tabs
- customer context rail
- masking state rail
- maker-checker approval rail
- catalog counts
- API-backed smoke panel

The browser then opened every staff-terminal transaction code through the launcher.

Result:

- 32 staff screens checked
- 15 `INQUIRY`
- 12 `COMMAND`
- 4 `CASE`
- 1 `DASHBOARD`
- 0 browser-check failures after fix

Per-screen assertions checked:

- title and transaction code visible
- screen opened into a tab
- expected renderer panels visible for the manifest type
- API-backed screens show `API-backed via @banking-lab/api-client`
- declared-only screens show `declared-only / not API-backed yet`
- reason-required screens show a visible business reason input or required-state message
- maker-checker screens show approval metadata
- declared-only command screens do not show fake success

## Finding Fixed

The first browser sweep found that `CASE` screens rendered status, owner, SLA, comments, timeline, and approval metadata, but did not render a dedicated business reason panel or structured error surface even when `audit.reasonRequired` was set.

Fix:

- Added `Case Handling Reason` to `CaseScreenRenderer`.
- Added `StructuredErrorView` to `CaseScreenRenderer`.
- Added staff Playwright assertions for `CMP202` case reason and structured error rendering.

## Other Channel Browser Checks

Each channel was opened in the in-app browser. The check verified the channel heading, every screen ID in the channel manifest directory, the channel-specific control text used by Playwright parity tests, and absence of page console errors.

Result:

- customer-web: 10 screens, 0 missing screen IDs, 0 missing key texts, 0 console errors
- complaint-portal: 8 screens, 0 missing screen IDs, 0 missing key texts, 0 console errors
- ops-console: 4 screens, 0 missing screen IDs, 0 missing key texts, 0 console errors
- audit-console: 3 screens, 0 missing screen IDs, 0 missing key texts, 0 console errors
- fds-aml-console: 6 screens, 0 missing screen IDs, 0 missing key texts, 0 console errors
- admin-console: 3 screens, 0 missing screen IDs, 0 missing key texts, 0 console errors

## Commands Re-run After Fix

```bash
npm run next:staff-terminal:typecheck
npm run next:staff-terminal:build
npm run test:e2e
npm run validate:manifests
```

`npm run test:e2e` result:

- 17 passed
- 32 skipped because API/Keycloak E2E environment variables were not configured

## Remaining Limits

The browser QA did not run live Spring API, Keycloak, or WebAuthn flows because `BANKING_LAB_E2E_API_BASE_URL` and `BANKING_LAB_E2E_KEYCLOAK_BASE_URL` were not configured in this session. Those conditional flows remain covered by skipped Playwright tests and require the target services to be supplied.
