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
- `ACC103` renders an API-backed account hold panel that can request hold approval, prove self-approval rejection, approve with a checker, request release approval, prove release self-approval rejection, approve release, and return the synthetic account to `ACTIVE` when `BANKING_LAB_E2E_API_BASE_URL` is configured;
- `LIM102` renders an API-backed transfer-limit panel that can request a limit-change approval, prove self-approval rejection, approve with a checker, and observe the new `account_limits` values when `BANKING_LAB_E2E_API_BASE_URL` is configured;
- `KYC101` renders an API-backed KYC review panel that can request synthetic KYC re-confirmation, prove self-approval rejection, approve with a checker, and observe `REVIEW_REQUIRED` in `customer_kyc_profiles` when `BANKING_LAB_E2E_API_BASE_URL` is configured;
- `FEE102` renders an API-backed fee waiver panel that can request synthetic fee waiver approval, prove self-approval rejection, reject a separate request with a checker, approve another request with a checker, and distinguish non-targeted approvals from targeted posted-fee refunds when `BANKING_LAB_E2E_API_BASE_URL` is configured;
- `FEE103` renders an API-backed fee policy parameter panel that can list seeded fee policies, request a `FEE_POLICY_PARAMETER_CHANGE`, prove self-approval rejection, approve with a checker, and observe an applied fee policy version when `BANKING_LAB_E2E_API_BASE_URL` is configured;
- `LED103` renders an API-backed transaction correction panel that can request a synthetic reversal correction, prove self-approval rejection, approve with a checker, and observe a `REVERSAL` ledger transaction without source-row mutation when `BANKING_LAB_E2E_API_BASE_URL` is configured;
- `PRD102` renders an API-backed deposit rate parameter panel that can list seeded deposit products, request a `PRODUCT_PARAMETER_CHANGE`, prove self-approval rejection, approve with a checker, and observe an applied rate version when `BANKING_LAB_E2E_API_BASE_URL` is configured;
- `WRK002` opens through the transaction-code launcher and renders the operational retry queue inquiry with the reason-required Spring endpoint `GET /api/staff/operations/retry-queue`;
- the staff API-backed panel includes an operational retry queue smoke that reads failed synthetic `outbox_events` with `OPS_MANAGER` simulator credentials when `BANKING_LAB_E2E_API_BASE_URL` is configured;
- `WRK003` opens through the transaction-code launcher and renders a workflow timeline inquiry with the reason-required Spring endpoint `GET /api/staff/workflows/{businessReferenceId}/timeline`;
- `WRK003` includes an API-backed timeline panel that reads synthetic workflow, approval, and audit timeline entries for `TX-SYN-CORR-001` when `BANKING_LAB_E2E_API_BASE_URL` is configured;
- `APR001` approval inbox renders API-backed list, selected approval, approval execution controls, and related audit events inside the manifest workspace when `BANKING_LAB_E2E_API_BASE_URL` is configured;
- `AUD001` audit log renders API-backed audit event rows, selected event details, and hash-chain status inside the manifest workspace when `BANKING_LAB_E2E_API_BASE_URL` is configured.

## Command

```bash
npm run test:e2e
```

The full command starts all Next.js channel dev servers through Playwright. API and Keycloak-dependent tests remain conditional on `BANKING_LAB_E2E_API_BASE_URL` and `BANKING_LAB_E2E_KEYCLOAK_BASE_URL`.

Latest local result after the 2026-06-05 workflow timeline update:

- `npm run test:e2e -- apps/staff-terminal/e2e/staff-terminal-parity.spec.ts`
- 5 passed
- 17 skipped because `BANKING_LAB_E2E_API_BASE_URL`, payment API, and Keycloak E2E environment variables were not configured

Latest full-channel result remains the previous Phase C fee policy/posting baseline:

- `npm run test:e2e`
- 17 passed
- 41 skipped because API/Keycloak E2E environment variables were not configured
