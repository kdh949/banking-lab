# Codex Implementation Prompt — Banking Lab 부족분 구현

너는 `banking-lab` 저장소를 맡은 시니어 금융 시스템 엔지니어다.

현재 저장소는 synthetic core banking lab이며, 실제 고객 돈, 실제 개인정보, 실제 지급결제망, 실제 KYC/금융기관 API를 절대 다루지 않는다. 모든 데이터는 synthetic data와 simulator만 사용한다.

이번 작업의 목표는 기존 구현을 기반으로 부족한 부분을 보강하여, 단순 은행 앱 클론이 아니라 **계정계·채널계·운영계·감사·전자민원·FDS/AML·정산·인증·장애복구·증적까지 갖춘 은행 업무 플랫폼 포트폴리오**로 완성도를 높이는 것이다.

절대 원칙:

1. 실제 고객 자금, 실제 개인정보, 실제 금융망, 실제 KYC, 실제 외부 금융기관 API를 사용하지 않는다.
2. Node.js reference runtime은 target-path 구현체가 아니다. 기존 Node oracle/reference는 회귀 비교용으로만 유지한다.
3. 신규 비즈니스 구현은 Kotlin/Spring Boot, PostgreSQL, Next.js, TypeScript, Keycloak, Temporal, Redpanda/Outbox, OpenTelemetry 계층을 기준으로 작성한다.
4. 기존 Node retirement gate, parity, evidence, security, passkey, final review 관련 테스트를 깨지 않는다.
5. 모든 고위험 업무는 maker-checker, audit log, structured error, idempotency, authorization policy를 갖춰야 한다.
6. 모든 새로운 화면은 screen manifest, Next.js renderer, TypeScript api-client, Spring API, PostgreSQL state, Playwright 또는 Spring integration test 중 어디까지 구현됐는지 명확히 드러나야 한다.
7. 구현 후 문서와 증적을 반드시 갱신한다.

---

## 1. 먼저 현재 코드베이스를 재분석하라

작업을 시작하기 전에 다음 파일을 반드시 읽고 현재 상태를 요약하라.

* `README.md`
* `CODEX_FULL_REWRITE_PLAN.md`
* `docs/migration/node-retirement-gate.json`
* `docs/test-evidence/evidence-gap-report.md`
* `docs/test-evidence/goal-completion-audit.md`
* `docs/architecture/frontend-channels-manifest-shells.md`
* `packages/screen-engine/src/types.ts`
* `packages/screen-engine/src/manifest.ts`
* `services/core-banking/src/main/kotlin/**`
* `apps/*/src/**`
* `screen-manifests/**`
* `.github/workflows/ci.yml`
* `docker-compose.yml`

그다음 `docs/implementation-coverage-matrix.md`를 새로 만들거나 갱신하라.

이 문서는 최소한 다음 컬럼을 가져야 한다.

| Area | Feature/Screen | Manifest | Next UI | API Client | Spring API | PostgreSQL | Keycloak/AuthZ | Audit | Maker-checker | E2E/Integration Test | Evidence | Status |
| ---- | -------------- | -------- | ------- | ---------- | ---------- | ---------- | -------------- | ----- | ------------- | -------------------- | -------- | ------ |

Status 값은 다음 중 하나로 통일한다.

* `complete`
* `api-backed`
* `manifest-only`
* `partial`
* `missing`
* `not-applicable`

현재 구현을 과장하지 말고 실제 코드 기준으로 분류하라.

---

## 2. 구현 목표

이번 작업의 핵심 목표는 다음 6가지다.

1. 직원 통합단말의 핵심 command 업무를 실제 API-backed 업무로 확장한다.
2. 예금상품, 수수료, 이자 계산 모듈을 추가하여 계정계 깊이를 보강한다.
3. FDS/AML 분석계를 Python/DuckDB 기반으로 추가한다.
4. TLA+ 또는 동등한 formal ledger verification을 실제 실행 가능한 CI gate로 강화한다.
5. Kubernetes/Helm 기반 배포 골격을 추가한다.
6. 부하테스트, 백업/복구, 운영 증적을 추가한다.

단, 한 번에 모든 것을 무리하게 끝내려 하지 말고, Phase별로 안전하게 구현하라. 각 Phase마다 테스트와 문서 갱신을 포함한다.

---

## 3. Phase A — 구현 범위 매트릭스와 문서 정리

### 해야 할 일

1. `docs/implementation-coverage-matrix.md` 작성.
2. README의 현재 상태와 실제 코드 상태가 충돌하는 부분이 있으면 정리.
3. `docs/test-evidence/evidence-gap-report.md`에 남은 갭을 최신화.
4. `docs/demo-scenarios/demo-video-script.md`가 있다면 현재 구현 기준으로 데모 시나리오를 갱신.
5. 현재 화면/기능을 다음 등급으로 분류.

   * manifest-only
   * read-model backed
   * command backed
   * approval backed
   * Keycloak backed
   * E2E backed

### 완료 조건

* coverage matrix가 모든 주요 앱을 포함해야 한다.

  * customer-web
  * staff-terminal
  * complaint-portal
  * ops-console
  * audit-console
  * fds-aml-console
  * admin-console
* 구현되지 않은 기능을 완료된 것처럼 표시하지 않는다.
* 기존 retirement/evidence 테스트를 깨지 않는다.

---

## 4. Phase B — 직원 통합단말 핵심 command 10개 API-backed 구현

직원 통합단말의 설득력을 높이기 위해 다음 업무를 실제 Spring API, PostgreSQL, audit, approval, Next UI, Playwright 또는 integration test까지 연결하라.

우선순위 업무:

1. 계좌 지급정지 요청
2. 계좌 지급정지 해제 요청
3. 이체한도 변경 요청
4. 고객 연락처 변경
5. 고객 주소 변경
6. 고객 KYC 재확인 요청
7. 수수료 면제 요청
8. 수수료 면제 승인/반려
9. 거래 정정 요청
10. 거래 정정 승인 후 reversal 또는 adjustment posting

### 구현 요구사항

각 업무는 가능한 한 동일한 패턴을 사용한다.

```text
screen manifest
→ Next.js staff-terminal panel
→ shared TypeScript api-client
→ Spring Controller
→ Spring Service
→ PostgreSQL migration/table
→ audit_events append
→ maker-checker approval
→ structured error
→ integration test
→ Playwright smoke
→ evidence update
```

### 공통 정책

1. 직원이 고객정보, 계좌정보, 거래정보를 조회하거나 변경할 때는 업무 사유가 필요하다.
2. PII 또는 민감정보는 기본 마스킹한다.
3. privileged unmask는 manager/checker 권한 및 time-boxed audit evidence가 있어야 한다.
4. maker와 checker는 같을 수 없다.
5. 권한 없는 actor는 structured `AUTHORIZATION_POLICY_VIOLATION`을 받아야 한다.
6. 잘못된 workflow 상태는 structured `WORKFLOW_STATE_VIOLATION`을 받아야 한다.
7. ledger posting이 필요한 정정 업무는 반드시 double-entry invariant를 만족해야 한다.
8. ledger source row는 update/delete하지 않는다. 정정은 reversal 또는 adjustment로만 처리한다.
9. idempotency key가 필요한 command는 같은 key 재시도 시 동일 결과를 반환해야 한다.
10. 실패한 command는 원장, 승인, audit state에 안전하게 no-side-effect 또는 명시적 failure evidence를 남겨야 한다.

### DB 예시

필요 시 Flyway migration을 추가하라.

예상 테이블:

```text
account_hold_requests
account_limit_change_requests
customer_profile_change_requests
customer_kyc_review_requests
fee_waiver_requests
transaction_correction_requests
```

각 request table은 최소한 다음 필드를 가져야 한다.

```text
request_id
business_reference_id
target_customer_id
target_account_id nullable
target_transaction_id nullable
requested_by
requested_role
reason
status
approval_id nullable
created_at
updated_at
executed_at nullable
metadata_json
```

### 테스트 요구사항

각 업무별로 최소한 다음을 검증하라.

1. 정상 요청 생성.
2. maker self-approval 거부.
3. checker 승인 후 상태 변경.
4. 권한 없는 사용자 거부.
5. reason 누락 거부.
6. 중복 승인 또는 잘못된 상태 전이 거부.
7. audit event 생성.
8. 필요한 경우 ledger posting 생성 및 invariant 유지.
9. Playwright에서 staff-terminal 화면을 통해 최소 smoke 검증.

---

## 5. Phase C — 예금상품, 수수료, 이자 계산 모듈 추가

현재 원장은 강하지만 은행 계정계 업무 깊이가 부족하다. 예금상품, 수수료, 이자 계산을 추가하라.

### 구현 대상

1. 예금상품 카탈로그
2. 계좌의 상품 가입 상태
3. 수수료 정책
4. 수수료 면제 정책
5. 일별 또는 월별 이자 계산
6. 이자 지급 posting
7. 수수료 posting
8. 상품/수수료/이자 parameter 변경에 대한 maker-checker 승인

### DB 예시

Flyway migration을 추가하라.

```text
deposit_products
product_interest_rate_versions
account_product_enrollments
fee_policies
fee_policy_versions
interest_accruals
interest_posting_batches
fee_posting_batches
```

### API 예시

```text
GET  /api/products/deposits
POST /api/staff/products/deposits/{productId}/rate-change-requests
POST /api/staff/fee-policies/{policyId}/change-requests
POST /api/ops/interest-accruals/run
POST /api/ops/interest-posting-batches
POST /api/ops/fee-posting-batches
```

### 원장 정책

1. 이자 지급은 ledger transaction으로 표현한다.
2. 수수료 부과도 ledger transaction으로 표현한다.
3. 수수료 환급은 reversal 또는 adjustment로 처리한다.
4. 상품 금리 변경은 과거 거래를 변형하지 않고 effective date 기반 versioning으로 처리한다.
5. 이자 계산 결과는 재현 가능해야 한다.

### 화면

다음 화면을 manifest와 Next shell에 추가하거나 API-backed로 승격하라.

* 상품 목록
* 상품 상세
* 상품 금리 변경 요청
* 수수료 정책 조회
* 수수료 정책 변경 요청
* 이자 계산 실행
* 이자 지급 배치 결과
* 수수료 배치 결과

### 테스트 요구사항

1. 상품 가입 계좌에 대해 이자 accrual 생성.
2. 이자 지급 batch가 balanced ledger posting을 생성.
3. 동일 batch idempotency key 재시도 시 중복 posting 없음.
4. 금리 변경은 maker-checker 승인 전에는 적용되지 않음.
5. 수수료 정책 변경도 maker-checker 승인 필요.
6. closed business date에는 posting 불가.
7. Playwright 또는 Spring integration test로 최소 1개 end-to-end 시나리오 검증.

---

## 6. Phase D — Python/DuckDB 기반 FDS/AML 분석계 추가

현재 FDS/AML은 rule/case workflow 중심이다. 이를 데이터 분석 파이프라인으로 보강하라.

### 목표

`analytics/aml-fds-python` 디렉터리를 만들고, synthetic ledger/customer transfer data를 기반으로 feature generation, rule score, anomaly score를 생성한다.

### 구조

```text
analytics/aml-fds-python/
├─ pyproject.toml
├─ README.md
├─ src/
│  ├─ feature_store.py
│  ├─ rules.py
│  ├─ scoring.py
│  ├─ model.py
│  ├─ mart.py
│  └─ api.py
├─ tests/
│  ├─ test_feature_store.py
│  ├─ test_rules.py
│  └─ test_scoring.py
└─ sample-data/
```

### 기능

1. synthetic transaction/customer data를 DuckDB mart로 적재.
2. velocity feature 생성.
3. first beneficiary feature 생성.
4. high amount anomaly feature 생성.
5. customer risk grade feature 생성.
6. rule-based score 계산.
7. 간단한 sklearn anomaly model 또는 deterministic anomaly scoring 구현.
8. 결과를 JSON/CSV로 export.
9. Spring FDS/AML service 또는 FDS/AML console에서 scoring result를 조회할 수 있도록 최소 연계 제공.

실제 Python service를 항상 띄우는 방식이 부담되면, 1차 구현은 batch CLI + generated artifact 방식으로 구현해도 된다.

예상 명령:

```bash
npm run analytics:fds-aml
```

또는

```bash
python -m aml_fds.scoring --input sample-data/transactions.csv --output docs/test-evidence/generated/fds-aml-analytics.json
```

### 테스트 요구사항

1. Python unit test 통과.
2. deterministic sample data 기준 score 결과가 snapshot 또는 expected value와 일치.
3. high amount/new beneficiary/velocity 케이스가 rule에 걸림.
4. 정상 케이스는 낮은 risk score.
5. generated artifact가 `docs/test-evidence/generated/fds-aml-analytics.json`에 저장.
6. FDS/AML console 또는 docs evidence에서 score 결과 확인 가능.

---

## 7. Phase E — Formal ledger verification 강화

현재 formal check가 static artifact 수준으로 fallback될 수 있다면 부족하다. 실제 TLC 또는 동등한 model checker가 실행되는 경로를 추가하라.

### 구현 대상

```text
formal/
├─ Ledger.tla
├─ Ledger.cfg
├─ Idempotency.tla
├─ Idempotency.cfg
└─ README.md
```

### 검증할 invariant

1. 모든 거래의 debit/credit 합계는 0.
2. 한 idempotency key는 하나의 business result만 만든다.
3. available balance는 음수가 되지 않는다.
4. reversal은 원거래를 정확히 반대로 만든다.
5. closed business date에는 posting이 생기지 않는다.
6. failed/held command는 ledger posting을 만들지 않는다.
7. adjustment는 승인된 업무 reference를 가져야 한다.

### 스크립트

`npm run formal:ledger`가 실제 model checker 실행을 시도해야 한다.

권장 동작:

1. 로컬 `tlc` 명령이 있으면 사용.
2. 없으면 Docker 기반 TLA+ 이미지 사용.
3. 둘 다 없으면 기본적으로 실패한다.
4. 단, 개발 편의를 위해 명시적 환경변수 `BANKING_LAB_ALLOW_FORMAL_STATIC_ONLY=true`가 있을 때만 static artifact check를 허용한다.
5. CI에서는 static-only를 허용하지 않는다.

### 결과물

```text
docs/test-evidence/generated/formal-ledger-tlc-result.json
docs/test-evidence/formal-ledger-verification.md
```

### 완료 조건

* `npm run formal:ledger`가 실제 model checker 실행 결과를 남긴다.
* static-only fallback은 CI pass 조건으로 인정하지 않는다.
* 실패 invariant가 있을 경우 CI가 실패해야 한다.

---

## 8. Phase F — Kubernetes/Helm 배포 골격 추가

Docker Compose는 유지하되, Kubernetes/Helm 기반 배포 가능성을 보여주는 최소 구조를 추가하라.

### 구현 대상

```text
infra/
├─ k8s/
│  ├─ namespace.yaml
│  ├─ core-banking-deployment.yaml
│  ├─ core-banking-service.yaml
│  ├─ core-banking-temporal-worker-deployment.yaml
│  ├─ postgres-statefulset.yaml
│  ├─ keycloak-deployment.yaml
│  ├─ redpanda-deployment.yaml
│  ├─ temporal-deployment.yaml
│  ├─ configmap.yaml
│  ├─ secret.example.yaml
│  └─ network-policy.yaml
├─ helm/
│  └─ banking-lab/
│     ├─ Chart.yaml
│     ├─ values.yaml
│     └─ templates/
└─ argocd/
   └─ banking-lab-application.yaml
```

### 정책

1. 실제 secret을 커밋하지 않는다.
2. `secret.example.yaml`만 제공한다.
3. 기본 값은 local synthetic lab 기준이다.
4. production-ready claim을 하지 않는다.
5. readiness/liveness probe를 포함한다.
6. core-banking, temporal-worker, postgres 정도는 최소 Helm template으로 표현한다.
7. NetworkPolicy를 통해 최소한의 분리 의도를 보여준다.

### 테스트

가능하면 다음 명령을 추가한다.

```bash
npm run k8s:validate
npm run helm:template
```

도구가 없을 수 있으므로 다음 중 가능한 방식으로 검증한다.

1. `helm template` 가능하면 실행.
2. `kubectl --dry-run=client` 가능하면 실행.
3. 없으면 YAML parse/structural test를 Node script로 수행.
4. 단, structural test는 실제 배포 검증이 아님을 문서에 명시한다.

---

## 9. Phase G — 부하테스트, 백업/복구, 운영 증적 추가

은행급 신뢰성을 주장하려면 기능 테스트 외에 성능과 복구 증적이 필요하다.

### 부하테스트

다음 중 하나를 선택한다.

* k6
* Gatling
* Node 기반 synthetic load script

권장 경로:

```text
tests/load/
├─ customer-transfer.k6.js
├─ staff-lookup.k6.js
├─ mixed-banking-traffic.k6.js
└─ README.md
```

테스트 시나리오:

1. 고객 계좌조회.
2. 고객 이체 요청.
3. 같은 idempotency key 재시도 burst.
4. 직원 고객조회 + audit append.
5. FDS held transfer 조회.
6. approval inbox 조회.

결과물:

```text
docs/test-evidence/load-test-summary.md
docs/test-evidence/generated/load-test-summary.json
```

### 백업/복구

PostgreSQL backup/restore drill을 추가한다.

```text
scripts/
├─ run-postgres-backup-drill.ts
└─ check-backup-restore-evidence.ts
```

검증할 것:

1. synthetic DB backup 생성.
2. 새 DB 또는 새 schema로 restore.
3. ledger transaction count 일치.
4. ledger posting balance invariant 유지.
5. audit hash-chain validity 유지.
6. approval/workflow 상태 일치.
7. customer_transfer_results 상태 일치.

결과물:

```text
docs/test-evidence/postgres-backup-restore-drill.md
docs/test-evidence/generated/postgres-backup-restore-drill.json
```

---

## 10. 테스트 방식

작업 전 baseline으로 가능한 테스트를 실행하고 결과를 기록하라.

필수 baseline:

```bash
npm ci
npm test
npm run validate:manifests
npm run test:screen-engine
npm run packages:typecheck
npm run scripts:typecheck
npm run test:e2e
docker compose config
docker compose --profile platform config
```

백엔드 관련 변경 후:

```bash
npm run test:core-banking
npm run test:core-banking:unit
npm run test:core-banking:integration
```

프론트엔드 변경 후:

```bash
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
```

보안/증적 관련:

```bash
npm run security:evidence
npm run evidence:refresh-check
npm run retirement:audit
npm run retirement:final-review:verify
npm run node:retirement-gate
npm run goal:completion-audit -- --require-complete
```

formal verification:

```bash
npm run formal:ledger
```

새로 추가한 기능에 맞춰 다음 스크립트도 추가하거나 실행하라.

```bash
npm run analytics:fds-aml
npm run k8s:validate
npm run helm:template
npm run load:smoke
npm run backup:restore-drill
```

---

## 11. 최종 테스트 통과 조건

최종적으로 다음 조건을 모두 만족해야 한다.

### 기능 조건

1. `docs/implementation-coverage-matrix.md`가 존재하고 최신 상태다.
2. 직원 통합단말 핵심 command 중 최소 5개 이상이 API-backed + audit + maker-checker + test 상태다.
3. 계좌 지급정지, 이체한도 변경, 수수료 면제, 고객 KYC 재확인, 거래 정정 중 최소 3개 이상은 Playwright 또는 Spring integration test가 있다.
4. 예금상품/수수료/이자 모듈 중 최소 하나는 ledger posting까지 연결된다.
5. FDS/AML analytics artifact가 생성되고, FDS/AML console 또는 evidence 문서에서 확인 가능하다.
6. Formal ledger verification은 static-only가 아니라 실제 model checker 실행 결과를 남긴다.
7. Kubernetes/Helm 구조가 추가되고 template/structural validation이 통과한다.
8. 부하테스트 또는 backup/restore drill 중 최소 하나 이상은 executable script와 generated evidence를 가진다.

### 품질 조건

1. 기존 Node reference boundary를 다시 target path dependency로 만들지 않는다.
2. 기존 Node retirement gate를 깨지 않는다.
3. 기존 Keycloak, passkey, audit, Temporal, Outbox, security evidence를 깨지 않는다.
4. 모든 신규 command는 structured error를 사용한다.
5. 모든 신규 고위험 command는 maker-checker를 사용한다.
6. 모든 신규 민감정보 조회/변경은 audit event를 남긴다.
7. 모든 신규 ledger posting은 double-entry invariant를 만족한다.
8. 모든 신규 idempotent command는 중복 실행 시 중복 side effect를 만들지 않는다.
9. 실제 개인정보, 실제 외부 API, 실제 금융망을 사용하지 않는다.
10. 모든 문서에서 synthetic-only boundary를 명확히 유지한다.

### 최종 명령 통과 조건

가능한 환경에서 아래 명령이 모두 통과해야 한다.

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
npm run test:core-banking
npm run test:core-banking:integration

npm run formal:ledger
npm run security:evidence
npm run evidence:refresh-check
npm run retirement:audit
npm run retirement:final-review:verify
npm run node:retirement-gate
npm run goal:completion-audit -- --require-complete

docker compose config
docker compose --profile platform config
```

새로 추가한 스크립트도 통과해야 한다.

```bash
npm run analytics:fds-aml
npm run k8s:validate
npm run helm:template
npm run load:smoke
npm run backup:restore-drill
```

환경상 Docker, Java, Helm, k6, TLC, Python 등이 없어서 일부 테스트를 실행할 수 없다면, 다음을 반드시 남겨라.

1. 실행하지 못한 정확한 명령.
2. 실패 원인.
3. 해당 명령이 필요한 이유.
4. 대체로 실행한 정적/구조 검증.
5. 사용자가 로컬에서 실행해야 할 명령.

단, 실행하지 못한 테스트를 통과한 것처럼 기록하지 마라.

---

## 12. 구현 방식

작업은 다음 순서로 진행하라.

1. 현재 상태 분석과 coverage matrix 작성.
2. 가장 작은 vertical slice 하나 선택.
3. DB migration 작성.
4. Spring domain/service/controller 작성.
5. TypeScript api-client 확장.
6. screen manifest 추가 또는 갱신.
7. Next.js channel panel 연결.
8. unit/integration/E2E test 추가.
9. evidence 문서 갱신.
10. 테스트 실행.
11. 다음 slice 반복.

한 번에 너무 많은 파일을 무리하게 바꾸지 말고, 각 Phase마다 commit 가능한 단위로 정리하라.

---

## 13. 최종 산출물

최종 응답에는 다음을 포함하라.

1. 구현한 기능 요약.
2. 변경한 주요 파일 목록.
3. 새로 추가한 DB migration 목록.
4. 새로 추가한 API 목록.
5. 새로 추가한 화면/manifest 목록.
6. 새로 추가한 테스트 목록.
7. 실행한 명령과 결과.
8. 실행하지 못한 명령과 사유.
9. 남은 한계.
10. 다음 작업 추천.

절대 “은행급 완성”이라고 과장하지 말고, 다음 표현을 유지하라.

> 실제 금융망과 실고객 데이터를 사용하지 않는 synthetic lab 환경에서, 은행 시스템의 핵심 통제와 운영 패턴을 구현·검증했다.

이제 위 지시를 기준으로 현재 저장소를 분석하고, Phase A부터 안전하게 구현을 시작하라.
