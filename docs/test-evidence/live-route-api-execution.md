# Live Route API Execution Evidence

Review date: 2026-06-11

Status: pass. `customer-web` and `staff-terminal` now have local synthetic Compose route-to-API pass evidence recorded for the current portfolio slice. The broader Keycloak/browser smokes remain environment-gated and skipped Playwright is still not pass evidence.

This evidence is synthetic-only. It does not use real customer money, real personal data, real KYC/AML providers, real payment or card networks, or external financial institution APIs.

## What Is Proven

- `apps/customer-web/e2e/customer-onboarding-self-service.spec.ts` contains an env-gated signup to login to staff maker-checker account opening to account list/detail to internal transfer to history/status flow.
- `npm run test:customer-web:self-service-api-e2e-compose` passed on 2026-06-11 against disposable PostgreSQL and Spring Boot services, proving signup, login, staff maker-checker account opening, owned account routes, internal transfer, idempotent replay, transaction history, and transfer status rendering.
- The customer-web flow calls target API client methods for signup, login, staff account-opening approval/execution, owned account list, internal recipient lookup, idempotent transfer command/replay, transaction history, and transfer status readback.
- The synthetic customer auth token now carries the configured `core-banking-api` audience and a deterministic synthetic device fingerprint; signup/login binds an active synthetic trusted device so the customer transfer route satisfies the trusted-device gate without disabling the policy.
- `apps/customer-web/e2e/customer-web-parity.spec.ts` contains additional env-gated API and Keycloak browser smokes for transfer retry, insufficient balance, history/status, held/failed transfer statuses, complaint entry, and complaint confirmation.
- Customer-web routes exist for `/signup`, `/login`, `/accounts`, `/accounts/[accountId]`, `/transfers/new`, `/transfers/[resultId]`, `/complaints`, `/complaints/[caseId]`, `/security`, and `/api/auth/keycloak-token`.
- `apps/staff-terminal` exposes the iWorks shell plus `/api/terminal-status`, and the existing one-page terminal now includes API-backed transaction-code screens without adding staff-terminal routes or manifests.
- `CUS101`, `ACC101`, and `TX101` use shared API client staff inquiry methods for reason-required customer, account, and transaction lookup with masked PII/account data and audit event visibility.
- `APR101`, `CMD101`, `WRK002`, and `WRK003` expose approval inbox actions, idempotent high-risk command request workbench metadata, outbox retry/dead-letter visibility, and workflow/audit/approval timeline visibility.
- `CALL101` through `CALL106` expose compact call-center search, interaction, redacted note, aftercall task, history, and escalation flows inside the integrated terminal while the dedicated call-center console remains intact.
- `StaffApiEvidencePanel` still provides the bounded Spring API evidence panel and Compose smoke path by calling `staffCustomerDetail("SYN-CUS-001", reason)` and `staffApprovals()` through the shared API client, preserving reason-required audit on customer detail lookup.
- `npm run test:staff-terminal:api-e2e-compose` passed on 2026-06-11 against disposable PostgreSQL and Spring Boot services, proving the iWorks shell plus bounded staff customer detail and approval-inbox route/API flow. The broader transaction-code screens are covered by typecheck, build, boundary check, Playwright smoke, and shared API client contract tests in this slice.
- The local Compose wrappers use explicit dev/test simulator-token opt-in plus the `core-banking-api` audience expected by the Spring Resource Server.

## What Is Not Proven

- A skipped Playwright test is not pass evidence.
- This slice does not claim hosted CI green; #83 remains the hosted runner blocker.
- The broader customer-web Keycloak browser smoke remains environment-gated in this document.
- This slice does not restore the retired `apps/staff-terminal/src/app/accounts`, approvals, audit, customers, tx, or workflows route set.
- High-risk staff command execution from the new workbench records request/approval metadata in the UI, but this evidence does not claim every command branch was live-executed through Compose.

## Generated Artifact

- `docs/test-evidence/generated/live-route-api-execution.json`
- `docs/test-evidence/generated/customer-web-self-service-api-e2e-compose-smoke.json`
- `docs/test-evidence/generated/staff-terminal-api-e2e-compose-smoke.json`

Regenerate it with:

```bash
npm run live-route:evidence
```

## Live Customer-Web Commands

Run the signup/login/account/transfer/history route flow against disposable synthetic Compose:

```bash
npm run test:customer-web:self-service-api-e2e-compose
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

The generated application statuses are `route-backed-live-pass` after the two local Compose smokes write pass stamps.
Those stamps are local-only evidence and do not claim hosted CI green.
