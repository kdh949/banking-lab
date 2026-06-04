목표: banking-lab 저장소를 “은행급 제어·검증 중심 실험실”에서 “실제로 조작 가능한 은행 업무 채널 플랫폼”으로 끌어올린다.

현재 우선순위는 백엔드 확장이 아니라 화면 깊이, manifest renderer 실사용화, 화면 수 확장, formal model, CI 자동화다.

반드시 지킬 원칙:
1. 실제 고객정보, 실제 송금, 실제 금융망, 실제 KYC, 실제 외부 금융 API는 절대 사용하지 않는다.
2. 모든 데이터와 외부 연계는 synthetic data와 simulator만 사용한다.
3. 기존 Node reference/legacy 경로는 삭제하지 말고, 현재 target-stack 동작의 oracle/reference로만 유지한다.
4. 구현 완료를 과장하지 않는다. 테스트하지 못한 것은 테스트하지 못했다고 기록한다.
5. docs-only 변경으로 완료 처리하지 않는다. 실제 동작하는 코드, 테스트, 증적을 함께 만든다.
6. AGENTS.md가 있으면 먼저 읽고 따른다. `.agents/skills` 아래 repo-scoped skill도 확인하고, 특히 manifest/screen/maker-checker/audit 관련 skill이 있으면 사용한다.
7. 모든 변경 후 가능한 테스트를 실행하고, 실패 시 원인을 고치거나 정확히 기록한다.
8. 최종 응답에는 변경 파일, 실행 명령어, 통과/실패 테스트, 남은 한계를 명확히 적는다.

작업 전 반드시 읽을 파일:
- README.md
- CODEX_FULL_REWRITE_PLAN.md
- package.json
- docs/migration/node-retirement-gate.json
- docs/migration/parity-scenarios.json
- packages/screen-engine/src/types.ts
- packages/screen-engine/src/manifest.ts
- apps/staff-terminal/src/app/page.tsx
- apps/staff-terminal/src/components/ApiBackedStaffPanel.tsx
- apps/customer-web/src/app/page.tsx
- packages/api-client/src/index.ts
- playwright.config.ts
- .agents/skills/*/SKILL.md 중 현재 작업에 관련 있는 것

작업 1: 직원 통합단말을 실제 업무 단말처럼 개선한다.

현재 staff-terminal은 manifest card/workbench에 가깝다. 이를 다음 구조로 바꿔라.

필수 UI:
- 거래코드 검색/입력 바
- 거래코드 또는 화면명 검색 결과
- 탭 기반 업무 화면
- 고객 컨텍스트 패널
- 업무 사유 입력
- 검색조건 영역
- 결과 테이블
- 상세 패널
- command action 영역
- maker-checker 승인 요청/승인 상태 패널
- 감사 타임라인
- 마스킹/언마스킹 상태 표시
- 오류/권한거부/업무상태 위반 structured error 표시

필수 동작:
- screen manifest 목록을 읽어 거래코드로 화면을 열 수 있어야 한다.
- 화면을 열면 탭으로 추가되어야 한다.
- INQUIRY 화면은 검색조건, 결과 테이블, 상세 패널을 렌더링해야 한다.
- COMMAND 화면은 입력 폼, 검증 메시지, 실행 버튼, 승인 필요 여부, 실행 결과를 렌더링해야 한다.
- CASE 화면은 상태, 담당자, SLA, 타임라인, 코멘트/처리 액션을 렌더링해야 한다.
- PARAMETER 화면은 현재값, 변경 예정값, 변경 이력, 승인 필요 여부를 렌더링해야 한다.
- 기존 Spring API가 있는 화면은 `@banking-lab/api-client`를 통해 실제 API를 호출해야 한다.
- 아직 API가 없는 화면은 fake success로 처리하지 말고 “declared-only / not API-backed yet” 상태를 명확히 보여줘야 한다.
- staff customer lookup, PII unmask, customer info change approval, complaint answer approval, FDS release/block, AML closure, reconciliation adjustment는 기존 API-backed smoke와 연결성을 유지해야 한다.

작업 2: manifest renderer를 진짜 renderer로 만든다.

현재 manifest는 카드 표시 중심이다. 다음 공통 renderer를 구현하거나 기존 구조를 확장하라.

권장 위치:
- packages/screen-engine
- packages/form-engine
- 필요하면 packages/ui 또는 apps/*/src/components/shared

구현 대상:
- ScreenRenderer
- InquiryScreenRenderer
- CommandScreenRenderer
- CaseScreenRenderer
- ParameterScreenRenderer
- DashboardScreenRenderer
- TransactionCodeLauncher
- TabWorkspace
- CustomerContextPanel
- SearchPanel
- DataTable
- DetailPanel
- FormRenderer
- ActionPanel
- ApprovalPanel
- AuditTimeline
- MaskedValue
- StructuredErrorView

renderer 요구사항:
- `ScreenManifest` type을 확장하되 기존 manifest와 호환성을 깨지 않는다.
- manifest validation을 강화한다.
- 모든 manifest는 screenId, app, type, domain, requiredRoles, audit, layout을 가져야 한다.
- `INQUIRY`는 query 또는 resultTable을 가져야 한다.
- `COMMAND`는 api.command 또는 actions 중 하나를 가져야 한다.
- `CASE`는 workflow 또는 actions를 가져야 한다.
- `PARAMETER`는 parameter namespace/currentValue/history 중 최소 하나를 가져야 한다.
- audit.reasonRequired, maskingPolicy, approval.required는 UI에 반영되어야 한다.
- renderer가 화면별 custom hardcoding으로 흐르지 않도록 한다.

작업 3: 화면 수를 60개 이상으로 늘린다.

목표는 “많은 JSON 파일”이 아니라 은행 업무 분류가 보이는 화면 세트다.

최소 수량:
- customer-web: 10개 이상
- staff-terminal: 30개 이상
- complaint-portal: 8개 이상
- fds-aml-console: 6개 이상
- ops-console + audit-console + admin-console 합산: 10개 이상
- 전체 screen-manifests 합산: 60개 이상

추가할 화면 예시:

customer-web:
- CWB-101 계좌요약
- CWB-102 계좌상세
- CWB-103 거래내역
- CWB-201 즉시이체
- CWB-202 이체결과
- CWB-203 이체상태
- CWB-301 민원접수
- CWB-302 내 민원조회
- CWB-303 민원답변확인
- CWB-401 보안/접속이력

staff-terminal:
- CST-001 고객검색
- CST-002 고객상세
- CST-003 고객360
- CST-101 고객 연락처 변경
- CST-102 고객 주소 변경
- CST-103 고객정보 변경 승인요청
- ACC-101 계좌검색
- ACC-102 계좌상세
- ACC-103 지급정지
- ACC-104 지급정지 해제
- LED-101 거래내역
- LED-102 원장거래 상세
- TRF-101 당행이체 조회
- TRF-102 이체취소/반려 조회
- LIM-101 이체한도 조회
- LIM-102 이체한도 변경 요청
- FEE-101 수수료 조회
- FEE-102 수수료 면제 요청
- CMP-201 민원 처리
- CMP-202 민원 답변 승인
- FDS-101 FDS 케이스 조회
- FDS-102 FDS release 요청
- FDS-103 FDS block 요청
- AML-101 AML 케이스 조회
- AML-102 STR 시뮬레이션 종료 요청
- REC-101 정산 미결 조회
- REC-102 정산 조정 요청
- BAT-101 일마감 상태
- APR-001 승인함
- AUD-001 감사로그 조회

complaint-portal:
- CMP-101 민원접수
- CMP-102 민원상태
- CMP-103 추가자료 제출
- CMP-104 답변확인
- CMP-105 민원종결 확인
- CMP-106 재심요청
- CMP-107 민원 유형 안내
- CMP-108 접수증 조회

fds-aml-console:
- FDS-101 케이스 대시보드
- FDS-201 거래심사
- FDS-202 release/block 승인흐름
- FDS-301 FDS 룰 파라미터
- AML-101 AML 케이스 대시보드
- AML-201 STR 시뮬레이션 케이스

ops/audit/admin:
- OPS-101 일마감 대시보드
- OPS-201 정산 미결
- OPS-202 정산 조정
- OPS-301 정산 파라미터
- AUD-101 감사 해시체인 검증
- AUD-102 직원 고객정보 조회 로그
- AUD-201 감사 보관 파라미터
- ADM-101 플랫폼 통제 대시보드
- ADM-201 보안정책 파라미터
- ADM-301 메뉴/권한 관리

추가 요구:
- 모든 화면은 transactionCode가 유일해야 한다.
- 모든 화면은 manifest validation을 통과해야 한다.
- 화면 수를 세는 테스트를 추가한다.
- screenId/transactionCode 중복을 검출하는 테스트를 추가한다.
- 화면 수가 60개 미만이면 테스트가 실패해야 한다.

작업 4: TLA+ 원장 모델을 추가한다.

목표:
- double-entry ledger 핵심 불변식을 코드 밖에서도 설명하고 검증할 수 있게 만든다.
- 우선 TLA+를 기본 선택으로 한다. Alloy가 더 적합하다고 판단하면 ADR에 근거를 남기고 Alloy를 선택해도 된다.

권장 경로:
- formal/tla/Ledger.tla
- formal/tla/Ledger.cfg
- docs/formal/ledger-model.md
- scripts/check-tla-model.sh 또는 scripts/check-tla-model.ts

모델에 포함할 개념:
- Accounts
- Transactions
- Postings
- Debit/Credit
- Balance projection
- IdempotencyKey
- Reversal
- Closed business date

검증할 불변식:
- 모든 posted transaction의 debit 합과 credit 합은 같다.
- 같은 idempotency key는 하나의 business result만 만든다.
- reversal은 original transaction을 참조한다.
- closed business date에는 direct posting이 불가능하다.
- account balance projection은 postings로부터 재계산 가능해야 한다.
- failed/held transfer는 ledger posting을 만들지 않는다.

실행 가능성:
- TLA+ tool이 로컬에 없으면 실행하지 못했다고 기록하되, spec/cfg/docs는 작성한다.
- 가능하면 CI 또는 npm script에서 TLC를 실행할 수 있게 만든다.
- 외부 다운로드가 필요한 경우 버전과 출처를 문서화하고, 실패 시 명확히 기록한다.
- TLA+ 실행이 불가능한 환경에서는 최소한 repository-level static check로 formal/tla/Ledger.tla와 Ledger.cfg 존재 및 핵심 invariant 이름을 검증한다.
- 단, static check를 “formal verification 완료”라고 표현하지 않는다.

작업 5: GitHub Actions CI를 추가한다.

현재 `.github/workflows/ci.yml`이 없으면 새로 만든다. 있으면 개선한다.

요구사항:
- workflow 파일은 `.github/workflows/ci.yml`에 둔다.
- trigger: pull_request, push to main, workflow_dispatch
- permissions는 최소 권한 원칙으로 `contents: read`를 기본값으로 둔다.
- Node/Next, Kotlin/Spring, manifest, Playwright, security, formal model을 분리된 job 또는 명확한 step으로 구성한다.
- 캐시는 npm과 Gradle에 적용한다.
- 실패한 job이 있으면 전체 CI가 실패해야 한다.
- security evidence가 tool 미설치로 skip되는 경우, skip을 pass로 위장하지 않는다.

권장 CI job:
1. node-and-manifests
   - npm ci
   - npm test
   - npm run validate:manifests
   - npm run packages:typecheck
   - npm run scripts:typecheck
2. next-builds
   - customer-web, staff-terminal, complaint-portal, ops-console, audit-console, fds-aml-console, admin-console typecheck/build
3. backend-core-banking
   - JDK 21 setup
   - Gradle setup
   - scripts/run-core-banking-tests.sh :services:core-banking:test
   - scripts/run-core-banking-tests.sh :services:core-banking:integrationTest
4. playwright-manifest-e2e
   - npx playwright install --with-deps
   - npm run test:e2e
   - API/Keycloak-dependent tests may remain conditional, but manifest renderer tests must run without external services.
5. security-evidence
   - npm audit --audit-level=high
   - npm run security:evidence
   - Semgrep/Trivy/SBOM/ZAP 관련 결과를 docs/test-evidence/generated 아래에 남기거나 CI artifact로 업로드
6. formal-model
   - TLA+ model check가 가능하면 TLC 실행
   - 불가능하면 static formal artifact check 실행
   - 결과를 docs/test-evidence/formal-ledger-model.md 또는 CI artifact로 남김

작업 6: 테스트와 증적을 추가한다.

필수 테스트:
- screen manifest 총개수 >= 60
- transactionCode uniqueness
- screenId uniqueness
- 각 app별 최소 화면 수
- INQUIRY/COMMAND/CASE/PARAMETER manifest shape validation
- staff-terminal 거래코드 검색으로 화면 열기
- staff-terminal 탭 추가/전환
- staff-terminal 고객 컨텍스트 표시
- staff-terminal reason-required 표시
- staff-terminal maker-checker 화면 표시
- customer-web/complaint/fds/ops/audit/admin 기존 Playwright parity가 깨지지 않음
- renderer가 declared-only 화면을 fake success로 표시하지 않음
- API-backed 화면은 기존 Spring API 호출 흐름 유지

증적 문서:
- docs/architecture/manifest-renderer-v2.md
- docs/architecture/staff-terminal-workspace.md
- docs/test-evidence/screen-manifest-expansion.md
- docs/test-evidence/staff-terminal-renderer-e2e.md
- docs/formal/ledger-model.md
- docs/test-evidence/ci.md

README 업데이트:
- “현재 화면은 manifest card 수준”이라는 과거 한계를 제거하거나 최신 상태로 갱신한다.
- 새 renderer, 60+ screen manifests, staff terminal workspace, TLA+/formal artifact, CI 상태를 정확히 설명한다.
- 실제 구현되지 않은 기능을 구현된 것처럼 쓰지 않는다.
- synthetic-only boundary를 유지한다.

완료 기준:
- 전체 screen manifest가 60개 이상이다.
- staff-terminal이 거래코드 검색, 탭, 고객 컨텍스트, 검색/결과/상세/액션/승인/감사 흐름을 실제 UI로 제공한다.
- manifest renderer가 INQUIRY, COMMAND, CASE, PARAMETER를 공통 렌더링한다.
- 기존 API-backed smoke 흐름이 깨지지 않는다.
- TLA+ 또는 Alloy formal artifact가 추가되고, 실행 가능 여부와 한계가 문서화된다.
- `.github/workflows/ci.yml`이 추가되어 Node, Next, Gradle, manifest, Playwright, security, formal checks를 자동화한다.
- npm/Gradle/Playwright/manifest/security/formal 관련 가능한 검증 명령을 실행했다.
- 실패 또는 미실행 테스트는 숨기지 않고 최종 보고서에 적는다.

권장 실행 명령:
- npm ci
- npm test
- npm run validate:manifests
- npm run packages:typecheck
- npm run scripts:typecheck
- npm run next:customer-web:typecheck
- npm run next:customer-web:build
- npm run next:staff-terminal:typecheck
- npm run next:staff-terminal:build
- npm run next:complaint-portal:typecheck
- npm run next:complaint-portal:build
- npm run next:ops-console:typecheck
- npm run next:ops-console:build
- npm run next:audit-console:typecheck
- npm run next:audit-console:build
- npm run next:fds-aml-console:typecheck
- npm run next:fds-aml-console:build
- npm run next:admin-console:typecheck
- npm run next:admin-console:build
- npm run test:e2e
- scripts/run-core-banking-tests.sh :services:core-banking:test
- scripts/run-core-banking-tests.sh :services:core-banking:integrationTest
- npm run security:evidence
- formal model check command, if available

최종 응답 형식:
1. Summary
2. Files changed
3. Implementation details
4. Tests run
5. Tests not run and why
6. Remaining gaps
7. Suggested next PR