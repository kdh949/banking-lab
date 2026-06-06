# Customer Onboarding And Self-Service Status

Review date: 2026-06-07

Scope: Phase 0 baseline for the synthetic customer/account onboarding and customer-web self-service goal. This document records target-stack Kotlin/Spring, PostgreSQL, TypeScript/Next.js, OpenAPI, API-client, manifest, and test evidence only. The legacy Node runtime remains a reference oracle and is not counted as target implementation.

## Current Baseline

- The target stack is already present: `services/core-banking` is Kotlin/Spring Boot, PostgreSQL-backed Flyway migrations live under `db/migrations`, customer/staff channels are Next.js apps, screen manifests are checked in, and shared API client methods exist for many current customer and staff flows.
- The foundation schema already has `customers`, `customer_kyc_profiles`, `accounts`, `account_limits`, `account_balance_projections`, ledger tables, `idempotency_keys`, `operator_approvals`, audit events, outbox events, and several existing staff request tables.
- Synthetic seed data creates fixed customers and accounts such as `SYN-CUS-001`, `SYN-CUS-002`, `ACC-SYN-001-001`, and `ACC-SYN-002-001`.
- Existing staff operations cover reason-required customer/account/transaction reads, PII unmask, customer information change, account hold/release, transfer-limit change, KYC review, fee waiver, and transaction correction. These use maker-checker patterns for high-risk actions.
- Existing customer operations cover a single account detail endpoint, transaction history, internal transfer command, transfer status, customer complaints, statements, certificates, cards, loans, notifications, and access history.
- Existing customer transfer service uses SERIALIZABLE retry around customer commands, validates source-account ownership, persists transfer results by idempotency key and command hash, routes successful internal transfers through `LedgerCommandService.internalTransfer`, and stores held/failed states without unsafe postings.
- Security currently authenticates `/api/**` except health endpoints. `/api/auth/session` returns the current authenticated principal and customerId claim, but unauthenticated customer signup/login endpoints are not yet present or whitelisted.
- Existing customer-web routes are route-backed workflow/state pages. They describe session source, structured errors, demo fallback, account/transfer states, and API method coverage, but they are not real signup/login/account-list/transfer forms using dynamic session state.
- Existing staff-terminal routes are route-backed workflow/state pages and a manifest renderer. They support existing transaction-code screens and API smoke panels, but they do not include customer onboarding or account-opening request screens.
- `docs/architecture/phase-4-customer-web.md` lists `GET /api/customer/accounts`, but the current Spring controller, OpenAPI contract, and API client do not expose a customer account-list operation. This status treats that as a baseline gap, not implemented behavior.

## Existing APIs

Current target-stack APIs relevant to this goal:

- `GET /api/auth/session`
- `GET /api/staff/customers/search`
- `GET /api/staff/customers/{customerId}/detail`
- `GET /api/staff/accounts/search`
- `GET /api/staff/transactions/search`
- `GET /api/staff/customers/{customerId}/transfer-limits`
- `GET /api/staff/operations/retry-queue`
- `GET /api/staff/workflows/{businessReferenceId}/timeline`
- `POST /api/staff/pii/unmask`
- `POST /api/staff/customers/{customerId}/change-requests`
- `POST /api/staff/accounts/{accountId}/hold-requests`
- `POST /api/staff/accounts/{accountId}/hold-release-requests`
- `POST /api/staff/accounts/{accountId}/limit-change-requests`
- `POST /api/staff/customers/{customerId}/kyc-review-requests`
- `POST /api/staff/accounts/{accountId}/fee-waiver-requests`
- `POST /api/staff/transactions/{transactionId}/correction-requests`
- `POST /api/staff/approvals/{approvalId}/approve`
- `POST /api/staff/approvals/{approvalId}/reject`
- `GET /api/customer/accounts/{accountId}/detail`
- `POST /api/customer/transfers`
- `GET /api/customer/transfers`
- `GET /api/customer/transactions`

Current API-client methods relevant to this goal:

- `staffCustomerDetail`
- `requestCustomerInfoChange`
- `requestAccountHold`
- `requestAccountHoldRelease`
- `staffTransferLimits`
- `requestTransferLimitChange`
- `requestCustomerKycReview`
- `requestFeeWaiver`
- `requestTransactionCorrection`
- `unmaskStaffCustomer`
- `customerAccountDetail`
- `requestCustomerTransfer`
- `customerTransactions`
- `customerTransfers`

## Missing APIs

Missing target-stack APIs required by the goal:

- `POST /api/staff/customers/onboarding-requests`
- `GET /api/staff/customers/onboarding-requests/{requestId}`
- `POST /api/staff/customers/onboarding-requests/{requestId}/approve`
- `POST /api/staff/customers/onboarding-requests/{requestId}/reject`
- `POST /api/staff/customers/onboarding-requests/{requestId}/execute`
- `POST /api/staff/customers/{customerId}/account-opening-requests`
- `GET /api/staff/account-opening-requests/{requestId}`
- `POST /api/staff/account-opening-requests/{requestId}/approve`
- `POST /api/staff/account-opening-requests/{requestId}/reject`
- `POST /api/staff/account-opening-requests/{requestId}/execute`
- `POST /api/auth/customer/signup`
- `POST /api/auth/customer/login`
- `GET /api/customer/accounts` for owned account list
- `GET /api/customer/recipients/internal-account-lookup`

Missing persistence/control pieces required by the goal:

- `customer_onboarding_requests`
- `customer_auth_identities`
- `account_opening_requests`
- synthetic account-number sequence/generator
- customer password hashing storage and login failure/lock state
- signup/login synthetic-auth profile settings separate from the current simulator-token decoder flags
- route-level unauthenticated exceptions for signup/login in both Spring Security and the custom authorization filter

Missing contract/client coverage:

- OpenAPI operation IDs for customer onboarding, account opening, customer signup, customer login, customer account list, and internal recipient lookup.
- API-client methods for the same operations.
- Contract markers for `syntheticOnly`, reason-required staff commands, and idempotent command policy on the new operations.

## Existing UI

Current customer-web routes:

- `/login`: workflow state page for OIDC/session boundary, not a synthetic username/password login form.
- `/accounts`: workflow state page for account overview, currently tied to manifest/API coverage rather than a dynamic owned account list.
- `/accounts/[accountId]`: workflow state page for account detail route state.
- `/transfers/new`: workflow state page for transfer states, not a real source account selector, recipient lookup, amount entry, and submit flow.
- `/transfers/[resultId]`: workflow state page for result/status states.
- Additional route-backed pages exist for complaints, cards, loans, payments, notifications, and security.

Current staff-terminal routes:

- `/customers/[customerId]`: route-backed customer lookup workflow.
- `/accounts/[accountId]`: route-backed account operations workflow.
- `/approvals`: approval inbox workflow.
- `/audit`: audit events workflow.
- `/tx/[transactionCode]`: transaction-code workflow.
- `/workflows/[businessReferenceId]`: workflow timeline route.
- The manifest renderer currently includes existing staff screens such as `CST-001`, `CST-002`, `CST-003`, `CST-103`, `ACC-101`, `ACC-102`, `ACC-103`, `ACC-104`, `LIM-101`, `LIM-102`, `APR-001`, and `AUD-001`.

Existing manifests relevant to this goal:

- `screen-manifests/customer-web/CWB-101.account-overview.json`
- `screen-manifests/customer-web/CWB-102.account-detail.json`
- `screen-manifests/customer-web/CWB-103.transaction-history.json`
- `screen-manifests/customer-web/CWB-201.internal-transfer.json`
- `screen-manifests/customer-web/CWB-202.transfer-result.json`
- `screen-manifests/customer-web/CWB-203.transfer-status.json`
- `screen-manifests/staff-terminal/CST-001.customer-search.json`
- `screen-manifests/staff-terminal/CST-002.customer-detail.json`
- `screen-manifests/staff-terminal/CST-003.customer-360.json`
- `screen-manifests/staff-terminal/CST-103.customer-info-change.json`
- `screen-manifests/staff-terminal/ACC-101.account-search.json`
- `screen-manifests/staff-terminal/ACC-102.account-detail.json`
- `screen-manifests/staff-terminal/ACC-103.account-hold.json`
- `screen-manifests/staff-terminal/ACC-104.account-hold-release.json`
- `screen-manifests/staff-terminal/APR-001.approval-inbox.json`

## Missing UI

Missing customer-web UI:

- `/signup`
- real `/login` synthetic username/password form backed by `POST /api/auth/customer/login`
- session/token state provider using returned synthetic login token or a configured OIDC token
- owned account-list view backed by `GET /api/customer/accounts`
- account detail page that loads from dynamic session/account selection instead of fixed demo IDs
- internal recipient lookup UI backed by `GET /api/customer/recipients/internal-account-lookup`
- transfer form with source account selector, recipient account lookup, amount input, idempotency key generation, structured error display, replay/held/posted/failure result handling, and result navigation

Missing staff-terminal UI/manifests:

- customer onboarding request route/screen
- customer onboarding approval/detail route/screen
- account opening request route/screen
- account opening approval/detail route/screen
- transaction-code entries for the above if they are exposed through the manifest renderer
- API-backed panels for request, approve/reject, execute, replay/conflict, and validation/auth failures

Missing screen manifests expected by the goal:

- `screen-manifests/customer-web/CWB-001-customer-signup.json`
- `screen-manifests/customer-web/CWB-002-customer-login.json`
- `screen-manifests/customer-web/CWB-101-account-list.json` or a deliberate update to the existing `CWB-101.account-overview.json`
- `screen-manifests/customer-web/CWB-201-internal-transfer-form.json` or a deliberate update to the existing `CWB-201.internal-transfer.json`
- `screen-manifests/staff-terminal/CST-201-customer-onboarding.json`
- `screen-manifests/staff-terminal/CST-202-customer-onboarding-approval.json`
- `screen-manifests/staff-terminal/ACC-201-account-opening.json`
- `screen-manifests/staff-terminal/ACC-202-account-opening-approval.json`

## Proposed Phases

Phase 1 should add synthetic customer onboarding as a high-risk staff command:

- Add `customer_onboarding_requests` and `customer_auth_identities`.
- Add `ApprovalBusinessTypes.CUSTOMER_ONBOARDING`.
- Add Kotlin request/detail/approve/reject/execute service and controller.
- Reuse persistent maker-checker and audit controls.
- Store idempotency key and command hash, including replay and conflict behavior.
- Execute only after independent checker approval and create `customers`, `customer_kyc_profiles`, and `customer_auth_identities`.
- Never call real KYC, Keycloak Admin API, real identity, or external data APIs.

Phase 2 should add account opening as a high-risk staff command:

- Add `account_opening_requests` and synthetic account-number generation.
- Add `ApprovalBusinessTypes.ACCOUNT_OPENING`.
- Request/approve/reject/execute against existing customers.
- Create `accounts`, `account_limits`, and `account_balance_projections` only after approval.
- If an initial deposit is allowed, call `LedgerCommandService.deposit` rather than inserting ledger rows directly.
- Ensure execute replay cannot create duplicate accounts or duplicate deposits.

Phase 3 should add customer signup/login:

- Add unauthenticated `POST /api/auth/customer/signup` and `POST /api/auth/customer/login`.
- Whitelist those routes in Spring Security and `BankingLabAuthorizationFilter`.
- Use `PasswordEncoder`; never store plaintext password or audit payload password values.
- Issue simulator bearer tokens only when explicit dev/test synthetic-auth settings allow it.
- Fail fast or return structured denial in prod-like profiles when synthetic token issue is enabled.
- Keep `/api/auth/session` as the authenticated session proof endpoint.

Phase 4 should replace customer-web workflow shells for this journey with real form-backed screens:

- Add `/signup`.
- Upgrade `/login`, `/accounts`, `/accounts/[accountId]`, `/transfers/new`, and `/transfers/[resultId]`.
- Use token/session state and customer ownership, not hard-coded customer/account IDs.
- Add account list and internal recipient lookup support.
- Render structured validation, auth, replay, held, posted, and unexpected-failure states.
- Label demo fallback clearly when live API/session settings are unavailable.

Phase 5 should update contracts, client, manifests, tests, and evidence:

- Update `contracts/openapi/core-banking.yaml`.
- Update `packages/api-client/src/index.ts`.
- Add or update screen manifests.
- Add Spring integration tests for onboarding, account opening, signup/login, account list, recipient lookup, and customer transfers.
- Add Playwright/API smoke for signup to login to accounts to transfer to history, with explicit skip reasons when live environment variables are absent.
- Update `docs/test-evidence/customer-onboarding-self-service.md` and `docs/implementation-coverage-matrix.md`.

## Commands Attempted

Inspection commands run during Phase 0 before this document was created:

- `git status --short --branch`
- `sed -n '1,240p' PLAN.md`
- `sed -n '1,260p' BANKING_LAB_CODEX_PROMPT.md`
- `sed -n '1,260p' AGENTS.md`
- `sed -n '1,260p' docs/codex/customer-onboarding-self-service-goals.md`
- `sed -n '261,620p' docs/codex/customer-onboarding-self-service-goals.md`
- `sed -n '621,1100p' docs/codex/customer-onboarding-self-service-goals.md`
- `sed -n '1,220p' /Users/donghyunkim/.codex/skills/commit-convention/SKILL.md`
- `git checkout -b codex/customer-onboarding-self-service`
- `rg --files tests`
- `rg --files docs`
- `rg --files services/core-banking/src/main/kotlin/lab/banking/core apps/customer-web/src apps/staff-terminal/src packages/api-client/src contracts/openapi screen-manifests`
- `rg -n "^(test|describe|it)\\(" tests/*.test.mjs`
- `rg -n "(RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping)" services/core-banking/src/main/kotlin/lab/banking/core/customer services/core-banking/src/main/kotlin/lab/banking/core/staff services/core-banking/src/main/kotlin/lab/banking/core/security services/core-banking/src/main/kotlin/lab/banking/core/approval`
- `rg -n "(/api/auth|/api/customer|/api/customers|/api/staff|/api/accounts|/api/transfers|customer|account|transfer|signup|login)" contracts/openapi/core-banking.yaml packages/api-client/src/index.ts apps/customer-web/src apps/staff-terminal/src`
- `rg --files apps/customer-web/src/app apps/staff-terminal/src/app`
- `sed -n '1,260p' docs/implementation-coverage-matrix.md`
- `sed -n '1,220p' services/core-banking/src/main/kotlin/lab/banking/core/customer/CustomerAccountController.kt`
- `sed -n '1,260p' services/core-banking/src/main/kotlin/lab/banking/core/customer/CustomerTransferController.kt`
- `sed -n '1,320p' services/core-banking/src/main/kotlin/lab/banking/core/staff/StaffAccessController.kt`
- `sed -n '1,260p' services/core-banking/src/main/kotlin/lab/banking/core/security/SecurityConfig.kt`
- `sed -n '1,220p' services/core-banking/src/main/kotlin/lab/banking/core/security/AuthSessionController.kt`
- `rg -n "CREATE TABLE (customers|customer_kyc_profiles|customer_auth_identities|customer_onboarding_requests|accounts|account_limits|account_balance_projections|account_opening_requests)|CREATE SEQUENCE synthetic_account|customer_auth_identities|customer_onboarding_requests|account_opening_requests" services db infra`
- `sed -n '1,140p' db/migrations/V001__foundation.sql`
- `rg -n "CREATE TABLE .*requests|approval|idempotency|command_hash|customer_auth|opening|onboarding" db/migrations services/core-banking/src/main/kotlin/lab/banking/core/staff services/core-banking/src/main/kotlin/lab/banking/core/approval`
- `sed -n '1,260p' services/core-banking/src/main/kotlin/lab/banking/core/customer/CustomerAccountService.kt`
- `sed -n '1,360p' services/core-banking/src/main/kotlin/lab/banking/core/customer/CustomerTransferService.kt`
- `sed -n '3180,3300p' packages/api-client/src/index.ts`
- `sed -n '1,430p' apps/customer-web/src/components/workflow-routes.tsx`
- `sed -n '1,340p' apps/staff-terminal/src/components/workflow-routes.tsx`
- `rg --files screen-manifests/customer-web screen-manifests/staff-terminal | rg "(CWB-001|CWB-002|CWB-101|CWB-201|CST-201|CST-202|ACC-201|ACC-202|signup|login|account-list|internal-transfer-form|onboarding|opening)"`
- `sed -n '400,440p' contracts/openapi/core-banking.yaml`
- `sed -n '1,180p' docs/architecture/phase-4-customer-web.md`
- `sed -n '1,200p' docs/architecture/phase-3-staff-terminal.md`
- `sed -n '1,180p' docs/test-evidence/phase-4-customer-web.md`
- `rg -n "Customer(Account|Transfer|Self|Signup|Login|Onboarding|Opening)|customerAccount|customerTransfers|signupCustomer|loginCustomer|listCustomerAccounts|internal-account-lookup|/api/customer/accounts" services/core-banking/src/test services/core-banking/src/integrationTest tests apps/customer-web/e2e apps/staff-terminal/e2e`
- `rg -n "class .*IntegrationTest|fun .*customer|@Test" services/core-banking/src/integrationTest/kotlin/lab/banking/core/customer services/core-banking/src/integrationTest/kotlin/lab/banking/core/staff services/core-banking/src/integrationTest/kotlin/lab/banking/core/security`
- `sed -n '1,260p' services/core-banking/src/main/kotlin/lab/banking/core/security/BankingLabAuthorizationFilter.kt`
- `sed -n '1,260p' services/core-banking/src/main/kotlin/lab/banking/core/security/BankingLabRouteAuthorizationManager.kt`
- `rg -n "CustomerAuth|PasswordEncoder|BCrypt|customer_auth|signup|login|synthetic-auth|dev-simulator-token|simulator-token" services/core-banking/src/main/kotlin services/core-banking/src/integrationTest db/migrations infra/docker-compose docker-compose.yml .github package.json apps/customer-web`
- `sed -n '1,280p' services/core-banking/src/main/kotlin/lab/banking/core/synthetic/SyntheticDataSeeder.kt`
- `sed -n '1,220p' services/core-banking/src/main/kotlin/lab/banking/core/customer/CustomerAccountModels.kt`
- `sed -n '1,260p' services/core-banking/src/main/kotlin/lab/banking/core/customer/CustomerTransferModels.kt`
- `sed -n '1,200p' services/core-banking/src/main/kotlin/lab/banking/core/approval/ApprovalModels.kt`
- `sed -n '1,220p' apps/customer-web/e2e/customer-web-parity.spec.ts`
- `sed -n '1,220p' apps/staff-terminal/e2e/staff-terminal-parity.spec.ts`
- `node -e "const p=require('./package.json'); console.log(JSON.stringify(p.scripts,null,2))"`
- `rg -n "customerAccountDetail|requestCustomerTransfer|customerTransactions|customerTransfers|signupCustomer|loginCustomer|listCustomerAccounts|lookupInternalAccountRecipient|requestStaffCustomerOnboarding|requestStaffAccountOpening|accountOpening|onboarding" packages/api-client/src/index.ts`
- `rg -n "customerAccountDetail|requestCustomerTransfer|customerTransactions|customerTransfers|signupCustomer|loginCustomer|listCustomerAccounts|lookupInternalAccountRecipient|requestStaffCustomerOnboarding|requestStaffAccountOpening|customerAccountList|internalAccount" contracts/openapi/core-banking.yaml`
- `rg -n "signup|login|account list|account overview|internal transfer|form|workflow shell|demo fallback|hard-coded|SYN-CUS-001|ACC-SYN-001-001" apps/customer-web/src/app apps/customer-web/src/components apps/staff-terminal/src/app apps/staff-terminal/src/components`
- `rg -n "CST-201|CST-202|ACC-201|ACC-202|CWB-001|CWB-002|CWB-101|CWB-201" screen-manifests docs tests`

Validation commands run after this document was created:

- `npm test`: passed, 174 tests.
- `npm run validate:manifests`: passed, 109 screen manifests.
- `npm run packages:typecheck`: passed for `@banking-lab/screen-engine`, `@banking-lab/form-engine`, `@banking-lab/api-client`, and `@banking-lab/auth-client`.
- `npm run scripts:typecheck`: passed.
- `npm run test:core-banking:unit`: first sandboxed run failed before tests with Gradle `FileLockContentionHandler` `java.net.SocketException: Operation not permitted`; escalated rerun passed with `:services:core-banking:test` up to date.
- `npm run test:core-banking:integration`: first sandboxed run failed before tests with Gradle `FileLockContentionHandler` `java.net.SocketException: Operation not permitted`; escalated rerun passed with `:services:core-banking:integrationTest` up to date.
- `git push -u origin codex/customer-onboarding-self-service`: passed.
- `gh pr create --base main --head codex/customer-onboarding-self-service --title "docs: 고객 셀프서비스 기준 상태 문서 추가" --body "..."`
  passed and opened PR #54.
- `gh pr checks 54`: completed with all hosted checks reported as failed.
- `gh api repos/kdh949/banking-lab/actions/runs/27068786983/jobs`: showed all 19 jobs completed in 2-4 seconds with no recorded steps and no runner assigned.
- `gh api repos/kdh949/banking-lab/check-runs/79894195214/annotations`: showed hosted CI was blocked before job startup because the GitHub account has failed recent payments or needs a higher spending limit.

## Phase 1 Staff Customer Onboarding Update

Implemented in commit `60cf986f` on branch `codex/customer-onboarding-self-service`:

- Added `customer_onboarding_requests` and `customer_auth_identities` for synthetic-only onboarding requests and local synthetic auth identity bindings.
- Added Spring customer onboarding service/controller/models with maker-checker approval, separation of duties, idempotency/command hash conflict detection, structured errors, password hashing through `PasswordEncoder`, and execution audit event `CUSTOMER_ONBOARDING_EXECUTED`.
- Added staff terminal screen manifests `CST-201` and `CST-202`, OpenAPI operation ids, and typed API client methods for request/get/approve/reject/execute.
- Added integration tests covering request replay, command hash conflict, self-approval rejection, checker approval, execution, hashed auth identity creation, missing reason, weak password, duplicate username, rejected execution, and audit persistence.
- Added Node structural tests for Phase 1 contract/client/manifest/persistence wiring.

Phase 1 validation commands:

- `node --test tests/customerOnboardingSelfService.test.mjs`: passed, 2 tests.
- `npm run validate:manifests`: passed, 111 screen manifests.
- `npm run contracts:lint`: first run failed because the approve endpoint was incorrectly marked reason-required without a reason field; contract metadata was corrected and rerun passed.
- `npm run contracts:check-client`: passed, 144 operation ids matched 137 shared client methods/exemptions.
- `npm --workspace @banking-lab/api-client run typecheck`: passed.
- `npm run next:staff-terminal:typecheck`: passed.
- `npm run test:core-banking:integration -- --tests lab.banking.core.onboarding.CustomerOnboardingIntegrationTest`: first Phase 1 run found two test/setup issues; after fixes, reruns passed. Latest rerun passed with Gradle up to date.
- `npm test`: passed, 176 tests.
- `git push`: passed, pushed commit `60cf986f` to PR #54.
- `gh pr view 54 --json url,headRefName,baseRefName,state,statusCheckRollup`: showed PR #54 remains open, with all hosted checks reported failed for run `27069232879`.
- `gh api repos/kdh949/banking-lab/check-runs/79895386075/annotations`: latest hosted CI annotation says the job was not started because recent account payments have failed or the spending limit needs to be increased.

## Phase 2 Staff Account Opening Update

Implemented in commit `ca9a319a` on branch `codex/customer-onboarding-self-service`:

- Added `account_opening_requests` and `synthetic_account_opening_seq` for synthetic account-opening workflow state and generated account numbers.
- Added Spring account-opening service/controller/models with maker-checker approval, idempotency/command hash conflict detection, request/review/execute states, structured errors, and execution audit event `ACCOUNT_OPENING_EXECUTED`.
- Execution creates `accounts`, `account_limits`, and zero `account_balance_projections`; optional opening deposits post only through `LedgerCommandService.deposit`, producing balanced `ledger_transactions`/`ledger_postings`, idempotency records, and outbox events.
- Added staff terminal screen manifests `ACC-201` and `ACC-202`, OpenAPI operation ids, and typed API client methods for request/get/approve/reject/execute.
- Added integration tests covering request replay, command hash conflict, self-approval rejection, checker approval, execution, generated account number masking, opening deposit ledger posting, execute replay, missing reason, invalid limits, missing deposit idempotency key, rejected execution, and audit/outbox persistence.
- Extended Node structural tests for Phase 2 account-opening contract/client/manifest/persistence/ledger-boundary wiring.

Phase 2 validation commands:

- `npm run test:core-banking:integration -- --tests lab.banking.core.account.AccountOpeningIntegrationTest`: passed.
- `node --test tests/customerOnboardingSelfService.test.mjs`: passed, 4 tests.
- `npm run validate:manifests`: passed, 113 screen manifests.
- `npm run contracts:lint`: passed.
- `npm run contracts:check-client`: passed, 149 operation ids matched 142 shared client methods/exemptions.
- `npm --workspace @banking-lab/api-client run typecheck`: passed.
- `npm run next:staff-terminal:typecheck`: passed.
- `npm run test:core-banking:integration -- --tests lab.banking.core.account.AccountOpeningIntegrationTest --tests lab.banking.core.onboarding.CustomerOnboardingIntegrationTest`: passed.
- `npm test`: passed, 178 tests.
- `git push`: passed, pushed commit `ca9a319a` to PR #54.
- `gh pr view 54 --json statusCheckRollup`: showed hosted CI run `27069520636` completed with all jobs failed within a few seconds.
- `gh api repos/kdh949/banking-lab/check-runs/79896132048/annotations`: latest hosted CI annotation says the job was not started because recent account payments have failed or the spending limit needs to be increased.

## Phase 3 Customer Signup/Login Update

Implemented in commit `2e0d2266` on branch `codex/customer-onboarding-self-service`:

- Added `POST /api/auth/customer/signup` and `POST /api/auth/customer/login` in Kotlin/Spring under `/api/auth/customer`.
- Added `synthetic_customer_signup_customer_seq` plus `signup_idempotency_key` and `signup_command_hash` on `customer_auth_identities` so self-signup is synthetic-only and idempotent.
- Signup creates `customers`, `customer_kyc_profiles` with `PENDING` synthetic KYC status, and `customer_auth_identities`; it hashes passwords with Spring `PasswordEncoder` and rejects duplicate/reserved usernames, weak passwords, and idempotency-key command hash conflicts.
- Login validates synthetic credentials, updates failed-login counters and last-login state, returns structured `CUSTOMER_AUTHENTICATION_FAILED` on invalid credentials, and does not store plaintext passwords in auth metadata or audit payloads.
- Added a guarded simulator-token issuer that returns lab-format bearer tokens only when `banking-lab.security.customer-auth.synthetic-token-issuer-enabled`, simulator-token decoding, and dev simulator tokens are explicitly enabled.
- Extended the prod-like profile guard so synthetic customer token issuance fails fast in `prod`, `production`, `prod-like`, and `prodlike` profiles.
- Whitelisted signup/login in both Spring Security and `BankingLabAuthorizationFilter`, while leaving `/api/auth/session` authenticated as the session proof endpoint.
- Added OpenAPI operation ids and API-client methods `signupCustomer` and `loginCustomer`.

Phase 3 validation commands:

- `npm run test:core-banking:integration -- --tests lab.banking.core.auth.CustomerAuthIntegrationTest`: first sandbox attempt failed before tests with Gradle `java.net.SocketException: Operation not permitted`; escalated rerun passed, 4 tests.
- `npm run test:core-banking:integration -- --tests lab.banking.core.auth.CustomerAuthIntegrationTest --tests lab.banking.core.account.AccountOpeningIntegrationTest --tests lab.banking.core.onboarding.CustomerOnboardingIntegrationTest`: passed.
- `npm run test:core-banking:unit -- --tests lab.banking.core.security.SpringSecurityResourceServerTest`: passed.
- `node --test tests/customerOnboardingSelfService.test.mjs`: passed, 6 tests.
- `npm run contracts:lint`: passed.
- `npm run contracts:check-client`: passed, 151 operation ids matched 144 shared client methods/exemptions.
- `npm --workspace @banking-lab/api-client run typecheck`: passed.
- `npm run validate:manifests`: passed, 113 screen manifests.
- `npm run next:staff-terminal:typecheck`: passed.
- `npm test`: passed, 180 tests.
- `git push`: passed, pushed commit `2e0d2266` to PR #54.
- `gh pr view 54 --json url,headRefName,baseRefName,state,statusCheckRollup`: PR #54 remained open against `main`; initial latest run `27069864662` was queued.
- `gh pr view 54 --json statusCheckRollup`: after polling, hosted CI run `27069864662` completed with all jobs failed within a few seconds.
- `gh api repos/kdh949/banking-lab/check-runs/79897048781/annotations`: latest hosted CI annotation says the job was not started because recent account payments have failed or the spending limit needs to be increased.

## Phase 4 Customer-Web Forms Update

Implemented in commit `2ab03926` on branch `codex/customer-onboarding-self-service`:

- Added customer account-list and internal synthetic recipient-lookup APIs in Kotlin/Spring.
- Account list returns only owned, non-system accounts for the authenticated/supplied synthetic customer context, masks account numbers, and writes customer self-service audit event `ACCOUNT_LIST_VIEW`.
- Internal recipient lookup only returns active, non-system synthetic internal accounts and masks account numbers; customer transfers now reject synthetic system accounts before ledger posting.
- Extended OpenAPI and the shared TypeScript API client with `customerAccounts` and `internalRecipientLookup`.
- Replaced customer-web workflow shell pages for `/signup`, `/login`, `/accounts`, `/accounts/[accountId]`, `/transfers/new`, and `/transfers/[resultId]` with form-backed React screens.
- Customer-web session state is stored from signup/login responses and used for authorization headers and customer/account context; the new forms do not use hard-coded `SYN-CUS-001` or `ACC-SYN-001-001`.
- Added explicit demo fallback when no live API base URL is configured, plus structured error display for validation/auth/replay/held/posted/failure states.
- Extended customer-web Playwright structural coverage and Node structural tests for the form-backed journey.

Phase 4 validation commands:

- `npm run test:core-banking:integration -- --tests lab.banking.core.customer.CustomerAccountApiParityIntegrationTest --tests lab.banking.core.customer.CustomerTransferApiParityIntegrationTest`: passed.
- `npm run next:customer-web:typecheck`: passed.
- `npm --workspace @banking-lab/api-client run typecheck`: passed.
- `node --test tests/nextScaffold.test.mjs tests/customerOnboardingSelfService.test.mjs`: passed, 22 tests.
- `npm run contracts:lint`: passed.
- `npm run contracts:check-client`: passed, 153 operation ids matched 146 shared client methods/exemptions.
- `npm run next:customer-web:build`: passed and built the `/signup`, `/login`, `/accounts`, and `/transfers/new` routes.
- `npm run next:customer-web`: sandboxed run failed with `listen EPERM`; escalated rerun started the local customer-web dev server at `http://localhost:3001`.
- Browser/Playwright route inspection through the Node REPL failed because Chromium could not acquire the required macOS sandbox port; escalated Playwright was used instead.
- `npm run test:e2e -- apps/customer-web/e2e/customer-web-parity.spec.ts --project=chromium`: first run failed due an assertion expecting transfer-copy while logged out; after fixing the test expectation, rerun passed 3 tests and skipped 11 API-backed smokes because live API environment variables were absent.
- `npm run validate:manifests`: passed, 113 screen manifests.
- `npm test`: failed with 180 passing tests and 1 failing test. The only failure was `tests/platformDeployment.test.mjs`, because `kubectl apply --dry-run=client --validate=false -f infra/k8s` tried to use the currently configured local Kubernetes API and received `the server is currently unable to handle the request`.
- `KUBECONFIG=/tmp/banking-lab-empty-kubeconfig npm run k8s:validate`: passed, 27 resources, `kubectl skipped_no_cluster`.
- `git push`: passed, pushed commit `2ab03926` to PR #54.
- `gh pr view 54 --json statusCheckRollup,headRefOid,url`: PR #54 pointed at `2ab03926`; hosted CI run `27070282790` completed with all jobs failed within a few seconds.
- `gh api repos/kdh949/banking-lab/check-runs/79898153086/annotations`: latest hosted CI annotation says the job was not started because recent account payments have failed or the spending limit needs to be increased.

## Phase 5 Contracts, Manifests, Evidence, And Smoke Update

Implemented on branch `codex/customer-onboarding-self-service` after Phase 4:

- Added customer-web manifests `CWB-001` and `CWB-002` for synthetic signup/login, including password masking, idempotent signup policy, dev/test synthetic-auth metadata, and self-service audit event coverage.
- Updated `CWB-101` and `CWB-201` to describe form-backed owned account list and internal synthetic transfer flows, including `customerAccounts`, `internalRecipientLookup`, `requestCustomerTransfer`, ownership enforcement, internal-recipient-only policy, and idempotency policy.
- Added an env-gated Playwright/API smoke at `apps/customer-web/e2e/customer-onboarding-self-service.spec.ts`. When live synthetic API and staff maker/checker bearer tokens are supplied, it signs up a unique customer, logs in, opens two approved synthetic accounts through staff maker-checker APIs, lists owned accounts, looks up the destination as an internal recipient, posts an idempotent transfer, checks transfer replay, reads source-account transaction history, and renders the account/result routes with stored session state.
- Updated `docs/test-evidence/customer-onboarding-self-service.md` and `docs/implementation-coverage-matrix.md`.
- Extended structural tests for the Phase 5 manifests, smoke, evidence, and reusable form-contract field list.
- Adjusted Kubernetes validation so an unavailable local Kubernetes API discovery response is recorded through the existing `skipped_no_cluster` path after structural manifest validation succeeds.

Phase 5 validation commands:

- `npm run test:core-banking:integration -- --tests lab.banking.core.onboarding.CustomerOnboardingIntegrationTest --tests lab.banking.core.account.AccountOpeningIntegrationTest --tests lab.banking.core.auth.CustomerAuthIntegrationTest --tests lab.banking.core.customer.CustomerAccountApiParityIntegrationTest --tests lab.banking.core.customer.CustomerTransferApiParityIntegrationTest`: passed.
- `npm run next:customer-web:typecheck`: passed.
- `npm run next:customer-web:build`: passed.
- `npm run next:staff-terminal:typecheck`: passed.
- `npm run packages:typecheck`: passed.
- `npm run scripts:typecheck`: passed.
- `npm run contracts:lint`: passed.
- `npm run contracts:check-client`: passed, 153 operation ids matched 146 shared client methods/exemptions.
- `npm run validate:manifests`: passed, 115 screen manifests.
- `node --test tests/customerOnboardingSelfService.test.mjs`: passed, 8 tests.
- `node --test tests/nextScaffold.test.mjs tests/customerOnboardingSelfService.test.mjs`: passed, 23 tests.
- `node --test tests/manifestExpansionForm.test.mjs`: passed, 2 tests.
- `npm test`: passed, 182 tests.
- `npm run k8s:validate`: passed, 27 resources, `kubectl skipped_no_cluster`.
- `npm run platform:validate`: passed for Kubernetes structural validation, Helm template validation, and Argo CD validation.
- `docker compose config`: passed.
- `docker compose --profile platform config`: passed.
- `npm run security:secrets-check`: passed, 886 files checked.
- `npm run security:evidence`: first sandboxed run failed due registry/Docker access restrictions; approved escalated rerun passed npm audit, Semgrep, Trivy, and CycloneDX SBOM, with DAST skipped because `BANKING_LAB_DAST_URL` was not set.
- `npm run test:e2e -- apps/customer-web/e2e/customer-onboarding-self-service.spec.ts --project=chromium`: passed with 1 skipped because live synthetic API and staff maker/checker token environment variables were absent.
- `npm run test:e2e -- apps/customer-web/e2e/customer-web-parity.spec.ts apps/customer-web/e2e/customer-onboarding-self-service.spec.ts --project=chromium`: passed 3 tests and skipped 12 live API-backed smokes because live API environment variables were absent.
- `npm run test:e2e -- --project=chromium`: passed 19 tests and skipped 59 live API/Keycloak/payment/notification smokes because corresponding environment variables were absent.

## Commands Not Attempted

Not attempted in Phase 0:

- `npm run contracts:lint`
- `npm run contracts:check-client`
- `npm run test:e2e`
- `docker compose config`
- `docker compose --profile platform config`

No additional Phase 5 implementation commands are pending locally. Hosted CI still must be rerun after GitHub billing/spending-limit settings allow jobs to start.
