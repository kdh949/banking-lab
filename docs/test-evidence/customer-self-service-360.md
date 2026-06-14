# Customer Self-Service 360 Evidence

Review date: 2026-06-14

Scope: synthetic-only customer profile, onboarding/login completion signals, customer self-service account-opening intake, Customer 360, account statement, consolidated statement, and statement artifact history for the target Kotlin/Spring and Next.js stack.

## Implemented Coverage

- `/api/customer/me` returns a masked customer profile owned by the current `CUSTOMER` token, creates synthetic onboarding checks, and audits `CUSTOMER_PROFILE_VIEW`.
- Customer login/signup responses now include onboarding status, duplicate-check status, and next required action without exposing raw PII.
- `/api/customer/account-opening-requests` accepts idempotent customer intake only. It records the request and audit event, but does not create `accounts`, `ledger_transactions`, or postings.
- `/api/customer/360` aggregates owned profile, account, ledger-activity, access-history, loan/card/complaint summaries, and explicit payment/notification source markers.
- `/api/customer/accounts/{accountId}/statement` and `/api/customer/statements/consolidated` compute read-only statement projections from ledger postings and persist `statement_artifact_snapshots`.
- `/api/customer/statements/artifacts` lists owned statement artifacts with source and payload hashes.
- Customer-web routes `/profile`, `/onboarding`, `/360`, `/accounts/[accountId]/statement`, and `/statements` render through the shared self-service shell and API client.
- Screen manifests `CWB-003`, `CWB-004`, `CWB-104`, `CWB-105`, `CWB-106`, and `CWB-107` declare self-service masking, ownership enforcement, synthetic-only boundaries, and statement snapshot metadata.

## Synthetic Boundary

- No real KYC provider, identity-provider admin API, document delivery service, payment network, or real customer data is used.
- Onboarding check evidence stores hashes/fingerprints and `raw_pii_stored = false`.
- Statement artifacts persist hashes and bounded JSON snapshots derived from synthetic ledger postings.
- Account-opening intake remains separated from staff maker-checker execution and ledger posting.

## Local Validation

- `npm run validate:manifests`: passed, 79 manifests.
- `npm run contracts:lint`: passed.
- `npm run contracts:check-client`: passed, 193 operationIds and 161 shared client methods/exemptions.
- `npm run contracts:diff-openapi`: passed, 157 core-banking controller operations matched the checked-in contract.
- `npm run contracts:runtime-evidence`: passed and regenerated `docs/test-evidence/generated/contract-runtime-evidence.json`.
- `npm run test:screen-engine`: passed, 10 tests.
- `npm run next:customer-web:typecheck`: passed.
- `npm run next:customer-web:build`: passed.
- `node --test tests/customerOnboardingSelfService.test.mjs`: passed, 10 tests.
- `node --test tests/migrationFoundation.test.mjs`: passed, 7 tests.
- `npm test`: passed, 197 tests.
- `npm run test:e2e -- apps/customer-web/e2e/customer-web-parity.spec.ts apps/customer-web/e2e/customer-self-service-360.spec.ts --project=chromium`: passed 3 tests and skipped 12 live API/Keycloak/payment/notification/customer-360 smokes because live environment variables were not configured.
- `git diff --check`: passed.

## Known Skips And Failures

- `./gradlew :services:core-banking:compileKotlin` could not be executed on this machine because the only installed JDK is JDK 26, Temurin `26.0.1`, and the current Gradle/Kotlin toolchain fails during configuration with `IllegalArgumentException: 26.0.1`.
- The added `CustomerSelfService360IntegrationTest` is checked in for PostgreSQL/Testcontainers coverage, but it must be run under the supported project JDK, preferably JDK 21, before marking this slice fully runtime-verified.
- `apps/customer-web/e2e/customer-self-service-360.spec.ts` is gated by `BANKING_LAB_E2E_API_BASE_URL`, `BANKING_LAB_E2E_STAFF_MAKER_BEARER_TOKEN`, and `BANKING_LAB_E2E_STAFF_CHECKER_BEARER_TOKEN`.

## Evidence Files

- `SPEC.md`
- `PLAN.md`
- `BANKING_LAB_CODEX_PROMPT.md`
- `contracts/openapi/core-banking.yaml`
- `packages/api-client/src/index.ts`
- `db/migrations/V042__customer_self_service_360.sql`
- `services/core-banking/src/main/kotlin/lab/banking/core/customer/CustomerSelfServiceController.kt`
- `services/core-banking/src/main/kotlin/lab/banking/core/customer/CustomerSelfServiceService.kt`
- `services/core-banking/src/integrationTest/kotlin/lab/banking/core/customer/CustomerSelfService360IntegrationTest.kt`
- `apps/customer-web/e2e/customer-self-service-360.spec.ts`
