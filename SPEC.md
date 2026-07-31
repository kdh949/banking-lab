# Customer Self-Service Onboarding/Login And Customer 360 Specification

작성일: 2026-06-14

## 1. Summary

현재 구현은 `CWB-001/002` self-signup/login, `CWB-101..103` 계좌 목록/상세/거래내역, `StatementService`의 statement/certificate/access-history read model을 갖추고 있다. RFP 기준의 부족분은 고객 온보딩 진행상태, 합성 KYC/중복 검증, 고객이 직접 요청하는 계좌개설 intake, 통합 Customer 360 read model, 단일/통합 statement artifact, 그리고 customer-web 실제 화면 승격이다.

이 스펙은 실 KYC, 실 PII, 실 금융망, 실 결제, 실 문서전송 없이 synthetic-only로 구현한다. 고객 self-service 계좌개설은 고객 요청까지만 받고, 실제 계좌 생성과 초기 입금 posting은 기존 staff maker-checker account-opening 실행 경로를 유지한다.

## 2. Goals

- 고객이 synthetic self-signup 후 자신의 KYC/중복검증/온보딩 진행상태를 볼 수 있다.
- 고객 login은 토큰의 `customerId` ownership을 모든 customer route에 일관되게 전파한다.
- 고객이 self-service로 synthetic account-opening request를 제출하고 상태를 추적할 수 있다.
- Customer 360 화면은 프로필, 계좌, 잔액, 대출, 카드, 민원, 결제, 알림, 최근활동, 접근이력을 하나의 canonical read model로 제공한다.
- Statement 기능은 고객 통합 statement와 계좌별 statement를 모두 제공하고, ledger source hash와 deterministic artifact snapshot을 남긴다.
- 모든 민감 read는 masked-by-default와 audit event를 유지한다.

## 3. Non-Goals

- 실제 KYC/CKYC/eKYC, 신용평가, 금융망, 결제망, 이메일/PDF 배송 연동은 구현하지 않는다.
- 고객 self-service request만으로 실제 계좌 row나 ledger posting을 만들지 않는다.
- 법적으로 유효한 은행 명세서, 실명확인, 실 입금증명, 실 고지서 발송은 만들지 않는다.
- Node.js MVP를 target implementation으로 확장하지 않는다.

## 4. Backend Scope

### 4.1 Self-Service Onboarding/Login

`POST /api/auth/customer/signup`은 유지하되 응답을 additive 확장한다.

Required response additions:

- `onboardingStatus`
- `kycStatus`
- `duplicateCheckStatus`
- `nextRequiredAction`

Add `GET /api/customer/me`.

- Request parameter로 `customerId`를 받지 않는다.
- `BankingLabAuthContext`의 token `customerId`만 사용한다.
- 반환 필드: `customerId`, `authSubject`, `username`, `maskedCustomerName`, `maskedPhone`, `maskedAddress`, `customerGrade`, `riskGrade`, `kycStatus`, `onboardingStatus`, `duplicateCheckStatus`, `lastLoginAt`, `authIdentityStatus`, `syntheticOnly`, `maskingPolicy`.
- 고객 토큰이 없거나 `CUSTOMER` role이 아니면 structured auth error를 반환한다.

Add synthetic onboarding checks table.

```sql
customer_onboarding_checks(
  check_id text primary key,
  customer_id text not null references customers(customer_id),
  check_type text not null,
  status text not null,
  risk_level text not null,
  evidence_json jsonb not null,
  synthetic_only boolean not null default true check (synthetic_only = true),
  created_at timestamptz not null default now()
)
```

Required `check_type` values:

- `DUPLICATE_IDENTITY`
- `KYC_SIMULATION`
- `TERMS_ACCEPTANCE`
- `CONTACT_REACHABILITY`

Duplicate check must use normalized synthetic name/phone/address fingerprints. Audit payloads must not include raw phone, address, or password.

### 4.2 Customer Self-Service Account Opening Request

Add `POST /api/customer/account-opening-requests`.

Command DTO:

- `idempotencyKey`
- `productCode`
- `accountAlias`
- `currency`
- `syntheticInitialDepositAmountMinor`
- `termsAccepted`

Behavior:

- Requires `CUSTOMER` role and token-owned customer.
- Creates customer intake only.
- Does not insert into `accounts`.
- Does not call `LedgerCommandService`.
- Does not create `ledger_transactions` or `ledger_postings`.
- Links to existing staff account-opening flow only after staff review.
- Idempotency key replay returns the first request.

Add `GET /api/customer/account-opening-requests`.

Response includes:

- `requestId`
- `status`
- `approvalId`
- `approvalStatus`
- `requestedProductCode`
- `requestedCurrency`
- `generatedAccountId`
- `generatedMaskedAccountNo`
- `createdAt`
- `updatedAt`

### 4.3 Customer 360

Add `GET /api/customer/360`.

Rules:

- Uses token-owned `customerId`.
- No arbitrary customer query parameter.
- Appends `CUSTOMER_360_VIEW` audit event.
- Audit payload contains counts, masked identifiers, and `syntheticOnly=true` only.
- No raw phone, address, account number, card number, or note body in audit payload.

DTO sections:

- `profile`
- `kycSummary`
- `accountSummary`
- `accounts`
- `loanSummary`
- `cardSummary`
- `complaintSummary`
- `paymentSummary`
- `notificationSummary`
- `recentLedgerActivity`
- `accessHistorySummary`
- `availableActions`
- `sourceWatermarks`

`CustomerAccountDetailDto` must be additive-expanded with:

- `openedAt`
- `limits`
- `holds`
- `recentTransactions`
- `statementActions`
- `syntheticOnly`
- `maskingPolicy`

### 4.4 Statements

Keep `GET /api/customers/{customerId}/statements` for compatibility.

Add customer-owned routes:

- `GET /api/customer/statements/consolidated?from&to`
- `GET /api/customer/accounts/{accountId}/statement?from&to`

Statement DTO additions:

- `statementId`
- `statementScope`
- `accountId`
- `sourceLedgerHash`
- `payloadHash`
- `generatedAt`
- `maskingPolicy`

Add statement artifact snapshots.

```sql
statement_artifact_snapshots(
  statement_id text primary key,
  customer_id text not null references customers(customer_id),
  account_id text references accounts(account_id),
  from_date date not null,
  to_date date not null,
  statement_scope text not null,
  source_ledger_hash text not null,
  payload_hash text not null,
  snapshot_json jsonb not null,
  synthetic_only boolean not null default true check (synthetic_only = true),
  created_at timestamptz not null default now(),
  last_viewed_at timestamptz not null default now()
)
```

Statement and certificate reads must not mutate ledger rows. They may append audit events and update statement snapshot `last_viewed_at`.

## 5. Frontend And Manifest Scope

Add customer-web routes:

- `/profile`
- `/onboarding`
- `/360`
- `/accounts/[accountId]/statement`
- `/statements`

Add customer-web manifests:

- `CWB-003.customer-profile.json`
- `CWB-004.self-service-account-opening-request.json`
- `CWB-104.customer-360.json`
- `CWB-105.account-statement.json`
- `CWB-106.consolidated-statement.json`
- `CWB-107.statement-artifact-history.json`

Update existing manifests:

- `CWB-102.account-detail.json`
- `CWB-103.transaction-history.json`
- `CWB-401.security-access-history.json`

Required manifest metadata:

- `audit.selfService=true`
- `audit.eventTypes`
- `audit.maskingPolicy=CUSTOMER_SELF`
- `api.clientMethod`
- `api.ownershipEnforced=true`
- `api.syntheticOnly=true`

The actual customer workflow must live in `CustomerSelfService.tsx` or route-owned components. `ApiBackedCustomerPanel` remains evidence/demo-only and must not be the only implementation of statements or 360.

## 6. Public Interface Additions

Add OpenAPI and API-client types:

- `CustomerProfileDto`
- `CustomerOnboardingCheckDto`
- `CustomerSelfServiceAccountOpeningCommand`
- `CustomerSelfServiceAccountOpeningRequestDto`
- `Customer360Dto`
- `Customer360AccountDto`
- `CustomerStatementArtifactDto`

Add structured error codes:

- `CUSTOMER_ONBOARDING_CHECK_FAILED`
- `CUSTOMER_ACCOUNT_OPENING_ALREADY_PENDING`
- `CUSTOMER_AUTH_IDENTITY_NOT_ACTIVE`

Reuse existing error families:

- `AUTHORIZATION_POLICY_VIOLATION`
- `REQUEST_VALIDATION_FAILED`
- `IDEMPOTENCY_KEY_CONFLICT`
- `RESOURCE_NOT_FOUND`

## 7. Acceptance Criteria

- Customer signup/login remains idempotent, hashed, and synthetic-token guarded.
- Customer profile and 360 routes reject missing or mismatched customer tokens.
- Customer self-service account-opening request creates no account and no ledger posting until staff maker-checker execution.
- Account and consolidated statements reconcile exactly to ledger postings for the requested date range.
- Statement artifacts are deterministic for identical ledger source and date range.
- Raw PII, raw account numbers, and passwords never appear in audit payloads.
- Customer-web exposes real route-backed screens for profile, onboarding, 360, account statement, and consolidated statements.
- Manifest validation covers all new and updated customer-web screens.
- Evidence documents commands actually run and skipped tests with reasons.
