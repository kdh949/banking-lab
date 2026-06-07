# Customer Onboarding Self-Service Evidence

Review date: 2026-06-07

Scope: synthetic-only customer onboarding, account opening, customer signup/login, owned account list, account detail/history, and internal synthetic transfer for the target Kotlin/Spring and Next.js stack. The legacy Node runtime remains an oracle only and is not a target dependency.

## Implemented Coverage

- Staff can request, approve/reject, and execute synthetic customer onboarding through maker-checker APIs and staff-terminal manifests `CST-201` and `CST-202`.
- Staff can request, approve/reject, and execute synthetic account opening through maker-checker APIs and staff-terminal manifests `ACC-201` and `ACC-202`.
- Account opening creates `accounts`, `account_limits`, and `account_balance_projections`; optional opening deposit posts only through `LedgerCommandService.deposit`.
- Customer signup/login uses `PasswordEncoder` hashing and guarded dev/test synthetic bearer-token issuance.
- Customer-web routes `/signup`, `/login`, `/accounts`, `/accounts/[accountId]`, `/transfers/new`, and `/transfers/[resultId]` use form-backed session state instead of hard-coded customer/account IDs.
- Customer account list and account detail enforce customer ownership, mask account numbers, and emit self-service audit events.
- Internal transfer requires an active internal synthetic recipient, is idempotent, and posts through the existing double-entry ledger path.

## Synthetic Boundary

- Real customer data, real money, real KYC, real payment networks, card networks, Open Banking, credit bureau APIs, and real Keycloak Admin API provisioning are not used.
- Persistent request/account/auth rows include synthetic-only metadata or audit payload fields showing real provider calls were not made.
- Customer-web displays a demo fallback when no live Spring API base URL is configured.
- Simulator bearer tokens are dev/test-only and are rejected by prod-like profile guards.

## Local Validation

- `npm run test:core-banking:integration -- --tests lab.banking.core.onboarding.CustomerOnboardingIntegrationTest --tests lab.banking.core.account.AccountOpeningIntegrationTest --tests lab.banking.core.auth.CustomerAuthIntegrationTest --tests lab.banking.core.customer.CustomerAccountApiParityIntegrationTest --tests lab.banking.core.customer.CustomerTransferApiParityIntegrationTest`: passed.
- `npm run next:customer-web:typecheck`: passed.
- `npm run next:customer-web:build`: passed.
- `npm run next:staff-terminal:typecheck`: passed.
- `npm run packages:typecheck`: passed.
- `npm run scripts:typecheck`: passed.
- `npm run contracts:lint`: passed.
- `npm run contracts:check-client`: passed.
- `npm run validate:manifests`: passed, 115 screen manifests.
- `node --test tests/customerOnboardingSelfService.test.mjs`: passed, 8 tests.
- `node --test tests/nextScaffold.test.mjs tests/customerOnboardingSelfService.test.mjs`: passed, 23 tests.
- `node --test tests/manifestExpansionForm.test.mjs`: passed, 2 tests.
- `npm test`: passed, 182 tests.
- `npm run k8s:validate`: passed, 27 resources with `kubectl skipped_no_cluster` for the unavailable local Kubernetes API.
- `npm run platform:validate`: passed; Kubernetes structural validation, Helm template validation, and Argo CD validation all passed.
- `docker compose config`: passed.
- `docker compose --profile platform config`: passed.
- `npm run security:secrets-check`: passed, 886 files checked.
- `npm run security:evidence`: first sandboxed run failed due registry/Docker access restrictions; approved escalated rerun passed npm audit, Semgrep, Trivy, and CycloneDX SBOM, with DAST skipped because `BANKING_LAB_DAST_URL` was not set.
- `npm run test:e2e -- apps/customer-web/e2e/customer-onboarding-self-service.spec.ts --project=chromium`: passed with 1 skipped because the live API and staff maker/checker token env vars were absent.
- `npm run test:e2e -- apps/customer-web/e2e/customer-web-parity.spec.ts apps/customer-web/e2e/customer-onboarding-self-service.spec.ts --project=chromium`: passed 3 tests and skipped 12 live API-backed smokes because live API environment variables were absent.
- `npm run test:e2e -- --project=chromium`: passed 19 tests and skipped 59 live API/Keycloak/payment/notification smokes because corresponding environment variables were absent.

## Known Skips And Failures

- `kubectl` client dry-run is recorded as `skipped_no_cluster` because the configured local Kubernetes API returned `the server is currently unable to handle the request`; structural Kubernetes validation still passed.
- The new signup to login to accounts to transfer to history Playwright/API smoke is gated by `BANKING_LAB_E2E_API_BASE_URL`, `BANKING_LAB_E2E_STAFF_MAKER_BEARER_TOKEN`, and `BANKING_LAB_E2E_STAFF_CHECKER_BEARER_TOKEN`; without those values it skips with an explicit reason.
- Plain local `security:evidence` skips DAST when `BANKING_LAB_DAST_URL` is not set; the 2026-06-07 local follow-up also ran Docker-forced security evidence against a disposable local synthetic core-banking target with DAST enabled.
- Hosted CI for PR #54 has not produced runnable job evidence. Inspected runs complete all jobs as failed within a few seconds before any runner starts, and check-run annotations say the job was not started because recent account payments have failed or the spending limit needs to be increased.

## 2026-06-07 Local CI-Equivalent Follow-Up

Branch: `codex/customer-onboarding-self-service`

Matrix baseline commit: `aed3b563a0d5caf07e099f24e8d4fabcb12fd7b3`

Post-fix verification commit: `480873add2d956ea9f1ff70554b5f17367f08abb`

Hosted GitHub Actions CI is blocked by billing/spending-limit and has not passed. Head-specific hosted-CI block records are posted in the PR discussion; inspected check-run annotations say the job was not started because recent account payments failed or the spending limit must be increased.

The local CI-equivalent matrix is recorded in `docs/test-evidence/ci.md`. Node reference tests, manifests, all Next.js channel typechecks/builds, contracts, platform structural validation, Compose config, Playwright manifest E2E, security evidence including Docker-forced ZAP DAST, formal ledger model, and non-core backend services passed. The initial full core-banking integration run failed in `MultiInstanceLedgerHaDrIntegrationTest` because independent security-disabled non-web Spring contexts could not create `CustomerAuthService` without a `PasswordEncoder` bean. Commit `480873add2d956ea9f1ff70554b5f17367f08abb` moved the password encoder into a web-independent Spring configuration; the exact HA test then passed, `ComplaintCaseApiParityIntegrationTest` passed on isolated rerun after a transient full-suite Flyway/PostgreSQL read timeout, and the final full `npm run test:core-banking:integration` rerun completed successfully.

## Evidence Files

- `docs/codex/customer-onboarding-self-service-status.md`
- `contracts/openapi/core-banking.yaml`
- `packages/api-client/src/index.ts`
- `screen-manifests/staff-terminal/CST-201.customer-onboarding-request.json`
- `screen-manifests/staff-terminal/CST-202.customer-onboarding-approval.json`
- `screen-manifests/staff-terminal/ACC-201.account-opening-request.json`
- `screen-manifests/staff-terminal/ACC-202.account-opening-approval.json`
- `screen-manifests/customer-web/CWB-001.customer-signup.json`
- `screen-manifests/customer-web/CWB-002.customer-login.json`
- `screen-manifests/customer-web/CWB-101.account-overview.json`
- `screen-manifests/customer-web/CWB-201.internal-transfer.json`
- `apps/customer-web/e2e/customer-onboarding-self-service.spec.ts`
