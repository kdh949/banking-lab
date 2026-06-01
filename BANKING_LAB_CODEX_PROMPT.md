# Bank-grade Core Banking Lab — Codex Master Prompt

> 목적: 이 문서는 Codex에 그대로 투입하거나, 저장소 루트의 `docs/`에 넣어 장기 프로젝트 지침으로 사용할 수 있는 **은행급 코어뱅킹·통합단말·고객웹·전자민원 플랫폼 구현 프롬프트**입니다.  
> 범위: 실제 고객 자금·실명 개인정보·지급결제망을 다루지 않는 **규제 대응형 모의 은행 시스템**을 만든다. 단, 설계·통제·검증 수준은 실제 은행 시스템을 지향한다.

---

## 0. Codex에게 주는 최상위 지시문

너는 은행 계정계, 채널계, 운영계, 정보계, 보안통제, 감사증적, 장애복구를 이해하는 시니어 금융 시스템 엔지니어다. 이 저장소의 목표는 단순 인터넷뱅킹 클론이 아니라, **은행이 망하면 안 되는 이유를 코드와 증적으로 보여주는 Bank-grade Core Banking Lab**을 구현하는 것이다.

이 프로젝트는 실제 예금 수취, 실제 송금, 실제 지급결제망 접속, 실제 고객 실명정보 처리를 하지 않는다. 모든 고객, 계좌, 거래, 외부기관, 인증, 신분확인, 금융감독기관 연계는 synthetic data와 simulator로 구현한다. 하지만 내부 구조는 실제 은행 시스템처럼 보수적으로 설계한다.

너는 아래 원칙을 반드시 지켜라.

1. 원장 정합성이 최우선이다. UI보다 double-entry ledger, idempotency, reversal, reconciliation, audit trail을 먼저 구현한다.
2. 잔액은 직접 수정하지 않는다. 모든 잔액 변화는 ledger transaction과 posting의 결과여야 한다.
3. 한 번 확정된 거래는 update/delete하지 않는다. 정정은 reversal transaction으로만 처리한다.
4. 모든 외부 요청은 idempotency key를 가져야 한다.
5. 고객정보 조회, 마스킹 해제, 다운로드, 관리자 조작은 반드시 사유와 감사로그를 남긴다.
6. 운영계 변경 업무는 maker-checker 승인 구조를 기본값으로 한다.
7. 화면 수를 늘리기 위해 손코딩하지 말고 screen manifest, form engine, workflow engine을 만든다.
8. 모든 업무 화면은 권한, 마스킹, 감사로그, 상태전이, 승인 여부를 명시해야 한다.
9. 장애, 중복 요청, timeout, 재시도, partial failure가 발생해도 원장 불변식이 깨지면 안 된다.
10. 모든 기능은 테스트와 증적을 남겨야 한다. 포트폴리오의 최종 산출물은 코드만이 아니라 ADR, threat model, 테스트 리포트, 장애훈련 리포트, 감사증적이다.

---

## 1. 프로젝트 이름과 포지셔닝

프로젝트명:

```text
Bank-grade Core Banking Lab
```

한 줄 설명:

```text
Double-entry ledger, channel banking, staff terminal, complaint workflow, maker-checker control, AML/FDS simulation, reconciliation, audit evidence, formal verification, chaos testing을 포함한 규제 대응형 모의 디지털뱅크 플랫폼
```

포트폴리오 소개 문구:

```text
단순 인터넷뱅킹 클론이 아니라, double-entry ledger, idempotent transaction processing, maker-checker control, AML/FDS workflow, reconciliation, formal specification, chaos testing, audit evidence까지 포함한 은행급 코어 시스템을 구현했습니다.
```

---

## 2. 법적·운영적 경계

이 프로젝트는 교육·포트폴리오 목적의 모의 시스템이다. 다음은 금지한다.

- 실제 예금 수취
- 실제 송금 또는 지급결제망 접속
- 실제 고객 주민등록번호, 계좌번호, 카드번호, 신분증 이미지 저장
- 실제 KYC/실명확인 서비스 연동
- 실제 금융기관 API에 무단 접속
- 실제 금융상품 판매 또는 중개
- 실제 고객 민원 접수처럼 오인될 수 있는 공개 서비스 운영

대신 아래를 구현한다.

- synthetic customer data
- simulated KYC provider
- simulated open banking provider
- simulated ISO 20022/KFTC/SWIFT-like messages
- simulated regulator transfer status
- simulated SMS/email/push provider
- simulated AML/FDS rule engine

참고 기준:

- 전자금융감독규정
- 전자금융거래법
- 금융분야 클라우드컴퓨팅서비스 이용 가이드
- OWASP ASVS
- NIST Cybersecurity Framework
- Basel operational resilience principles
- BCBS 239 risk data aggregation principles

---

## 3. 전체 시스템 아키텍처

```text
[채널계]
Mobile Web / Customer Web / Complaint Portal / Partner API
        |
WAF-sim / API Gateway / BFF / OAuth2-OIDC / MFA / Device Binding
        |
[업무 서비스 계층]
Customer Service
Account Service
Transfer Service
Payment Simulator
Complaint Service
Notification Service
Workflow Service
        |
[계정계 Core]
Double-entry Ledger
Posting Engine
Balance Projection
Limit / Hold / Fee / Interest
EOD Batch / Closing
        |
[대외계/연계계]
OpenBanking-sim
KYC-sim
ISO20022-sim
KFTC-sim
SWIFT-sim
SMS/Email/Push-sim
Regulator-sim
        |
[운영계]
Staff Integrated Terminal
Maker-Checker Approval
Case Management
Reconciliation
AML/FDS Review
Incident Console
Batch Console
Audit Viewer
        |
[정보계]
Risk Mart
Regulatory Report
BI Dashboard
Data Lakehouse-sim
Lineage
        |
[공통]
IAM
KMS/HSM-sim
SIEM-sim
Observability
Backup/Restore
DR Drill
CI/CD
IaC
Evidence Pack
```

---

## 4. 추천 기술 스택

### 4.1 Backend

- Kotlin + Spring Boot 또는 Java + Spring Boot
- PostgreSQL
- Redis
- Kafka 또는 Redpanda
- Temporal 또는 자체 workflow/state machine
- Testcontainers
- Flyway 또는 Liquibase
- OpenAPI/Swagger

### 4.2 Frontend

- TypeScript
- Next.js 또는 React
- Monorepo 기반 apps/packages 구조
- 공통 UI 패키지
- screen manifest 기반 화면 생성
- Playwright E2E

### 4.3 Infra

- Docker Compose for local
- Kubernetes 확장 가능 구조
- Terraform
- Helm
- Argo CD 선택
- OpenTelemetry
- Prometheus
- Grafana
- Loki/Tempo

### 4.4 Security

- Keycloak for OAuth2/OIDC
- MFA/WebAuthn simulation
- RBAC + ABAC
- PII masking
- Audit log hash chain
- SAST: Semgrep
- SCA: dependency scan
- Secret scan
- Container scan: Trivy
- IaC scan
- SBOM generation

---

## 5. 저장소 구조

```text
banking-lab/
├─ apps/
│  ├─ customer-web/              # 고객 웹뱅킹
│  ├─ staff-terminal/            # 직원 통합단말
│  ├─ complaint-portal/          # 전자민원 접수 포털
│  ├─ ops-console/               # 운영자 포털
│  ├─ audit-console/             # 감사/보안 포털
│  └─ fds-aml-console/           # 이상거래/AML 심사 포털
│
├─ services/
│  ├─ api-gateway/
│  ├─ bff-customer/
│  ├─ bff-staff/
│  ├─ core-banking/              # 계정계
│  ├─ ledger-service/            # 원장 핵심
│  ├─ customer-service/
│  ├─ account-service/
│  ├─ transfer-service/
│  ├─ workflow-service/           # 결재/상태전이
│  ├─ complaint-service/
│  ├─ fds-service/
│  ├─ aml-service/
│  ├─ reconciliation-service/
│  ├─ notification-service/
│  ├─ reporting-service/
│  └─ external-simulators/
│
├─ packages/
│  ├─ ui/                         # 공통 디자인 시스템
│  ├─ api-client/
│  ├─ form-engine/
│  ├─ screen-engine/
│  ├─ workflow-client/
│  ├─ auth/
│  ├─ validation/
│  └─ banking-domain/
│
├─ screen-manifests/
│  ├─ customer-web/
│  ├─ staff-terminal/
│  ├─ complaint-portal/
│  ├─ ops-console/
│  ├─ audit-console/
│  └─ fds-aml-console/
│
├─ infra/
│  ├─ docker-compose/
│  ├─ k8s/
│  ├─ terraform/
│  ├─ helm/
│  └─ observability/
│
├─ docs/
│  ├─ adr/
│  ├─ architecture/
│  ├─ regulatory-mapping/
│  ├─ threat-model/
│  ├─ test-evidence/
│  ├─ failure-drills/
│  ├─ reconciliation-reports/
│  └─ demo-scenarios/
│
├─ skills/
│  ├─ bank-screen-generator/
│  │  └─ SKILL.md
│  ├─ bank-api-generator/
│  │  └─ SKILL.md
│  ├─ ledger-invariant-review/
│  │  └─ SKILL.md
│  ├─ maker-checker-review/
│  │  └─ SKILL.md
│  ├─ audit-log-review/
│  │  └─ SKILL.md
│  ├─ complaint-workflow-builder/
│  │  └─ SKILL.md
│  ├─ banking-e2e-scenario/
│  │  └─ SKILL.md
│  ├─ threat-model-stride/
│  │  └─ SKILL.md
│  └─ evidence-pack-builder/
│     └─ SKILL.md
│
├─ AGENTS.md
├─ README.md
└─ docker-compose.yml
```

---

## 6. 은행 업무 도메인 분해

### 6.1 채널계

고객과 외부 사용자가 접근하는 영역이다.

- customer-web
- complaint-portal
- partner-api
- API gateway
- BFF
- OAuth2/OIDC
- MFA
- device binding
- rate limit
- session timeout
- fraud pre-check

### 6.2 계정계

고객의 돈과 직접 연결된 core domain이다. 모의 시스템이더라도 가장 보수적으로 구현한다.

- customer
- account
- ledger transaction
- ledger posting
- balance projection
- transfer
- fee
- interest
- hold
- limit
- reversal
- EOD closing

### 6.3 운영계

은행 직원이 사용하는 업무 포털이다.

- staff integrated terminal
- approval inbox
- customer 360
- account operation
- complaint case
- AML/FDS case
- reconciliation
- batch console
- incident console
- audit viewer

### 6.4 정보계

리스크, 감독보고, 통계, BI, 데이터 품질을 담당한다.

- risk mart
- regulatory report
- transaction analytics
- case statistics
- complaint SLA report
- ledger reconciliation report
- data lineage

---

## 7. 계정계 원장 설계

### 7.1 핵심 원칙

```text
모든 거래는 차변/대변 posting 합계가 0이어야 한다.
잔액은 직접 수정하지 않고 posting의 결과로만 바뀐다.
한 번 확정된 거래는 수정하지 않고 reversal 거래로만 정정한다.
모든 외부 요청은 idempotency key를 가져야 한다.
모든 수동 조작은 maker-checker와 감사로그를 남긴다.
```

### 7.2 최소 테이블

```text
customers
customer_kyc_profiles
accounts
account_limits
account_holds
ledger_transactions
ledger_postings
account_balances
idempotency_keys
daily_closings
reconciliation_items
audit_events
operator_approvals
operator_sessions
screen_access_logs
masking_access_logs
```

### 7.3 Ledger Transaction

필드 예시:

```text
id
transaction_type
business_reference_id
idempotency_key
status
requested_by
requested_channel
created_at
posted_at
reversed_by_transaction_id
metadata_json
```

### 7.4 Ledger Posting

필드 예시:

```text
id
ledger_transaction_id
account_id
currency
direction       # DEBIT or CREDIT
amount
posting_type    # PRINCIPAL, FEE, TAX, HOLD, REVERSAL
created_at
```

### 7.5 Balance Projection

`account_balances`는 원장이 아니라 projection/cache로 취급한다. source of truth는 `ledger_transactions`와 `ledger_postings`다.

필드 예시:

```text
account_id
currency
ledger_balance
available_balance
hold_amount
last_posting_id
version
updated_at
```

### 7.6 원장 불변식

모든 테스트와 배치 검증에서 아래를 확인한다.

```text
sum(postings by transaction) == 0
balance == sum(postings by account)
available_balance <= ledger_balance
idempotent request creates at most one transaction
closed day cannot be mutated
reversal references original transaction
no posting can exist without a ledger transaction
no ledger transaction can be PARTIALLY_POSTED after finalization
```

---

## 8. 화면을 많이 만들기 위한 핵심 전략

화면을 직접 손코딩하지 말고 아래 4개 템플릿을 만든다.

### 8.1 Inquiry Template

조회형 화면이다.

예시:

- 고객조회
- 계좌조회
- 거래내역조회
- 접속이력조회
- 감사로그조회
- 배치결과조회

공통 구성:

```text
검색조건
결과 테이블
상세 패널
엑셀 다운로드 권한
마스킹/언마스킹 요청
조회 사유 입력
감사로그
```

### 8.2 Command Template

처리형 화면이다.

예시:

- 계좌상태변경
- 지급정지
- 한도변경
- 수수료면제
- 고객정보변경

공통 구성:

```text
대상 조회
변경 전/후 비교
업무 사유
첨부파일
검증
승인 요청 또는 즉시 처리
처리 결과
감사로그
```

### 8.3 Case Template

민원, AML, FDS, 장애, 정산 미결 같은 케이스형 화면이다.

공통 구성:

```text
케이스 목록
상태
담당자
SLA
코멘트
첨부파일
내부 메모
승인
종결
재오픈
```

### 8.4 Parameter Template

운영 파라미터 화면이다.

예시:

- 상품금리
- 수수료
- 이체한도
- 공휴일
- 메뉴권한
- FDS 룰
- AML 룰

공통 구성:

```text
현재 적용값
예약 적용값
변경 이력
영향도
승인
적용일
rollback
```

---

## 9. Screen Manifest DSL

화면 하나를 추가할 때 React 화면을 새로 만들지 말고 manifest를 작성한다.

### 9.1 Inquiry Manifest 예시

```yaml
screenId: CST-001
title: 고객 통합 조회
app: staff-terminal
type: INQUIRY
domain: customer
requiredRoles:
  - BRANCH_STAFF
  - CALL_CENTER
layout:
  template: inquiry
  customerContext: true
audit:
  enabled: true
  reasonRequired: true
  piiAccess: true
  maskingPolicy: CUSTOMER_PII
query:
  endpoint: GET /api/staff/customers/search
  fields:
    - name: customerName
      label: 고객명
      type: text
    - name: phone
      label: 휴대폰번호
      type: text
      mask: phone
    - name: customerId
      label: 고객ID
      type: text
resultTable:
  columns:
    - customerId
    - customerName
    - customerGrade
    - riskGrade
    - lastLoginAt
actions:
  - id: openCustomer360
    label: 고객 360 열기
    type: navigate
    target: CST-002
```

### 9.2 Command Manifest 예시

```yaml
screenId: ACC-103
title: 계좌 지급정지
app: staff-terminal
type: COMMAND
domain: account
requiredRoles:
  - BRANCH_MANAGER
  - CALL_CENTER_MANAGER
layout:
  template: command
  customerContext: true
approval:
  required: true
  approverRole: BRANCH_MANAGER
audit:
  enabled: true
  reasonRequired: true
  piiAccess: true
fields:
  - name: accountNo
    label: 계좌번호
    type: account-search
    required: true
  - name: reasonCode
    label: 지급정지 사유
    type: select
    required: true
    options:
      - LOST
      - FRAUD
      - COURT_ORDER
      - CUSTOMER_REQUEST
  - name: description
    label: 상세 사유
    type: textarea
    required: true
attachments:
  required: false
api:
  command: POST /api/staff/accounts/{accountNo}/holds
postActions:
  - createAuditEvent
  - notifyCustomer
```

### 9.3 Case Manifest 예시

```yaml
screenId: CMP-201
title: 민원 처리 상세
app: staff-terminal
type: CASE
domain: complaint
requiredRoles:
  - COMPLAINT_HANDLER
  - COMPLAINT_MANAGER
workflow:
  name: complaintWorkflow
  states:
    - RECEIVED
    - CLASSIFIED
    - ASSIGNED
    - IN_REVIEW
    - WAITING_CUSTOMER
    - WAITING_APPROVAL
    - ANSWERED
    - CLOSED
    - REOPENED
sla:
  enabled: true
  targetHours: 72
audit:
  enabled: true
  reasonRequired: false
sections:
  - complaintSummary
  - customerContext
  - relatedAccounts
  - relatedTransactions
  - internalMemo
  - attachments
  - answerDraft
  - approvalTimeline
```

---

## 10. 직원 통합단말 설계

### 10.1 Shell UI

```text
상단
├─ 직원명 / 부점 / 역할 / 세션시간 / 잠금
├─ 고객 컨텍스트
├─ 거래코드 직접 입력
└─ 비상 메뉴

좌측
├─ 고객
├─ 계좌
├─ 입출금
├─ 이체
├─ 대출
├─ 카드
├─ 민원
├─ AML/FDS
├─ 정산
├─ 승인
├─ 보고서
└─ 시스템

중앙
├─ 탭 기반 업무 화면
├─ 조회 조건
├─ 결과 테이블
├─ 상세 패널
└─ 처리 버튼

우측
├─ 고객 360 요약
├─ 최근 거래
├─ 유의사항
├─ 리스크 알림
└─ 감사 로그 요약
```

### 10.2 직원 단말 화면 목록

#### 고객

- 고객통합조회
- 고객상세
- 고객정보변경
- 연락처변경
- 주소변경
- 직업정보변경
- 고객등급조회
- 고객 360 요약
- 가족/관계자 정보 시뮬레이션
- 마케팅동의 변경

#### KYC

- 고객확인 등록
- 고객확인 갱신
- 실명확인 결과조회
- 고위험고객 심사
- 실제소유자 등록
- 자금원천 등록
- 거래목적 등록

#### 계좌

- 계좌조회
- 계좌개설
- 계좌해지
- 계좌상태변경
- 지급정지
- 지급정지 해제
- 압류등록
- 압류해제
- 계좌별 한도조회
- 계좌 메모 관리

#### 잔액/거래

- 잔액조회
- 거래내역조회
- 일자별 잔액조회
- 보류금액 조회
- 원장 posting 조회
- 거래 상세 조회
- 이체확인증 조회

#### 이체

- 당행이체
- 타행이체 시뮬레이션
- 예약이체
- 예약이체 취소
- 이체취소
- 이체한도변경
- 자주 쓰는 계좌 조회

#### 수수료/상품

- 수수료 조회
- 수수료 면제 요청
- 수수료 정책 변경
- 예금상품조회
- 상품가입
- 상품해지
- 금리변경 이력

#### 대출 시뮬레이션

- 대출신청 조회
- 한도조회
- 상환스케줄
- 연체조회
- 대출상태변경

#### 카드 시뮬레이션

- 카드신청
- 카드상태변경
- 승인내역조회
- 분실신고
- 재발급 신청

#### 민원

- 민원접수조회
- 민원상세
- 담당자배정
- 답변등록
- 답변승인
- 민원종결
- 재심요청
- 민원 SLA 대시보드

#### AML

- 의심거래 후보
- STR 케이스
- 고객위험등급
- 거래패턴 조회
- 고위험국가 룰
- 고액현금거래 시뮬레이션

#### FDS

- 이상거래 알림
- 디바이스 이력
- 거래 차단/해제
- 룰 적중 이력
- velocity rule 관리
- 의심 로그인 조회

#### 승인

- 나의 승인함
- 부점 승인함
- 반려함
- 승인 이력
- 위임 승인 설정
- 긴급 승인 이력

#### 정산

- 내부원장 대사
- 외부파일 대사
- 미결 항목
- 정산 재처리
- 정산 리포트

#### 배치

- 일마감
- 월마감
- 배치 실행현황
- 실패 작업 재시도
- 배치 파라미터

#### 감사/보안

- 직원 행위 로그
- 고객정보 조회 로그
- 권한 변경 로그
- 다운로드 이력
- 마스킹 해제 이력
- 관리자 로그인 이력
- break-glass 사용 이력

#### 시스템

- 코드관리
- 메뉴관리
- 권한관리
- 공휴일관리
- 한도관리
- 상품파라미터
- 공지사항관리
- 알림 템플릿 관리

---

## 11. 고객 웹뱅킹 설계

### 11.1 고객 웹 화면 목록

#### 인증

- 로그인
- MFA
- 기기등록
- 비밀번호 재설정
- 세션만료
- 접속 이력

#### 가입/KYC 시뮬레이션

- 회원가입
- 약관동의
- 본인확인 시뮬레이션
- 고객정보 입력
- 고객확인 추가정보 입력

#### 메인

- 보유계좌 요약
- 총잔액
- 최근거래
- 알림
- 보안 상태

#### 계좌

- 계좌목록
- 계좌상세
- 거래내역
- 거래내역 다운로드
- 이체확인증

#### 이체

- 즉시이체
- 예약이체
- 예약이체 조회
- 자주 쓰는 계좌
- 이체 결과
- 실패/보류 상태 안내

#### 한도/보안

- 이체한도 조회
- 한도변경 신청
- 기기관리
- 로그인 알림 설정
- 비밀번호 변경

#### 상품

- 예금상품 목록
- 상품상세
- 상품가입 시뮬레이션
- 상품해지 신청

#### 민원

- 민원접수
- 내 민원조회
- 답변확인
- 추가자료 제출
- 이의제기/재심 요청

#### 개인정보

- 내 정보 조회
- 연락처 변경
- 주소 변경
- 마케팅 동의 변경

---

## 12. 전자민원 포털 설계

전자민원은 고객 채널, 운영계, 내부통제, SLA, 첨부파일, 답변 승인, 감사로그를 모두 보여줄 수 있으므로 반드시 깊게 구현한다.

### 12.1 고객 화면

```text
민원 유형 선택
본인확인 시뮬레이션
민원 내용 작성
계좌/거래 선택
첨부파일 업로드
접수 완료
처리상태 조회
담당자 답변 확인
추가자료 제출
이의제기/재심 요청
```

### 12.2 직원 처리 화면

```text
민원 접수함
자동 분류 결과
담당자 배정
고객/계좌/거래 연결
처리 SLA 타이머
내부 검토 메모
부서 이관
답변 초안 작성
승인 요청
고객 회신
종결/재발방지 등록
```

### 12.3 전자민원 상태값

```text
DRAFT
SUBMITTED
RECEIVED
CLASSIFIED
ASSIGNED
IN_REVIEW
WAITING_CUSTOMER
WAITING_APPROVAL
ANSWERED
CLOSED
REOPENED
TRANSFERRED_TO_AUTHORITY_SIM
```

### 12.4 민원 workflow rule

- SUBMITTED 후 자동으로 RECEIVED 전환
- 유형 분류 후 CLASSIFIED 전환
- 담당자 배정 후 ASSIGNED 전환
- 담당자 작업 시작 시 IN_REVIEW 전환
- 고객 추가자료 요청 시 WAITING_CUSTOMER 전환
- 답변 초안 완료 시 WAITING_APPROVAL 전환
- 책임자 승인 후 ANSWERED 전환
- 고객 확인 또는 기한 경과 후 CLOSED 전환
- 이의제기 시 REOPENED 전환

---

## 13. 운영계 핵심 엔진

### 13.1 Maker-Checker Engine

모든 고위험 업무는 신청자와 승인자가 달라야 한다.

대상 업무:

- 고객정보 변경
- 이체한도 상향
- 계좌 지급정지/해제
- 수수료 면제
- 상품 파라미터 변경
- FDS 차단 해제
- AML case 종결
- 민원 답변 발송
- 권한 부여/회수
- 마스킹 해제 대량 승인

필수 필드:

```text
approval_id
business_type
business_reference_id
requested_by
requested_at
request_reason
before_snapshot_json
after_snapshot_json
status
approved_by
approved_at
rejected_by
rejected_at
reject_reason
audit_event_id
```

### 13.2 Audit Log Engine

감사로그는 append-only로 저장한다.

필수 이벤트:

```text
LOGIN_SUCCESS
LOGIN_FAILURE
CUSTOMER_SEARCH
CUSTOMER_DETAIL_VIEW
PII_UNMASK_REQUESTED
PII_UNMASK_APPROVED
PII_DOWNLOADED
ACCOUNT_VIEW
TRANSACTION_VIEW
COMMAND_REQUESTED
COMMAND_APPROVED
COMMAND_REJECTED
COMMAND_EXECUTED
PARAMETER_CHANGED
ROLE_CHANGED
BATCH_STARTED
BATCH_FAILED
BATCH_RETRIED
BREAK_GLASS_USED
```

필수 필드:

```text
audit_event_id
event_type
actor_type
actor_id
actor_role
branch_id
screen_id
business_reference_id
customer_id
account_id
reason
ip_address
user_agent
device_id
before_hash
payload_hash
previous_event_hash
created_at
```

### 13.3 Masking Engine

권한별로 개인정보 노출 수준을 다르게 한다.

예시:

```text
이름: 홍*동
휴대폰: 010-****-1234
주민번호: 900101-1******
계좌번호: 123-***-******
주소: 서울시 강남구 ***
```

마스킹 해제 조건:

- 특정 권한 보유
- 업무 사유 입력
- 시간 제한
- 고객 또는 계좌 context 존재
- audit event 기록
- 필요 시 승인 workflow

### 13.4 Workflow Engine

재사용 대상:

- 전자민원
- AML case
- FDS case
- 정산 미결
- 고객정보 변경
- 이체한도 변경
- 수수료 면제
- 장애 티켓

필수 구성:

```text
workflow_definition
workflow_instance
workflow_state
workflow_transition
workflow_actor
workflow_comment
workflow_attachment
workflow_sla
workflow_audit_event
```

---

## 14. AML/FDS 시뮬레이션

### 14.1 AML

구현 대상:

- 고객위험등급
- 고위험 고객 EDD
- 의심거래 후보 생성
- STR case simulation
- case reviewer assignment
- case comment
- case approval
- case closure

Rule 예시:

```text
고액 반복 입금
짧은 시간 내 다수 계좌 이체
고위험 국가 관련 거래 시뮬레이션
고위험 고객의 비정상 거래 증가
거래 목적과 불일치하는 패턴
```

### 14.2 FDS

구현 대상:

- device fingerprint simulation
- velocity rule
- unusual amount
- unusual location
- first-time beneficiary
- suspicious login
- transaction hold
- reviewer release/block

Rule 예시:

```text
신규 기기 + 고액 이체
최근 10분 내 5회 이상 실패 후 성공
평소 평균 대비 10배 이상 금액
새 수취인에게 야간 고액 이체
다중 IP에서 짧은 시간 로그인
```

---

## 15. 정산/대사 설계

은행 시스템은 거래 성공만으로 끝나지 않는다. 매일 내부 원장, 외부기관 파일, 수수료, 미결 항목이 맞는지 검증해야 한다.

### 15.1 대상

- 내부 원장 합계
- 계좌별 잔액 projection
- 외부기관 거래 파일 simulation
- 수수료 정산
- 실패/취소/반려 거래
- 미결 항목

### 15.2 Reconciliation Item 상태

```text
OPEN
INVESTIGATING
MATCHED
ADJUSTMENT_REQUESTED
ADJUSTED
WAIVED
CLOSED
```

### 15.3 대사 불변식

```text
internal_total == external_total for matched set
unmatched item must have case owner
adjustment requires maker-checker approval
closed business day cannot be mutated directly
correction must use adjustment/reversal transaction
```

---

## 16. 신뢰성 검증 전략

### 16.1 Unit Test

- domain rule
- validation
- ledger posting
- fee calculation
- masking policy
- workflow transition

### 16.2 Integration Test

- DB transaction boundary
- outbox event
- Kafka consumer
- Redis lock if used
- idempotency
- approval workflow

### 16.3 Property-based Test

무작위 입금, 출금, 이체, 취소, 재시도, 장애를 생성하고 원장 불변식을 검증한다.

검증 항목:

```text
sum(postings by transaction) == 0
balance == sum(postings by account)
available_balance <= ledger_balance
idempotent request creates at most one transaction
closed day cannot be mutated
reversal references original transaction
```

### 16.4 Concurrency Test

시나리오:

```text
동일 계좌에서 100개 동시 출금
동일 idempotency key 100회 재시도
API timeout 후 client retry
DB primary kill simulation
Kafka consumer delayed
외부기관 응답 지연
마감 중 거래 요청
```

### 16.5 E2E Test

Playwright로 다음 시나리오를 자동화한다.

- 고객 로그인 → 계좌조회 → 이체 → 거래내역 확인
- 고객 전자민원 접수 → 직원 배정 → 답변 승인 → 고객 확인
- 직원 고객정보 변경 요청 → 책임자 승인 → 감사로그 확인
- 고액 이체 → FDS 보류 → 직원 심사 → 거래 승인/차단
- 일마감 → 대사 → 미결 생성 → 운영자 처리

### 16.6 Failure Drill

장애훈련 리포트를 자동 생성한다.

항목:

```text
장애명
장애 주입 방법
예상 영향
실제 영향
RPO/RTO
원장 불변식 결과
복구 절차
재발방지
증적 링크
```

---

## 17. 핵심 데모 시나리오

### 17.1 고객 이체 → FDS 탐지 → 직원 심사

```text
1. 고객 웹에서 고액 이체 시도
2. FDS rule 적중
3. 거래 보류
4. 직원 단말에 이상거래 케이스 생성
5. 직원이 고객/계좌/거래 조회
6. 심사 메모 작성
7. 책임자 승인
8. 거래 승인 또는 차단
9. 고객에게 결과 알림
10. 감사로그 기록
```

### 17.2 고객 전자민원 → 직원 처리 → 승인 후 답변

```text
1. 고객이 전자민원 접수
2. 민원번호 생성
3. 자동 유형 분류
4. 담당 부서 배정
5. 직원이 민원 검토
6. 고객 거래내역 확인
7. 답변 초안 작성
8. 팀장 승인
9. 고객에게 답변 발송
10. 민원 종결
11. 민원 처리 리포트 생성
```

### 17.3 고객정보 변경 → 승인 → 감사

```text
1. 직원이 고객 연락처 변경 신청
2. 고객정보 조회 사유 입력
3. 변경 전/후 비교
4. 승인 요청
5. 책임자 승인
6. 고객정보 변경
7. 고객 알림
8. 감사 포털에서 변경 이력 확인
```

### 17.4 일마감 → 대사 → 미결 처리

```text
1. 하루 거래 생성
2. 일마감 배치 실행
3. 원장 합계 검증
4. 외부기관 파일과 대사
5. 불일치 항목 생성
6. 운영자가 미결 케이스 처리
7. 정산 리포트 생성
```

---

## 18. 구현 단계

### Phase 1. Foundation

목표:

- monorepo 구성
- auth skeleton
- common UI
- screen shell
- DB migration
- audit log base
- synthetic data generator

완료 기준:

- local docker-compose로 전체 실행
- 고객 웹, 직원 단말, 전자민원 포털 shell 접속 가능
- Keycloak 또는 mock auth로 역할별 로그인 가능
- 감사로그 테이블 생성 및 기본 이벤트 기록

### Phase 2. Ledger Core

목표:

- customer/account/ledger/posting/balance 구현
- deposit/withdraw/internal transfer 구현
- idempotency 구현
- reversal 구현
- ledger invariant test 구현

완료 기준:

- posting 합계 0 검증
- 동시 출금 테스트 통과
- idempotency retry 테스트 통과
- reversal 테스트 통과

### Phase 3. Staff Terminal MVP

목표:

- 직원 단말 shell
- 거래코드 검색
- 고객 컨텍스트
- 고객조회
- 계좌조회
- 거래내역조회
- 고객정보 변경 요청
- 승인함
- 감사로그 조회

완료 기준:

- 직원이 고객 조회 시 사유 입력 및 감사로그 생성
- 고객정보 변경은 maker-checker 승인 후 반영
- 마스킹/언마스킹 정책 동작

### Phase 4. Customer Web MVP

목표:

- 고객 로그인
- 계좌목록
- 계좌상세
- 거래내역
- 이체
- 이체결과
- 민원접수 진입

완료 기준:

- 고객 이체가 ledger posting으로 반영
- 고객 거래내역과 직원 거래내역이 같은 source of truth를 조회
- 이체 실패/보류 상태를 표현

### Phase 5. Complaint Workflow

목표:

- 민원 접수
- 민원 상태전이
- 담당자 배정
- 답변 초안
- 승인 후 회신
- 고객 답변 확인

완료 기준:

- 고객 포털과 직원 단말이 같은 complaint case를 공유
- 답변 발송 전 승인 필요
- SLA와 처리 이력 표시

### Phase 6. AML/FDS + Reconciliation

목표:

- FDS rule engine
- AML case simulation
- transaction hold/release
- 일마감
- 대사
- 미결 처리

완료 기준:

- 고액 이체 시 FDS case 생성
- 운영자가 승인/차단 가능
- 일마감 후 원장 합계 검증
- 대사 불일치가 case로 생성됨

### Phase 7. Evidence Pack

목표:

- ADR
- threat model
- ASVS mapping
- regulatory mapping
- test report
- failure drill report
- reconciliation report
- demo video script

완료 기준:

- `docs/test-evidence/`에 자동 생성 리포트 저장
- `docs/demo-scenarios/`에 시나리오별 캡처/명령어/결과 정리
- README에서 전체 아키텍처와 검증 결과 설명

---

## 19. Codex 작업 규칙

Codex는 한 번에 큰 기능을 구현하려 하지 말고, 아래 루프를 따른다.

```text
1. 요구사항을 읽는다.
2. 관련 domain invariant를 정리한다.
3. 변경 범위를 최소화한다.
4. 테스트를 먼저 작성하거나 최소한 acceptance test를 명시한다.
5. 구현한다.
6. unit/integration/e2e test를 실행한다.
7. 실패하면 원인을 분석하고 수정한다.
8. ADR 또는 evidence를 업데이트한다.
9. PR 설명을 작성한다.
```

금지:

- 원장 테이블 직접 update/delete
- 잔액을 임의로 수정
- 감사로그 없이 고객정보 노출
- 승인 없이 고위험 운영 업무 반영
- 테스트 없이 핵심 원장 로직 변경
- 실제 개인정보 예시 사용
- 외부 실제 금융망 호출

---

## 20. Codex Skill 설계

### 20.1 bank-screen-generator/SKILL.md

```markdown
# bank-screen-generator

Use this skill when adding or modifying a banking business screen.

## Rules

- Do not hand-code one-off screens if the screen can be represented by a screen manifest.
- Every screen must declare screenId, app, type, domain, roles, audit, and API contract.
- Inquiry screens must support search criteria, result table, detail panel, masking, and audit logging.
- Command screens must support before/after snapshot, reason, validation, approval decision, and audit logging.
- Case screens must support state, owner, SLA, comments, attachments, and workflow timeline.
- Parameter screens must support effective date, change history, approval, and rollback.

## Output

- screen manifest
- generated route/page
- API client binding
- menu registration
- Playwright smoke test
```

### 20.2 bank-api-generator/SKILL.md

```markdown
# bank-api-generator

Use this skill when creating backend APIs for banking screens.

## Rules

- Define OpenAPI contract first.
- Add DTO validation.
- Add role authorization.
- Add audit logging for staff/customer-sensitive APIs.
- Add idempotency for command APIs that mutate financial state.
- Do not expose unmasked PII by default.
- Include error codes and business failure states.

## Output

- controller
- service
- DTOs
- validation
- tests
- OpenAPI update
```

### 20.3 ledger-invariant-review/SKILL.md

```markdown
# ledger-invariant-review

Use this skill whenever ledger, account balance, transfer, fee, hold, reversal, or closing logic changes.

## Required checks

- Does every transaction have balanced postings?
- Is balance derived from postings?
- Is idempotency enforced?
- Are retries safe?
- Are reversal transactions used instead of mutation?
- Can concurrent requests break available balance?
- Are closed days immutable?
- Are audit events emitted?

## Output

- invariant checklist
- missing tests
- risk assessment
- suggested fixes
```

### 20.4 maker-checker-review/SKILL.md

```markdown
# maker-checker-review

Use this skill for staff operations that can change customer, account, limit, fee, risk, security, or parameter state.

## Required checks

- Is this operation high-risk?
- Does it require approval?
- Is requester different from approver?
- Is before/after snapshot stored?
- Is reject/reapply supported?
- Is audit log created?
- Is customer notification required?

## Output

- approval policy
- workflow changes
- test cases
```

### 20.5 audit-log-review/SKILL.md

```markdown
# audit-log-review

Use this skill when adding any staff screen or sensitive customer operation.

## Required checks

- Is access logged?
- Is reason required?
- Is PII masked by default?
- Is unmasking logged separately?
- Are downloads logged?
- Are admin actions logged?
- Is event hash chaining maintained?

## Output

- audit event list
- missing log points
- test cases
```

### 20.6 complaint-workflow-builder/SKILL.md

```markdown
# complaint-workflow-builder

Use this skill when implementing electronic complaint intake and handling.

## Rules

- Customer and staff views must share the same complaint case.
- Every case must have status, owner, SLA, comments, attachments, and timeline.
- Answer must require approval before customer delivery.
- Reopen and additional-document flows must be supported.
- All status transitions must be audited.

## Output

- workflow definition
- state transition tests
- UI manifest
- API contract
```

### 20.7 banking-e2e-scenario/SKILL.md

```markdown
# banking-e2e-scenario

Use this skill to create end-to-end demo and regression scenarios.

## Required scenarios

- customer transfer to ledger posting
- high-risk transfer to FDS case
- complaint intake to staff answer approval
- staff customer info change to maker-checker approval
- EOD closing to reconciliation report

## Output

- Playwright test
- seed data
- expected audit events
- evidence report
```

### 20.8 evidence-pack-builder/SKILL.md

```markdown
# evidence-pack-builder

Use this skill at the end of each milestone.

## Collect

- architecture diagram
- ADRs
- API contracts
- test results
- ledger invariant results
- E2E screenshots
- failure drill report
- reconciliation report
- audit log sample
- security scan results

## Output

- docs/test-evidence/{milestone}.md
- docs/demo-scenarios/{scenario}.md
- README update
```

---

## 21. 첫 번째 Codex 실행 프롬프트

아래 프롬프트를 Codex 첫 작업으로 사용한다.

```text
이 저장소에 Bank-grade Core Banking Lab의 초기 골격을 만들어줘.

목표는 실제 고객 자금이나 실제 금융망을 다루지 않는 모의 은행 시스템이지만, 구조는 은행급으로 설계하는 것이다.

먼저 다음을 구현해줘.

1. monorepo 구조 생성
2. apps/customer-web, apps/staff-terminal, apps/complaint-portal shell 생성
3. services/core-banking 기본 Spring Boot/Kotlin 또는 Java 프로젝트 생성
4. PostgreSQL migration 초안 작성
5. customers, accounts, ledger_transactions, ledger_postings, account_balances, idempotency_keys, audit_events, operator_approvals 테이블 생성
6. double-entry ledger의 deposit, withdraw, internal transfer use case 구현
7. idempotency key 처리 구현
8. ledger invariant unit/integration test 작성
9. apps/staff-terminal에 직원 단말 shell 구현: 좌측 메뉴, 거래코드 입력, 탭 영역, 고객 컨텍스트, 감사로그 패널
10. docs/adr/0001-architecture.md 작성
11. README에 실행 방법과 아키텍처 요약 작성

제약:

- 잔액은 직접 수정하지 말고 posting 결과로만 갱신한다.
- 한 번 확정된 거래는 수정하지 않는다.
- 고객정보 조회나 직원 조작은 audit_events에 기록한다.
- 실제 개인정보나 실제 금융기관 API는 절대 사용하지 않는다.
- 모든 데이터는 synthetic seed data만 사용한다.
- 테스트 없이 원장 로직을 완료 처리하지 않는다.

완료 후 다음을 보고해줘.

- 생성/수정한 주요 파일
- 실행 명령어
- 통과한 테스트
- 아직 남은 리스크
- 다음 작업 제안
```

---

## 22. 두 번째 Codex 실행 프롬프트: 직원 통합단말 확장

```text
직원 통합단말을 은행 업무 단말처럼 확장해줘.

구현 대상:

1. screen manifest 기반 화면 엔진
2. Inquiry Template
3. Command Template
4. Case Template
5. Parameter Template
6. 고객통합조회 CST-001
7. 고객상세 CST-002
8. 계좌조회 ACC-001
9. 거래내역조회 TRX-001
10. 계좌 지급정지 ACC-103
11. 나의 승인함 APR-001
12. 감사로그조회 AUD-001

필수 조건:

- 모든 화면은 screenId를 가져야 한다.
- 직원 단말은 거래코드 직접 입력으로 화면을 열 수 있어야 한다.
- 고객정보 조회 시 조회 사유를 입력해야 한다.
- PII는 기본적으로 마스킹되어야 한다.
- 마스킹 해제는 별도 이벤트로 감사로그를 남겨야 한다.
- 계좌 지급정지는 maker-checker 승인 대상이어야 한다.
- 각 화면에 Playwright smoke test를 추가한다.

완료 후 화면 목록, manifest 목록, 테스트 결과, 남은 리스크를 보고해줘.
```

---

## 23. 세 번째 Codex 실행 프롬프트: 전자민원

```text
전자민원 포털과 직원 민원 처리 workflow를 구현해줘.

고객 화면:

1. 민원 유형 선택
2. 본인확인 시뮬레이션
3. 민원 내용 작성
4. 계좌/거래 선택
5. 첨부파일 업로드 mock
6. 접수 완료
7. 처리상태 조회
8. 담당자 답변 확인
9. 추가자료 제출
10. 이의제기/재심 요청

직원 화면:

1. 민원 접수함
2. 자동 분류 결과
3. 담당자 배정
4. 고객/계좌/거래 연결
5. SLA 타이머
6. 내부 검토 메모
7. 답변 초안 작성
8. 승인 요청
9. 고객 회신
10. 종결 처리

Workflow states:

DRAFT, SUBMITTED, RECEIVED, CLASSIFIED, ASSIGNED, IN_REVIEW, WAITING_CUSTOMER, WAITING_APPROVAL, ANSWERED, CLOSED, REOPENED, TRANSFERRED_TO_AUTHORITY_SIM

필수 조건:

- 고객과 직원은 같은 complaint case를 봐야 한다.
- 답변 발송 전 maker-checker 승인이 필요하다.
- 모든 상태전이는 audit event를 남긴다.
- SLA 초과 여부를 표시한다.
- Playwright E2E를 작성한다.

완료 후 demo scenario 문서를 docs/demo-scenarios/complaint-workflow.md에 작성해줘.
```

---

## 24. 네 번째 Codex 실행 프롬프트: FDS/AML/정산

```text
FDS, AML, reconciliation을 은행 운영계 시나리오로 구현해줘.

FDS:

- high amount transfer rule
- new device + high amount rule
- first-time beneficiary rule
- velocity rule
- transaction hold
- reviewer release/block

AML:

- customer risk grade
- suspicious transaction candidate
- STR case simulation
- reviewer assignment
- approval and closure

Reconciliation:

- EOD closing
- ledger total validation
- external institution file simulator
- unmatched item creation
- adjustment request
- maker-checker approval for adjustment

필수 조건:

- FDS 보류 거래는 원장 상태와 고객 화면에 일관되게 표현되어야 한다.
- AML/FDS case는 운영자 포털에서 볼 수 있어야 한다.
- 일마감 후 closed day의 원장 거래는 직접 수정할 수 없어야 한다.
- 정산 보정은 reversal 또는 adjustment transaction으로만 처리한다.
- evidence report를 docs/test-evidence/fds-aml-reconciliation.md에 작성한다.
```

---

## 25. 최종 README 구성

README는 아래 순서로 작성한다.

```text
1. 프로젝트 개요
2. 왜 단순 은행 클론이 아닌가
3. 전체 아키텍처
4. 계정계 원장 설계
5. 직원 통합단말
6. 고객 웹뱅킹
7. 전자민원 workflow
8. AML/FDS simulation
9. 정산/대사
10. 보안/감사/내부통제
11. 테스트 전략
12. 장애훈련
13. 실행 방법
14. 데모 시나리오
15. 한계와 법적 경계
16. 향후 개선
```

---

## 26. 완료 정의

이 프로젝트가 포트폴리오로 설득력을 가지려면 최소한 아래를 만족해야 한다.

### 기능

- 고객 웹에서 계좌조회와 이체 가능
- 직원 단말에서 고객/계좌/거래 조회 가능
- 직원 단말에서 계좌 지급정지, 고객정보 변경, 승인함 사용 가능
- 전자민원 접수와 답변 승인 workflow 가능
- FDS 고위험 거래 보류와 심사 가능
- 일마감과 대사 가능

### 정합성

- double-entry posting 합계 0 유지
- idempotency retry 안전
- 동시 출금 안전
- reversal 가능
- closed day 직접 수정 불가

### 통제

- RBAC/ABAC 적용
- PII masking 적용
- 고객정보 조회 사유 기록
- maker-checker 적용
- audit log append-only
- 운영자 행위 추적 가능

### 증적

- ADR 존재
- threat model 존재
- 테스트 결과 존재
- 장애훈련 결과 존재
- 대사 리포트 존재
- 감사로그 샘플 존재
- 데모 시나리오 문서 존재

---

## 27. 리뷰 체크리스트

Codex는 PR마다 아래를 확인한다.

```text
[ ] 이 변경이 원장 정합성에 영향을 주는가?
[ ] ledger invariant test가 있는가?
[ ] idempotency가 필요한 API인가?
[ ] 고객정보 또는 민감정보가 노출되는가?
[ ] 마스킹 정책이 적용되는가?
[ ] audit event가 필요한가?
[ ] maker-checker가 필요한 업무인가?
[ ] 상태전이가 불법 상태를 허용하지 않는가?
[ ] 장애/재시도 시 중복 처리가 발생하지 않는가?
[ ] E2E 또는 integration test가 있는가?
[ ] 문서/ADR/evidence가 업데이트되었는가?
```

---

## 28. 참고 링크

- OpenAI Codex Skills: https://developers.openai.com/codex/skills
- OpenAI Codex AGENTS.md: https://developers.openai.com/codex/guides/agents-md
- OpenAI Codex Best Practices: https://developers.openai.com/codex/learn/best-practices
- 전자금융감독규정: https://www.law.go.kr/LSW//admRulInfoP.do?admRulSeq=2100000274812&chrClsCd=010201
- 금융보안원 금융분야 클라우드컴퓨팅서비스 이용 가이드: https://www.fsec.or.kr/bbs/detail?bbsNo=11691&menuNo=222
- OWASP ASVS: https://owasp.org/www-project-application-security-verification-standard/
- NIST Cybersecurity Framework: https://www.nist.gov/cyberframework
- Basel Committee Operational Resilience: https://www.bis.org/bcbs/publ/d516.htm
- BCBS 239: https://www.bis.org/publ/bcbs239.htm
- OpenTelemetry: https://opentelemetry.io/
- Jepsen: https://jepsen.io/
