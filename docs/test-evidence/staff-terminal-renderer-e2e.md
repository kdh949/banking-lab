# Staff Terminal Renderer E2E Evidence

Date: 2026-06-04

## Coverage

The staff Playwright spec now checks:

- transaction-code launcher is visible;
- transaction code search opens `CST001`;
- opening `CMP202` adds and activates a second tab;
- clicking the `CST001` tab switches back to inquiry rendering;
- inquiry screens show search, result table, detail, reason-required, masking, and structured error controls;
- command screens show form, before/after snapshot, maker-checker approval, and no fake success for declared-only commands;
- API-backed staff smoke panels remain present for configured Spring API runs;
- `APR001` approval inbox renders API-backed list, selected approval, approval execution controls, and related audit events inside the manifest workspace when `BANKING_LAB_E2E_API_BASE_URL` is configured;
- `AUD001` audit log renders API-backed audit event rows, selected event details, and hash-chain status inside the manifest workspace when `BANKING_LAB_E2E_API_BASE_URL` is configured.

## Command

```bash
npm run test:e2e
```

The full command starts all Next.js channel dev servers through Playwright. API and Keycloak-dependent tests remain conditional on `BANKING_LAB_E2E_API_BASE_URL` and `BANKING_LAB_E2E_KEYCLOAK_BASE_URL`.

Latest local result:

- 17 passed
- 34 skipped because API/Keycloak E2E environment variables were not configured

Latest targeted staff-terminal result:

- `npm run test:e2e -- apps/staff-terminal/e2e/staff-terminal-parity.spec.ts`
- 5 passed
- 7 skipped because `BANKING_LAB_E2E_API_BASE_URL` was not configured
