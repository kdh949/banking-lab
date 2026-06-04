# API-backed Channel Smoke Evidence

Date: 2026-06-04

## Scope

This evidence records browser-backed Next.js channel calls into the Spring Boot core-banking API across the seven active channel apps, plus browser-backed command smoke for customer transfer retry/failure visibility, customer transfer history, customer held FDS status visibility, durable customer held/failed transfer status parity, customer complaint entry, customer complaint confirmation, customer-web Keycloak propagation for all current API-backed customer paths, staff-terminal Keycloak propagation for masked lookup, privileged unmask, customer-change approval, account hold/release approval, transfer-limit change approval, KYC re-confirmation approval, and WebAuthn required-action completion, complaint-portal Keycloak propagation for answer approval and workflow failure-state, ops-console Keycloak propagation for reconciliation adjustment and workflow failure-state, audit-console Keycloak propagation for hash-chain read-model evidence, FDS/AML-console Keycloak propagation for risk read-model, release/block/closure approvals, and workflow failure-state, admin-console Keycloak propagation for the `security-admin01` platform-control summary, privileged unmask, customer change approval, account hold/release approval, transfer-limit change approval, KYC re-confirmation approval, complaint answer approval, FDS release approval, FDS block approval, AML closure approval, reconciliation adjustment approval, and complaint/FDS/AML/reconciliation workflow failure-states. It does not mark Node retirement ready.

## Changes Proven

- `@banking-lab/api-client` provides a shared TypeScript client for staff customer detail and customer account detail calls.
- `@banking-lab/auth-client` creates synthetic simulator Bearer tokens for local smoke and PKCE authorization URLs for the first customer-web Keycloak login smoke.
- `customer-web` renders an API-backed account panel that calls `GET /api/customer/accounts/{accountId}/detail` with customer ownership context.
- `customer-web` can execute a live Keycloak Authorization Code + PKCE browser smoke, exchange the code through the Next BFF route `POST /api/auth/keycloak-token`, render the same masked account detail, submit idempotent transfer retry, transfer failure, history/status, held/failed status, complaint entry, and complaint confirmation paths with a Keycloak-issued Bearer token while Spring simulator tokens are disabled.
- `customer-web` can execute a Spring API-backed browser command smoke by calling `POST /api/customer/transfers` twice with one idempotency key and rendering the replayed transaction ID.
- `customer-web` can execute a Spring API-backed browser failure smoke by attempting an over-balance transfer through `POST /api/customer/transfers` and rendering structured `LEDGER_INSUFFICIENT_AVAILABLE_BALANCE` details from the real route.
- `customer-web` can execute a Spring API-backed browser history/status smoke by posting a customer transfer, reading it through `GET /api/customer/transactions`, and reading held transfer state through `GET /api/customer/transfers`.
- `customer-web` can execute a Spring API-backed held/failed status smoke by posting a high-amount transfer that becomes `HELD`, posting an invalid negative-amount command that becomes durable `FAILED`, and reading both outcomes back through `GET /api/customer/transfers`.
- `customer-web` can execute a Spring API-backed complaint entry smoke by posting `POST /api/customer/complaints`, creating a `RECEIVED` complaint case with timeline and audit evidence.
- `customer-web` can execute a Spring API-backed complaint confirmation smoke by posting `POST /api/customer/complaints/{caseId}/confirm`, closing an answered complaint, rendering `customerConfirmedAt`, and reading a `CLOSED` timeline event.
- The Spring customer transaction history API reads the same ledger transaction/posting source as the staff transaction search API, and integration tests assert the same transaction ID appears through both channels.
- The Spring customer transfer status API exposes durable FDS held-transfer details from `fds_cases.transfer_status` without creating unsafe ledger postings for the held transfer.
- The Spring customer transfer API now persists channel-visible command outcomes in `customer_transfer_results` so `POSTED`, `HELD`, `FAILED`, and `BLOCKED` statuses can be read without treating failed or held commands as ledger postings.
- FDS release/block decisions update the linked customer transfer result to `POSTED` or `BLOCKED` after maker-checker approval while retaining balanced ledger posting rules for released transfers.
- The Spring customer transfer API checks customer ownership before delegating to the ledger service, so customer-channel transfer smoke uses the customer route rather than the generic ledger route.
- `staff-terminal` renders an API-backed inquiry panel that calls `GET /api/staff/customers/{customerId}/detail` with a business reason.
- `staff-terminal` renders `APR001` as an API-backed manifest workspace panel that calls `GET /api/approvals`, refreshes selection through `GET /api/approvals/{approvalId}`, executes `POST /api/staff/approvals/{approvalId}/approve` through `@banking-lab/api-client`, and reads related audit events through `GET /api/audit/events`.
- `staff-terminal` renders `AUD001` as an API-backed manifest workspace panel that calls `GET /api/audit/events`, supports event selection, and displays hash-chain status inside the manifest workspace.
- `staff-terminal` can execute a Spring API-backed privileged unmask smoke by first proving branch-role denial is visible as a structured browser error, then approving a time-boxed `UNMASKED_TIMEBOXED` response as `manager01`.
- `staff-terminal` can execute a Spring API-backed browser command smoke by requesting a customer information change for `SYN-CUS-CMD-001`, proving self-approval rejection for the maker actor, approving with a separate manager actor, and observing a masked updated phone.
- `staff-terminal` can execute a Spring API-backed account hold/release command smoke for `ACC-SYN-HOLD-001` by requesting an `ACCOUNT_HOLD` approval, proving maker self-approval rejection, approving as a separate branch manager, requesting an `ACCOUNT_HOLD_RELEASE` approval, proving release self-approval rejection, approving as a separate ops checker, and observing the account return to `ACTIVE` without ledger source-row mutation.
- `staff-terminal` can execute a Spring API-backed transfer-limit command smoke for `ACC-SYN-LIMIT-001` by requesting a `TRANSFER_LIMIT_CHANGE` approval, proving maker self-approval rejection, approving as a separate branch manager, and observing `account_limits` update without ledger source-row mutation.
- `staff-terminal` can execute a Spring API-backed KYC re-confirmation command smoke for `SYN-CUS-KYC-001` by requesting a `CUSTOMER_KYC_REVIEW` approval, proving maker self-approval rejection, approving as a separate branch manager, and observing `customer_kyc_profiles.kyc_status = REVIEW_REQUIRED` without ledger source-row mutation or real KYC provider calls.
- `staff-terminal` can execute a live Keycloak Authorization Code + PKCE browser smoke, exchange branch and checker codes through the Next BFF route `POST /api/auth/keycloak-token`, render masked lookup with the `branch01` token, execute privileged unmask with the `manager01` token, request a customer information change as `branch01`, and approve it with the `manager01` token while Spring simulator tokens are disabled.
- `staff-terminal` can complete a live Keycloak `webauthn-register` required action using a Chromium virtual authenticator, exchange the returned authorization code through the Next BFF route, and call Spring with the resulting signed Bearer token while Spring simulator tokens are disabled.
- The synthetic Keycloak realm backing that WebAuthn smoke now has explicit local WebAuthn policy and `PASSKEY_RECOVERY_ADMIN` role segregation evidence in `docs/test-evidence/keycloak-live-realm-smoke.md`.
- `complaint-portal` renders an API-backed case panel that calls `GET /api/staff/complaints`.
- `complaint-portal` can execute the first Spring API-backed browser command smoke by drafting an answer for `CMP-SYN-CMD-001` and approving the resulting maker-checker approval.
- `complaint-portal` can execute a Spring API-backed browser failure-state smoke by attempting a duplicate answer draft for already answered `CMP-SYN-FAIL-001` and rendering structured `WORKFLOW_STATE_VIOLATION` details from the real route.
- `complaint-portal` can execute a live Keycloak Authorization Code + PKCE browser smoke, exchange complaint handler and checker codes through the Next BFF route `POST /api/auth/keycloak-token`, render `CMP-SYN-001` with the `complaint01` token, draft an answer as `complaint01`, approve it as `manager01`, and render the duplicate answer-draft `WORKFLOW_STATE_VIOLATION` while Spring simulator tokens are disabled.
- `ops-console` renders an API-backed reconciliation panel that calls `GET /api/ops/reconciliation-items`.
- `ops-console` can execute a Spring API-backed browser command smoke by requesting a reconciliation adjustment for `REC-SYN-CMD-001`, approving the generated maker-checker approval, and observing `ADJUSTED` plus a posted ledger transaction.
- `ops-console` can execute a Spring API-backed browser failure-state smoke by attempting an adjustment request for already adjusted `REC-SYN-FAIL-001` and rendering structured `WORKFLOW_STATE_VIOLATION` details from the real route.
- `ops-console` can execute a live Keycloak Authorization Code + PKCE browser smoke, exchange ops operator and checker codes through the Next BFF route `POST /api/auth/keycloak-token`, render `REC-SYN-001` with the `ops01` token, request a reconciliation adjustment as `ops01`, approve it as `manager01`, and render the adjusted-item `WORKFLOW_STATE_VIOLATION` while Spring simulator tokens are disabled.
- `audit-console` renders an API-backed audit panel that calls `GET /api/audit/events` and displays hash-chain validity.
- `audit-console` can execute a live Keycloak Authorization Code + PKCE browser smoke, exchange an auditor code through the Next BFF route `POST /api/auth/keycloak-token`, and render hash-chain validity plus `AUD-SYN-SEED-001` while Spring simulator tokens are disabled.
- `fds-aml-console` renders an API-backed risk panel that calls `GET /api/staff/fds-cases` and `GET /api/staff/aml-cases`.
- `admin-console` renders an API-backed platform-control panel that calls `GET /api/admin/platform/summary`, keeps synthetic-only and Node reference boundary controls visible, and can execute a live Keycloak Authorization Code + PKCE browser smoke for `security-admin01`.
- `fds-aml-console` can execute a Spring API-backed browser command smoke by requesting release for `FDS-SYN-CMD-001`, approving the generated maker-checker approval, and observing a posted ledger transaction.
- `fds-aml-console` can execute a Spring API-backed browser command smoke by requesting block for `FDS-SYN-BLOCK-CMD-001`, approving the generated maker-checker approval, and observing `BLOCKED` with no ledger posting.
- `fds-aml-console` can execute a Spring API-backed browser command smoke by requesting AML closure for `AML-SYN-CMD-001`, approving the generated maker-checker approval, and observing the workflow status move to `CLOSED` with `STR_SIMULATED`.
- `fds-aml-console` can execute a Spring API-backed browser failure-state smoke by attempting release for already released `FDS-SYN-FAIL-001` and rendering structured `WORKFLOW_STATE_VIOLATION` details from the real route.
- `fds-aml-console` can execute a Spring API-backed browser failure-state smoke by attempting closure for already closed `AML-SYN-FAIL-001` and rendering structured `WORKFLOW_STATE_VIOLATION` details from the real route.
- `fds-aml-console` can execute a live Keycloak Authorization Code + PKCE browser smoke, exchange risk reviewer and checker codes through the Next BFF route `POST /api/auth/keycloak-token`, and execute FDS release, FDS block, AML closure, and duplicate workflow failure-state paths with Spring simulator tokens disabled.
- The Spring API can seed synthetic customers/accounts when `BANKING_LAB_SYNTHETIC_SEED_ENABLED=true`.
- The synthetic seed now includes deterministic complaint, FDS, AML, reconciliation, audit, command-only customer rows, account hold command rows, command-only complaint confirmation and failure-state rows, command-only FDS account rows, FDS release/block command rows, FDS/AML Keycloak command rows, command-only FDS failure-state rows, command-only AML case rows, command-only AML failure-state rows, command-only reconciliation command rows, and command-only reconciliation failure-state rows for repeatable live channel smoke without mutating read-model account assertions.
- The synthetic seed now includes dedicated transfer-limit and KYC command rows so the `LIM102` and `KYC101` browser smokes do not mutate existing account/customer read-model assertions.
- The Spring security filter keeps RBAC/ABAC enforcement enabled while allowing browser CORS preflight and CORS-readable structured denial responses for channel clients.
- Audit event appends now use a PostgreSQL hash-chain lock row so concurrent browser API smoke paths keep `previous_event_hash` ordered instead of surfacing transient HTTP 500s.
- Staff approval execution retries transient PostgreSQL SERIALIZABLE conflicts up to five times so parallel browser command approvals do not leak transient `40001` conflicts as HTTP 500s.
- FDS decision request creation and AML closure request creation now use the same bounded SERIALIZABLE retry policy for browser command-smoke concurrency.
- Reconciliation adjustment request creation uses the same bounded SERIALIZABLE retry policy for browser command-smoke concurrency.
- Staff masked customer detail uses bounded SERIALIZABLE retry so reason-required audited inquiry smoke does not leak transient audit hash-chain lock conflicts as HTTP 500s.
- Staff privileged unmask uses bounded SERIALIZABLE retry so branch-denial plus manager-approved unmask browser smoke does not leak transient audit hash-chain lock conflicts as HTTP 500s during parallel channel runs.
- Customer transfer commands use bounded SERIALIZABLE retry so parallel browser transfer smokes do not leak transient PostgreSQL `40001` conflicts as HTTP 500s.
- Customer complaint entry uses bounded SERIALIZABLE retry so parallel browser audit writes do not leak transient PostgreSQL `40001` conflicts as HTTP 500s.
- Customer complaint confirmation uses the same bounded SERIALIZABLE retry and writes a customer `COMMAND_EXECUTED` audit event while preserving customer ownership checks.
- Playwright exercises the real browser path from Next.js to the live Spring API.

## 2026-06-04 APR001/AUD001 Manifest Workspace Update

This update added Playwright coverage for API-configured staff terminal runs without marking the API-backed browser path passed in the current local environment.

Commands run:

- `npm run validate:manifests` passed.
- `npm run packages:typecheck` passed.
- `npm run next:staff-terminal:typecheck` passed.
- `npm test` passed with 131 Node reference/oracle tests.
- `npm run test:screen-engine` passed with 10 tests.
- `npm run next:staff-terminal:build` passed.
- `npm run test:e2e -- apps/staff-terminal/e2e/staff-terminal-parity.spec.ts` passed locally with 5 passed and 8 skipped because `BANKING_LAB_E2E_API_BASE_URL` was not configured.
- `npm run test:e2e` passed locally with 17 passed and 35 skipped because API/Keycloak E2E environment variables were not configured.
- `npm run evidence:phase3` passed and regenerated `docs/test-evidence/generated/phase-3-staff-terminal.json`.
- `npm run evidence:pack` passed and regenerated the evidence pack summary.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test` passed after sandbox escalation.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.approval.ApprovalApiParityIntegrationTest --tests lab.banking.core.audit.AuditMaskingParityIntegrationTest` could not run to completion in this environment. The sandboxed attempt was blocked by Gradle lock socket creation, and the escalated attempt reached Testcontainers initialization but Docker was unavailable (`/var/run/docker.sock` missing).

Not proven locally on 2026-06-04:

- API-backed APR001 browser approval execution against a live Spring API.
- API-backed AUD001 browser audit-event retrieval against a live Spring API.

Those two browser paths are covered by conditional Playwright tests and require `BANKING_LAB_E2E_API_BASE_URL` plus a running Spring API with synthetic seed data.

## 2026-06-04 ACC103/ACC104 Account Hold Update

This update adds the first Phase B staff-command vertical slice from `docs/codex/implementation_missing_features_goals.md`.

Changes:

- `db/migrations/V013__account_hold_requests.sql` adds durable account hold/release request state with idempotency keys.
- `POST /api/staff/accounts/{accountId}/hold-requests` creates `ACCOUNT_HOLD` maker-checker approvals.
- `POST /api/staff/accounts/{accountId}/hold-release-requests` creates `ACCOUNT_HOLD_RELEASE` maker-checker approvals.
- Approval execution updates account status plus hold/available balance projections only; ledger transactions and postings are not updated for hold/release state.
- The shared api-client and staff-terminal manifest renderer expose a conditional ACC103 browser smoke.

Commands run:

- `npm run validate:manifests` passed.
- `npm run packages:typecheck` passed.
- `npm run next:staff-terminal:typecheck` passed.
- `npm run next:staff-terminal:build` passed.
- `npm run test:screen-engine` passed with 10 tests.
- `npm run test:core-banking:unit -- --rerun-tasks` passed after sandbox escalation.
- `scripts/run-core-banking-tests.sh :services:core-banking:compileIntegrationTestKotlin` passed after sandbox escalation.
- `npm test` passed with 131 Node reference/oracle tests.
- `npm run scripts:typecheck` passed.
- `npm run test:e2e -- apps/staff-terminal/e2e/staff-terminal-parity.spec.ts` passed locally with 5 passed and 8 skipped because `BANKING_LAB_E2E_API_BASE_URL` was not configured.

Not proven locally on 2026-06-04:

- `npm run test:core-banking:integration -- --tests lab.banking.core.staff.StaffAccessApiParityIntegrationTest` reached Testcontainers initialization but failed because local Docker provider discovery failed.
- API-backed ACC103/ACC104 browser execution against a live Spring API was not run locally because `BANKING_LAB_E2E_API_BASE_URL` was not configured.

## 2026-06-04 LIM102 Transfer Limit Update

This update adds the next Phase B staff-command vertical slice from `docs/codex/implementation_missing_features_goals.md`.

Changes:

- `db/migrations/V014__account_limit_change_requests.sql` adds durable transfer-limit change request state with idempotency keys.
- `GET /api/staff/customers/{customerId}/transfer-limits` reads account transfer limits with reason-required `LIMIT_VIEW` audit.
- `POST /api/staff/accounts/{accountId}/limit-change-requests` creates `TRANSFER_LIMIT_CHANGE` maker-checker approvals.
- Approval execution updates `account_limits` only; ledger transactions and postings are not updated for limit policy state.
- The shared api-client and staff-terminal manifest renderer expose a conditional LIM102 browser smoke.

Commands run so far:

- `npm run validate:manifests` passed.
- `npm run packages:typecheck` passed.
- `npm run next:staff-terminal:typecheck` passed.
- `npm run next:staff-terminal:build` passed.
- `npm run test:screen-engine` passed with 10 tests.
- `scripts/run-core-banking-tests.sh :services:core-banking:compileKotlin :services:core-banking:compileIntegrationTestKotlin` passed after sandbox escalation.
- `npm test` passed with 131 Node reference/oracle tests.
- `npm run scripts:typecheck` passed.
- `npm run test:core-banking:unit -- --rerun-tasks` passed after sandbox escalation.
- `npm run test:e2e -- apps/staff-terminal/e2e/staff-terminal-parity.spec.ts` passed locally with 5 passed and 9 skipped because `BANKING_LAB_E2E_API_BASE_URL` was not configured.
- `npm run test:e2e` passed locally with 17 passed and 36 skipped because API/Keycloak variables were not configured.
- `docker compose config` passed.
- `docker compose --profile platform config` passed.
- `npm run evidence:refresh-check` passed.
- `git diff --check` passed.

Not proven locally on 2026-06-04:

- `npm run test:core-banking:integration -- --tests lab.banking.core.staff.StaffAccessApiParityIntegrationTest` reached Testcontainers initialization but failed because Docker provider discovery failed.
- API-backed LIM102 browser execution against a live Spring API was not run locally because `BANKING_LAB_E2E_API_BASE_URL` was not configured.

## 2026-06-04 KYC101 KYC Review Update

This update adds the next Phase B staff-command vertical slice from `docs/codex/implementation_missing_features_goals.md`.

Changes:

- `db/migrations/V015__customer_kyc_review_requests.sql` adds durable KYC review request state with idempotency keys.
- `POST /api/staff/customers/{customerId}/kyc-review-requests` creates `CUSTOMER_KYC_REVIEW` maker-checker approvals.
- Approval execution updates `customer_kyc_profiles.kyc_status` to `REVIEW_REQUIRED` only after checker approval.
- The executed audit payload records `realKycProviderCalled=false`, `syntheticOnly=true`, and `ledgerSourceRowsMutated=false`.
- The shared api-client and staff-terminal manifest renderer expose a conditional `KYC101` browser smoke.

Commands run:

- `npm run validate:manifests` passed with 67 manifests.
- `npm run packages:typecheck` passed.
- `npm run next:staff-terminal:typecheck` passed.
- `scripts/run-core-banking-tests.sh :services:core-banking:compileKotlin :services:core-banking:compileIntegrationTestKotlin` passed after sandbox escalation.
- `npm test` passed with 131 Node reference/oracle tests.
- `npm run test:screen-engine` passed with 10 tests.
- `npm run scripts:typecheck` passed.
- `npm run next:staff-terminal:build` passed.
- `npm run test:e2e -- apps/staff-terminal/e2e/staff-terminal-parity.spec.ts` passed locally with 5 passed and 10 skipped because `BANKING_LAB_E2E_API_BASE_URL` was not configured.
- `npm run test:e2e` passed locally with 17 passed and 37 skipped because API/Keycloak variables were not configured.
- `npm run test:core-banking:unit -- --rerun-tasks` passed after sandbox escalation.
- `docker compose config` passed.
- `docker compose --profile platform config` passed.
- `npm run evidence:refresh-check` passed.
- `git diff --check` passed.

Not proven locally on 2026-06-04:

- `npm run test:core-banking:integration -- --tests lab.banking.core.staff.StaffAccessApiParityIntegrationTest` compiled the integration sources, then failed at Testcontainers initialization because Docker provider discovery failed locally.
- API-backed KYC101 browser execution against a live Spring API was not run locally because `BANKING_LAB_E2E_API_BASE_URL` was not configured.

## Commands

```bash
npm run packages:typecheck
npm run next:staff-terminal:typecheck
npm run next:customer-web:typecheck
npm run next:staff-terminal:build
npm run next:customer-web:build
npm run test:e2e
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test :services:core-banking:integrationTest
scripts/run-core-banking-tests.sh :services:core-banking:bootJar
env COMPOSE_PROJECT_NAME=banking-lab-api-backed-smoke BANKING_LAB_POSTGRES_PORT=15433 BANKING_LAB_CORE_BANKING_PORT=18082 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres core-banking
curl -fsS http://127.0.0.1:18082/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18082 npm run test:e2e
env COMPOSE_PROJECT_NAME=banking-lab-api-backed-smoke docker compose --profile platform down -v
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test :services:core-banking:integrationTest
scripts/run-core-banking-tests.sh :services:core-banking:bootJar
env COMPOSE_PROJECT_NAME=banking-lab-api-backed-command-smoke BANKING_LAB_POSTGRES_PORT=15444 BANKING_LAB_CORE_BANKING_PORT=18084 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres core-banking
curl -fsS http://127.0.0.1:18084/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18084 npm run test:e2e
curl -fsS http://127.0.0.1:18084/health
env COMPOSE_PROJECT_NAME=banking-lab-api-backed-command-smoke docker compose --profile platform down -v
npm run next:fds-aml-console:typecheck
npm run next:fds-aml-console:build
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test :services:core-banking:integrationTest
scripts/run-core-banking-tests.sh :services:core-banking:bootJar
env COMPOSE_PROJECT_NAME=banking-lab-fds-command-smoke BANKING_LAB_POSTGRES_PORT=15445 BANKING_LAB_CORE_BANKING_PORT=18086 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres core-banking
curl -fsS http://127.0.0.1:18086/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18086 npm run test:e2e
curl -fsS http://127.0.0.1:18086/health
env COMPOSE_PROJECT_NAME=banking-lab-fds-command-smoke docker compose --profile platform down -v
npm run packages:typecheck
npm run next:fds-aml-console:typecheck
npm run next:fds-aml-console:build
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test :services:core-banking:integrationTest
scripts/run-core-banking-tests.sh :services:core-banking:bootJar
env COMPOSE_PROJECT_NAME=banking-lab-aml-command-smoke BANKING_LAB_POSTGRES_PORT=15446 BANKING_LAB_CORE_BANKING_PORT=18087 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres core-banking
curl -fsS http://127.0.0.1:18087/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18087 npm run test:e2e
curl -fsS http://127.0.0.1:18087/health
env COMPOSE_PROJECT_NAME=banking-lab-aml-command-smoke docker compose --profile platform down -v
npm run packages:typecheck
npm run next:fds-aml-console:typecheck
npm run next:fds-aml-console:build
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test :services:core-banking:integrationTest
scripts/run-core-banking-tests.sh :services:core-banking:bootJar
env COMPOSE_PROJECT_NAME=banking-lab-fds-block-command-smoke BANKING_LAB_POSTGRES_PORT=15447 BANKING_LAB_CORE_BANKING_PORT=18088 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres core-banking
curl -fsS http://127.0.0.1:18088/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18088 npm run test:e2e
curl -fsS http://127.0.0.1:18088/health
env COMPOSE_PROJECT_NAME=banking-lab-fds-block-command-smoke docker compose --profile platform down -v
npm run packages:typecheck
npm run next:ops-console:typecheck
npm run next:ops-console:build
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test :services:core-banking:integrationTest
scripts/run-core-banking-tests.sh :services:core-banking:bootJar
env COMPOSE_PROJECT_NAME=banking-lab-reconciliation-command-smoke BANKING_LAB_POSTGRES_PORT=15448 BANKING_LAB_CORE_BANKING_PORT=18089 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres core-banking
curl -fsS http://127.0.0.1:18089/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18089 npm run test:e2e
curl -fsS http://127.0.0.1:18089/health
env COMPOSE_PROJECT_NAME=banking-lab-reconciliation-command-smoke docker compose --profile platform down -v
npm run packages:typecheck
npm run next:staff-terminal:typecheck
npm run next:staff-terminal:build
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test :services:core-banking:integrationTest
scripts/run-core-banking-tests.sh :services:core-banking:bootJar
env COMPOSE_PROJECT_NAME=banking-lab-staff-change-command-smoke BANKING_LAB_POSTGRES_PORT=15449 BANKING_LAB_CORE_BANKING_PORT=18090 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres core-banking
curl -fsS http://127.0.0.1:18090/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18090 npm run test:e2e
curl -fsS http://127.0.0.1:18090/health
env COMPOSE_PROJECT_NAME=banking-lab-staff-change-command-smoke docker compose --profile platform down -v
npm run next:complaint-portal:typecheck
npm run next:complaint-portal:build
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test :services:core-banking:integrationTest
scripts/run-core-banking-tests.sh :services:core-banking:bootJar
env COMPOSE_PROJECT_NAME=banking-lab-complaint-failure-smoke BANKING_LAB_POSTGRES_PORT=15450 BANKING_LAB_CORE_BANKING_PORT=18091 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres core-banking
curl -fsS http://127.0.0.1:18091/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18091 npm run test:e2e
curl -fsS http://127.0.0.1:18091/health
env COMPOSE_PROJECT_NAME=banking-lab-complaint-failure-smoke docker compose --profile platform down -v
npm run next:fds-aml-console:typecheck
npm run next:fds-aml-console:build
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test :services:core-banking:integrationTest
scripts/run-core-banking-tests.sh :services:core-banking:bootJar
env COMPOSE_PROJECT_NAME=banking-lab-fds-failure-smoke BANKING_LAB_POSTGRES_PORT=15451 BANKING_LAB_CORE_BANKING_PORT=18092 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres core-banking
curl -fsS http://127.0.0.1:18092/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18092 npm run test:e2e
curl -fsS http://127.0.0.1:18092/health
env COMPOSE_PROJECT_NAME=banking-lab-fds-failure-smoke docker compose --profile platform down -v
npm run next:fds-aml-console:typecheck
npm run next:fds-aml-console:build
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test :services:core-banking:integrationTest
scripts/run-core-banking-tests.sh :services:core-banking:bootJar
env COMPOSE_PROJECT_NAME=banking-lab-aml-failure-smoke BANKING_LAB_POSTGRES_PORT=15452 BANKING_LAB_CORE_BANKING_PORT=18093 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres core-banking
curl -fsS http://127.0.0.1:18093/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18093 npm run test:e2e
curl -fsS http://127.0.0.1:18093/health
env COMPOSE_PROJECT_NAME=banking-lab-aml-failure-smoke docker compose --profile platform down -v
npm run next:ops-console:typecheck
npm run next:ops-console:build
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.reconciliation.ReconciliationOpsApiParityIntegrationTest
scripts/run-core-banking-tests.sh :services:core-banking:bootJar
env BANKING_LAB_POSTGRES_PORT=15453 BANKING_LAB_CORE_BANKING_PORT=18094 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose -p banking-lab-rec-failure-smoke --profile platform up -d --build postgres core-banking
curl -fsS http://127.0.0.1:18094/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18094 npm run test:e2e
curl -fsS http://127.0.0.1:18094/health
env BANKING_LAB_POSTGRES_PORT=15453 BANKING_LAB_CORE_BANKING_PORT=18094 docker compose -p banking-lab-rec-failure-smoke --profile platform down -v
npm run packages:typecheck
npm run next:customer-web:typecheck
npm run next:customer-web:build
npm run test:e2e
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.customer.CustomerTransferApiParityIntegrationTest
scripts/run-core-banking-tests.sh :services:core-banking:bootJar
env BANKING_LAB_POSTGRES_PORT=15454 BANKING_LAB_CORE_BANKING_PORT=18095 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose -p banking-lab-customer-transfer-smoke --profile platform up -d --build postgres core-banking
curl -fsS http://127.0.0.1:18095/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18095 npm run test:e2e
curl -fsS http://127.0.0.1:18095/health
env BANKING_LAB_POSTGRES_PORT=15454 BANKING_LAB_CORE_BANKING_PORT=18095 docker compose -p banking-lab-customer-transfer-smoke --profile platform down -v
npm run packages:typecheck
npm run next:customer-web:typecheck
npm run next:customer-web:build
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.customer.CustomerTransferApiParityIntegrationTest
scripts/run-core-banking-tests.sh :services:core-banking:bootJar
env BANKING_LAB_POSTGRES_PORT=15455 BANKING_LAB_CORE_BANKING_PORT=18096 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose -p banking-lab-customer-history-smoke --profile migration up -d --build postgres core-banking
curl -fsS http://127.0.0.1:18096/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18096 npm run test:e2e
env BANKING_LAB_POSTGRES_PORT=15455 BANKING_LAB_CORE_BANKING_PORT=18096 docker compose -p banking-lab-customer-history-smoke --profile migration down -v
npm run packages:typecheck
npm run next:customer-web:typecheck
npm run next:customer-web:build
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.customer.CustomerTransferApiParityIntegrationTest
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar
env BANKING_LAB_POSTGRES_PORT=15456 BANKING_LAB_CORE_BANKING_PORT=18097 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile migration -p banking-lab-customer-failed-status-smoke up --build -d postgres core-banking
curl -fsS http://127.0.0.1:18097/actuator/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18097 npm run test:e2e
env BANKING_LAB_POSTGRES_PORT=15456 BANKING_LAB_CORE_BANKING_PORT=18097 docker compose --profile migration -p banking-lab-customer-failed-status-smoke down -v
npm run packages:typecheck
npm run next:customer-web:typecheck
npm run next:customer-web:build
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.complaint.CustomerComplaintEntryApiParityIntegrationTest
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar
env BANKING_LAB_POSTGRES_PORT=15457 BANKING_LAB_CORE_BANKING_PORT=18098 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile migration -p banking-lab-customer-complaint-smoke up --build -d postgres core-banking
curl -fsS http://127.0.0.1:18098/actuator/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18098 npm run test:e2e
env BANKING_LAB_POSTGRES_PORT=15457 BANKING_LAB_CORE_BANKING_PORT=18098 docker compose --profile migration -p banking-lab-customer-complaint-smoke down -v
npm run test:e2e
npm run packages:typecheck
npm run next:customer-web:typecheck
npm run next:customer-web:build
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.complaint.CustomerComplaintConfirmApiParityIntegrationTest
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test :services:core-banking:integrationTest --tests 'lab.banking.core.complaint.*'
scripts/run-core-banking-tests.sh :services:core-banking:bootJar
env COMPOSE_PROJECT_NAME=banking-lab-complaint-confirm-smoke BANKING_LAB_POSTGRES_PORT=15458 BANKING_LAB_CORE_BANKING_PORT=18099 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres core-banking
curl -fsS http://127.0.0.1:18099/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18099 npm run test:e2e
curl -fsS http://127.0.0.1:18099/health
env COMPOSE_PROJECT_NAME=banking-lab-complaint-confirm-smoke docker compose --profile platform down -v
env COMPOSE_PROJECT_NAME=banking-lab-keycloak-status-smoke BANKING_LAB_POSTGRES_PORT=15462 BANKING_LAB_CORE_BANKING_PORT=18106 BANKING_LAB_KEYCLOAK_PORT=18107 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://127.0.0.1:18107/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking
curl -fsS http://127.0.0.1:18107/realms/banking-lab/.well-known/openid-configuration
curl -fsS http://127.0.0.1:18106/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18106 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18107 npx playwright test apps/customer-web/e2e/customer-web-parity.spec.ts -g "interactive Keycloak"
curl -fsS http://127.0.0.1:18106/health
env COMPOSE_PROJECT_NAME=banking-lab-keycloak-status-smoke docker compose --profile platform down -v
npm run packages:typecheck
npm run next:staff-terminal:typecheck
npm run next:staff-terminal:build
scripts/run-core-banking-tests.sh :services:core-banking:bootJar
env COMPOSE_PROJECT_NAME=banking-lab-staff-keycloak-smoke BANKING_LAB_POSTGRES_PORT=15463 BANKING_LAB_CORE_BANKING_PORT=18108 BANKING_LAB_KEYCLOAK_PORT=18109 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://127.0.0.1:18109/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking
curl -fsS http://127.0.0.1:18109/realms/banking-lab/.well-known/openid-configuration
curl -fsS http://127.0.0.1:18108/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18108 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18109 npx playwright test apps/staff-terminal/e2e/staff-terminal-parity.spec.ts -g "interactive Keycloak"
curl -fsS http://127.0.0.1:18108/health
env COMPOSE_PROJECT_NAME=banking-lab-staff-keycloak-smoke docker compose --profile platform down -v
npm run next:complaint-portal:typecheck
npm run next:complaint-portal:build
env COMPOSE_PROJECT_NAME=banking-lab-complaint-keycloak-smoke BANKING_LAB_POSTGRES_PORT=15464 BANKING_LAB_CORE_BANKING_PORT=18110 BANKING_LAB_KEYCLOAK_PORT=18111 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://127.0.0.1:18111/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18111/realms/banking-lab/.well-known/openid-configuration
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18110/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18110 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18111 npx playwright test apps/complaint-portal/e2e/complaint-portal-parity.spec.ts -g "interactive Keycloak"
curl -fsS http://127.0.0.1:18110/health
env COMPOSE_PROJECT_NAME=banking-lab-complaint-keycloak-smoke docker compose --profile platform down -v
npm run next:ops-console:typecheck
npm run next:ops-console:build
env COMPOSE_PROJECT_NAME=banking-lab-ops-keycloak-smoke BANKING_LAB_POSTGRES_PORT=15465 BANKING_LAB_CORE_BANKING_PORT=18112 BANKING_LAB_KEYCLOAK_PORT=18113 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://127.0.0.1:18113/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18113/realms/banking-lab/.well-known/openid-configuration
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18112/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18112 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18113 npx playwright test apps/ops-console/e2e/ops-console-parity.spec.ts -g "interactive Keycloak"
curl -fsS http://127.0.0.1:18112/health
env COMPOSE_PROJECT_NAME=banking-lab-ops-keycloak-smoke docker compose --profile platform down -v
npm run next:audit-console:typecheck
npm run next:audit-console:build
env COMPOSE_PROJECT_NAME=banking-lab-audit-keycloak-smoke BANKING_LAB_POSTGRES_PORT=15466 BANKING_LAB_CORE_BANKING_PORT=18114 BANKING_LAB_KEYCLOAK_PORT=18115 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://127.0.0.1:18115/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18115/realms/banking-lab/.well-known/openid-configuration
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18114/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18114 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18115 npx playwright test apps/audit-console/e2e/audit-console-parity.spec.ts -g "interactive Keycloak"
curl -fsS http://127.0.0.1:18114/health
env COMPOSE_PROJECT_NAME=banking-lab-audit-keycloak-smoke docker compose --profile platform down -v
npm run next:fds-aml-console:typecheck
npm run next:fds-aml-console:build
env COMPOSE_PROJECT_NAME=banking-lab-risk-keycloak-smoke BANKING_LAB_POSTGRES_PORT=15476 BANKING_LAB_CORE_BANKING_PORT=18124 BANKING_LAB_KEYCLOAK_PORT=18125 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://127.0.0.1:18125/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18125/realms/banking-lab/.well-known/openid-configuration
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18124/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18124 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18125 npx playwright test apps/fds-aml-console/e2e/fds-aml-console-parity.spec.ts -g "interactive Keycloak"
curl -fsS http://127.0.0.1:18124/health
env COMPOSE_PROJECT_NAME=banking-lab-risk-keycloak-smoke docker compose --profile platform down -v
env COMPOSE_PROJECT_NAME=banking-lab-risk-keycloak-smoke BANKING_LAB_POSTGRES_PORT=15476 BANKING_LAB_CORE_BANKING_PORT=18124 BANKING_LAB_KEYCLOAK_PORT=18125 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://127.0.0.1:18125/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres keycloak core-banking
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18125/realms/banking-lab/.well-known/openid-configuration
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18124/health
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18124 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18125 npx playwright test apps/fds-aml-console/e2e/fds-aml-console-parity.spec.ts
curl -fsS http://127.0.0.1:18124/health
env COMPOSE_PROJECT_NAME=banking-lab-risk-keycloak-smoke docker compose --profile platform down -v
npm run packages:typecheck
npm run next:staff-terminal:typecheck
npm run test:e2e -- apps/staff-terminal/e2e/staff-terminal-parity.spec.ts
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.security.SecurityAuthorizationIntegrationTest
scripts/run-core-banking-tests.sh :services:core-banking:bootJar
env COMPOSE_PROJECT_NAME=banking-lab-staff-unmask-smoke BANKING_LAB_POSTGRES_PORT=15479 BANKING_LAB_CORE_BANKING_PORT=18130 BANKING_LAB_KEYCLOAK_PORT=18131 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://localhost:18131/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build --force-recreate postgres keycloak core-banking
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18130/health
env CI=1 BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18130 npx playwright test apps/staff-terminal/e2e/staff-terminal-parity.spec.ts -g "privileged unmask"
env COMPOSE_PROJECT_NAME=banking-lab-staff-unmask-smoke BANKING_LAB_POSTGRES_PORT=15479 BANKING_LAB_CORE_BANKING_PORT=18130 BANKING_LAB_KEYCLOAK_PORT=18131 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://localhost:18131/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build --force-recreate postgres keycloak core-banking
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://localhost:18131/realms/banking-lab/.well-known/openid-configuration
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18130/health
env CI=1 BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18130 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://localhost:18131 npx playwright test apps/staff-terminal/e2e/staff-terminal-parity.spec.ts -g "interactive Keycloak"
curl -fsS http://127.0.0.1:18130/health
env COMPOSE_PROJECT_NAME=banking-lab-staff-unmask-smoke docker compose --profile platform down -v
scripts/run-core-banking-tests.sh :services:core-banking:bootJar
env COMPOSE_PROJECT_NAME=banking-lab-channel-gate-smoke BANKING_LAB_POSTGRES_PORT=15480 BANKING_LAB_CORE_BANKING_PORT=18132 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile platform up -d --build postgres core-banking
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18132/health
env CI=1 BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18132 npx playwright test apps/staff-terminal/e2e/staff-terminal-parity.spec.ts -g "privileged unmask"
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18132 npm run test:e2e
curl -fsS http://127.0.0.1:18132/health
env COMPOSE_PROJECT_NAME=banking-lab-channel-gate-smoke docker compose --profile platform down -v
```

## Result

Passed. The latest baseline Playwright suite passed 12 manifest-shell tests with 30 API/Keycloak-backed tests skipped when no API or Keycloak URL was configured. With `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18082`, Playwright passed all 18 read-model tests. With `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18084`, Playwright passed all 19 tests including complaint answer approval. With `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18086`, Playwright passed all 20 tests including FDS release approval. With `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18087`, Playwright passed all 21 tests including AML closure approval. With `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18088`, Playwright passed all 22 tests including FDS block approval. With `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18089`, Playwright passed all 23 tests including reconciliation adjustment approval. With `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18090`, Playwright passed all 24 tests including customer change approval. With `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18091`, Playwright passed all 25 tests including complaint workflow failure-state. With `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18092`, Playwright passed all 26 tests including FDS workflow failure-state. With `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18093`, Playwright passed all 27 tests including AML workflow failure-state. With `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18094`, Playwright passed all 28 tests including reconciliation workflow failure-state. With `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18095`, Playwright passed all 30 tests. With `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18096`, Playwright passed all 31 tests. With `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18097`, Playwright passed all 32 tests. With `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18098`, Playwright passed all 33 tests. With `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18099`, Playwright passed all 34 tests, including:

- customer masked account detail: `SYN-CUS-001`, `LAB-***-0001`, `100000000 KRW`;
- customer transfer retry smoke: `CWB-TRF-RETRY-...`, one `TX-...`, `POSTED`, and `same transaction id`;
- customer transfer failure smoke: `LEDGER_INSUFFICIENT_AVAILABLE_BALANCE`, domain `ledger`, status `409`, and route `/api/customer/transfers`;
- customer transfer history/held-status smoke: `CWB-HIST-...`, `TX-...`, `INTERNAL_TRANSFER`, `CUSTOMER_WEB`, `DEBIT`, `FDS-SYN-001`, `HELD`, and `15000000`;
- customer held/failed status smoke: `CWB-HELD-...`, `CWB-FAILED-...`, `HELD`, `FAILED`, `REQUEST_VALIDATION_FAILED`, and `FDS-...`;
- customer complaint entry smoke: `CMP-...`, `SYN-CUS-001`, `ACCOUNT_ACCESS`, and `RECEIVED`;
- customer complaint confirmation smoke: `CMP-SYN-CONFIRM-001`, `SYN-CUS-001`, `CLOSED`, `customerConfirmedAt`, and a `CLOSED` timeline event;
- staff masked customer detail: `SYN-CUS-001`, `010-****-1001`, `AUD-...`, and reason `API-backed channel parity smoke`.
- staff customer change command smoke: `SYN-CUS-CMD-001`, approval ID `APR-...`, self-approval error `MAKER_CHECKER_SELF_APPROVAL_REJECTED`, masked updated phone `010-****-1399`, and executed status.
- complaint case: `CMP-SYN-001`, `SYN-CUS-001`, `IN_REVIEW`;
- complaint answer command smoke: `CMP-SYN-CMD-001`, approval ID `APR-...`, and workflow status `ANSWERED`;
- complaint workflow failure-state smoke: `CMP-SYN-FAIL-001`, `WORKFLOW_STATE_VIOLATION`, domain `workflow`, status `409`, and route `/api/staff/complaints/CMP-SYN-FAIL-001/answer-drafts`;
- reconciliation item: `REC-SYN-001`, `OPEN`, `12000 KRW`;
- audit event evidence: `hashChainValid=true`, `AUD-SYN-SEED-001`, `SYNTHETIC_SEED`;
- FDS/AML cases: `FDS-SYN-001`, `AML-SYN-001`, `INVESTIGATING`.
- FDS release command smoke: `FDS-SYN-CMD-001`, approval ID `APR-...`, workflow status `RELEASED`, and ledger transaction ID `TX-...`.
- FDS block command smoke: `FDS-SYN-BLOCK-CMD-001`, approval ID `APR-...`, workflow status `BLOCKED`, and no ledger posting.
- FDS workflow failure-state smoke: `FDS-SYN-FAIL-001`, `WORKFLOW_STATE_VIOLATION`, domain `workflow`, status `409`, and route `/api/staff/fds-cases/FDS-SYN-FAIL-001/release-requests`.
- AML closure command smoke: `AML-SYN-CMD-001`, approval ID `APR-...`, workflow status `CLOSED`, and `STR_SIMULATED` disposition.
- AML workflow failure-state smoke: `AML-SYN-FAIL-001`, `WORKFLOW_STATE_VIOLATION`, domain `workflow`, status `409`, and route `/api/staff/aml-cases/AML-SYN-FAIL-001/closure-requests`.
- reconciliation adjustment command smoke: `REC-SYN-CMD-001`, approval ID `APR-...`, workflow status `ADJUSTED`, and ledger transaction ID `TX-...`.
- reconciliation workflow failure-state smoke: `REC-SYN-FAIL-001`, `WORKFLOW_STATE_VIOLATION`, domain `workflow`, status `409`, and route `/api/ops/reconciliation-items/REC-SYN-FAIL-001/adjustment-requests`.

The post-command `/health` check still returned `auditHashChainValid=true`.

The targeted live Keycloak browser smoke also passed 1 Playwright test with `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18106` and `BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18107`, proving customer-web account detail, idempotent customer transfer retry, transfer failure, transaction history, held FDS status, durable held/failed transfer status, complaint entry, and complaint confirmation can run through a live Keycloak token while the Spring simulator-token fallback is disabled. Post-smoke `/health` still returned `auditHashChainValid=true`.

The targeted staff-terminal live Keycloak browser smoke passed 1 Playwright test with `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18108` and `BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18109`, proving branch staff masked lookup plus branch-maker/manager-checker customer-change approval can run through live Keycloak tokens while the Spring simulator-token fallback is disabled. Post-smoke `/health` still returned `auditHashChainValid=true`.

The targeted staff-terminal privileged unmask browser smoke passed 1 Playwright test with `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18130`, simulator tokens enabled, and a fresh seed database. The page first rendered `AUTHORIZATION_POLICY_VIOLATION` for a branch-staff unmask attempt, then rendered `privileged unmask approved`, `manager01`, `UNMASKED_TIMEBOXED`, `010-0000-1001`, `300`, and `AUD-...` for the manager approval path. `SecurityAuthorizationIntegrationTest` now also asserts auth-filter denials include CORS headers for `http://localhost:3002`, so browser clients can read structured denial bodies instead of seeing opaque `Failed to fetch` failures.

The targeted staff-terminal live Keycloak browser smoke then passed 2 Playwright tests with `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18130` and `BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://localhost:18131`, while Spring simulator-token fallback was disabled. The staff/checker test now proves `manager01` can execute privileged unmask with a Keycloak-issued token and render `Keycloak unmask approved`, `UNMASKED_TIMEBOXED`, `010-0000-1001`, `300`, and `AUD-...` before running the customer-change approval path. The same run also re-proved the WebAuthn required-action smoke. Post-smoke `/health` still returned `auditHashChainValid=true`.

The targeted complaint-portal live Keycloak browser smoke passed 1 Playwright test with `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18110` and `BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18111`, proving complaint handler read-model access, complaint answer approval, and duplicate answer workflow-state failure rendering can run through live Keycloak tokens while the Spring simulator-token fallback is disabled. The page rendered `Keycloak complaint case loaded`, `Bearer`, `CMP-SYN-001`, `SYN-CUS-001`, `IN_REVIEW`, `Keycloak complaint checker loaded`, `manager01`, `Keycloak answer approved`, `APR-...`, `complaint01`, `manager01`, `CMP-SYN-CMD-001`, `ANSWERED`, `Keycloak workflow state rejected`, `CMP-SYN-FAIL-001`, `WORKFLOW_STATE_VIOLATION`, domain `workflow`, status `409`, and route `/api/staff/complaints/CMP-SYN-FAIL-001/answer-drafts`. Post-smoke `/health` still returned `auditHashChainValid=true`.

The targeted ops-console live Keycloak browser smoke passed 1 Playwright test with `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18112` and `BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18113`, proving reconciliation read-model access, reconciliation adjustment approval, and adjusted-item workflow-state failure rendering can run through live Keycloak tokens while the Spring simulator-token fallback is disabled. The page rendered `Keycloak reconciliation item loaded`, `Bearer`, `REC-SYN-001`, `OPEN`, `12000 KRW`, `Keycloak reconciliation checker loaded`, `manager01`, `Keycloak reconciliation adjusted`, `APR-...`, `ops01`, `manager01`, `REC-SYN-CMD-001`, `ADJUSTED`, `TX-...`, `workflow state rejected`, `REC-SYN-FAIL-001`, `WORKFLOW_STATE_VIOLATION`, domain `workflow`, status `409`, and route `/api/ops/reconciliation-items/REC-SYN-FAIL-001/adjustment-requests`. Post-smoke `/health` still returned `auditHashChainValid=true`.

The targeted audit-console live Keycloak browser smoke passed 1 Playwright test with `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18114` and `BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18115`, proving audit hash-chain read-model access can run through a live Keycloak token while the Spring simulator-token fallback is disabled. The page rendered `Keycloak audit loaded`, `Bearer`, `valid`, `AUD-SYN-SEED-001`, and `SYNTHETIC_SEED`. Post-smoke `/health` still returned `auditHashChainValid=true`.

The targeted FDS/AML-console live Keycloak browser smoke passed 1 Playwright test with `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18124` and `BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18125`, proving risk read-model access, FDS release approval with a posted ledger transaction, FDS block approval without ledger posting, AML closure with `STR_SIMULATED`, and duplicate FDS/AML workflow-state failure rendering can run through live Keycloak tokens while the Spring simulator-token fallback is disabled. The page rendered `Keycloak risk cases loaded`, `Bearer`, `FDS-SYN-001`, `AML-SYN-001`, `INVESTIGATING`, `Keycloak risk checker loaded`, `compliance01`, `Keycloak risk approvals completed`, `risk01`, `FDS-SYN-OIDC-REL-001`, `RELEASED`, `TX-...`, `FDS-SYN-OIDC-BLOCK-001`, `BLOCKED`, `not posted`, `AML-SYN-OIDC-CLOSE-001`, `CLOSED`, `STR_SIMULATED`, `workflow state rejected`, `FDS-SYN-FAIL-001`, `AML-SYN-FAIL-001`, `WORKFLOW_STATE_VIOLATION`, domain `workflow`, and status `409`. Post-smoke `/health` still returned `auditHashChainValid=true`.

The full FDS/AML-console Playwright spec also passed 9 tests with both `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18124` and `BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18125` configured while Spring simulator tokens were enabled. This proves the simulator-token browser command/failure paths and the live Keycloak reviewer/checker paths can coexist in one fresh seeded database because the Keycloak approval smoke uses OIDC-specific cases `FDS-SYN-OIDC-REL-001`, `FDS-SYN-OIDC-BLOCK-001`, and `AML-SYN-OIDC-CLOSE-001` instead of reusing the simulator command cases. Post-smoke `/health` still returned `auditHashChainValid=true`.

The targeted staff-terminal WebAuthn browser smoke passed 1 Chromium Playwright test with `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18126` and `BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://localhost:18127`, proving the synthetic `manager-webauthn01` user can complete Keycloak `webauthn-register` with a virtual authenticator and then call Spring through the staff-terminal BFF token exchange. The page rendered `Keycloak WebAuthn manager loaded`, `manager-webauthn01`, `Bearer`, `SYN-CUS-001`, `010-****-1001`, and `AUD-...`; direct grant for `manager-webauthn-block01` returned `400 invalid_grant`.

The follow-up passkey policy/recovery segregation smoke passed through `KeycloakRealmPolicyTest`, `LiveKeycloakRealmIntegrationTest`, and the same targeted WebAuthn browser smoke against a fresh stack on ports `15478`, `18128`, and `18129`. It verifies the local WebAuthn policy, `PASSKEY_RECOVERY_ADMIN` role, segregated `security-admin01` token roles, Spring JWKS validation with simulator tokens disabled, and unchanged WebAuthn required-action blocking for direct grants.

The 2026-06-03 API-backed channel gate closure run passed `scripts/run-core-banking-tests.sh :services:core-banking:bootJar`, started a fresh synthetic Compose stack on PostgreSQL `15480` and core-banking `18132`, and returned `/health` with `auditHashChainValid=true`. The targeted staff-terminal privileged unmask smoke then passed 1 Chromium Playwright test, proving branch-role denial and manager `UNMASKED_TIMEBOXED` approval after moving the unmask command onto the bounded SERIALIZABLE staff-access retry path. The full API-backed Playwright suite then passed 35 tests with 7 Keycloak-dependent tests skipped because `BANKING_LAB_E2E_KEYCLOAK_BASE_URL` was intentionally unset for this simulator-token run. Post-smoke `/health` still returned `auditHashChainValid=true`.

The targeted admin-console live API/Keycloak browser smoke passed 4 Chromium Playwright tests with `BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18090` and `BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://localhost:18091`. The run used a fresh Compose stack with PostgreSQL on `15449`, Spring core-banking on `18090`, and Keycloak on `18091`; Spring validated signed Keycloak tokens through the internal JWKS URI while the browser used the external issuer `http://localhost:18091/realms/banking-lab`. The page rendered the manifest shell, loaded the Spring admin platform summary, exchanged a `security-admin01` authorization code through the Next BFF token route, and rendered `Keycloak admin summary loaded`, `Bearer`, `SYNTHETIC_ONLY:PASS`, and `NODE_REFERENCE_BOUNDARY:BLOCKED`. An initial run failed with HTTP 404 because the Compose image used a stale `core-banking-*-migration.jar`; rebuilding `:services:core-banking:bootJar` and recreating the container fixed the evidence path.

## Remaining Gaps

- Current customer-web API-backed smoke paths, the staff-terminal masked lookup/privileged unmask/customer-change approval/WebAuthn path, the complaint-portal answer approval/workflow failure-state path, the ops-console reconciliation adjustment/workflow failure-state path, the audit-console hash-chain read-model path, and the FDS/AML risk read-model/release/block/closure/failure-state paths have live interactive Keycloak browser login evidence.
- Admin-console platform-control summary now has live interactive Keycloak browser login evidence for the synthetic `security-admin01` role set.
- WebAuthn required-action completion is proven with a local virtual authenticator, and the imported synthetic realm now has explicit local WebAuthn policy plus `PASSKEY_RECOVERY_ADMIN` role segregation evidence. Non-synthetic passkey operations, hardware attestation policy, enterprise recovery runbooks, and production deployment evidence remain out of scope for this lab slice.
- Customer transfer retry/failure, customer transfer history/held-status, customer held/failed status parity, customer complaint entry, customer complaint confirmation, staff privileged unmask, staff customer change approval, complaint answer approval, FDS release approval, FDS block approval, AML closure approval, reconciliation adjustment approval, and complaint/FDS/AML/reconciliation workflow failure-states have browser evidence; complaint answer/failure, ops reconciliation adjustment/failure, audit hash-chain read-model, and FDS/AML risk command/failure paths also have live Keycloak evidence.
- Node retirement remains blocked by non-synthetic passkey operations and final retirement review.
