# Live Route API Execution Evidence

Review date: 2026-06-10

Status: partial. `customer-web` has route-backed live API execution tests in source, gated by live synthetic-stack environment variables. `staff-terminal` keeps the iWorks integrated-terminal shell and now has a bounded Spring API evidence panel for reason-required staff customer detail and approval-inbox reads.

This evidence is synthetic-only. It does not use real customer money, real personal data, real KYC/AML providers, real payment or card networks, or external financial institution APIs.

## What Is Proven

- `apps/customer-web/e2e/customer-onboarding-self-service.spec.ts` contains an env-gated signup to login to staff maker-checker account opening to account list/detail to internal transfer to history/status flow.
- The customer-web flow calls target API client methods for signup, login, staff account-opening approval/execution, owned account list, internal recipient lookup, idempotent transfer command/replay, transaction history, and transfer status readback.
- `apps/customer-web/e2e/customer-web-parity.spec.ts` contains additional env-gated API and Keycloak browser smokes for transfer retry, insufficient balance, history/status, held/failed transfer statuses, complaint entry, and complaint confirmation.
- Customer-web routes exist for `/signup`, `/login`, `/accounts`, `/accounts/[accountId]`, `/transfers/new`, `/transfers/[resultId]`, `/complaints`, `/complaints/[caseId]`, `/security`, and `/api/auth/keycloak-token`.
- `apps/staff-terminal` exposes the iWorks shell plus `/api/terminal-status`, and `StaffApiEvidencePanel` can call Spring staff APIs when `NEXT_PUBLIC_BANKING_API_BASE_URL` and simulator-token opt-in are configured.
- The staff-terminal bounded API panel calls `staffCustomerDetail("SYN-CUS-001", reason)` and `staffApprovals()` through the shared API client, preserving reason-required audit on customer detail lookup.
- The local Compose wrapper uses explicit dev/test simulator-token opt-in plus the `core-banking-api` audience expected by the Spring Resource Server.

## What Is Not Proven

- A skipped Playwright test is not pass evidence.
- This slice does not claim a fresh local or hosted live customer-web API run. It records source coverage and the exact command/env gate needed to run it.
- This slice does not restore the retired `apps/staff-terminal/src/app/accounts`, approvals, audit, customers, tx, or workflows route set.
- The staff-terminal API evidence panel is route-backed and env-gated; a skipped Playwright run is still not live execution evidence.
- High-risk staff command execution remains backend/security covered unless a dedicated frontend command path is added.

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

## Staff-Terminal Commands

Run the source boundary check:

```bash
npm run integrated-terminal:boundary-check
```

Run the local disposable staff-terminal Spring API smoke when Docker and the synthetic stack are available:

```bash
npm run test:staff-terminal:api-e2e-compose
```

The generated staff-terminal status is `route-backed-live-gated`. It proves the source and wrapper path exist, but it is only live evidence after the Compose smoke runs successfully.
