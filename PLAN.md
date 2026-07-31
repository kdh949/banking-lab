## 0. 전체 최종 목표

다음 5가지를 완료하라.

1. `README.md`, `docs/implementation-coverage-matrix.md`, 관련 evidence/status 문서의 오래된 문구와 status 불일치를 정리한다.
2. GitHub Actions hosted CI가 실제로 green으로 도는 상태를 확보하고, 로컬 증적과 hosted CI 증적을 분리한다.
3. `customer-web`과 `staff-terminal`의 주요 route가 단순 manifest shell이 아니라 실제 live API execution flow를 수행한다는 demo/evidence를 추가한다.
4. OpenAPI DTO-level diffing과 AsyncAPI/event envelope runtime validation을 추가한다.
5. 고객센터 상담사용 전산을 별도 `call-center-console` 앱으로 만들거나, 현실적으로 부담되면 `staff-terminal` 안에 상담 이력/상담 메모/통화 사유/후처리 workflow를 API-backed로 추가한다.

최종 결과는 다음 질문에 명확히 답할 수 있어야 한다.

- 지금 어떤 기능이 실제 API-backed인가?
- 어떤 기능이 browser route에서 live API까지 실행되는가?
- 어떤 기능이 단순 manifest/structural coverage인가?
- 어떤 테스트와 CI가 그것을 증명하는가?
- 어떤 부분이 synthetic lab 한계로 남아 있는가?

---

## 1. 전역 원칙

### 반드시 지킬 것

- 실제 고객 자금, 실제 PII, 실제 금융망, 실제 KYC/AML provider, 실제 카드망, 실제 결제망을 절대 사용하지 않는다.
- 모든 외부 연계는 synthetic simulator, fixture, mock, local-only provider로 유지한다.
- 기존 원장 append-only 원칙을 깨지 않는다.
- finalized ledger row를 update/delete하지 않는다.
- 고위험 업무는 maker-checker, reason-required audit, structured error, authorization policy를 유지한다.
- `legacy-node-reference`는 oracle/reference로만 유지한다. target implementation으로 되돌리지 않는다.
- 이미 구현된 기능을 중복 구현하지 않는다.
- evidence 문서에는 “실제로 실행한 명령”과 “구조적으로만 검증한 것”을 구분한다.
- hosted CI가 막히거나 실패했으면 green이라고 쓰지 않는다. 실패/차단 원인을 명확히 기록한다.

### 금지 조건

- “production-ready”, “real banking ready”, “actual payment network ready” 같은 과장 문구를 문서에 추가하지 않는다.
- 실제 secret, 실제 token, 실제 인증서 private key, 실제 계좌/카드/주민번호 형태의 데이터를 커밋하지 않는다.
- generated evidence를 수동으로 조작해서 pass처럼 보이게 하지 않는다.
- structural test만 통과했는데 runtime/live 검증 완료로 표시하지 않는다.
- Playwright skip을 pass evidence로 포장하지 않는다.
- DTO/OpenAPI diff 실패를 임시로 ignore하지 않는다.
- bounded context의 auth failure audit을 core-banking만큼 검증하지 않고 complete로 표시하지 않는다.

---

## 2. 통합 테스트 목표

최종적으로 다음 명령이 통과해야 한다. 환경상 Docker, JDK, GitHub Actions runner, browser, network가 없어서 일부 실행이 불가능한 경우, 그 사실을 evidence에 명확히 기록하고 fallback을 “complete”로 표시하지 않는다.

### 로컬 필수 명령

```bash
npm ci
npm test
npm run validate:manifests
npm run packages:typecheck
npm run scripts:typecheck
npm run contracts:lint
npm run contracts:check-client
npm run contracts:check-events
npm run platform:validate
npm run security:posture-check
npm run formal:ledger
````

### Backend 테스트

```bash
npm run test:core-banking:unit
npm run test:core-banking:integration
npm run test:payment-service:unit
npm run test:payment-service:integration
npm run test:notification-service:unit
npm run test:notification-service:integration
npm run test:reporting-service:unit
npm run test:reporting-service:integration
```

### Frontend 테스트

```bash
npm run next:customer-web:typecheck
npm run next:staff-terminal:typecheck
npm run next:complaint-portal:typecheck
npm run next:ops-console:typecheck
npm run next:audit-console:typecheck
npm run next:fds-aml-console:typecheck
npm run next:admin-console:typecheck
npm run test:e2e
```

### 운영/증적 테스트

```bash
npm run load:synthetic
npm run postgres:backup-drill
npm run postgres:backup-drill:docker-live
npm run dr:multi-instance-drill
npm run analytics:fds-aml:test
npm run analytics:fds-aml
npm run data:dq-check
npm run governance:evidence
```

### CI 목표

`.github/workflows/ci.yml`에서 다음 job들이 실제 hosted GitHub Actions에서 green이어야 한다.

* node-and-manifests
* next-builds
* backend-core-banking
* backend-payment-service
* backend-notification-service
* backend-reporting-service
* backend-all-gradle
* platform-validation
* contracts-validation
* compose-platform-config
* playwright-manifest-e2e
* security-evidence
* formal-model

Hosted CI가 GitHub billing/spending-limit 등 외부 사유로 실행되지 않으면, 이를 “green”으로 표시하지 말고 `docs/test-evidence/ci-hosted-run-status.md`에 blocked evidence로 기록한다.

---

# Phase 0 — 현재 상태 재분석 및 작업 범위 고정

## 목표

현재 repo의 실제 상태를 다시 확인하고, 오래된 문서/증적/coverage 표현을 찾아낸다. 구현보다 먼저 “무엇이 이미 구현됐고 무엇이 아직 남았는지”를 정리한다.

## 구현 필요 내용

다음 파일을 먼저 읽고 요약하라.

* `README.md`
* `docs/implementation-coverage-matrix.md`
* `docs/codex/remaining-hardening-status.md`
* `docs/test-evidence/evidence-gap-report.md`
* `.github/workflows/ci.yml`
* `package.json`
* `services/core-banking/src/main/kotlin/**`
* `services/payment-service/src/main/kotlin/**`
* `services/notification-service/src/main/kotlin/**`
* `services/reporting-service/src/main/kotlin/**`
* `apps/customer-web/src/**`
* `apps/staff-terminal/src/**`
* `packages/api-client/src/**`
* `contracts/openapi/**`
* `contracts/events/**`
* `contracts/asyncapi/**`
* `screen-manifests/**`

그 후 `docs/codex/final-hardening-baseline.md`를 새로 만들거나 갱신하라.

이 문서에는 다음을 포함한다.

* 현재 구현 요약
* 아직 남은 hardening gap
* 이번 작업에서 고칠 범위
* 이번 작업에서 고치지 않을 범위
* synthetic-only boundary
* 테스트/증적 갱신 정책
* hosted CI와 local evidence 구분 정책

## 테스트 방법

```bash
npm test
npm run validate:manifests
npm run packages:typecheck
npm run scripts:typecheck
```

## 금지 조건

* 현재 구현을 과장해서 complete로 표시하지 않는다.
* 이전 generated evidence를 새로 실행한 것처럼 쓰지 않는다.
* 실제 외부 provider 연동 계획을 추가하지 않는다.

## 종료 조건

* `docs/codex/final-hardening-baseline.md`가 존재한다.
* 현재 작업 범위가 명확히 정리된다.
* 기존 테스트가 깨지지 않는다.

---

# Phase 1 — README 및 Coverage Matrix 정합성 정리

## 목표

문서와 실제 구현 상태를 일치시킨다. 특히 README의 오래된 “future improvements”와 coverage matrix의 status enum 불일치를 제거한다.

## 구현 필요 내용

### 1. README 정리

`README.md`에서 다음을 점검한다.

* 이미 구현된 것을 future improvement로 남겨둔 문구 제거
* “runtime persistence is in-memory” 같은 오래된 문구가 있으면 legacy Node runtime에만 해당한다고 명확히 표시하거나 삭제
* PostgreSQL/Flyway, Keycloak/JWKS, outbox, observability, formal verification, K8s/Helm, load/backup evidence가 이미 구현된 범위와 남은 범위를 구분
* synthetic-only boundary를 유지
* 실제 은행/실제 금융망 readiness로 오해될 표현 제거

### 2. Coverage Matrix 정규화

`docs/implementation-coverage-matrix.md`의 status enum과 row 값을 일치시킨다.

허용 status를 명확히 정의한다. 예시는 다음 중 하나를 선택한다.

```text
complete
api-backed-read
api-backed-command
browser-e2e-backed
live-keycloak-backed
route-backed-live-gated
manifest-only
partial
missing
not-applicable
```

또는 `route-backed-live-gated`를 제거하고 기존 status로 정규화한다.

각 row는 다음 기준으로 다시 분류한다.

* API-backed read인가?
* API-backed command인가?
* browser route에서 live API execution까지 검증됐는가?
* Keycloak/JWKS live propagation이 검증됐는가?
* structural/manifest-only인가?
* partial인 이유가 무엇인가?

### 3. Evidence 문서 정합성

다음 문서도 필요한 경우 갱신한다.

* `docs/test-evidence/evidence-gap-report.md`
* `docs/codex/remaining-hardening-status.md`
* `docs/test-evidence/api-backed-channel-smoke.md`
* `docs/test-evidence/ci-coverage-hardening.md`

## 테스트 방법

```bash
npm test
npm run validate:manifests
npm run evidence:refresh-check
```

문서만 바뀌어도 coverage matrix consistency를 검사하는 테스트가 없다면 추가하라.

예시:

```bash
node --test tests/coverageMatrixStatus.test.mjs
```

새 테스트는 다음을 확인해야 한다.

* matrix에서 사용된 status가 허용 enum 안에 있다.
* `missing`/`partial` row가 evidence상 complete로 포장되지 않는다.
* README가 금지된 outdated phrase를 포함하지 않는다.

## 금지 조건

* 문서 정리를 이유로 실제 구현 상태를 부풀리지 않는다.
* matrix status enum과 실제 row 값을 또 다르게 만들지 않는다.
* 오래된 문구를 단순히 다른 문서로 옮기지 않는다.

## 종료 조건

* README와 coverage matrix가 충돌하지 않는다.
* coverage matrix status enum 검증 테스트가 통과한다.
* `npm test`, `validate:manifests`, `evidence:refresh-check`가 통과한다.

---

# Phase 2 — Hosted CI Green Run 확보 및 CI Evidence 정리

## 목표

현재 local evidence 중심의 상태를 hosted GitHub Actions 기준으로도 검증 가능하게 만든다.

## 구현 필요 내용

### 1. CI workflow 점검

`.github/workflows/ci.yml`을 검토하고 다음 job들이 빠짐없이 있는지 확인한다.

* Node/reference/manifests
* Next.js channel builds
* core-banking unit/integration
* payment-service unit/integration
* notification-service unit/integration
* reporting-service unit/integration
* aggregate Gradle unit
* platform validation
* contract validation
* docker compose config
* Playwright E2E
* security evidence
* formal model

### 2. CI self-check script 강화

`npm run ci:check-workflow`가 다음을 검증하도록 보강한다.

* 필수 job 존재
* 필수 npm script 존재
* 각 service의 unit/integration job 존재
* contract/platform/formal/security job 존재
* CI에서 static-only formal이 허용되지 않는지 확인
* security evidence가 skip-only로 pass되지 않는지 확인

### 3. Hosted CI evidence 문서 추가

`docs/test-evidence/ci-hosted-run-status.md`를 만든다.

내용:

* 최신 commit SHA
* GitHub Actions run URL
* 각 job 결과
* 실패/차단된 job이 있다면 원인
* billing/spending-limit 등 외부 차단이면 green claim 금지
* local fallback command와 hosted CI 결과 구분

### 4. GitHub Actions 차단 대응

Codex가 직접 hosted CI를 실행할 수 없는 경우:

* workflow_dispatch가 가능한지 문서화
* 사용자가 수동 실행할 체크리스트 작성
* CI가 차단됐으면 blocked evidence로 남김
* 절대 green으로 표시하지 않음

## 테스트 방법

```bash
npm run ci:check-workflow
npm test
npm run contracts:lint
npm run contracts:check-client
npm run contracts:check-events
npm run platform:validate
```

가능하면 hosted GitHub Actions에서 전체 workflow를 실행하고 결과 URL을 evidence 문서에 기록한다.

## 금지 조건

* local pass를 hosted CI green으로 표시하지 않는다.
* hosted CI가 billing 문제로 막힌 상태를 성공으로 표시하지 않는다.
* 실패한 job을 임시로 제거해서 green으로 만들지 않는다.
* 테스트 시간을 줄이기 위해 core integration test를 무단 skip하지 않는다.

## 종료 조건

* CI workflow self-check가 통과한다.
* hosted CI run URL과 결과가 문서화된다.
* hosted CI가 불가능하면 blocked status가 명확히 기록된다.
* local fallback evidence와 hosted evidence가 분리된다.

---

# Phase 3 — Customer Web / Staff Terminal Live API Execution Evidence

## 목표

`customer-web`과 `staff-terminal`의 주요 route가 단순 route shell이나 manifest renderer가 아니라, 실제 Spring API와 통신하여 command/read workflow를 실행한다는 증거를 만든다.

## 구현 필요 내용

### 1. Customer Web live route flow

다음 route flow를 구현 또는 보강한다.

```text
/signup
/login
/accounts
/accounts/[accountId]
/transfers/new
/transfers/[resultId]
/complaints
/complaints/[caseId]
/security
/cards
/cards/[cardId]
/loans
/payments
/notifications
```

최소 live API 시나리오:

1. synthetic customer signup
2. login
3. session 확인
4. account list 조회
5. account detail/history 조회
6. internal recipient lookup
7. transfer submit
8. transfer result 조회
9. idempotency replay 확인
10. insufficient balance 또는 held/blocked 상태 확인
11. complaint intake
12. complaint detail/status 확인
13. access history 조회

### 2. Staff Terminal live route flow

다음 route flow를 구현 또는 보강한다.

```text
/tx/[transactionCode]
/customers/[customerId]
/accounts/[accountId]
/approvals
/audit
/workflows/[businessReferenceId]
```

최소 live API 시나리오:

1. reason-required customer lookup
2. missing reason structured error
3. account inquiry
4. transaction inquiry
5. fee waiver request
6. transaction correction request
7. approval inbox 조회
8. maker self-approval rejection
9. checker approval
10. resulting reversal/refund ledger transaction 확인
11. audit event 확인
12. workflow timeline 확인

### 3. Playwright live API 테스트 추가

환경변수가 설정된 경우 live API 테스트를 실행한다.

예시 환경변수:

```bash
BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:8081
BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:8085
BANKING_LAB_E2E_CUSTOMER_WEB_URL=http://127.0.0.1:3001
BANKING_LAB_E2E_STAFF_TERMINAL_URL=http://127.0.0.1:3002
```

새 테스트 파일 예시:

```text
apps/customer-web/e2e/customer-web-live-api-flow.spec.ts
apps/staff-terminal/e2e/staff-terminal-live-api-flow.spec.ts
```

테스트는 env가 없으면 명확히 skip하되, skip을 pass evidence로 포장하지 않는다.

### 4. Evidence 문서 추가

다음 문서를 작성한다.

```text
docs/test-evidence/live-route-api-execution.md
docs/test-evidence/generated/live-route-api-execution.json
```

내용:

* 실행 환경
* API base URL
* Keycloak 사용 여부
* 실행한 route 목록
* 실행한 command/read 목록
* 성공/실패/skip 목록
* 생성된 business reference id
* audit event id
* ledger transaction id
* approval id
* structured error code
* idempotency replay 여부

## 테스트 방법

```bash
npm run next:customer-web:typecheck
npm run next:staff-terminal:typecheck
npm run packages:typecheck
npm run test:e2e -- apps/customer-web/e2e/customer-web-live-api-flow.spec.ts
npm run test:e2e -- apps/staff-terminal/e2e/staff-terminal-live-api-flow.spec.ts
```

가능하면 Compose stack을 띄워 다음도 실행한다.

```bash
docker compose --profile platform up -d postgres keycloak redpanda temporal core-banking
npm run test:e2e -- apps/customer-web/e2e/customer-web-live-api-flow.spec.ts
npm run test:e2e -- apps/staff-terminal/e2e/staff-terminal-live-api-flow.spec.ts
```

## 금지 조건

* fixture-only UI 테스트를 live API execution으로 표시하지 않는다.
* API base URL이 없어서 skip된 테스트를 pass로 표시하지 않는다.
* simulator token을 사용할 경우 명시적 dev/test opt-in 없이 사용하지 않는다.
* customer token으로 다른 customer resource에 접근하도록 구현하지 않는다.
* staff reason-required policy를 우회하지 않는다.

## 종료 조건

* customer-web live API route flow evidence가 생성된다.
* staff-terminal live API route flow evidence가 생성된다.
* 주요 route가 loading/success/error/replay/held/blocked/authorization-denied 상태를 표시한다.
* Playwright test가 env 기반으로 정상 실행 또는 명확한 skip evidence를 남긴다.

---

# Phase 4 — OpenAPI DTO-Level Diffing 및 Event Envelope Runtime Validation

## 목표

현재 structural contract gate를 넘어서, 실제 구현 DTO/API client/OpenAPI가 drift되지 않도록 검증한다. 또한 live event producer가 AsyncAPI/event schema envelope를 실제로 만족하는지 runtime integration test로 검증한다.

## 구현 필요 내용

### 1. DTO-level OpenAPI diffing

다음 중 하나의 방식을 선택하라.

#### Option A — Springdoc 기반

각 Spring service에 springdoc을 추가하고 `/v3/api-docs` 또는 test context에서 OpenAPI JSON을 생성한다.

대상:

* core-banking
* payment-service
* notification-service
* reporting-service

생성 결과:

```text
docs/test-evidence/generated/openapi/core-banking.generated.json
docs/test-evidence/generated/openapi/payment-service.generated.json
docs/test-evidence/generated/openapi/notification-service.generated.json
docs/test-evidence/generated/openapi/reporting-service.generated.json
```

검증:

* generated operationId가 checked-in contract와 일치
* path/method가 누락되지 않음
* response/request schema가 generic placeholder만으로 남지 않음
* structured error response가 선언됨

#### Option B — DTO Schema Export 기반

Springdoc 도입이 부담되면 Kotlin DTO metadata 또는 Jackson schema/export script로 최소 schema를 생성하고 checked-in OpenAPI와 비교한다.

### 2. Contract diff script 추가

새 script를 추가한다.

```text
scripts/check-openapi-generated-diff.ts
```

package script:

```json
"contracts:diff-openapi": "node --experimental-strip-types scripts/check-openapi-generated-diff.ts"
```

검증 실패 조건:

* checked-in OpenAPI에 없는 runtime path 발견
* runtime에는 없는 checked-in path 발견
* operationId 불일치
* request/response DTO 이름 불일치
* structured error metadata 누락
* synthetic-only boundary metadata 누락

### 3. Event envelope runtime validation

현재 `contracts/events/*.schema.json`, AsyncAPI backbone, producer envelope metadata가 있다. 여기에 runtime validation을 추가한다.

대상 event producer:

* core-banking outbox
* payment-service publisher
* notification-service consumer/producer 해당 시
* reporting-service publisher

추가할 것:

```text
scripts/validate-event-envelope-runtime.ts
```

또는 각 service integration test에서 JSON Schema validator 사용.

검증할 필드:

```json
{
  "eventId": "...",
  "eventType": "...",
  "aggregateType": "...",
  "aggregateId": "...",
  "occurredAt": "...",
  "sourceService": "...",
  "syntheticOnly": true,
  "schemaVersion": "...",
  "payload": {}
}
```

검증해야 할 것:

* `syntheticOnly=true`
* `sourceService` 존재
* `eventType` schema와 실제 payload 일치
* Kafka/Redpanda record header와 JSON body envelope 불일치 없음
* broker ack 이후에만 PUBLISHED 처리
* failed/dead-letter 상태도 schema에 맞음
* real network/provider 사용 플래그가 false임

### 4. CI 연결

`.github/workflows/ci.yml`의 contracts-validation job에 추가한다.

```bash
npm run contracts:diff-openapi
npm run contracts:validate-runtime-events
```

## 테스트 방법

```bash
npm run contracts:lint
npm run contracts:check-client
npm run contracts:check-events
npm run contracts:diff-openapi
npm run contracts:validate-runtime-events
npm run test:payment-service:integration
npm run test:notification-service:integration
npm run test:reporting-service:integration
npm run test:core-banking:integration
```

## 금지 조건

* OpenAPI를 통과시키기 위해 DTO를 `object` 또는 `any`로 뭉개지 않는다.
* runtime event validation 실패를 warning으로 낮추지 않는다.
* event schema에 맞추기 위해 synthetic-only control field를 제거하지 않는다.
* checked-in contract를 실제 구현과 무관하게 대충 수정하지 않는다.

## 종료 조건

* `contracts:diff-openapi`가 통과한다.
* runtime event envelope validation이 integration test에서 통과한다.
* contracts-validation CI job에 새 gate가 포함된다.
* 관련 evidence 문서가 갱신된다.

---

# Phase 5 — 고객센터 상담사용 전산 구현

## 목표

현재 staff-terminal이 상담 업무 일부를 대체하고 있지만, 고객센터 상담사용 전산으로 보기에는 상담 이력/상담 메모/통화 사유/후처리 workflow가 부족하다. 이를 별도 `call-center-console` 앱으로 만들거나, 최소한 staff-terminal 내부에 API-backed 상담 업무 패널을 추가한다.

가능하면 새 앱을 만든다.

```text
apps/call-center-console
```

현실적으로 범위가 부담되면 staff-terminal에 다음 화면을 추가한다.

```text
CALL-101 상담 고객 검색
CALL-102 상담 세션 상세
CALL-103 상담 메모 등록
CALL-104 후처리 업무 생성
CALL-105 상담 이력 조회
CALL-106 상담 escalation/민원 전환
```

## 구현 필요 내용

### 1. DB migration

새 migration을 추가한다.

예시:

```sql
call_center_cases
call_center_interactions
call_center_notes
call_center_aftercall_tasks
call_center_escalations
call_center_access_audit
```

최소 필드:

```text
case_id
interaction_id
customer_id
account_id nullable
channel: PHONE | CHAT | EMAIL | BRANCH | WEB
contact_reason_code
status: OPEN | AFTERCALL | ESCALATED | CLOSED
created_by
created_by_role
assigned_to
reason
started_at
ended_at
metadata_json
synthetic_only
```

상담 메모는 free-form이므로 다음 통제를 넣는다.

* real PII 금지
* 메모 payload masking
* audit에는 원문 메모를 복사하지 않음
* note body는 synthetic fixture만 사용
* 민감정보 pattern 검사 또는 redaction helper 적용

### 2. Spring API

`core-banking` 안에 `callcenter` package를 만들거나, 별도 service로 분리한다. 이번 phase에서는 core-banking 내부 bounded module로 충분하다.

API 예시:

```text
GET  /api/staff/call-center/customers/search?query=&reason=
POST /api/staff/call-center/interactions
GET  /api/staff/call-center/interactions/{interactionId}
POST /api/staff/call-center/interactions/{interactionId}/notes
POST /api/staff/call-center/interactions/{interactionId}/aftercall-tasks
POST /api/staff/call-center/interactions/{interactionId}/escalations
POST /api/staff/call-center/interactions/{interactionId}/close
GET  /api/staff/call-center/customers/{customerId}/history?reason=
```

### 3. 상담 workflow

구현할 흐름:

1. 상담원이 고객 검색을 한다.
2. reason이 없으면 거부된다.
3. 상담 세션을 시작한다.
4. 계좌/거래/민원/FDS 관련 context를 조회한다.
5. 상담 메모를 남긴다.
6. 후처리 task를 만든다.
7. 필요하면 complaint case로 escalation한다.
8. 상담 세션을 종료한다.
9. audit/access history에 상담 조회/메모/후처리/종료가 남는다.

### 4. 권한 정책

허용 role 예시:

```text
CALL_CENTER_AGENT
CALL_CENTER_MANAGER
BRANCH_STAFF
COMPLAINT_HANDLER
COMPLIANCE_MANAGER
AUDITOR read-only
```

정책:

* 상담원은 고객 검색/상담 시작/메모/후처리 가능
* 상담원은 PII unmask 불가
* manager는 escalation/재배정 가능
* auditor는 read-only
* compliance는 감사/위반 점검 가능
* 모든 조회는 reason-required
* customer ownership이 아니라 staff reason policy 기반

### 5. UI 구현

Option A: 새 앱

```text
apps/call-center-console
```

필수 페이지:

```text
/
 /customers/search
 /customers/[customerId]
 /interactions/[interactionId]
 /aftercall
 /history
```

Option B: staff-terminal 내부

```text
screen-manifests/staff-terminal/CALL-101.customer-search.json
screen-manifests/staff-terminal/CALL-102.interaction-detail.json
screen-manifests/staff-terminal/CALL-103.note-entry.json
screen-manifests/staff-terminal/CALL-104.aftercall-task.json
screen-manifests/staff-terminal/CALL-105.history.json
screen-manifests/staff-terminal/CALL-106.escalation.json
```

### 6. API client

`packages/api-client/src/index.ts`에 call-center client method를 추가한다.

예시:

```ts
searchCallCenterCustomers(...)
startCallCenterInteraction(...)
getCallCenterInteraction(...)
addCallCenterNote(...)
createAftercallTask(...)
escalateCallCenterInteraction(...)
closeCallCenterInteraction(...)
listCallCenterHistory(...)
```

### 7. Evidence 문서

```text
docs/test-evidence/call-center-console.md
docs/test-evidence/generated/call-center-console.json
```

포함 내용:

* 구현한 화면/API/DB
* role policy
* reason-required audit
* 상담 메모 masking/redaction
* escalation workflow
* complaint 전환 여부
* 테스트 명령 결과
* synthetic-only boundary

## 테스트 방법

### Backend integration test

새 테스트:

```text
services/core-banking/src/integrationTest/kotlin/lab/banking/core/callcenter/CallCenterWorkflowIntegrationTest.kt
```

검증 항목:

* reason 없으면 고객 검색 거부
* 상담 세션 시작 성공
* 상담 메모 등록 성공
* 상담 메모 audit에 원문 복사 안 됨
* PII-like payload redaction
* 후처리 task 생성
* escalation 생성
* complaint case 전환 시 source reference 남김
* 상담 종료 후 상태 전이
* auditor read-only
* unauthorized role 거부
* audit event 생성
* customer access history에 반영

### Frontend test

새 앱이면:

```bash
npm run next:call-center-console:typecheck
npm run test:e2e -- apps/call-center-console/e2e/call-center-console.spec.ts
```

staff-terminal 내부면:

```bash
npm run next:staff-terminal:typecheck
npm run test:e2e -- apps/staff-terminal/e2e/call-center-workflow.spec.ts
```

### 공통

```bash
npm run validate:manifests
npm run packages:typecheck
npm run test:core-banking:integration -- --tests lab.banking.core.callcenter.CallCenterWorkflowIntegrationTest
```

## 금지 조건

* 실제 상담 녹취, 실제 전화번호, 실제 PII를 넣지 않는다.
* 상담 메모 원문을 audit payload에 복사하지 않는다.
* 상담원에게 PII unmask 권한을 기본 부여하지 않는다.
* 상담 업무를 단순 고객조회 화면으로 끝내지 않는다.
* 후처리/이관/escalation 없이 “상담 전산 complete”로 표시하지 않는다.

## 종료 조건

* 상담 검색, 세션 시작, 메모, 후처리, escalation, 종료가 API-backed로 동작한다.
* reason-required audit이 남는다.
* 상담 메모 masking/redaction 테스트가 통과한다.
* 고객센터 화면 또는 staff-terminal CALL 화면이 typecheck/E2E를 통과한다.
* coverage matrix에 고객센터 상담사용 전산 row가 추가된다.
* evidence 문서가 갱신된다.

---

# Phase 6 — 최종 Evidence Pack 및 Demo Flow 정리

## 목표

모든 phase가 끝난 뒤, 구현 상태를 한눈에 보여주는 최종 증적과 시연 플로우를 만든다.

## 구현 필요 내용

### 1. Final scorecard 작성

`docs/test-evidence/final-hardening-scorecard.md`를 만든다.

항목별로 다음을 평가한다.

* 원장 무결성
* idempotency/reversal/adjustment
* 직원 통합단말
* 고객 채널
* 전자민원
* 고객센터 상담 전산
* FDS/AML 실질 적용
* FDS/AML admin/workflow/analytics
* 예금/수수료/이자
* 카드/대출
* payment/notification/reporting bounded context
* 보안/JWKS/Keycloak
* 감사/내부통제
* 운영/DR/증적
* K8s/Helm/Argo
* 문서 정합성

각 항목은 다음을 포함한다.

```text
score
status
implemented evidence
remaining limitation
next step
```

### 2. Demo script 갱신

`docs/demo-scenarios/demo-video-script.md`를 갱신한다.

5~10분 시연 플로우:

1. README에서 synthetic-only boundary 설명
2. customer signup/login
3. customer account/transfer
4. staff reason-required lookup
5. fee waiver 또는 transaction correction request
6. checker approval
7. resulting ledger reversal/refund 확인
8. complaint intake/escalation
9. FDS held transfer release/block
10. ops EOD/reconciliation
11. audit hash-chain/access history
12. call center workflow
13. evidence pack/CI/contract/formal 확인

### 3. 최종 명령

다음 명령을 실행하고 결과를 문서화한다.

```bash
npm ci
npm test
npm run validate:manifests
npm run packages:typecheck
npm run scripts:typecheck
npm run contracts:lint
npm run contracts:check-client
npm run contracts:check-events
npm run contracts:diff-openapi
npm run contracts:validate-runtime-events
npm run platform:validate
npm run security:posture-check
npm run formal:ledger
npm run evidence:pack
```

가능하면 다음도 실행한다.

```bash
npm run test:core-banking:integration
npm run test:payment-service:integration
npm run test:notification-service:integration
npm run test:reporting-service:integration
npm run load:synthetic
npm run postgres:backup-drill:docker-live
npm run dr:multi-instance-drill
```

## 테스트 방법

```bash
npm run evidence:pack
npm run evidence:refresh-check
npm test
```

가능하면 hosted GitHub Actions 전체 workflow를 실행하고 결과 URL을 scorecard에 넣는다.

## 금지 조건

* 최종 점수를 과장하지 않는다.
* live 실행이 안 된 항목을 live-backed로 표시하지 않는다.
* synthetic-only limitation을 숨기지 않는다.
* 남은 gap을 삭제하지 않는다.

## 종료 조건

* `docs/test-evidence/final-hardening-scorecard.md`가 생성된다.
* `docs/demo-scenarios/demo-video-script.md`가 최신 구현과 일치한다.
* coverage matrix, README, evidence-gap-report가 서로 충돌하지 않는다.
* 전체 핵심 테스트가 통과하거나, 실행 불가 항목은 명확한 blocked evidence를 남긴다.
* 최종 문서에서 production-ready claim 없이 synthetic banking lab 완성도를 정확히 설명한다.

---

## 최종 완료 조건

이번 작업은 다음이 모두 만족될 때 완료된다.

1. README와 coverage matrix의 오래된 문구/status 불일치가 없다.
2. hosted GitHub Actions 결과가 green이거나, 외부 차단 사유가 명확히 문서화되어 있다.
3. customer-web과 staff-terminal의 live API route execution evidence가 있다.
4. OpenAPI DTO-level diffing gate가 있다.
5. event envelope runtime validation gate가 있다.
6. 고객센터 상담사용 전산 기능이 API-backed로 추가되어 있다.
7. 상담 이력, 상담 메모, 후처리, escalation, audit이 구현되어 있다.
8. 모든 새 기능은 synthetic-only boundary를 지킨다.
9. 모든 새 고위험 command는 reason-required, authorization, maker-checker, audit, structured error를 갖는다.
10. final scorecard와 demo script가 최신 상태다.
11. `npm test`, `validate:manifests`, `packages:typecheck`, `scripts:typecheck`, `contracts:*`, `formal:ledger`, 관련 backend/frontend 테스트가 통과한다.
12. 남은 한계는 삭제하지 않고 정확히 문서화한다.
