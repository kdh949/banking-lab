# Codex Goal Plan — Synthetic Customer/Account Onboarding And Customer Web Self-Service

## 0. 목적

현재 `banking-lab` main 브랜치는 synthetic banking lab로서 원장, 직원 조회/운영, 고객 계좌 상세 조회, 고객 내부이체, Keycloak/OIDC smoke, maker-checker, audit, security, contracts, evidence가 상당히 구현되어 있다.

하지만 다음 사용자 흐름은 아직 부족하다.

1. 직원용 화면에서 사용자가 직접 가상 고객을 생성하는 흐름
2. 직원용 화면에서 사용자가 직접 가상 계좌를 개설하는 흐름
3. 고객용 웹페이지에서 회원가입하는 흐름
4. 고객용 웹페이지에서 로그인 후 자기 계좌 목록을 조회하는 흐름
5. 고객용 웹페이지에서 실제 form으로 내부 계좌 간 이체하는 흐름

이번 작업의 목표는 실제 외부 금융망이나 실고객 데이터를 붙이는 것이 아니라, 기존 synthetic boundary 안에서 **직원이 가상 고객/계좌를 만들고, 고객이 가입/로그인/계좌조회/내부이체를 직접 체험할 수 있는 end-to-end demo banking flow**를 완성하는 것이다.

---

## 1. 반드시 유지할 경계

절대 추가하지 말 것:

- 실제 고객 자금
- 실제 개인정보
- 실제 금융결제망
- 실제 카드망
- 실제 오픈뱅킹
- 실제 KYC/신용평가 API
- 실제 Keycloak Admin API를 통한 운영 계정 자동 생성
- 운영용 비밀번호/토큰/secret commit

이번 구현은 전부 synthetic-only로 유지한다.

권장 표현:

> 실제 금융망과 실고객 데이터를 사용하지 않는 synthetic lab 환경에서, 고객/계좌 온보딩과 고객 웹 self-service banking flow를 구현·검증했다.

---

## 2. 현재 코드 기준 요약

현재 확인된 상태:

### 이미 있는 것

- 직원용 고객 검색/상세 조회
- 직원용 계좌 검색/거래 검색
- 고객정보 변경 요청
- 계좌 지급정지/해제 요청
- 한도 변경 요청
- KYC review 요청
- 수수료 면제 요청
- 거래 정정 요청
- 고객용 단일 계좌 상세 조회
- 고객용 거래내역 조회
- 고객용 내부 계좌 이체
- Keycloak/OIDC login smoke
- `/api/auth/session`
- synthetic seed 고객/계좌

### 부족한 것

- 직원이 신규 가상 고객을 직접 생성하는 API/UI
- 직원이 신규 가상 계좌를 직접 개설하는 API/UI
- 고객 self-signup API/UI
- 고객 synthetic login API/UI
- 고객별 계좌 목록 API
- 고객 웹 실제 입력 form 기반 계좌조회/내부이체
- signup → login → account list → transfer → history까지 이어지는 Playwright/API e2e

---

## 3. 전체 구현 방향

이번 구현은 다음 5개 vertical slice로 진행한다.

| Phase | 목표 |
| --- | --- |
| Phase 0 | 현재 상태 재확인과 baseline 문서화 |
| Phase 1 | 직원용 synthetic customer onboarding |
| Phase 2 | 직원용 account opening |
| Phase 3 | 고객 self-signup / synthetic login |
| Phase 4 | 고객 계좌 목록/상세/내부이체 실제 form UI |
| Phase 5 | contracts, tests, evidence, CI 연결 |

---

## 4. 공통 설계 원칙

### 4.1 모든 신규 command 공통 정책

모든 신규 command는 다음을 지킨다.

1. `idempotencyKey`를 가진다.
2. command hash를 저장하고, 같은 idempotency key + 다른 payload는 conflict 처리한다.
3. reason-required 여부를 명확히 한다.
4. staff high-risk command는 maker-checker를 사용한다.
5. audit event를 남긴다.
6. structured error를 사용한다.
7. `syntheticOnly=true`를 metadata와 응답에 포함한다.
8. 실제 외부 API를 호출하지 않는다.

### 4.2 staff command 정책

직원 업무는 기본적으로 다음 형태를 따른다.

```text
request
→ approval submit
→ independent checker approve/reject
→ execute
→ durable state mutation
→ audit/event/evidence
```

단, 단순 조회는 reason-required audit만 적용한다.

### 4.3 customer self-service 정책

고객 self-service는 lab UX를 위해 다음을 허용한다.

- `/api/auth/customer/signup`은 unauthenticated route로 허용
- `/api/auth/customer/login`은 unauthenticated route로 허용
- login은 dev/test synthetic mode에서만 `lab.<claims>.sig` simulator bearer token을 발급
- prod-like profile에서는 synthetic login/token issue가 fail-fast 또는 403이어야 함
- password는 synthetic이어도 plaintext 저장 금지
- BCrypt 또는 Spring Security `PasswordEncoder` 사용
- customerId, accountId, accountNo는 모두 synthetic prefix 사용

---

## 5. Phase 0 — Baseline 확인

### 작업

다음 파일을 먼저 읽고 현재 상태를 요약한다.

```text
services/core-banking/src/main/kotlin/lab/banking/core/customer/**
services/core-banking/src/main/kotlin/lab/banking/core/staff/**
services/core-banking/src/main/kotlin/lab/banking/core/security/**
services/core-banking/src/main/kotlin/lab/banking/core/synthetic/SyntheticDataSeeder.kt
apps/customer-web/src/**
apps/staff-terminal/src/**
packages/api-client/src/index.ts
contracts/openapi/core-banking.yaml
screen-manifests/**
docs/implementation-coverage-matrix.md
```

새 문서를 만든다.

```text
docs/codex/customer-onboarding-self-service-status.md
```

필수 섹션:

```text
# Customer Onboarding And Self-Service Status

## Current Baseline
## Existing APIs
## Missing APIs
## Existing UI
## Missing UI
## Proposed Phases
## Commands Attempted
## Commands Not Attempted
```

### 테스트

```bash
npm test
npm run validate:manifests
npm run packages:typecheck
npm run scripts:typecheck
npm run test:core-banking:unit
npm run test:core-banking:integration
```

### 통과 조건

- 현재 기능을 과장하지 않는다.
- customer/account 생성이 없다는 사실을 명확히 기록한다.
- signup/login/account-list/form-transfer 갭을 명확히 기록한다.

---

## 6. Phase 1 — 직원용 Synthetic Customer Onboarding

## 6.1 목표

직원이 staff-terminal에서 신규 synthetic 고객 생성을 요청하고, 독립 checker가 승인한 뒤, 고객 row와 KYC profile, auth binding을 생성할 수 있게 한다.

## 6.2 DB migration

새 Flyway migration을 추가한다.

권장 파일명:

```text
db/migrations/V037__synthetic_customer_onboarding.sql
```

예상 테이블:

```sql
CREATE TABLE customer_onboarding_requests (
  request_id TEXT PRIMARY KEY,
  idempotency_key TEXT NOT NULL UNIQUE,
  command_hash TEXT NOT NULL,

  status TEXT NOT NULL CHECK (status IN (
    'PENDING_APPROVAL',
    'APPROVED',
    'REJECTED',
    'EXECUTED',
    'FAILED'
  )),

  requested_by TEXT NOT NULL,
  requested_by_role TEXT NOT NULL,
  reason TEXT NOT NULL,

  approval_id TEXT NOT NULL REFERENCES operator_approvals(approval_id),

  requested_customer_name TEXT NOT NULL,
  requested_customer_phone TEXT NOT NULL,
  requested_customer_address TEXT NOT NULL,
  requested_customer_grade TEXT NOT NULL,
  requested_risk_grade TEXT NOT NULL,
  requested_source_of_funds_code TEXT NOT NULL,
  requested_transaction_purpose_code TEXT NOT NULL,

  generated_customer_id TEXT,
  generated_auth_subject TEXT,

  approved_by TEXT,
  approved_at TIMESTAMPTZ,
  rejected_by TEXT,
  rejected_at TIMESTAMPTZ,
  reject_reason TEXT,

  executed_by TEXT,
  executed_at TIMESTAMPTZ,

  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

인증 binding 테이블도 추가한다.

```sql
CREATE TABLE customer_auth_identities (
  auth_subject TEXT PRIMARY KEY,
  customer_id TEXT NOT NULL UNIQUE REFERENCES customers(customer_id),
  username TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED')),
  failed_login_count INTEGER NOT NULL DEFAULT 0 CHECK (failed_login_count >= 0),
  last_login_at TIMESTAMPTZ,
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

주의:

- 고객 생성 시 `customers`, `customer_kyc_profiles`, `customer_auth_identities`를 함께 생성한다.
- customerId는 deterministic하게 만들거나 sequence 기반으로 만든다.
- 예: `CUS-SYN-YYYYMMDD-000001` 또는 `SYN-CUS-NEW-000001`
- 실제 주민번호, 실제 이메일, 실제 전화번호 검증은 하지 않는다.

## 6.3 API

Staff API를 추가한다.

```text
POST /api/staff/customers/onboarding-requests
GET  /api/staff/customers/onboarding-requests/{requestId}
POST /api/staff/customers/onboarding-requests/{requestId}/approve
POST /api/staff/customers/onboarding-requests/{requestId}/reject
POST /api/staff/customers/onboarding-requests/{requestId}/execute
```

요청 DTO 예시:

```kotlin
data class CustomerOnboardingRequestCommand(
    val requestedBy: String,
    val requestedByRole: String?,
    val reason: String,
    val idempotencyKey: String,
    val customerName: String,
    val customerPhone: String,
    val customerAddress: String,
    val customerGrade: String = "STANDARD",
    val riskGrade: String = "LOW",
    val sourceOfFundsCode: String = "SALARY",
    val transactionPurposeCode: String = "DAILY_BANKING",
    val username: String,
    val temporaryPassword: String
)
```

승인/반려/실행 DTO도 별도 작성한다.

## 6.4 서비스

새 서비스 권장:

```text
services/core-banking/src/main/kotlin/lab/banking/core/onboarding/CustomerOnboardingService.kt
services/core-banking/src/main/kotlin/lab/banking/core/onboarding/CustomerOnboardingController.kt
```

권장 비즈니스 타입:

```kotlin
ApprovalBusinessTypes.CUSTOMER_ONBOARDING
```

없으면 추가한다.

실행 시 생성할 row:

```text
customers
customer_kyc_profiles
customer_auth_identities
audit_events
workflow_events, if existing workflow helper is available
```

## 6.5 권한

Route policy:

```text
request: BRANCH_STAFF, BRANCH_MANAGER, COMPLIANCE_MANAGER
approve/reject: BRANCH_MANAGER, COMPLIANCE_MANAGER
execute: BRANCH_STAFF, BRANCH_MANAGER, OPS_MANAGER
```

요구사항:

- maker self-approval 거부
- reason missing 거부
- duplicate username 거부
- weak synthetic password 거부
- rejected request execute 거부
- already executed replay 처리 또는 state violation 처리 명확화

## 6.6 UI

staff-terminal에 route 또는 panel을 추가한다.

권장 route:

```text
apps/staff-terminal/src/app/customers/new/page.tsx
apps/staff-terminal/src/app/customers/onboarding-requests/[requestId]/page.tsx
```

기존 manifest shell을 쓴다면 screen manifest도 추가한다.

```text
screen-manifests/staff-terminal/CST-201-customer-onboarding.json
screen-manifests/staff-terminal/CST-202-customer-onboarding-approval.json
```

UI 상태:

```text
DRAFT
PENDING_APPROVAL
SELF_APPROVAL_REJECTED
APPROVED
REJECTED
EXECUTED
DUPLICATE_USERNAME
VALIDATION_FAILED
AUTHORIZATION_DENIED
UNEXPECTED_FAILURE
```

## 6.7 테스트

Integration test:

```text
CustomerOnboardingIntegrationTest
CustomerOnboardingAuthorizationIntegrationTest
```

검증 항목:

1. 정상 request 생성
2. reason 누락 거부
3. weak password 거부
4. duplicate username 거부
5. maker self-approval 거부
6. checker approve 성공
7. rejected request execute 거부
8. approved request execute 성공
9. customers row 생성
10. customer_kyc_profiles row 생성
11. customer_auth_identities row 생성
12. password_hash가 plaintext와 다름
13. idempotency replay
14. command hash conflict
15. audit event 생성

---

## 7. Phase 2 — 직원용 Account Opening

## 7.1 목표

기존 synthetic 고객에게 직원이 신규 계좌 개설을 요청하고, 승인 후 `accounts`, `account_limits`, `account_balance_projections`, 선택적 초기 입금 posting을 생성할 수 있게 한다.

## 7.2 DB migration

권장 파일:

```text
db/migrations/V038__synthetic_account_opening.sql
```

예상 테이블:

```sql
CREATE TABLE account_opening_requests (
  request_id TEXT PRIMARY KEY,
  idempotency_key TEXT NOT NULL UNIQUE,
  command_hash TEXT NOT NULL,

  status TEXT NOT NULL CHECK (status IN (
    'PENDING_APPROVAL',
    'APPROVED',
    'REJECTED',
    'EXECUTED',
    'FAILED'
  )),

  customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  product_id TEXT,
  currency CHAR(3) NOT NULL DEFAULT 'KRW',

  requested_daily_transfer_limit_minor BIGINT NOT NULL CHECK (requested_daily_transfer_limit_minor >= 0),
  requested_single_transfer_limit_minor BIGINT NOT NULL CHECK (requested_single_transfer_limit_minor >= 0),
  requested_monthly_transfer_limit_minor BIGINT NOT NULL DEFAULT 0 CHECK (requested_monthly_transfer_limit_minor >= 0),

  initial_deposit_minor BIGINT NOT NULL DEFAULT 0 CHECK (initial_deposit_minor >= 0),
  initial_deposit_transaction_id TEXT REFERENCES ledger_transactions(ledger_transaction_id),

  generated_account_id TEXT,
  generated_account_no TEXT UNIQUE,

  requested_by TEXT NOT NULL,
  requested_by_role TEXT NOT NULL,
  reason TEXT NOT NULL,
  approval_id TEXT NOT NULL REFERENCES operator_approvals(approval_id),

  approved_by TEXT,
  approved_at TIMESTAMPTZ,
  rejected_by TEXT,
  rejected_at TIMESTAMPTZ,
  reject_reason TEXT,

  executed_by TEXT,
  executed_at TIMESTAMPTZ,

  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

계좌번호 생성을 위한 sequence나 generator table을 추가한다.

```sql
CREATE SEQUENCE synthetic_account_no_seq START 1000001;
```

## 7.3 API

```text
POST /api/staff/customers/{customerId}/account-opening-requests
GET  /api/staff/account-opening-requests/{requestId}
POST /api/staff/account-opening-requests/{requestId}/approve
POST /api/staff/account-opening-requests/{requestId}/reject
POST /api/staff/account-opening-requests/{requestId}/execute
```

## 7.4 실행 정책

승인 후 execute 시:

1. `accounts` 생성
2. `account_limits` 생성
3. `account_balance_projections` 생성
4. `initialDepositMinor > 0`이면 `LedgerCommandService.deposit(...)`로 balanced ledger posting 생성
5. `account_product_enrollments`가 이미 존재하면 product enrollment 생성
6. audit event 생성

주의:

- 계좌 생성은 승인 전에는 절대 발생하면 안 된다.
- initial deposit도 승인 후 execute에서만 발생해야 한다.
- ledger source row는 절대 직접 insert하지 말고 가능하면 `LedgerCommandService.deposit`을 사용한다.
- idempotency replay 시 중복 계좌/중복 입금이 생기면 안 된다.

## 7.5 UI

staff-terminal route:

```text
apps/staff-terminal/src/app/customers/[customerId]/accounts/new/page.tsx
apps/staff-terminal/src/app/account-opening-requests/[requestId]/page.tsx
```

manifest:

```text
screen-manifests/staff-terminal/ACC-201-account-opening.json
screen-manifests/staff-terminal/ACC-202-account-opening-approval.json
```

## 7.6 테스트

Integration test:

```text
AccountOpeningIntegrationTest
```

검증 항목:

1. 기존 customer 대상 opening request 생성
2. 없는 customer 거부
3. reason 누락 거부
4. invalid limit 거부
5. maker self-approval 거부
6. checker approve 성공
7. execute 전에는 accounts row 없음
8. execute 후 accounts/account_limits/projection 생성
9. initial deposit > 0이면 ledger transaction/posting 생성
10. initial deposit ledger transaction balanced
11. execute replay 시 중복 계좌/중복 입금 없음
12. audit event 생성

---

## 8. Phase 3 — Customer Self-Signup And Synthetic Login

## 8.1 목표

고객용 웹에서 사용자가 직접 synthetic 회원가입을 하고, dev/test synthetic login으로 token을 받아 고객 API를 사용할 수 있게 한다.

## 8.2 API

Unauthenticated route로 허용할 API:

```text
POST /api/auth/customer/signup
POST /api/auth/customer/login
```

Authenticated route:

```text
GET /api/auth/session
```

기존 `/api/auth/session`은 유지한다.

## 8.3 SecurityConfig 수정

다음 route는 인증 없이 허용해야 한다.

```text
/api/auth/customer/signup
/api/auth/customer/login
```

기존 custom authorization filter도 동일하게 예외 처리한다.

단, signup/login은 synthetic mode에서만 동작해야 한다.

권장 설정:

```yaml
banking-lab:
  synthetic-auth:
    enabled: false
    issue-simulator-token-enabled: false
```

local/dev/test에서만 true.

prod-like profile에서 true이면 startup fail-fast 또는 endpoint 403.

## 8.4 DB

`customer_auth_identities`는 Phase 1에서 추가한 테이블을 재사용한다.

self-signup이 고객과 계좌를 바로 만들지, approval을 거칠지 정책을 명확히 선택한다.

권장 MVP 정책:

- Self-signup은 고객과 auth identity를 즉시 생성한다.
- KYC status는 `PENDING` 또는 `VERIFIED` 중 하나를 선택하되 synthetic boundary를 명확히 한다.
- 계좌는 자동으로 만들지 않는다.
- 단, demo 편의상 선택적으로 `openDefaultAccount=true`를 지원하고, 이 경우 account opening service를 내부적으로 호출하지 말고 별도의 self-service account opening flow로 처리한다.
- 더 은행스럽게 하려면 self-signup 후 직원이 계좌를 개설하는 흐름을 유지한다.

권장 MVP:

```text
signup → customer + kyc PENDING + auth identity 생성
staff account opening → 계좌 생성
login → account list 조회
```

## 8.5 DTO

```kotlin
data class CustomerSignupCommand(
    val username: String,
    val password: String,
    val customerName: String,
    val customerPhone: String,
    val customerAddress: String,
    val reason: String? = "Synthetic customer self-signup"
)
```

```kotlin
data class CustomerLoginCommand(
    val username: String,
    val password: String
)
```

응답:

```kotlin
data class CustomerLoginResponse(
    val tokenType: String = "Bearer",
    val accessToken: String,
    val customerId: String,
    val subject: String,
    val expiresInSeconds: Long,
    val syntheticOnly: Boolean = true
)
```

## 8.6 Password policy

Synthetic이어도 다음을 지킨다.

- password 최소 길이 8
- username과 같은 password 금지
- password hash 저장
- login 실패 횟수 증가
- 5회 실패 시 LOCKED 처리하거나 structured error 반환
- plaintext password audit payload에 저장 금지

## 8.7 테스트

```text
CustomerSelfSignupIntegrationTest
CustomerSyntheticLoginIntegrationTest
```

검증 항목:

1. signup 성공
2. duplicate username 거부
3. weak password 거부
4. customers row 생성
5. kyc profile 생성
6. auth identity 생성
7. password_hash plaintext 아님
8. login 성공 시 simulator bearer token 반환
9. login 실패 시 structured error
10. locked/disabled user login 거부
11. prod-like profile에서 synthetic login/token issue 거부
12. token으로 `/api/auth/session` 호출 시 customerId 확인

---

## 9. Phase 4 — Customer Account List And Real Transfer Form

## 9.1 목표

고객 웹에서 로그인 후 고정 seed ID 없이 다음을 수행한다.

```text
signup or login
→ session 확인
→ 내 계좌 목록 조회
→ 계좌 상세/거래내역 조회
→ 내부 계좌로 이체
→ 이체 결과 및 거래내역 확인
```

## 9.2 Customer account list API

추가 API:

```text
GET /api/customer/accounts?customerId={customerId}
```

또는 token claim만 사용하려면:

```text
GET /api/customer/accounts
```

권장:

- `customerId` query parameter를 허용하되 `BankingLabAuthContext.requireCustomerOwnership(customerId)`를 반드시 적용
- token claim 기반 default도 지원 가능

응답:

```kotlin
data class CustomerAccountListResponse(
    val items: List<CustomerAccountSummaryDto>,
    val syntheticOnly: Boolean = true
)

data class CustomerAccountSummaryDto(
    val customerId: String,
    val accountId: String,
    val maskedAccountNo: String,
    val status: String,
    val currency: String,
    val ledgerBalanceMinor: Long,
    val availableBalanceMinor: Long,
    val holdAmountMinor: Long
)
```

## 9.3 Internal recipient lookup

내부 계좌 이체 UX를 위해 수취 계좌 확인 API를 추가한다.

```text
GET /api/customer/recipients/internal-account-lookup?accountNo=...
```

응답:

```kotlin
data class InternalAccountRecipientPreview(
    val accountId: String,
    val maskedAccountNo: String,
    val maskedCustomerName: String,
    val currency: String,
    val status: String,
    val syntheticOnly: Boolean = true
)
```

정책:

- 존재하지 않는 계좌는 structured not found
- CLOSED/HOLD 계좌는 수취 불가 또는 warning
- 본인 계좌/타인 계좌 모두 internal transfer 대상이 될 수 있음
- 수취인 이름은 masked 처리

## 9.4 Customer Web UI

기존 route shell을 실제 form으로 승격한다.

구현 대상:

```text
apps/customer-web/src/app/signup/page.tsx
apps/customer-web/src/app/login/page.tsx
apps/customer-web/src/app/accounts/page.tsx
apps/customer-web/src/app/accounts/[accountId]/page.tsx
apps/customer-web/src/app/transfers/new/page.tsx
apps/customer-web/src/app/transfers/[resultId]/page.tsx
```

권장 client components:

```text
CustomerSignupForm
CustomerLoginForm
CustomerSessionProvider
CustomerAccountList
CustomerAccountDetail
InternalTransferForm
TransferResultPanel
StructuredErrorPanel
```

상태:

```text
SIGNED_OUT
SIGNUP_SUBMITTING
SIGNUP_CREATED
LOGIN_SUBMITTING
LOGIN_SUCCEEDED
SESSION_EXPIRED
ACCOUNTS_LOADING
ACCOUNTS_LOADED
TRANSFER_DRAFT
RECIPIENT_LOOKUP_SUCCEEDED
TRANSFER_SUBMITTING
TRANSFER_POSTED
TRANSFER_REPLAYED
TRANSFER_HELD
TRANSFER_FAILED
AUTHORIZATION_DENIED
UNEXPECTED_FAILURE
```

## 9.5 Token storage

MVP는 `sessionStorage` 또는 memory state를 사용할 수 있다.

주의:

- localStorage 장기 저장은 피한다.
- token이 없는 상태에서 accounts/transfer 접근 시 login으로 유도한다.
- synthetic token 사용 중임을 UI에 표시한다.
- 실제 운영 보안이라고 과장하지 않는다.

## 9.6 Transfer form

Form fields:

```text
source account selector
recipient account number or accountId
amountMinor
reason optional
idempotencyKey auto-generated
```

동작:

1. account list에서 source account 선택
2. recipient lookup
3. amount 입력
4. submit
5. `POST /api/customer/transfers`
6. status 표시
7. result page 또는 history refresh

기존 transfer API를 재사용한다.

## 9.7 테스트

Playwright:

```text
tests/e2e/customer-self-service.spec.ts
```

시나리오:

1. signup
2. synthetic login
3. account list empty or seeded account visible
4. staff/opening API fixture로 account 생성 또는 seed account 사용
5. account detail 조회
6. internal recipient lookup
7. transfer submit
8. retry same idempotency key replay
9. transaction history shows transfer
10. cross-customer account detail denied

환경 변수가 없으면 skip하되, skip reason 명확히 출력한다.

---

## 10. Phase 5 — Contracts, Evidence, CI

## 10.1 OpenAPI 업데이트

`contracts/openapi/core-banking.yaml`에 추가한다.

Operation IDs:

```text
requestStaffCustomerOnboarding
getStaffCustomerOnboardingRequest
approveStaffCustomerOnboardingRequest
rejectStaffCustomerOnboardingRequest
executeStaffCustomerOnboardingRequest

requestStaffAccountOpening
getStaffAccountOpeningRequest
approveStaffAccountOpeningRequest
rejectStaffAccountOpeningRequest
executeStaffAccountOpeningRequest

signupCustomer
loginCustomer
listCustomerAccounts
lookupInternalAccountRecipient
```

각 operation은 다음 marker를 명시한다.

```yaml
x-banking-lab-contract:
  syntheticOnly: true
x-reason-required: true/false
x-idempotent-command: true/false
```

## 10.2 API Client

`packages/api-client/src/index.ts`에 methods 추가:

```ts
requestStaffCustomerOnboarding(...)
getStaffCustomerOnboardingRequest(...)
approveStaffCustomerOnboardingRequest(...)
rejectStaffCustomerOnboardingRequest(...)
executeStaffCustomerOnboardingRequest(...)

requestStaffAccountOpening(...)
getStaffAccountOpeningRequest(...)
approveStaffAccountOpeningRequest(...)
rejectStaffAccountOpeningRequest(...)
executeStaffAccountOpeningRequest(...)

signupCustomer(...)
loginCustomer(...)
listCustomerAccounts(...)
lookupInternalAccountRecipient(...)
```

## 10.3 Screen manifests

추가 또는 갱신:

```text
screen-manifests/customer-web/CWB-001-customer-signup.json
screen-manifests/customer-web/CWB-002-customer-login.json
screen-manifests/customer-web/CWB-101-account-list.json
screen-manifests/customer-web/CWB-201-internal-transfer-form.json

screen-manifests/staff-terminal/CST-201-customer-onboarding.json
screen-manifests/staff-terminal/CST-202-customer-onboarding-approval.json
screen-manifests/staff-terminal/ACC-201-account-opening.json
screen-manifests/staff-terminal/ACC-202-account-opening-approval.json
```

## 10.4 Evidence

새 문서:

```text
docs/test-evidence/customer-onboarding-self-service.md
```

필수 내용:

```text
Implemented controls
API list
DB migration list
Security boundary
Maker-checker rules
Synthetic auth rules
Commands run
Commands not run
Residual risks
Demo scenario
```

`docs/implementation-coverage-matrix.md`도 업데이트한다.

## 10.5 CI

기존 CI에 새 테스트가 자연스럽게 포함되어야 한다.

추가 가능한 targeted scripts:

```json
{
  "test:customer-onboarding": "scripts/run-core-banking-tests.sh :services:core-banking:integrationTest --tests '*CustomerOnboarding*'",
  "test:account-opening": "scripts/run-core-banking-tests.sh :services:core-banking:integrationTest --tests '*AccountOpening*'",
  "test:customer-self-service": "scripts/run-core-banking-tests.sh :services:core-banking:integrationTest --tests '*CustomerSelf*'"
}
```

---

## 11. 최종 테스트 명령

변경 영역 최소 테스트:

```bash
npm run validate:manifests
npm run packages:typecheck
npm run scripts:typecheck
npm run contracts:lint
npm run contracts:check-client
npm run test:core-banking:integration -- --tests '*CustomerOnboarding*'
npm run test:core-banking:integration -- --tests '*AccountOpening*'
npm run test:core-banking:integration -- --tests '*CustomerSelf*'
npm run next:customer-web:typecheck
npm run next:customer-web:build
npm run next:staff-terminal:typecheck
npm run next:staff-terminal:build
npm run test:e2e -- --grep "customer self-service"
```

최종 회귀 테스트:

```bash
npm test
npm run validate:manifests
npm run packages:typecheck
npm run scripts:typecheck
npm run contracts:lint
npm run contracts:check-client
npm run contracts:check-events
npm run test:core-banking:unit
npm run test:core-banking:integration
npm run next:customer-web:typecheck
npm run next:customer-web:build
npm run next:staff-terminal:typecheck
npm run next:staff-terminal:build
npm run test:e2e
docker compose config
docker compose --profile platform config
```

가능하면:

```bash
npm run platform:validate
npm run security:secrets-check
npm run security:evidence
```

---

## 12. 최종 통과 조건

### 기능 조건

1. 직원이 synthetic 고객 onboarding request를 생성할 수 있다.
2. 직원 self-approval은 거부된다.
3. checker 승인 후 execute하면 `customers`, `customer_kyc_profiles`, `customer_auth_identities`가 생성된다.
4. 직원이 기존 synthetic 고객에게 account opening request를 생성할 수 있다.
5. checker 승인 후 execute하면 `accounts`, `account_limits`, `account_balance_projections`가 생성된다.
6. initial deposit 옵션이 있으면 balanced ledger posting이 생성된다.
7. 고객이 self-signup을 할 수 있다.
8. 고객이 synthetic login을 할 수 있다.
9. login token으로 `/api/auth/session`에서 customerId를 확인할 수 있다.
10. 고객이 자기 계좌 목록을 조회할 수 있다.
11. 고객이 자기 계좌 상세와 거래내역을 조회할 수 있다.
12. 고객이 내부 계좌로 이체할 수 있다.
13. 같은 idempotency key 이체 재시도는 replay된다.
14. 다른 고객 계좌 조회는 거부된다.

### 품질 조건

1. 실제 외부 API 호출 없음
2. 실제 개인정보 없음
3. password plaintext 저장 없음
4. prod-like profile에서 synthetic token issue 차단
5. 모든 신규 high-risk staff command는 maker-checker 적용
6. 모든 신규 command는 audit event 생성
7. 모든 신규 command는 structured error 사용
8. contracts validation 통과
9. manifest validation 통과
10. customer/staff Next build 통과

---

## 13. 최종 응답 형식

Codex는 최종 응답에 다음을 포함한다.

```text
## Summary
## Implemented Phases
## Changed Files
## New Migrations
## New APIs
## New UI Routes / Components
## New Screen Manifests
## New Tests
## Commands Run
## Commands Not Run
## Evidence Updated
## Residual Risks
## Next Recommended Step
```

실행하지 않은 테스트를 통과한 것처럼 쓰지 않는다.
