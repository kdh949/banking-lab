# Codex Remaining Hardening Goals — Banking Lab

## 0. 문서 목적

이 문서는 `banking-lab` 저장소에서 이미 구현된 synthetic banking lab 범위를 유지하면서, 남아 있는 완성도 부족분을 보완하기 위한 Codex 구현 지시서다.

이 문서는 다음 한계를 해결 대상으로 삼지 않는다.

- 실제 고객 자금 처리
- 실제 개인정보 처리
- 실제 금융결제망, 카드망, 오픈뱅킹망 연계
- 실제 KYC/신용평가/외부 금융기관 API 호출
- 상용 금융회사 인허가·감독 대응

위 항목은 이 프로젝트의 의도적인 synthetic boundary로 유지한다. 이번 작업의 목표는 그 경계를 넘는 것이 아니라, 현재 synthetic lab 안에서 **코드 품질, 운영성, 보안 표준성, 테스트 신뢰성, 프론트 업무 완성도, 원장 운영성**을 높이는 것이다.

---

## 1. 전체 목표

현재 저장소는 원장, 복식부기, 멱등성, reversal, adjustment, maker-checker, audit, FDS/AML, reconciliation, complaint, 일부 product/card/loan/payment/notification/reporting 도메인을 이미 갖고 있다.

이번 작업의 목표는 기능을 무작정 늘리는 것이 아니라 다음 부족분을 보완하는 것이다.

1. 전체 서비스 CI를 강화한다.
2. 인증/인가 구현을 Spring Security 표준 기반으로 정리한다.
3. 프론트엔드를 demo/smoke panel에서 실제 업무 플로우 중심 UI로 승격한다.
4. OpenAPI/AsyncAPI 계약과 TypeScript client 생성·검증 체계를 만든다.
5. 원장 projection 재생성, drift 탐지, repair workflow를 추가한다.
6. 운영 보안, secrets, observability, SLO, runbook, audit export 증적을 보강한다.
7. payment/notification/reporting 서비스를 독립 bounded context로 더 강하게 검증한다.

---

## 2. 작업 원칙

### 2.1 절대 유지해야 할 것

1. Node reference runtime은 target-path 구현체가 아니다. 회귀 비교용 oracle로만 유지한다.
2. 신규 기능은 Kotlin/Spring Boot, PostgreSQL/Flyway, TypeScript/Next.js, shared api-client, manifest, evidence 문서 흐름을 따른다.
3. 기존 ledger invariant, idempotency, reversal, adjustment, audit hash-chain, maker-checker, structured error를 깨지 않는다.
4. 실제 외부 금융망·실고객 데이터·실제 개인정보 처리는 추가하지 않는다.
5. 테스트를 실행하지 못한 경우 통과했다고 쓰지 않는다.
6. 각 phase는 commit 가능한 단위로 작게 끝낸다.
7. 기존 문서가 현재 코드와 불일치하면 문서를 수정하되, 구현되지 않은 것을 완료로 표시하지 않는다.

### 2.2 신규 기능의 공통 완료 기준

모든 신규 command 또는 workflow는 가능한 한 다음 체인을 갖춰야 한다.

```text
Flyway migration
→ Kotlin domain/service/controller
→ authorization policy
→ structured error
→ audit event
→ maker-checker, if high-risk
→ idempotency, if externally retried
→ outbox event, if downstream side effect exists
→ TypeScript api-client
→ screen manifest
→ Next.js UI
→ integration test
→ Playwright or API smoke
→ evidence document
→ coverage matrix update
```

---

## 3. Phase 0 — Baseline 재확인과 작업 분기

### 목표

현재 저장소의 실제 상태를 다시 확인하고, 이미 완료된 기능을 중복 구현하지 않도록 한다.

### 구현 작업

1. 다음 파일을 읽고 현재 구현 상태를 요약한다.

```text
README.md
docs/implementation-coverage-matrix.md
docs/codex/implementation_missing_features_goals.md
docs/test-evidence/evidence-gap-report.md
package.json
settings.gradle.kts
build.gradle.kts
.github/workflows/ci.yml
docker-compose.yml
services/*/build.gradle.kts
services/*/src/main/kotlin/**
apps/*/src/**
packages/api-client/src/**
packages/auth-client/src/**
screen-manifests/**
```

2. `docs/codex/remaining-hardening-status.md`를 새로 작성한다.

필수 섹션:

```text
# Remaining Hardening Status

## Already Implemented
## Partially Implemented
## Missing
## Risks
## Phase Plan
## Commands Attempted
## Commands Not Attempted
```

3. `docs/implementation-coverage-matrix.md`에 이번 phase의 기준 상태를 반영한다.

### 테스트 방법

```bash
npm ci
npm test
npm run validate:manifests
npm run packages:typecheck
npm run scripts:typecheck
docker compose config
```

가능하면 다음도 실행한다.

```bash
npm run test:core-banking:unit
npm run test:core-banking:integration
```

### 통과 조건

- `docs/codex/remaining-hardening-status.md`가 존재한다.
- 이미 구현된 항목을 새 작업 대상으로 중복 기재하지 않는다.
- 실행한 명령과 실행하지 못한 명령을 구분해 기록한다.
- baseline에서 실패한 테스트가 있으면 원인과 영향 범위를 기록한다.
- 이 phase에서는 기능 추가보다 현재 상태의 정확한 정리가 우선이다.

---

## 4. Phase 1 — CI 전체 서비스 검증 강화

### 문제

현재 루트 `package.json`에는 core-banking 외에도 payment, notification, reporting 서비스 테스트 스크립트가 있다. 그러나 GitHub Actions의 backend job이 일부 서비스만 검증하면, 멀티서비스 구조가 깨져도 늦게 발견된다.

### 목표

모든 주요 서비스와 플랫폼 검증을 CI에 포함한다.

### 구현 작업

#### 4.1 GitHub Actions job 추가

`.github/workflows/ci.yml`에 다음 job을 추가하거나 기존 job을 확장한다.

```text
backend-payment-service
backend-notification-service
backend-reporting-service
backend-all-gradle
platform-validation
contracts-validation
compose-platform-config
```

권장 명령:

```bash
npm run test:payment-service:unit
npm run test:payment-service:integration

npm run test:notification-service:unit
npm run test:notification-service:integration

npm run test:reporting-service:unit
npm run test:reporting-service:integration

npm run platform:validate
docker compose --profile platform config
```

#### 4.2 CI 비용이 큰 테스트 분리

다음은 기본 PR CI와 nightly/manual CI를 구분한다.

PR CI:

```text
unit test
integration test
manifest validation
typecheck
Next build
contract validation
platform structural validation
```

manual/nightly CI:

```text
Docker live smoke
Keycloak live realm smoke
DAST/ZAP
full platform live runtime smoke
backup/restore live drill
load test
```

#### 4.3 CI evidence 문서 추가

다음 문서를 추가한다.

```text
docs/test-evidence/ci-coverage-hardening.md
```

필수 내용:

```text
- 어떤 job이 어떤 risk를 막는지
- PR CI와 manual/nightly CI의 차이
- Docker/Helm/Keycloak/TLC가 없을 때 fallback 정책
- 실행하지 못한 테스트를 pass로 기록하지 않는 정책
```

### 테스트 방법

```bash
npm run test:payment-service:unit
npm run test:payment-service:integration
npm run test:notification-service:unit
npm run test:notification-service:integration
npm run test:reporting-service:unit
npm run test:reporting-service:integration
npm run platform:validate
docker compose --profile platform config
```

GitHub Actions YAML 자체 검증:

```bash
python - <<'PY'
import yaml, pathlib
yaml.safe_load(pathlib.Path(".github/workflows/ci.yml").read_text())
print("ci yaml ok")
PY
```

Python/YAML 도구가 없으면 Node 기반 YAML parser 또는 단순 structural check script를 추가한다.

### 통과 조건

- core, payment, notification, reporting 서비스 테스트가 CI에 명시적으로 연결된다.
- platform validation이 CI에 연결된다.
- CI job name과 실행 명령이 문서화된다.
- CI에서 통과하지 않는 테스트를 evidence에 통과로 쓰지 않는다.
- 기존 `node-and-manifests`, `next-builds`, `formal-model`, `security-evidence` job을 깨지 않는다.

---

## 5. Phase 2 — 인증/인가 표준화

### 문제

현재 인증/인가 구조는 방향은 좋지만, custom filter와 custom JWT/JWKS decoder에 의존한다. 학습용으로는 좋지만 운영형 코드베이스로 보이기에는 약하다.

### 목표

Spring Security OAuth2 Resource Server 기반으로 토큰 검증을 표준화하고, 기존 RBAC/ABAC/step-up/trusted-device/session 정책은 명확한 policy layer로 분리한다.

### 구현 작업

#### 5.1 dependency 추가

각 Spring 서비스에 필요한 범위로 추가한다.

```kotlin
implementation("org.springframework.boot:spring-boot-starter-security")
implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
testImplementation("org.springframework.security:spring-security-test")
```

우선순위:

1. `services/core-banking`
2. `services/payment-service`
3. `services/notification-service`
4. `services/reporting-service`

#### 5.2 SecurityConfig 도입

예상 파일:

```text
services/core-banking/src/main/kotlin/lab/banking/core/security/SecurityConfig.kt
services/core-banking/src/main/kotlin/lab/banking/core/security/BankingLabJwtAuthenticationConverter.kt
services/core-banking/src/main/kotlin/lab/banking/core/security/BankingLabRouteAuthorizationManager.kt
```

요구사항:

1. `/api/health`, actuator health는 허용한다.
2. `/api/**`는 기본적으로 인증이 필요하다.
3. JWT issuer, audience를 설정으로 검증한다.
4. roles, realm_access.roles, resource_access.*.roles, scope를 기존 `BankingLabPrincipal`과 동등하게 해석한다.
5. customerId, sessionId, authTime, acr/amr, deviceFingerprint claim을 보존한다.
6. 기존 `BankingLabSecurityPolicyEnforcer`의 step-up, trusted-device, session revocation 정책을 유지한다.
7. route role matrix는 코드 하드코딩만으로 끝내지 말고, 최소한 별도 policy object 또는 configuration으로 분리한다.

#### 5.3 custom decoder 정리

기존 `SignedJwtJwksTokenDecoder`는 다음 중 하나로 처리한다.

- test/dev helper로만 유지
- deprecated 처리
- Spring Security 기반 테스트로 대체 후 제거

단, 한 번에 제거해서 전체 테스트를 깨뜨리지 말고, compatibility adapter를 둔다.

#### 5.4 simulator token 통제

1. simulator token은 dev/test profile에서만 허용한다.
2. prod-like profile에서 simulator token이 켜지면 application startup fail-fast한다.
3. 문서에 double opt-in 조건을 명시한다.

예상 설정:

```yaml
banking-lab:
  security:
    simulator-tokens-enabled: false
    dev-simulator-token-enabled: false
```

#### 5.5 method-level authorization

고위험 service method에 다음과 같은 method-level authorization을 적용한다.

```kotlin
@PreAuthorize(...)
```

대상:

```text
PII unmask
approval approve/reject
ledger adjustment
EOD close
security parameter change
authorization/menu-role parameter change
AML/FDS closure
audit export
projection rebuild approval
```

### 테스트 방법

Unit/integration test 추가:

```text
SecurityAuthorizationIntegrationTest
JwksAuthorizationIntegrationTest
SpringSecurityResourceServerIntegrationTest
SimulatorTokenProfileGuardTest
StepUpTrustedDevicePolicyIntegrationTest
```

검증 케이스:

1. 토큰 없음 → 401
2. 잘못된 signature → 401
3. 잘못된 issuer → 401
4. 잘못된 audience → 401
5. role 부족 → 403
6. customer token이 다른 customerId 조회 → 403
7. step-up 필요한 route에서 amr/acr 부족 → 403
8. trusted device 필요한 route에서 device claim 없음 → 403
9. revoked session → 401
10. dev/test에서만 simulator token 허용
11. prod-like profile에서 simulator token enabled면 startup 실패

실행 명령:

```bash
npm run test:core-banking:integration -- --tests '*Security*'
npm run test:core-banking:integration -- --tests '*Authorization*'
```

### 통과 조건

- `/api/**` 보호가 Spring Security filter chain에 의해 수행된다.
- 기존 route role policy와 ABAC 정책이 유지된다.
- custom JWT decoder가 운영 경로의 필수 요소가 아니다.
- simulator token은 dev/test 전용으로 제한된다.
- 기존 Keycloak/JWKS 테스트가 통과한다.
- 인증/인가 실패는 structured error와 audit evidence를 남긴다.

---

## 6. Phase 3 — 프론트 업무 플로우 승격

### 문제

현재 Next.js 앱은 API-backed smoke panel 성격이 강하다. 고객·직원·운영자 관점의 실제 업무 흐름으로 보이려면 route, form, token lifecycle, error recovery, 상태 화면을 강화해야 한다.

### 목표

고정 synthetic ID와 단일 smoke panel 중심 구조를 줄이고, channel별 실제 업무 플로우 UI를 만든다.

### 구현 작업

#### 6.1 customer-web 라우트 분리

예상 라우트:

```text
/apps/customer-web/src/app/login/page.tsx
/apps/customer-web/src/app/accounts/page.tsx
/apps/customer-web/src/app/accounts/[accountId]/page.tsx
/apps/customer-web/src/app/transfers/new/page.tsx
/apps/customer-web/src/app/transfers/[resultId]/page.tsx
/apps/customer-web/src/app/complaints/page.tsx
/apps/customer-web/src/app/complaints/[caseId]/page.tsx
/apps/customer-web/src/app/cards/page.tsx
/apps/customer-web/src/app/cards/[cardId]/page.tsx
/apps/customer-web/src/app/loans/page.tsx
/apps/customer-web/src/app/payments/page.tsx
/apps/customer-web/src/app/notifications/page.tsx
/apps/customer-web/src/app/security/page.tsx
```

필수 기능:

1. 로그인 상태 표시
2. token refresh 또는 재로그인 안내
3. 고객 계좌 목록/상세 조회
4. 거래내역 조회
5. 이체 입력 form
6. 이체 결과 상태 표시: `POSTED`, `HELD`, `FAILED`, `BLOCKED`
7. FDS hold 안내
8. structured error 표시
9. step-up required 안내
10. 민원 등록/상세/답변 확인

#### 6.2 hard-coded synthetic ID 제거

기존 demo path는 유지할 수 있으나, 일반 UI path는 다음 원칙을 따른다.

1. customerId는 token claim 또는 session endpoint에서 가져온다.
2. accountId는 API 조회 결과에서 선택한다.
3. `SYN-CUS-001`, `ACC-SYN-001-001` 같은 값은 demo seed 또는 smoke panel에만 남긴다.
4. demo seed 사용 시 화면에 `synthetic demo`로 명확히 표시한다.

#### 6.3 staff-terminal 업무 화면 강화

예상 라우트 또는 manifest-backed panel:

```text
/staff-terminal
/staff-terminal/tx/[transactionCode]
/staff-terminal/customers/[customerId]
/staff-terminal/accounts/[accountId]
/staff-terminal/approvals
/staff-terminal/audit
/staff-terminal/workflows/[businessReferenceId]
```

우선 구현할 업무:

1. 고객 조회: reason-required
2. 계좌 조회: reason-required
3. 거래 조회: reason-required
4. 승인함
5. 계좌 지급정지/해제
6. 이체한도 변경
7. KYC 재확인
8. 거래 정정/reversal
9. 수수료 면제/refund
10. privileged unmask

#### 6.4 공통 UI 컴포넌트

추가 또는 정리할 컴포넌트:

```text
AuthBoundary
SessionBanner
StructuredErrorPanel
ReasonInput
StepUpRequiredPanel
IdempotencyResultPanel
ApprovalStatusTimeline
AuditReferencePanel
FdsHoldStatusPanel
MoneyInput
AccountSelector
CustomerSelector
```

#### 6.5 Playwright 시나리오

추가할 e2e:

```text
customer account detail
customer transfer posted
customer transfer held
customer transfer failed validation
customer complaint entry
staff reason-required lookup
staff approval approve/reject
staff transaction correction smoke
ops EOD monitor smoke
audit event list smoke
```

### 테스트 방법

```bash
npm run next:customer-web:typecheck
npm run next:customer-web:build
npm run next:staff-terminal:typecheck
npm run next:staff-terminal:build
npm run next:ops-console:typecheck
npm run next:ops-console:build
npm run next:audit-console:typecheck
npm run next:audit-console:build
npm run test:e2e
```

추가 targeted Playwright 명령을 만들 수 있다.

```bash
npx playwright test tests/e2e/customer-workflows.spec.ts
npx playwright test tests/e2e/staff-terminal-workflows.spec.ts
```

### 통과 조건

- customer-web의 주요 업무가 route별로 분리된다.
- hard-coded customer/account ID가 일반 업무 path에서 제거된다.
- structured error가 사용자에게 의미 있게 표시된다.
- 이체 `POSTED`, `HELD`, `FAILED` 상태가 UI에서 확인된다.
- staff-terminal에서 reason-required 조회와 approval workflow를 실제 API로 수행한다.
- Playwright가 최소 고객 3개, 직원 3개 핵심 flow를 검증한다.

---

## 7. Phase 4 — OpenAPI/AsyncAPI 계약과 client 생성 체계

### 문제

현재 shared TypeScript API client는 유용하지만, API 계약과 구현의 drift를 자동으로 막기 어렵다.

### 목표

OpenAPI/AsyncAPI를 계약의 source로 만들고, API client와 테스트가 계약을 기준으로 검증되게 한다.

### 구현 작업

#### 7.1 계약 파일 구조

```text
contracts/
  openapi/
    core-banking.yaml
    payment-service.yaml
    notification-service.yaml
    reporting-service.yaml
  asyncapi/
    banking-domain-events.yaml
    payment-events.yaml
    notification-events.yaml
    reporting-events.yaml
  schemas/
    ledger-transaction-posted.schema.json
    payment-instruction-settled.schema.json
    notification-delivery-requested.schema.json
    report-artifact-generated.schema.json
```

#### 7.2 API client 구조 분리

현재 `packages/api-client/src/index.ts`가 너무 커지면 다음처럼 분리한다.

```text
packages/api-client/src/
  index.ts
  core/
    customer.ts
    ledger.ts
    staff.ts
    audit.ts
    product.ts
    loan.ts
    card.ts
  payment/
    payments.ts
  notification/
    notifications.ts
  reporting/
    reports.ts
  generated/
```

#### 7.3 계약 검증 스크립트

추가 스크립트:

```json
{
  "contracts:lint": "node --experimental-strip-types scripts/lint-contracts.ts",
  "contracts:check-client": "node --experimental-strip-types scripts/check-api-client-contract.ts",
  "contracts:check-events": "node --experimental-strip-types scripts/check-event-contracts.ts"
}
```

검증 내용:

1. OpenAPI YAML parse 가능
2. 모든 path에 operationId 존재
3. error response가 structured error schema 사용
4. idempotent command에 `Idempotency-Key` 또는 body idempotencyKey 정책 명시
5. reason-required endpoint에 reason parameter/body/header 중 하나 명시
6. AsyncAPI event에 `syntheticOnly`, `sourceService`, `eventType`, `aggregateId`, `occurredAt` 포함
7. JSON Schema validation 통과

#### 7.4 Spring controller와 계약 drift 방지

가능하면 다음 중 하나를 구현한다.

1. springdoc-openapi로 generated spec을 만들고 committed spec과 diff
2. controller route scanner로 최소 path/method 존재 여부 검증
3. integration test에서 주요 endpoint response shape 검증

### 테스트 방법

```bash
npm run contracts:lint
npm run contracts:check-client
npm run contracts:check-events
npm run packages:typecheck
npm run scripts:typecheck
```

서비스별 integration test:

```bash
npm run test:core-banking:integration
npm run test:payment-service:integration
npm run test:notification-service:integration
npm run test:reporting-service:integration
```

### 통과 조건

- 모든 주요 REST API가 OpenAPI에 등록된다.
- 모든 주요 domain event가 AsyncAPI 또는 JSON Schema로 등록된다.
- structured error contract가 문서와 테스트에 반영된다.
- TypeScript api-client가 계약과 불일치하면 CI가 실패한다.
- 이벤트 envelope가 계약과 불일치하면 CI가 실패한다.

---

## 8. Phase 5 — 원장 projection 재생성, drift 탐지, repair workflow

### 문제

원장은 강하지만 운영 중 projection이 깨졌을 때 탐지·재계산·증적화하는 기능이 필요하다. 실제 원장 source row는 그대로 두고 projection만 재생성할 수 있어야 한다.

### 목표

`ledger_postings`를 source of truth로 하여 balance projection drift를 탐지하고, maker-checker 승인 후 안전하게 projection을 재생성한다.

### 구현 작업

#### 8.1 DB migration

예상 테이블:

```text
ledger_projection_drift_runs
ledger_projection_drift_items
ledger_projection_rebuild_requests
ledger_projection_rebuild_runs
ledger_projection_rebuild_items
```

필수 컬럼 예시:

```text
run_id
request_id
account_id
currency
expected_ledger_balance_minor
actual_ledger_balance_minor
expected_available_balance_minor
actual_available_balance_minor
hold_amount_minor
drift_amount_minor
status
requested_by
approved_by
reason
approval_id
source_posting_count
source_last_posting_id
source_hash
created_at
completed_at
metadata_json
```

#### 8.2 drift detector

서비스:

```text
LedgerProjectionIntegrityService
```

기능:

1. 전체 계좌 projection 검증
2. 특정 accountId 검증
3. 특정 businessDate 이하 posting 기준 검증
4. source posting hash 생성
5. drift item 저장
6. audit event append

SQL 원칙:

```sql
SELECT account_id, currency,
       SUM(CASE WHEN direction = 'DEBIT' THEN -amount_minor ELSE amount_minor END) AS expected_balance
FROM ledger_postings
GROUP BY account_id, currency
```

단, 실제 부호 정책은 기존 `LedgerInvariants.signedAmount`와 정확히 일치해야 한다.

#### 8.3 rebuild request

API:

```text
POST /api/ops/ledger/projection-drift-runs
GET  /api/ops/ledger/projection-drift-runs/{runId}

POST /api/ops/ledger/projection-rebuild-requests
POST /api/ops/ledger/projection-rebuild-requests/{requestId}/approve
POST /api/ops/ledger/projection-rebuild-requests/{requestId}/reject
POST /api/ops/ledger/projection-rebuild-requests/{requestId}/execute
GET  /api/ops/ledger/projection-rebuild-runs/{runId}
```

정책:

1. drift check는 reason-required다.
2. rebuild request는 maker-checker가 필요하다.
3. maker self-approval은 거부한다.
4. rebuild는 ledger_transactions/ledger_postings를 update/delete하지 않는다.
5. rebuild는 `account_balance_projections`만 재계산한다.
6. rebuild 전후 hash와 source posting count를 남긴다.
7. rebuild 실행 중 concurrent posting과 충돌하지 않게 account row/projection row lock을 잡는다.

#### 8.4 UI

Ops console에 추가:

```text
OPS-LEDGER-101 Projection Drift Monitor
OPS-LEDGER-102 Projection Rebuild Request
OPS-LEDGER-103 Projection Rebuild Evidence
```

Staff terminal 또는 audit console에는 read-only evidence를 표시한다.

#### 8.5 테스트

Integration test:

```text
LedgerProjectionIntegrityIntegrationTest
LedgerProjectionRebuildWorkflowIntegrationTest
```

검증 케이스:

1. 정상 projection은 drift 0건
2. 테스트에서 projection 값을 의도적으로 틀리게 만든 뒤 drift 탐지
3. drift item이 expected/actual 값을 정확히 기록
4. rebuild request 생성
5. maker self-approval 거부
6. checker 승인 후 execute
7. execute 후 projection이 posting 합계와 일치
8. ledger_transactions와 ledger_postings row count가 변하지 않음
9. closed business date 정책과 충돌하지 않음
10. audit event 생성
11. idempotency key 재시도 시 중복 rebuild run 없음

### 테스트 방법

```bash
npm run test:core-banking:integration -- --tests '*LedgerProjection*'
npm run next:ops-console:typecheck
npm run next:ops-console:build
npm run test:e2e -- --grep "projection"
```

### 통과 조건

- drift detector가 실제 posting 합계와 projection 차이를 탐지한다.
- rebuild는 승인 없이 실행되지 않는다.
- rebuild 후 projection이 정확히 복구된다.
- ledger source rows는 변경되지 않는다.
- audit/evidence가 남는다.
- ops-console에서 결과를 확인할 수 있다.

---

## 9. Phase 6 — 운영 보안, secrets, observability, runbook

### 문제

로컬 lab 수준의 Compose/K8s 설정은 있으나, 운영형 구조로 보이려면 secrets, observability, SLO, runbook, audit export 증적이 필요하다.

### 목표

실제 외부 서비스를 쓰지 않더라도, 운영 보안과 장애 대응 구조를 synthetic 환경에서 검증 가능하게 만든다.

### 구현 작업

#### 9.1 secrets hygiene

추가 파일:

```text
.env.example
docs/security/secrets-management.md
scripts/check-secret-placeholders.ts
```

요구사항:

1. 실제 secret을 커밋하지 않는다.
2. dev default secret은 dev-only임을 명시한다.
3. K8s Secret은 `secret.example.yaml` 또는 ExternalSecret 예시만 둔다.
4. CI에서 private key, password, token pattern을 검사한다.
5. production-like profile에서 기본 password 사용 시 fail-fast하는 guard를 추가한다.

테스트:

```bash
npm run security:secrets-check
```

#### 9.2 observability

추가 파일:

```text
infra/observability/prometheus-rules.yaml
infra/observability/grafana-dashboard-core-banking.json
infra/observability/grafana-dashboard-outbox.json
docs/operations/slo.md
docs/operations/observability.md
```

핵심 metric:

```text
ledger_command_latency
ledger_command_error_rate
idempotency_replay_count
outbox_pending_count
outbox_dead_letter_count
authorization_denied_count
audit_append_failure_count
payment_instruction_failure_count
notification_dead_letter_count
report_artifact_generation_failure_count
```

서비스에 이미 metric이 있으면 재사용한다. 없으면 Micrometer counter/timer를 추가한다.

#### 9.3 audit export

추가 기능:

```text
POST /api/audit/exports
GET  /api/audit/exports/{exportId}
```

DB:

```text
audit_export_jobs
audit_export_files
```

정책:

1. auditor/compliance만 export 가능
2. reason-required
3. step-up required
4. export file은 local synthetic artifact로 저장
5. NDJSON 또는 JSONL 형식
6. hash-chain start/end, row count, sha256 저장
7. ledger rows mutated = false
8. audit event append

#### 9.4 runbooks

추가 문서:

```text
docs/operations/runbooks/ledger-drift.md
docs/operations/runbooks/outbox-dead-letter.md
docs/operations/runbooks/authz-denial-spike.md
docs/operations/runbooks/eod-failure.md
docs/operations/runbooks/postgres-restore.md
```

각 runbook 필수 섹션:

```text
Symptoms
Detection
Immediate Containment
Diagnosis Queries
Recovery Steps
Evidence To Capture
Rollback
Escalation
Post-incident Review
```

### 테스트 방법

```bash
npm run security:secrets-check
npm run security:evidence
npm run test:core-banking:integration -- --tests '*AuditExport*'
npm run next:audit-console:typecheck
npm run next:audit-console:build
```

가능하면 observability structural validation:

```bash
npm run observability:validate
```

### 통과 조건

- secret placeholder check가 통과한다.
- 운영형 profile에서 기본 secret 사용 시 실패한다.
- 핵심 metric이 actuator/prometheus 또는 문서화된 endpoint에서 노출된다.
- audit export가 reason/role/step-up 정책을 따른다.
- audit export 결과에 row count, hash, sha256, syntheticOnly가 남는다.
- runbook 5개 이상이 존재한다.

---

## 10. Phase 7 — payment/notification/reporting bounded context 하드닝

### 문제

서비스는 분리되어 있지만, 각 서비스를 독립 bounded context로 설득하려면 계약, outbox idempotency, retry/DLQ, 권한, UI, evidence가 더 명확해야 한다.

### 목표

payment, notification, reporting을 core-banking의 부속 기능이 아니라 독립 서비스처럼 검증 가능하게 만든다.

### 10.1 payment-service

#### 구현 작업

1. settlement calendar simulator
2. biller registry versioning
3. payment instruction lifecycle state machine 정리
4. duplicate idempotency key replay
5. cancellation workflow maker-checker
6. outbox retry/dead-letter replay API
7. payment event AsyncAPI contract
8. ops-console payment outbox monitor
9. staff-terminal payment instruction inquiry reason-required
10. failure injection test

#### 테스트

```bash
npm run test:payment-service:unit
npm run test:payment-service:integration
npm run test:payment-service:outbox-worker-compose
npm run test:payment-service:domain-publisher-compose
```

통과 조건:

- payment instruction 생성/조회/취소/정산 lifecycle이 deterministic하다.
- core-banking posting bridge가 idempotent하다.
- outbox publish 실패 시 retry 또는 DLQ로 이동한다.
- cancellation은 maker-checker 정책을 따른다.
- event contract가 통과한다.

### 10.2 notification-service

#### 구현 작업

1. template rendering engine
2. template approval workflow
3. recipient preference/suppression policy
4. provider abstraction with synthetic provider
5. delivery rate limit
6. delivery retry/dead-letter
7. admin preference management
8. customer preference self-service
9. audit delivery history with masking
10. notification event AsyncAPI contract

#### 테스트

```bash
npm run test:notification-service:unit
npm run test:notification-service:integration
npm run test:notification-service:provider-dead-letter-compose
npm run test:notification-service:keycloak-service-token
```

통과 조건:

- real provider는 기본 비활성화다.
- synthetic provider 실패가 dead-letter로 남는다.
- template activation은 승인 전에는 불가능하다.
- customer는 자기 preference/history만 조회 가능하다.
- delivery history는 masking된다.
- event contract가 통과한다.

### 10.3 reporting-service

#### 구현 작업

1. report definition versioning
2. artifact generation idempotency
3. artifact content hash
4. export package metadata
5. retention sweep
6. report workflow timeline
7. reporting outbox publisher
8. audit-console artifact history
9. admin-console artifact management
10. report event AsyncAPI contract

#### 테스트

```bash
npm run test:reporting-service:unit
npm run test:reporting-service:integration
npm run test:reporting-service:domain-publisher-compose
npm run test:reporting-service:keycloak-service-token
```

통과 조건:

- 같은 idempotency key로 artifact를 중복 생성하지 않는다.
- artifact sha256이 deterministic하다.
- retention sweep은 ledger rows를 변경하지 않는다.
- export는 reason-required/audit-required다.
- reporting event contract가 통과한다.

---

## 11. Phase 8 — 대규모 원장 운영성 증적

### 문제

현재 원장 구조는 좋지만, 운영 규모를 설득하려면 partition, archive, read model rebuild, 대량 test evidence가 필요하다.

### 목표

실제 금융망을 붙이지 않고도 대량 원장 데이터 운영 패턴을 synthetic data로 검증한다.

### 구현 작업

#### 11.1 synthetic ledger dataset generator

추가:

```text
scripts/generate-large-ledger-dataset.ts
docs/test-evidence/generated/large-ledger-dataset-summary.json
```

기능:

1. N customers
2. M accounts
3. K ledger transactions
4. balanced postings only
5. idempotency keys
6. deterministic seed
7. configurable business date range

#### 11.2 partition/archive evidence

추가:

```text
docs/architecture/ledger-partition-archive-plan.md
scripts/check-ledger-partition-readiness.ts
```

가능하면 실제 partition migration을 추가한다. 어렵다면 최소한 다음을 검증한다.

1. business_date index coverage
2. partition route consistency
3. old date archive candidate query
4. account statement query plan evidence
5. reconciliation date-range query evidence

#### 11.3 benchmark smoke

추가:

```text
npm run ledger:large-dataset-smoke
npm run ledger:query-benchmark
```

결과:

```text
docs/test-evidence/ledger-large-dataset-smoke.md
docs/test-evidence/generated/ledger-query-benchmark.json
```

### 테스트 방법

```bash
npm run ledger:large-dataset-smoke
npm run ledger:query-benchmark
npm run ledger:integrity-check
```

### 통과 조건

- synthetic large dataset 생성이 deterministic하다.
- 모든 생성 거래가 balanced다.
- projection 합계가 posting 합계와 일치한다.
- 주요 조회 query benchmark 결과가 JSON으로 남는다.
- archive/partition readiness 문서가 존재한다.
- 실제 운영 규모라고 과장하지 않는다.

---

## 12. 최종 테스트 명령

환경이 허용하는 범위에서 다음 명령을 실행한다.

```bash
npm ci

npm test
npm run validate:manifests
npm run test:screen-engine
npm run packages:typecheck
npm run scripts:typecheck

npm run next:customer-web:typecheck
npm run next:customer-web:build
npm run next:staff-terminal:typecheck
npm run next:staff-terminal:build
npm run next:complaint-portal:typecheck
npm run next:complaint-portal:build
npm run next:ops-console:typecheck
npm run next:ops-console:build
npm run next:audit-console:typecheck
npm run next:audit-console:build
npm run next:fds-aml-console:typecheck
npm run next:fds-aml-console:build
npm run next:admin-console:typecheck
npm run next:admin-console:build

npm run test:e2e

npm run test:core-banking:unit
npm run test:core-banking:integration
npm run test:payment-service:unit
npm run test:payment-service:integration
npm run test:notification-service:unit
npm run test:notification-service:integration
npm run test:reporting-service:unit
npm run test:reporting-service:integration

npm run contracts:lint
npm run contracts:check-client
npm run contracts:check-events

npm run security:secrets-check
npm run security:evidence
npm run evidence:refresh-check

npm run formal:ledger

npm run platform:validate
docker compose config
docker compose --profile platform config
```

선택/환경 의존 테스트:

```bash
npm run platform:live-runtime-smoke
npm run security:evidence:docker
npm run postgres:backup-drill:docker-live
npm run load:synthetic
npm run ledger:large-dataset-smoke
npm run ledger:query-benchmark
```

---

## 13. 최종 완료 조건

### 13.1 기능 완료 조건

1. 모든 주요 서비스 테스트가 CI에 연결된다.
2. core-banking의 인증/인가가 Spring Security Resource Server 기반으로 동작한다.
3. simulator token은 dev/test 전용으로 제한된다.
4. customer-web과 staff-terminal의 주요 업무가 route 또는 manifest-backed workflow로 분리된다.
5. hard-coded synthetic customer/account ID가 일반 업무 path에서 제거된다.
6. OpenAPI/AsyncAPI 계약 파일이 존재한다.
7. API client와 event schema가 계약 검증을 통과한다.
8. ledger projection drift detector가 존재한다.
9. ledger projection rebuild workflow가 maker-checker와 audit을 포함한다.
10. secrets hygiene check가 존재한다.
11. audit export 또는 observability/runbook 중 최소 하나 이상이 executable evidence를 가진다.
12. payment/notification/reporting 중 최소 2개 서비스가 독립 CI integration test와 contract test를 가진다.

### 13.2 품질 완료 조건

1. 기존 ledger invariant가 깨지지 않는다.
2. 기존 idempotency/reversal/adjustment 정책이 깨지지 않는다.
3. 기존 maker-checker self-approval rejection이 유지된다.
4. 기존 Keycloak/JWKS 경로가 유지된다.
5. 기존 evidence/retirement/parity gate가 깨지지 않는다.
6. 신규 high-risk command는 reason-required, audit-required, maker-checker-required다.
7. 신규 API는 structured error를 사용한다.
8. 신규 UI는 실패 상태를 숨기지 않는다.
9. 테스트를 못 돌린 경우 정확히 기록한다.
10. 문서에서 synthetic lab의 범위를 유지한다.

### 13.3 최종 보고 형식

Codex는 최종 응답에 다음을 반드시 포함한다.

```text
## Summary
## Implemented Phases
## Changed Files
## New Migrations
## New APIs
## New UI Routes / Manifests
## New Tests
## Commands Run
## Commands Not Run
## Evidence Updated
## Remaining Risks
## Recommended Next Step
```

실패하거나 실행하지 못한 테스트가 있으면, 그 이유를 숨기지 말고 정확히 쓴다.

---

## 14. 작업 우선순위

시간이 부족하면 다음 순서로 줄인다.

1. Phase 1: CI 전체 서비스 검증
2. Phase 2: 인증/인가 표준화
3. Phase 5: ledger projection drift/rebuild
4. Phase 3: customer/staff 주요 UI workflow
5. Phase 4: OpenAPI/AsyncAPI contract
6. Phase 6: secrets/observability/audit export
7. Phase 7: payment/notification/reporting bounded context hardening
8. Phase 8: 대규모 원장 운영성 증적

가장 우선순위가 높은 vertical slice는 다음이다.

```text
Spring Security 표준화
+ core-banking security integration test
+ CI에 전체 서비스 test job 추가
+ ledger projection drift detector
+ ops-console projection monitor
+ evidence 문서 갱신
```
