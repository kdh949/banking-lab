# Codex Full Rewrite Plan — Bank-grade Core Banking Lab

> 목적: 현재 `banking-lab` 코드베이스를 기반으로 하되, 기존 Node.js `.mjs` MVP를 최종 구현체로 확장하지 않고, 처음 목표한 은행급 기술 스택으로 **전면 재구축**하도록 Codex에게 지시하는 실행 문서입니다.
>
> 최종 목표: Kotlin/Java + Spring Boot, PostgreSQL, Kafka/Redpanda, Temporal, Next.js, Keycloak, OpenTelemetry, Kubernetes, Terraform, Helm, Argo CD 기반의 규제 대응형 모의 은행 시스템을 만든다.

> 현재 상태(2026-06-06): 이 문서는 rewrite 실행 방향을 남긴 계획 문서다. 현재 저장소는 Spring Boot 서비스, Next.js 채널 앱, PostgreSQL/Flyway, Redpanda/Kafka Outbox, Temporal, Keycloak, Python/DuckDB analytics, Kubernetes/Helm/Terraform/Argo CD, 보안/관측성 증거를 포함하며, `docs/migration/node-retirement-gate.json`은 현재 synthetic lab 범위에서 `ready` 상태다. 아래의 단계별 문구 중 과거 `placeholder`나 target 구조 표현은 계획 맥락으로 읽고, 현재 증거 판단은 `docs/test-evidence/evidence-gap-report.md`, `docs/test-evidence/parity-coverage-matrix.md`, `docs/test-evidence/node-retirement-review.md`를 우선한다.

---

## 0. Codex에게 주는 최상위 지시

너는 은행 계정계, 채널계, 운영계, 정보계, 보안통제, 감사증적, 장애복구를 이해하는 시니어 금융 시스템 엔지니어다.

현재 저장소의 Node.js `.mjs` 구현은 빠른 MVP이자 동작 참고용 oracle이다. 하지만 최종 목표는 Node.js 런타임을 계속 확장하는 것이 아니다. 최종 구현체는 아래 목표 스택으로 전면 재구축한다.

핵심 지시:

1. 기존 Node.js 런타임은 **참고용 reference/oracle**로만 사용한다.
2. 신규 시스템은 Kotlin/Java + Spring Boot 중심으로 재설계한다.
3. 원장은 PostgreSQL에 영속화한다.
4. 이벤트는 Kafka 또는 Redpanda와 Outbox Pattern으로 처리한다.
5. 장기 업무 workflow는 Temporal을 기본값으로 사용한다.
6. 고객 웹과 직원 통합단말은 TypeScript + Next.js/React로 구현한다.
7. 인증은 Keycloak, OAuth2/OIDC, WebAuthn/MFA 구조를 기준으로 한다.
8. 운영계 Admin은 React 기반 RBAC/ABAC를 적용한다.
9. AML/FDS/분석 영역은 Python, DuckDB/Spark, scikit-learn을 사용한다.
10. 관측성은 OpenTelemetry, Prometheus, Grafana, Loki/Tempo로 구성한다.
11. 배포는 Docker Compose에서 시작하되 Kubernetes, Terraform, Helm, Argo CD로 확장 가능해야 한다.
12. 보안검증은 OWASP ASVS 5.0, SAST, DAST, SCA, SBOM, Trivy, Semgrep를 포함한다.
13. 실제 고객 자금, 실제 개인정보, 실제 금융망, 실제 KYC, 실제 금융기관 API는 절대 사용하지 않는다.
14. 모든 데이터는 synthetic data와 simulator로만 구성한다.

---

## 1. 전면 재작성 원칙

### 1.1 기존 Node 구현의 역할

기존 Node.js 구현은 다음 용도로만 사용한다.

```text
reference behavior
도메인 규칙 확인
테스트 시나리오 추출
API 의미 확인
데모 흐름 참고
문서/증적 참고
```

기존 Node.js 구현을 다음 용도로 쓰지 않는다.

```text
최종 런타임
운영 대상 백엔드
주요 비즈니스 로직 확장 대상
은행급 영속성 계층
보안/인증 계층
최종 API gateway
최종 BFF
```

### 1.2 재작성 방식

전면 재작성은 한 번에 전부 지우는 방식이 아니라, **새 목표 스택을 별도 경로에 구축하고, 기능 단위로 기존 Node 기능을 대체**하는 방식으로 진행한다.

```text
current Node MVP
    ↓ reference only
new target architecture
    ↓ Kotlin/Spring + Next.js + PostgreSQL + Kafka + Temporal
production-like local lab
```

Node 삭제는 마지막 단계에서만 한다.

---

## 2. 최종 목표 기술 스택

### 2.1 핵심 스택

| 영역 | 목표 기술 |
|---|---|
| 계정계 Core | Kotlin/Java + Spring Boot |
| 원장 DB | PostgreSQL, SERIALIZABLE/REPEATABLE READ 검증 |
| 이벤트 | Kafka 또는 Redpanda + Outbox Pattern |
| 장기 워크플로우 | Temporal |
| 채널계 | TypeScript + Next.js / React |
| BFF/API | Kotlin 또는 TypeScript |
| 운영계 Admin | React + RBAC/ABAC |
| AML/FDS/분석 | Python, Spark/DuckDB, scikit-learn |
| 인프라 | Docker, Kubernetes, Terraform, Helm, Argo CD |
| 인증 | Keycloak, OAuth2/OIDC, WebAuthn/MFA |
| 관측성 | OpenTelemetry + Prometheus + Grafana + Loki/Tempo |
| 보안검증 | OWASP ASVS 5.0, SAST/DAST/SCA, SBOM, Trivy, Semgrep |

### 2.2 웹/운영계 스택

| 영역 | 목표 기술 |
|---|---|
| 고객 웹/직원 단말 | TypeScript + Next.js |
| 공통 UI | React + shadcn/ui 또는 자체 디자인 시스템 |
| 백엔드 | Kotlin/Spring Boot 또는 Java/Spring Boot |
| DB | PostgreSQL |
| 캐시 | Redis |
| 이벤트 | Kafka 또는 Redpanda |
| 워크플로우 | Temporal 또는 직접 구현한 상태머신 |
| 인증 | Keycloak |
| 관측성 | OpenTelemetry + Prometheus + Grafana + Loki |
| 테스트 | Playwright, JUnit, Testcontainers |
| 배포 | Docker Compose → Kubernetes 확장 |

---

## 3. 목표 아키텍처

```text
[External / User Channels]
Customer Web / Staff Terminal / Complaint Portal / Admin Console
        |
[Edge]
Nginx or API Gateway / WAF-sim / Rate Limit / TLS local profile
        |
[Identity]
Keycloak / OAuth2 / OIDC / WebAuthn MFA / RBAC / ABAC
        |
[BFF Layer]
customer-bff / staff-bff / admin-bff
TypeScript or Kotlin
        |
[Domain Services]
core-banking-service        Kotlin/Spring Boot
customer-service            Kotlin/Spring Boot
account-service             Kotlin/Spring Boot
transfer-service            Kotlin/Spring Boot
ledger-service              Kotlin/Spring Boot
workflow-service            Temporal workers
complaint-service           Kotlin/Spring Boot + Temporal
fds-service                 Kotlin/Spring Boot + Python scoring adapter
aml-service                 Kotlin/Spring Boot + Python analytics adapter
reconciliation-service      Kotlin/Spring Boot
notification-service        Kotlin/Spring Boot
reporting-service           Kotlin/Spring Boot / Python
        |
[Data]
PostgreSQL / Redis / Kafka-Redpanda / Object Storage-sim
        |
[Analytics]
Python / DuckDB / Spark optional / scikit-learn
        |
[Observability]
OpenTelemetry / Prometheus / Grafana / Loki / Tempo
        |
[Platform]
Docker Compose / Kubernetes / Terraform / Helm / Argo CD
```

---

## 4. 신규 저장소 구조 목표

Codex는 기존 구조를 참고하되, 최종 구조를 아래와 같이 정리한다.

```text
banking-lab/
├─ apps/
│  ├─ customer-web/                 # Next.js 고객 웹뱅킹
│  ├─ staff-terminal/               # Next.js 직원 통합단말
│  ├─ complaint-portal/             # Next.js 전자민원 포털
│  ├─ admin-console/                # React/Next 운영계 Admin
│  ├─ audit-console/                # 감사/보안 포털
│  └─ fds-aml-console/              # 이상거래/AML 심사 포털
│
├─ services/
│  ├─ core-banking/                 # Kotlin/Spring Boot 계정계 Core
│  ├─ customer-service/             # 고객 도메인
│  ├─ account-service/              # 계좌 도메인
│  ├─ transfer-service/             # 이체 도메인
│  ├─ ledger-service/               # 원장 도메인, 필요 시 core-banking 내부 모듈
│  ├─ complaint-service/            # 민원 도메인 + Temporal workflow
│  ├─ workflow-workers/             # Temporal workers
│  ├─ fds-service/                  # FDS rule/case service
│  ├─ aml-service/                  # AML case service
│  ├─ reconciliation-service/       # 일마감/대사
│  ├─ notification-service/         # 알림 simulator
│  ├─ reporting-service/            # 정보계/리포트
│  └─ external-simulators/          # KYC, open banking, SMS, regulator simulator
│
├─ analytics/
│  ├─ aml-fds-python/               # Python scoring and analytics
│  ├─ notebooks/                    # optional local analysis
│  └─ duckdb/                       # local mart definitions
│
├─ packages/
│  ├─ ui/                           # React/shadcn or custom design system
│  ├─ api-client/                   # generated TypeScript API clients
│  ├─ screen-engine/                # manifest-driven screen renderer
│  ├─ form-engine/                  # shared form schema/validation
│  ├─ auth-client/                  # OIDC client helpers
│  └─ banking-contracts/            # OpenAPI, schemas, event contracts
│
├─ contracts/
│  ├─ openapi/
│  ├─ asyncapi/
│  ├─ events/
│  └─ temporal/
│
├─ infra/
│  ├─ docker-compose/
│  ├─ k8s/
│  ├─ helm/
│  ├─ terraform/
│  ├─ argocd/
│  ├─ keycloak/
│  ├─ observability/
│  └─ security/
│
├─ db/
│  ├─ migrations/
│  ├─ seed/
│  └─ testdata/
│
├─ tests/
│  ├─ e2e/                          # Playwright
│  ├─ contract/
│  ├─ load/
│  └─ security/
│
├─ docs/
│  ├─ architecture/
│  ├─ adr/
│  ├─ migration/
│  ├─ regulatory-mapping/
│  ├─ threat-model/
│  ├─ test-evidence/
│  ├─ failure-drills/
│  └─ demo-scenarios/
│
├─ legacy-node-reference/           # 선택: Node reference 보관 위치
├─ AGENTS.md
├─ README.md
└─ docker-compose.yml
```

---

## 5. 계정계 Core 재구축 지시

### 5.1 목표

Kotlin/Spring Boot로 계정계 Core를 새로 만든다. 기존 Node `LedgerCore`는 참고만 한다.

구현 대상:

```text
Customer
Account
LedgerTransaction
LedgerPosting
AccountBalanceProjection
IdempotencyKey
DailyClosing
Reversal
Adjustment
AccountHold
AccountLimit
```

### 5.2 핵심 원칙

```text
모든 금융 이동은 double-entry posting으로 표현한다.
ledger_transactions와 ledger_postings가 source of truth다.
account_balances는 projection/cache다.
확정된 거래는 update/delete하지 않는다.
정정은 reversal 또는 adjustment로만 처리한다.
모든 command는 idempotency key를 가진다.
DB transaction boundary 안에서 idempotency와 posting insert를 함께 보장한다.
```

### 5.3 Spring Boot 구현 요구사항

Codex는 다음 구조를 만든다.

```text
services/core-banking/src/main/kotlin/lab/banking/core/
├─ CoreBankingApplication.kt
├─ ledger/
│  ├─ domain/
│  │  ├─ LedgerTransaction.kt
│  │  ├─ LedgerPosting.kt
│  │  ├─ Account.kt
│  │  ├─ BalanceProjection.kt
│  │  └─ LedgerInvariants.kt
│  ├─ application/
│  │  ├─ LedgerCommandService.kt
│  │  ├─ DepositCommand.kt
│  │  ├─ WithdrawalCommand.kt
│  │  ├─ InternalTransferCommand.kt
│  │  ├─ ReversalCommand.kt
│  │  └─ AdjustmentCommand.kt
│  ├─ persistence/
│  │  ├─ LedgerRepository.kt
│  │  ├─ AccountRepository.kt
│  │  ├─ IdempotencyRepository.kt
│  │  └─ BalanceProjectionRepository.kt
│  └─ api/
│     ├─ LedgerController.kt
│     └─ LedgerDtos.kt
├─ audit/
├─ approval/
├─ masking/
├─ workflow/
├─ common/
└─ config/
```

### 5.4 Isolation level 검증

PostgreSQL transaction isolation 검증을 포함한다.

테스트 대상:

```text
READ COMMITTED에서 발생 가능한 race를 문서화한다.
REPEATABLE READ에서 동시 출금이 안전한지 검증한다.
SERIALIZABLE에서 write skew 또는 overdraft가 방지되는지 검증한다.
동일 idempotency key가 동시에 들어와도 posting은 하나만 생성되어야 한다.
```

권장 구현:

```kotlin
@Transactional(isolation = Isolation.SERIALIZABLE)
fun internalTransfer(command: InternalTransferCommand): LedgerCommandResult
```

단, 성능/충돌 비용을 비교하기 위해 REPEATABLE READ 테스트도 작성한다.

---

## 6. PostgreSQL 설계 지시

### 6.1 DB 원칙

Codex는 PostgreSQL을 원장 source of truth로 사용한다.

필수 테이블:

```text
customers
customer_kyc_profiles
accounts
account_limits
account_holds
ledger_transactions
ledger_postings
account_balance_projections
idempotency_keys
daily_closings
reconciliation_items
audit_events
operator_approvals
outbox_events
inbox_events
workflow_instances
workflow_events
```

### 6.2 필수 제약

```text
ledger_transactions.idempotency_key UNIQUE
ledger_postings.amount_minor > 0
ledger_postings.direction IN ('DEBIT', 'CREDIT')
operator_approvals.approved_by <> requested_by
closed business date mutation 방지
finalized ledger source row update/delete 방지
outbox_events aggregate_id + event_type + idempotency_key 중복 방지
```

### 6.3 Migration 요구사항

Codex는 Flyway-compatible migration을 만든다.

```text
db/migrations/V001__foundation.sql
db/migrations/V002__ledger_constraints.sql
db/migrations/V003__audit_approval_workflow.sql
db/migrations/V004__outbox_inbox.sql
db/migrations/V005__fds_aml_reconciliation.sql
```

기존 `infra/db/migrations/001_foundation.sql`이 있다면 내용을 검토하여 새 Flyway migration으로 이관한다.

---

## 7. Kafka/Redpanda + Outbox Pattern 지시

### 7.1 목표

은행 업무 command와 후속 이벤트 발행을 분리한다.

이벤트 발행 대상:

```text
LedgerTransactionPosted
LedgerTransactionReversed
TransferHeldByFds
TransferReleasedByFds
TransferBlockedByFds
ComplaintSubmitted
ComplaintAnswered
ApprovalRequested
ApprovalApproved
ApprovalRejected
DailyClosingCompleted
ReconciliationItemCreated
AdjustmentPosted
AuditEventAppended
```

### 7.2 Outbox 설계

필수 테이블:

```text
outbox_events
├─ outbox_event_id
├─ aggregate_type
├─ aggregate_id
├─ event_type
├─ idempotency_key
├─ payload_json
├─ headers_json
├─ status
├─ retry_count
├─ next_retry_at
├─ created_at
├─ published_at
└─ error_message
```

상태:

```text
PENDING
PUBLISHED
FAILED
DEAD_LETTER
```

### 7.3 Codex 구현 요구사항

1. 업무 DB transaction 안에서 outbox row를 같이 insert한다.
2. 별도 publisher가 outbox를 polling하거나 Debezium-ready 구조로 만든다.
3. Kafka/Redpanda topic contract를 `contracts/asyncapi`에 작성한다.
4. consumer idempotency를 위해 inbox table을 둔다.
5. 동일 event가 재전달되어도 downstream 상태가 중복 변경되지 않아야 한다.

---

## 8. Temporal Workflow 지시

### 8.1 목표

장기 업무를 DB 상태값만으로 처리하지 않고 Temporal workflow로 모델링한다.

Temporal 적용 대상:

```text
전자민원 처리
고객정보 변경 승인
이체한도 변경 승인
FDS release/block 심사
AML case closure
정산 미결 조정
일마감 workflow
장애/배치 재처리 workflow
```

### 8.2 Temporal 구조

```text
services/workflow-workers/
├─ complaint/
│  ├─ ComplaintWorkflow.kt
│  ├─ ComplaintActivities.kt
│  └─ ComplaintSignals.kt
├─ approval/
├─ fds/
├─ aml/
├─ reconciliation/
└─ closing/
```

### 8.3 Workflow 원칙

```text
Workflow는 장기 상태와 timeout/SLA를 관리한다.
금융 원장 posting은 반드시 core-banking service의 transaction boundary에서만 수행한다.
Temporal activity는 idempotent해야 한다.
Workflow signal은 maker-checker 승인과 연결되어야 한다.
```

---

## 9. 인증/인가 지시 — Keycloak, OAuth2/OIDC, WebAuthn/MFA

### 9.1 목표

Mock user 기반 인증을 제거하고 Keycloak 기반 인증/인가로 전환한다.

구성:

```text
Keycloak realm: banking-lab
clients:
  customer-web
  staff-terminal
  admin-console
  core-banking-api
roles:
  CUSTOMER
  BRANCH_STAFF
  BRANCH_MANAGER
  CALL_CENTER
  COMPLAINT_HANDLER
  FDS_REVIEWER
  AML_REVIEWER
  OPS_OPERATOR
  AUDITOR
  COMPLIANCE_MANAGER
  SYSTEM
```

### 9.2 RBAC/ABAC

RBAC:

```text
role 기반 API 접근 제어
screen manifest requiredRoles와 연동
```

ABAC:

```text
고객은 자기 계좌만 조회 가능
직원은 업무 사유가 있는 경우만 고객정보 조회 가능
감사자는 read-only
FDS/AML reviewer는 case owner 또는 role 조건 필요
운영자는 정산/배치만 수행 가능
```

### 9.3 WebAuthn/MFA

고위험 작업에는 MFA step-up을 모델링한다.

대상:

```text
PII unmask
bulk download
approval execution
FDS release
AML STR closure
reconciliation adjustment
role grant
break-glass
```

---

## 10. 채널계 — TypeScript + Next.js 지시

### 10.1 목표

고객 웹, 직원 통합단말, 전자민원, 운영계 Admin을 Next.js로 재구축한다.

### 10.2 앱 구성

```text
apps/customer-web       고객 웹뱅킹
apps/staff-terminal     직원 통합단말
apps/complaint-portal   전자민원 포털
apps/admin-console      운영계 Admin
apps/audit-console      감사 포털
apps/fds-aml-console    FDS/AML 포털
```

### 10.3 공통 UI

```text
packages/ui
├─ Button
├─ DataTable
├─ SearchPanel
├─ FormRenderer
├─ ScreenShell
├─ ApprovalPanel
├─ AuditTrail
├─ MaskedText
├─ RiskBadge
├─ StatusBadge
├─ Timeline
├─ AttachmentPanel
└─ CommentThread
```

shadcn/ui를 사용해도 되지만, 은행 업무 단말에 맞는 자체 디자인 시스템을 우선한다.

### 10.4 Screen Manifest Engine

기존 screen manifest 전략은 유지하되, 실제 Next.js 화면 렌더링으로 완성한다.

템플릿:

```text
INQUIRY
COMMAND
CASE
PARAMETER
DASHBOARD
```

모든 화면은 manifest로부터 다음을 읽어야 한다.

```text
screenId
transactionCode
title
app
type
domain
requiredRoles
layout
audit
maskingPolicy
approval
query
fields
resultTable
workflow
api
```

---

## 11. BFF/API 지시

### 11.1 목표

프론트엔드가 core domain service를 직접 호출하지 않도록 BFF/API 계층을 둔다.

구성 선택:

```text
customer-bff: TypeScript 또는 Kotlin
staff-bff: Kotlin 권장
admin-bff: Kotlin 권장
```

### 11.2 책임

```text
OIDC token 검증
role/permission enforcement
screen manifest permission enforcement
API aggregation
PII masking policy enforcement
request correlation id propagation
OpenTelemetry tracing propagation
structured error translation
```

---

## 12. AML/FDS/분석 지시 — Python, DuckDB/Spark, scikit-learn

### 12.1 목표

초기에는 rule-based로 시작하되, 분석 파이프라인을 Python으로 분리한다.

구성:

```text
analytics/aml-fds-python/
├─ pyproject.toml
├─ src/
│  ├─ feature_store.py
│  ├─ rules.py
│  ├─ scoring.py
│  ├─ model.py
│  └─ api.py
├─ tests/
└─ notebooks/
```

### 12.2 기능

```text
velocity feature
new device feature
first beneficiary feature
amount anomaly feature
customer risk feature
rule scoring
simple sklearn anomaly model
DuckDB local mart
batch feature generation
```

### 12.3 연계

Kotlin FDS/AML service는 Python scoring adapter를 호출하거나, Kafka event를 통해 scoring 결과를 수신한다.

---

## 13. 관측성 지시

### 13.1 목표

모든 서비스에 trace, metric, log를 심는다.

구성:

```text
OpenTelemetry SDK/Agent
Prometheus
Grafana
Loki
Tempo
```

### 13.2 필수 trace span

```text
HTTP request
BFF request
ledger command
DB transaction
idempotency check
posting insert
outbox insert
Kafka publish
Temporal workflow start
Temporal activity
approval execution
FDS/AML decision
reconciliation run
```

### 13.3 필수 metric

```text
ledger_command_total
ledger_command_duration_seconds
idempotency_replay_total
approval_pending_total
approval_rejected_total
fds_held_transfer_total
aml_case_open_total
reconciliation_unmatched_total
outbox_pending_total
outbox_dead_letter_total
audit_event_total
```

---

## 14. 보안검증 지시

### 14.1 목표

CI에서 보안 검사를 자동화한다.

필수 도구:

```text
Semgrep
Trivy
OWASP Dependency Check 또는 Gradle dependency audit
npm audit
SBOM generation
container scan
secret scan
DAST placeholder
OWASP ASVS 5.0 mapping
```

### 14.2 산출물

```text
docs/regulatory-mapping/owasp-asvs-mapping.md
docs/test-evidence/generated/security-evidence-summary.md
docs/test-evidence/security-docker-rerun.md
infra/security/sbom.md
docs/threat-model/system-threat-model.md
```

---

## 15. 인프라 지시

### 15.1 Docker Compose

로컬 개발용 compose 구성:

```text
postgres
redis
redpanda
redpanda-console
temporal
temporal-ui
keycloak
core-banking
customer-bff
staff-bff
customer-web
staff-terminal
prometheus
grafana
loki
tempo
otel-collector
```

### 15.2 Kubernetes

Kubernetes 확장 구조:

```text
infra/k8s
infra/helm/banking-lab
infra/argocd
```

### 15.3 Terraform

로컬/클라우드 추상화 준비:

```text
infra/terraform/main.tf
```

---

## 16. 테스트 전략

### 16.1 Backend

```text
JUnit 5
Spring Boot Test
Testcontainers PostgreSQL
Testcontainers Kafka/Redpanda
Temporal test environment
Contract tests
Concurrency tests
Isolation level tests
```

### 16.2 Frontend

```text
TypeScript typecheck
React component tests
Playwright E2E
Accessibility checks
visual smoke tests optional
```

### 16.3 Domain tests

필수 테스트:

```text
double-entry posting balance
idempotent replay
concurrent withdrawal
closed day mutation rejection
reversal policy
adjustment policy
maker-checker self approval rejection
PII masking
reason-required access
FDS hold/release/block
AML closure approval
reconciliation adjustment
outbox publish retry
Temporal workflow timeout
```

---

## 17. 마이그레이션/재작성 단계

### Phase 0 — Freeze Node Reference

목표:

```text
현재 Node 구현을 reference oracle로 고정한다.
```

작업:

```text
npm test 통과 확인
npm run validate:manifests 통과 확인
npm run evidence:pack 통과 확인
Node reference behavior 문서화
```

산출물:

```text
docs/migration/node-reference-freeze.md
legacy-node-reference/ 또는 기존 경로 유지
```

### Phase 1 — Target Platform Foundation

목표:

```text
Spring Boot, PostgreSQL, Keycloak, Redpanda, Temporal, Redis, Observability가 로컬에서 뜨는 기반을 만든다.
```

작업:

```text
Gradle wrapper 추가
Spring Boot multi-module 정리
Docker Compose target stack 구성
PostgreSQL Flyway migration 적용
Keycloak realm import 구성
Redpanda topic 생성
Temporal namespace 구성
OpenTelemetry collector 구성
```

### Phase 2 — Kotlin/Spring Ledger Core

목표:

```text
Node 원장 기능을 Kotlin/Spring/PostgreSQL로 대체한다.
```

작업:

```text
ledger domain model
ledger repository
idempotency repository
LedgerCommandService
LedgerController
JUnit/Testcontainers tests
SERIALIZABLE/REPEATABLE READ concurrency tests
```

### Phase 3 — Audit, Masking, Approval

목표:

```text
은행급 운영통제를 Spring/PostgreSQL로 구현한다.
```

작업:

```text
AuditEvent persistence
hash chain
MaskingPolicy service
Approval service
RBAC/ABAC integration
reason-required middleware
```

### Phase 4 — Eventing and Outbox

목표:

```text
원장/승인/민원/FDS/정산 이벤트를 Kafka/Redpanda로 전파한다.
```

작업:

```text
outbox_events table
outbox publisher
Kafka/Redpanda topics
AsyncAPI docs
inbox idempotency
consumer retry/dead-letter
```

### Phase 5 — Temporal Workflows

목표:

```text
장기 업무를 Temporal workflow로 이전한다.
```

작업:

```text
ComplaintWorkflow
ApprovalWorkflow
FdsReviewWorkflow
AmlCaseWorkflow
ReconciliationWorkflow
DailyClosingWorkflow
Temporal tests
```

### Phase 6 — Next.js Channels

목표:

```text
고객 웹, 직원 단말, 전자민원, 운영계 포털을 Next.js로 재구축한다.
```

작업:

```text
customer-web
staff-terminal
complaint-portal
admin-console
audit-console
fds-aml-console
shared UI
manifest renderer
OIDC login
Playwright E2E
```

### Phase 7 — AML/FDS Analytics

목표:

```text
Python 기반 AML/FDS 분석 파이프라인을 추가한다.
```

작업:

```text
DuckDB mart
feature generation
rule scoring
sklearn anomaly model
batch scoring
Kafka scoring events
```

### Phase 8 — Kubernetes/Helm/Argo CD

목표:

```text
로컬 compose를 넘어 Kubernetes 배포 구조를 만든다.
```

작업:

```text
Kubernetes manifests
Helm chart
Argo CD app
config/secrets strategy
readiness/liveness probes
resource requests/limits
```

### Phase 9 — Security and Evidence

목표:

```text
포트폴리오와 실무 검토에 필요한 보안/감사/검증 증적을 만든다.
```

작업:

```text
ASVS mapping
threat model
Semgrep
Trivy
SBOM
SCA
DAST placeholder
failure drill
evidence pack
```

---

## 18. Codex 첫 실행 프롬프트

아래 프롬프트를 Codex에 그대로 입력한다.

```text
현재 banking-lab의 Node.js .mjs 구현은 최종 구현체가 아니라 reference oracle로만 유지한다.

목표는 처음 계획한 은행급 기술 스택으로 전면 재구축하는 것이다:
- Kotlin/Java + Spring Boot
- PostgreSQL
- Kafka/Redpanda + Outbox Pattern
- Temporal
- TypeScript + Next.js/React
- Keycloak OAuth2/OIDC + WebAuthn/MFA
- Redis
- OpenTelemetry + Prometheus + Grafana + Loki/Tempo
- Docker Compose → Kubernetes/Terraform/Helm/Argo CD
- Python + DuckDB/Spark + scikit-learn for AML/FDS analytics
- OWASP ASVS 5.0, Semgrep, Trivy, SBOM, SAST/DAST/SCA

먼저 Phase 0과 Phase 1을 구현해라.

작업:
1. 현재 Node reference의 테스트와 문서를 freeze하는 문서를 작성한다.
2. Node runtime은 삭제하지 않는다.
3. Gradle wrapper를 추가한다.
4. Spring Boot multi-module 구조를 정리한다.
5. core-banking Spring Boot 서비스가 JDK 21, Kotlin, Spring Boot, Flyway, PostgreSQL, Testcontainers 기반으로 실행 가능하게 만든다.
6. Docker Compose에 postgres, redis, redpanda, temporal, keycloak, otel-collector, prometheus, grafana, loki, tempo를 추가한다.
7. Keycloak realm import 초안을 추가한다.
8. Flyway migration을 db/migrations/V001__foundation.sql 형식으로 정리한다.
9. 기존 infra/db/migrations/001_foundation.sql과 새 migration의 관계를 문서화한다.
10. docs/architecture/target-stack-architecture.md를 작성한다.
11. docs/migration/full-rewrite-roadmap.md를 작성한다.
12. README에 현재 Node reference와 target stack rewrite 방향을 명확히 분리해서 설명한다.

완료 후 보고:
- 변경 파일
- 실행한 명령어
- 통과한 테스트
- 실행하지 못한 테스트와 이유
- 다음 단계에서 구현할 ledger vertical slice

주의:
- 실제 고객정보, 실제 송금, 실제 금융 API는 절대 사용하지 않는다.
- 테스트가 통과하지 않았는데 통과했다고 쓰지 않는다.
- Node reference를 삭제하지 않는다.
- 최종 구현체는 Node가 아니라 Kotlin/Spring Boot + PostgreSQL 중심이다.
```

---

## 19. Codex 두 번째 실행 프롬프트 — Ledger Vertical Slice

```text
Phase 2를 구현해라. Kotlin/Spring Boot + PostgreSQL 기반 계정계 원장 vertical slice를 만든다.

구현 대상:
1. LedgerTransaction, LedgerPosting, Account, AccountBalanceProjection, IdempotencyKey Kotlin domain model
2. PostgreSQL repository
3. LedgerCommandService
4. deposit, withdraw, internal transfer, reversal, adjustment API
5. idempotency key DB 저장 및 replay
6. double-entry invariant 검증
7. SERIALIZABLE transaction boundary
8. REPEATABLE READ vs SERIALIZABLE 동시 출금 테스트
9. Testcontainers PostgreSQL 기반 JUnit 테스트
10. structured API error contract 적용

필수 테스트:
- deposit은 suspense debit + customer credit으로 balanced posting 생성
- withdrawal은 customer debit + suspense credit으로 balanced posting 생성
- internal transfer는 from debit + to credit
- 동일 idempotency key 재시도는 transaction 1개만 생성
- 잔액 부족 출금은 409 structured error
- 동시 출금은 overdraft를 만들 수 없음
- reversal은 원거래를 참조하고 잔액을 복구
- closed business date에는 직접 posting 불가
- adjustment는 open business date에 balanced ADJUSTMENT posting 생성

Node reference와 의미가 다르면 ADR을 작성한다.
```

---

## 20. Codex 세 번째 실행 프롬프트 — Outbox, Kafka, Temporal

```text
Phase 4와 Phase 5의 첫 vertical slice를 구현해라.

목표:
- ledger transaction posted 후 outbox event 생성
- outbox publisher가 Redpanda/Kafka로 event 발행
- consumer는 inbox idempotency로 중복 처리 방지
- complaint workflow는 Temporal로 시작

구현 대상:
1. outbox_events, inbox_events migration
2. OutboxEvent entity/repository/service
3. Redpanda topic configuration
4. LedgerTransactionPosted event
5. Outbox publisher worker
6. AsyncAPI event contract
7. Temporal local compose service
8. ComplaintWorkflow skeleton
9. Temporal test environment 기반 workflow test

완료 기준:
- ledger command와 outbox insert가 같은 DB transaction 안에서 수행됨
- outbox publisher retry 가능
- duplicate event consume 방지
- Temporal workflow start/complete 테스트 통과
```

---

## 21. Codex 네 번째 실행 프롬프트 — Next.js Channels

```text
Phase 6을 구현해라. 기존 static app shell을 대체할 Next.js 채널계를 만든다.

대상 앱:
- customer-web
- staff-terminal
- complaint-portal
- admin-console
- audit-console
- fds-aml-console

우선 customer-web과 staff-terminal부터 구현한다.

customer-web:
- 로그인/OIDC placeholder
- 계좌목록
- 계좌상세
- 거래내역
- 이체
- 이체결과
- 민원접수 진입

staff-terminal:
- 거래코드 입력
- 고객 컨텍스트
- 고객조회
- 계좌조회
- 거래내역조회
- 고객정보 변경 요청
- 승인함
- 감사로그

공통:
- screen manifest 기반 렌더링
- shared UI package
- typed API client
- masked data rendering
- reason-required input
- Playwright E2E
```

---

## 22. Definition of Done

이 전면 재작성 단계가 완료되었다고 말하려면 최소한 아래를 만족해야 한다.

```text
Kotlin/Spring Boot core-banking이 PostgreSQL에 원장을 영속화한다.
SERIALIZABLE/REPEATABLE READ 동시성 테스트가 존재한다.
Kafka/Redpanda outbox 이벤트가 동작한다.
Temporal workflow가 최소 complaint 또는 approval flow에 적용된다.
Next.js customer-web과 staff-terminal이 실제 API와 연결된다.
Keycloak 기반 OIDC login 또는 최소 local realm integration이 있다.
OpenTelemetry trace가 backend request와 DB transaction에 붙는다.
Playwright E2E가 customer transfer와 staff approval을 검증한다.
JUnit/Testcontainers가 ledger/idempotency/reversal/concurrency를 검증한다.
Semgrep/Trivy/SBOM/SCA 명령이 문서화되고 실행 가능하다.
Node.js reference는 더 이상 핵심 동작의 유일한 증거가 아니다.
```

---

## 23. 포트폴리오 표현 기준

전면 재작성 후에는 이렇게 표현할 수 있어야 한다.

```text
Kotlin/Spring Boot와 PostgreSQL 기반 double-entry ledger를 중심으로,
Kafka/Redpanda Outbox, Temporal workflow, Keycloak OIDC, Next.js 채널계,
OpenTelemetry 관측성, Testcontainers/JUnit/Playwright 검증을 갖춘
규제 대응형 모의 은행 시스템을 구현했습니다.
```

그 전까지는 이렇게 표현한다.

```text
현재는 Node.js reference MVP를 기반으로 Kotlin/Spring Boot, PostgreSQL,
Kafka/Temporal, Next.js 기반 목표 아키텍처로 전면 재구축 중입니다.
```
