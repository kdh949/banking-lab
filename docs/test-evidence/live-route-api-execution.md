# Live Route API Execution Evidence

Review date: 2026-06-10

Status: partial. `customer-web` has route-backed live API execution tests in source, gated by live synthetic-stack environment variables. `staff-terminal` live route-to-API execution remains blocked by the current iWorks integrated-terminal boundary.

This evidence is synthetic-only. It does not use real customer money, real personal data, real KYC/AML providers, real payment or card networks, or external financial institution APIs.

## What Is Proven

- `apps/customer-web/e2e/customer-onboarding-self-service.spec.ts` contains an env-gated signup to login to staff maker-checker account opening to account list/detail to internal transfer to history/status flow.
- The customer-web flow calls target API client methods for signup, login, staff account-opening approval/execution, owned account list, internal recipient lookup, idempotent transfer command/replay, transaction history, and transfer status readback.
- `apps/customer-web/e2e/customer-web-parity.spec.ts` contains additional env-gated API and Keycloak browser smokes for transfer retry, insufficient balance, history/status, held/failed transfer statuses, complaint entry, and complaint confirmation.
- Customer-web routes exist for `/signup`, `/login`, `/accounts`, `/accounts/[accountId]`, `/transfers/new`, `/transfers/[resultId]`, `/complaints`, `/complaints/[caseId]`, `/security`, and `/api/auth/keycloak-token`.
- `apps/staff-terminal` currently exposes only the iWorks shell plus `/api/terminal-status`. Its source boundary intentionally excludes retired staff API-backed routes and retired staff manifests.

## What Is Not Proven

- A skipped Playwright test is not pass evidence.
- This slice does not claim a fresh local or hosted live customer-web API run. It records source coverage and the exact command/env gate needed to run it.
- `staff-terminal` does not have live route-to-API execution evidence in the current app because the official frontend is the iWorks integrated shell, not the retired API-backed staff route set.
- Staff Spring control APIs remain backend/security covered, but that is not the same as staff-terminal browser route execution.

## Generated Artifact

- `docs/test-evidence/generated/live-route-api-execution.json`

Regenerate it with:

```bash
npm run live-route:evidence
```

## Live Customer-Web Commands

Run the signup/login/account/transfer/history route flow when the synthetic Spring stack and staff simulator tokens are available:

```bash
BANKING_LAB_E2E_API_BASE_URL=<spring-api> \
BANKING_LAB_E2E_STAFF_MAKER_BEARER_TOKEN=<maker> \
BANKING_LAB_E2E_STAFF_CHECKER_BEARER_TOKEN=<checker> \
npm run test:e2e -- apps/customer-web/e2e/customer-onboarding-self-service.spec.ts --project=chromium
```

Run the broader customer-web API/Keycloak browser smokes when Keycloak is configured:

```bash
BANKING_LAB_E2E_API_BASE_URL=<spring-api> \
BANKING_LAB_E2E_KEYCLOAK_BASE_URL=<keycloak> \
npm run test:e2e -- apps/customer-web/e2e/customer-web-parity.spec.ts --project=chromium
```

## Staff-Terminal Boundary

Run the source boundary check:

```bash
npm run integrated-terminal:boundary-check
```

The expected status is `blocked-by-integrated-terminal-boundary` for staff-terminal live route/API execution until a new staff operator route or call-center workflow is implemented and exercised against the Spring API.
