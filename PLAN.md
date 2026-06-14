# Customer Self-Service And Customer 360 Implementation Plan

작성일: 2026-06-14

## 1. Objective

`SPEC.md`의 Customer self-service onboarding/login, Customer 360, account views, statements 기능을 target stack으로 구현한다.

Target stack:

- Kotlin/Spring Boot core-banking service
- PostgreSQL/Flyway
- OpenAPI + TypeScript API client
- Next.js customer-web
- screen-manifests/customer-web
- JUnit/Testcontainers, Node structural tests, Playwright

절대 Node.js MVP를 target implementation으로 확장하지 않는다. Node/runtime은 기존 regression/reference 범위로만 둔다.

## 2. Priority Rules

우선순위는 다음 순서다.

1. Ledger/read-model 안전성: statement와 certificate는 ledger row를 변경하지 않는다.
2. Ownership enforcement: customer route는 token-owned `customerId`만 사용한다.
3. Masking/audit: customer PII와 account number는 masked-by-default, read audit은 append-only.
4. Maker-checker: 고객 self-service account-opening request는 intake만 만들고 실제 계좌 생성은 staff maker-checker execution으로 유지한다.
5. Contract stability: OpenAPI, API client, manifests, frontend route를 함께 맞춘다.
6. Evidence: 실행하지 않은 테스트를 통과로 기록하지 않는다.

## 3. Milestone 0 - Baseline And Guardrails

Priority: P0

Purpose:

- 현재 target implementation 상태를 고정하고 구현 중 회귀를 막는다.

Tasks:

- `SPEC.md`, `AGENTS.md`, `BANKING_LAB_CODEX_PROMPT.md`, `docs/migration/structured-api-error-contract.md`, `docs/architecture/phase-4-customer-web.md`를 읽는다.
- 현재 customer/auth/account/statement 구현을 확인한다.
- 새 Flyway migration 번호는 `V042__customer_self_service_360.sql`부터 사용한다.
- 기존 `CWB-001`, `CWB-002`, `CWB-101`, `CWB-102`, `CWB-103`, `CWB-401` manifest를 기준선으로 삼는다.

Validation:

- `git status --short`
- `npm run validate:manifests`
- `npm run contracts:lint`

Exit criteria:

- 구현 전 baseline commands와 현재 미추적/수정 파일이 기록되어 있다.
- 새 작업이 건드릴 파일 범위가 확정되어 있다.

## 4. Milestone 1 - Backend Schema And Domain Models

Priority: P0

Purpose:

- self-service onboarding checks, account-opening intake, statement artifact snapshot을 PostgreSQL source of truth로 추가한다.

Files:

- `db/migrations/V042__customer_self_service_360.sql`
- `services/core-banking/src/main/kotlin/lab/banking/core/customer/*`
- `services/core-banking/src/main/kotlin/lab/banking/core/statement/*`

Tasks:

- `customer_onboarding_checks` 테이블 추가.
- `customer_self_service_account_opening_requests` 테이블 추가.
- `statement_artifact_snapshots` 테이블 추가.
- 필요한 indexes 추가:
  - customer onboarding checks: `(customer_id, check_type, created_at desc)`
  - account opening requests: `(customer_id, status, created_at desc)`
  - statement snapshots: `(customer_id, from_date, to_date)`, `(account_id, from_date, to_date)`
- DTO 추가:
  - `CustomerProfileDto`
  - `CustomerOnboardingCheckDto`
  - `CustomerSelfServiceAccountOpeningCommand`
  - `CustomerSelfServiceAccountOpeningRequestDto`
  - `Customer360Dto`
  - `Customer360AccountDto`
  - `CustomerStatementArtifactDto`

Validation:

- `npm run test:core-banking:integration -- --tests '*Customer*' --tests '*Statement*'`

Exit criteria:

- Flyway migration이 Testcontainers PostgreSQL에서 적용된다.
- 새 테이블은 `synthetic_only=true` check를 가진다.
- schema만으로 실제 account/ledger row를 만들지 않는다.

## 5. Milestone 2 - Customer Profile And Onboarding Checks

Priority: P0

Purpose:

- signup/login 이후 고객이 자신의 프로필, KYC, 중복검증, 다음 조치를 조회할 수 있게 한다.

Files:

- `CustomerAuthService.kt`
- `CustomerAccountService.kt` 또는 새 `CustomerProfileService.kt`
- `CustomerAccountController.kt` 또는 새 `CustomerProfileController.kt`
- `CustomerAuthIntegrationTest.kt`

Tasks:

- `POST /api/auth/customer/signup` 응답에 `onboardingStatus`, `duplicateCheckStatus`, `nextRequiredAction`을 additive 추가.
- signup 시 synthetic onboarding checks 생성:
  - `DUPLICATE_IDENTITY`
  - `KYC_SIMULATION`
  - `TERMS_ACCEPTANCE`
  - `CONTACT_REACHABILITY`
- Duplicate check는 normalized synthetic profile fingerprint를 사용한다.
- `GET /api/customer/me` 추가.
- `GET /api/customer/me`는 query `customerId`를 받지 않고 token claim만 사용한다.
- 고객 토큰 없음, role mismatch, inactive auth identity를 structured error로 처리한다.
- Audit event:
  - `CUSTOMER_PROFILE_VIEW`
  - payload에는 result status/count/fingerprint만 저장하고 raw PII는 저장하지 않는다.

Validation:

- `npm run test:core-banking:integration -- --tests lab.banking.core.auth.CustomerAuthIntegrationTest`
- `npm run test:core-banking:integration -- --tests '*CustomerProfile*'`

Exit criteria:

- self-signup replay는 기존 customer와 checks를 재사용한다.
- raw password, raw phone, raw address가 audit/metadata에 없다.
- profile read는 cross-customer access를 허용하지 않는다.

## 6. Milestone 3 - Customer Self-Service Account Opening Intake

Priority: P0

Purpose:

- 고객이 계좌개설을 요청하고 상태를 볼 수 있지만, 실제 계좌 생성과 ledger posting은 staff maker-checker 이후에만 일어나게 한다.

Files:

- `services/core-banking/src/main/kotlin/lab/banking/core/account/*`
- `services/core-banking/src/main/kotlin/lab/banking/core/customer/*`
- `services/core-banking/src/integrationTest/kotlin/lab/banking/core/account/*`

Tasks:

- `POST /api/customer/account-opening-requests` 추가.
- `GET /api/customer/account-opening-requests` 추가.
- Command hash와 idempotency replay를 구현한다.
- 중복 pending request 정책:
  - 같은 customer, productCode, currency에 `CUSTOMER_SUBMITTED` 또는 `STAFF_REVIEWING`이 있으면 `CUSTOMER_ACCOUNT_OPENING_ALREADY_PENDING`.
- Staff maker가 intake를 기존 `/api/staff/accounts/opening-requests`로 전환할 수 있도록 linkage 필드를 둔다.
- 고객 route는 account row나 ledger transaction을 만들지 않는다.
- Audit event:
  - `CUSTOMER_ACCOUNT_OPENING_REQUESTED`
  - `CUSTOMER_ACCOUNT_OPENING_STATUS_VIEW`

Validation:

- `npm run test:core-banking:integration -- --tests '*AccountOpening*' --tests '*CustomerSelfService*'`

Exit criteria:

- 고객 request 직후 `accounts`, `ledger_transactions`, `ledger_postings` row count가 증가하지 않는다.
- staff 승인/실행 전까지 generated account fields는 null이다.
- idempotency conflict는 `IDEMPOTENCY_KEY_CONFLICT`를 반환한다.

## 7. Milestone 4 - Customer 360 Read Model

Priority: P0

Purpose:

- RFP의 360-degree customer information 요구를 synthetic lab 범위에서 canonical read model로 제공한다.

Files:

- `services/core-banking/src/main/kotlin/lab/banking/core/customer/Customer360*`
- `services/core-banking/src/integrationTest/kotlin/lab/banking/core/customer/Customer360IntegrationTest.kt`

Tasks:

- `GET /api/customer/360` 추가.
- Token-owned customer only로 동작한다.
- Sections:
  - profile
  - kycSummary
  - accountSummary
  - accounts
  - loanSummary
  - cardSummary
  - complaintSummary
  - paymentSummary
  - notificationSummary
  - recentLedgerActivity
  - accessHistorySummary
  - availableActions
  - sourceWatermarks
- Existing tables에서 join/read한다. v1에서는 materialized projection table을 만들지 않는다.
- 최근 활동은 최대 10건으로 제한한다.
- raw account number 대신 masked account number만 반환한다.
- Audit event `CUSTOMER_360_VIEW` 추가.

Validation:

- `npm run test:core-banking:integration -- --tests lab.banking.core.customer.Customer360IntegrationTest`

Exit criteria:

- cross-customer account, complaint, loan, card, notification이 섞이지 않는다.
- response와 audit payload에 raw account number/phone/address가 없다.
- ledger balance는 projection에서 읽고 직접 계산 source-of-truth로 저장하지 않는다.

## 8. Milestone 5 - Statement Artifact Hardening

Priority: P0

Purpose:

- 고객 통합 statement와 계좌별 statement를 deterministic artifact로 제공한다.

Files:

- `StatementService.kt`
- `StatementController.kt`
- `StatementModels.kt`
- `StatementReadModelIntegrationTest.kt`

Tasks:

- 기존 `/api/customers/{customerId}/statements` 유지.
- `GET /api/customer/statements/consolidated?from&to` 추가.
- `GET /api/customer/accounts/{accountId}/statement?from&to` 추가.
- `statementId`, `statementScope`, `sourceLedgerHash`, `payloadHash`, `generatedAt`, `maskingPolicy` 추가.
- 동일 customer/date/source hash 요청은 동일 `statementId`를 반환한다.
- Snapshot은 `statement_artifact_snapshots`에 저장한다.
- Reads are read-only for ledger:
  - no insert/update/delete on `ledger_transactions`
  - no insert/update/delete on `ledger_postings`
  - no balance projection mutation

Validation:

- `npm run test:core-banking:integration -- --tests lab.banking.core.statement.StatementReadModelIntegrationTest`

Exit criteria:

- debit/credit totals와 opening/closing balance가 ledger postings 합계와 일치한다.
- account statement는 해당 account만 포함한다.
- consolidated statement는 customer-owned accounts만 포함한다.
- staff read는 기존대로 reason-required다.

## 9. Milestone 6 - OpenAPI, API Client, Structured Errors

Priority: P1

Purpose:

- backend route와 frontend client contract를 안정화한다.

Files:

- `contracts/openapi/core-banking.yaml`
- `packages/api-client/src/index.ts`
- `docs/migration/structured-api-error-contract.md`
- `tests/openApiGeneratedDiff.test.mjs`
- `tests/apiErrorContract.test.mjs`

Tasks:

- 새 endpoints와 DTO schema를 OpenAPI에 추가.
- API client methods 추가:
  - `customerProfile`
  - `requestCustomerAccountOpening`
  - `customerAccountOpeningRequests`
  - `customer360`
  - `customerConsolidatedStatement`
  - `customerAccountStatement`
- Structured error codes 추가:
  - `CUSTOMER_ONBOARDING_CHECK_FAILED`
  - `CUSTOMER_ACCOUNT_OPENING_ALREADY_PENDING`
  - `CUSTOMER_AUTH_IDENTITY_NOT_ACTIVE`
- `BankingLabAuthorizationFilter` route-to-screen mapping 추가:
  - `/api/customer/me` -> `CWB-003`
  - `/api/customer/account-opening-requests` -> `CWB-004`
  - `/api/customer/360` -> `CWB-104`
  - customer statement routes -> `CWB-105`/`CWB-106`

Validation:

- `npm run contracts:lint`
- `npm run contracts:check-client`
- `npm run contracts:diff-openapi`
- `npm test -- tests/apiErrorContract.test.mjs tests/openApiGeneratedDiff.test.mjs`

Exit criteria:

- API client compiles.
- New errors have contract tests.
- Generated OpenAPI diff gate passes or records intentional checked-in contract update.

## 10. Milestone 7 - Customer-Web Manifests And Screen Validation

Priority: P1

Purpose:

- customer-web 화면 정의가 screen platform 규범을 따른다.

Files:

- `screen-manifests/customer-web/*.json`
- `packages/screen-engine/src/types.ts`
- `packages/screen-engine/test/manifest-parity.test.ts`
- `tests/manifest.test.mjs`

Tasks:

- Add manifests:
  - `CWB-003.customer-profile.json`
  - `CWB-004.self-service-account-opening-request.json`
  - `CWB-104.customer-360.json`
  - `CWB-105.account-statement.json`
  - `CWB-106.consolidated-statement.json`
  - `CWB-107.statement-artifact-history.json`
- Update:
  - `CWB-102.account-detail.json`
  - `CWB-103.transaction-history.json`
  - `CWB-401.security-access-history.json`
- Required metadata:
  - `audit.selfService=true`
  - `audit.eventTypes`
  - `audit.maskingPolicy=CUSTOMER_SELF`
  - `api.clientMethod`
  - `api.ownershipEnforced=true`
  - `api.syntheticOnly=true`
- Add validator/test coverage for customer-web self-service metadata.

Validation:

- `npm run validate:manifests`
- `npm run test:screen-engine`
- `npm test -- tests/manifest.test.mjs tests/manifestExpansion.test.mjs`

Exit criteria:

- All new screens validate.
- Inquiry screens have masking/audit/ownership metadata.
- Command screen `CWB-004` has idempotency policy.

## 11. Milestone 8 - Customer-Web Routes And UX

Priority: P1

Purpose:

- Evidence/demo panel이 아니라 실제 customer route에서 기능을 사용한다.

Files:

- `apps/customer-web/src/app/profile/page.tsx`
- `apps/customer-web/src/app/onboarding/page.tsx`
- `apps/customer-web/src/app/360/page.tsx`
- `apps/customer-web/src/app/accounts/[accountId]/statement/page.tsx`
- `apps/customer-web/src/app/statements/page.tsx`
- `apps/customer-web/src/components/CustomerSelfService.tsx`
- `apps/customer-web/src/components/workflow-routes.tsx`

Tasks:

- Stored session을 재사용하되 expired/missing session state를 명확히 표시한다.
- `/profile`은 `customerProfile`을 호출한다.
- `/onboarding`은 onboarding checks와 account-opening requests를 표시하고 새 request form을 제공한다.
- `/360`은 `customer360`을 호출하고 summary cards + tables로 표시한다.
- `/accounts/[accountId]/statement`는 계좌별 statement date filter를 제공한다.
- `/statements`는 consolidated statement date filter를 제공한다.
- `ApiBackedCustomerPanel`은 smoke/evidence 영역으로 유지하되 기능의 유일한 UI가 되지 않게 한다.
- 화면 텍스트는 synthetic-only boundary를 명확히 하되, 사용법 설명문을 과도하게 넣지 않는다.

Validation:

- `npm run next:customer-web:typecheck`
- `npm run next:customer-web:build`
- `npm run test:e2e -- apps/customer-web/e2e/customer-web-parity.spec.ts --project=chromium`

Exit criteria:

- 새 route가 build에 포함된다.
- session 없음, API 미설정, auth denied, loaded 상태가 모두 UI에 표시된다.
- raw PII/account number가 표시되지 않는다.

## 12. Milestone 9 - End-To-End Evidence

Priority: P1

Purpose:

- 실제 구현 완성도를 evidence로 남긴다.

Files:

- `apps/customer-web/e2e/customer-self-service-360.spec.ts`
- `docs/test-evidence/customer-self-service-360-hardening.md`
- `docs/implementation-coverage-matrix.md`

Tasks:

- Playwright live synthetic API smoke:
  - signup
  - login
  - profile
  - account-opening request intake
  - staff approval/execute via API setup
  - 360 shows generated account
  - account statement shows posted transaction
  - consolidated statement totals reconcile
- Negative smoke:
  - missing session
  - wrong customer token
  - duplicate pending account-opening request
- Evidence doc records commands, pass/fail/skip reasons, invariants, security/control impact.

Validation:

- `npm run test:e2e -- apps/customer-web/e2e/customer-self-service-360.spec.ts --project=chromium`
- `npm test -- tests/customerOnboardingSelfService.test.mjs tests/customerWeb.test.mjs`

Exit criteria:

- Live API smoke passes when environment variables are configured.
- Without live API env, tests skip with explicit reason.
- Evidence does not claim unrun tests passed.

## 13. Milestone 10 - Final Regression

Priority: P2

Purpose:

- 이 기능이 기존 banking lab target-stack 범위를 깨지 않았음을 확인한다.

Commands:

- `npm run validate:manifests`
- `npm run contracts:lint`
- `npm run contracts:check-client`
- `npm run contracts:diff-openapi`
- `npm run test:core-banking:integration -- --tests '*Customer*' --tests '*AccountOpening*' --tests '*Statement*'`
- `npm run next:customer-web:typecheck`
- `npm run next:customer-web:build`
- `npm test -- tests/customerOnboardingSelfService.test.mjs tests/customerWeb.test.mjs tests/manifest.test.mjs tests/apiErrorContract.test.mjs`

Exit criteria:

- All P0 and P1 milestones pass.
- Failing/skipped tests are documented with reason.
- No direct balance mutation or unbalanced posting is introduced.
- No real PII, real KYC, real money, or real provider integration is introduced.

## 14. Implementation Boundaries

Allowed target areas:

- `db/migrations/V042__*.sql`
- `services/core-banking/src/main/kotlin/lab/banking/core/customer/**`
- `services/core-banking/src/main/kotlin/lab/banking/core/auth/**`
- `services/core-banking/src/main/kotlin/lab/banking/core/account/**`
- `services/core-banking/src/main/kotlin/lab/banking/core/statement/**`
- `contracts/openapi/core-banking.yaml`
- `packages/api-client/src/index.ts`
- `screen-manifests/customer-web/**`
- `apps/customer-web/src/**`
- targeted tests and evidence docs

Do not edit unless required by compile/test:

- payment-service
- notification-service
- reporting-service
- staff-terminal official iWorks terminal
- runtime synthetic reference
- unrelated generated AML evidence

## 15. Done Definition

The project is complete only when:

- `SPEC.md` acceptance criteria are implemented.
- Customer-owned routes use token `customerId`, not user-supplied arbitrary customer IDs.
- Customer 360 and statements are backed by Spring/PostgreSQL, not frontend-only mock data.
- Account-opening self-service request cannot bypass staff maker-checker execution.
- Statement reads are ledger-read-only and deterministic.
- Manifests, OpenAPI, API client, frontend, backend, and evidence agree.
- Required tests are run or explicitly skipped with reason.
