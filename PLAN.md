# Goal 모드용 전면 전환 계획: iWorks 통합단말 단일화와 레거시 제거

## 1. Goal 목표

Codex Goal 모드의 최종 목표는 현재 브라우저에서 동작 중인 `http://127.0.0.1:3002/` iWorks 스타일 통합단말을 직원 단말의 유일한 공식 구현으로 만들고, 기존 직원 단말/프론트엔드 프로토타입, manifest renderer, API-backed mock panel, DESIGN/QA 잔재, Node 레거시 참조를 모두 제거하는 것이다.

완료 후 저장소는 다음 상태여야 한다.

- `apps/staff-terminal`은 현재 `IntegratedTerminalApp` 중심으로만 구성된다.
- 직원 단말용 old route, old manifest renderer, old API-backed panel, old workflow route가 없다.
- `screen-manifests/staff-terminal`이 없다.
- `legacy-node-reference`가 없다.
- 문서와 evidence는 현재 iWorks 통합단말만 설명한다.
- 테스트와 boundary check가 레거시 재유입을 막는다.
- 모든 커밋/PR 메시지는 `commit-convention` 스킬을 따른다.

## 2. Goal 모드 운영 규칙

작업 시작 전:
- `commit-convention` 스킬을 먼저 적용한다.
- `git status --short`로 사용자 변경과 기존 변경을 확인한다.
- 브랜치가 필요하면 `codex/integrated-terminal-cleanup`을 사용한다.
- 작업 중 절대 사용자 미추적 파일을 임의 삭제하지 않는다. 현재 알려진 unrelated untracked 파일 `docs/codex/customer-onboarding-self-service-goals.md`는 건드리지 않는다.

중간 체크포인트:
- 각 Phase 종료마다 `git diff --stat`, 관련 테스트, boundary 검색을 실행한다.
- 커밋 후보 메시지를 `commit-convention` 형식으로 점검한다.
- 논리 커밋 단위는 아래를 기본값으로 한다:
  - `refactor: 직원 단말 iWorks 화면 단일화`
  - `test: 통합단말 기준 검증 추가`
  - `docs: 통합단말 산출물 기준 문서 재정리`
  - `chore: 레거시 Node 참조와 잔재 제거`

금지:
- 커밋 제목에 스코프를 붙이지 않는다.
- `코드 업데이트`, `버그 수정` 같은 모호한 제목을 쓰지 않는다.
- 실행하지 않은 테스트를 통과로 보고하지 않는다.

## 3. Phase별 구현 절차

### Phase 0. 기준선 고정

수행:
- 현재 동작 중인 iWorks 통합단말을 기준 화면으로 확정한다.
- `apps/staff-terminal/src/app/page.tsx`가 `IntegratedTerminalApp`만 렌더링하는지 확인한다.
- 현재 보존 대상 파일을 확인한다:
  - `apps/staff-terminal/src/components/integrated-terminal.tsx`
  - `apps/staff-terminal/src/components/integrated-terminal.css`
  - `apps/staff-terminal/src/app/page.tsx`
  - `apps/staff-terminal/src/app/layout.tsx`
  - `apps/staff-terminal/src/app/globals.css`
  - `apps/staff-terminal/src/app/api/terminal-status/route.ts`
  - `apps/staff-terminal/public/fonts/material-symbols-outlined.ttf`

검증:
- `npm run next:staff-terminal:typecheck`
- `npm run next:staff-terminal:build`

### Phase 1. staff-terminal 앱 단일화

삭제:
- `apps/staff-terminal/src/components/ApiBackedStaffPanel.tsx`
- `apps/staff-terminal/src/components/manifest-renderer.tsx`
- `apps/staff-terminal/src/components/terminal-screens.tsx`
- `apps/staff-terminal/src/components/terminal-ui.tsx`
- `apps/staff-terminal/src/components/terminal-ui.css`
- `apps/staff-terminal/src/components/workflow-routes.tsx`
- `apps/staff-terminal/src/components/workflow-route-summaries.ts`
- `apps/staff-terminal/src/lib/manifestLoader.ts`
- `apps/staff-terminal/src/app/accounts`
- `apps/staff-terminal/src/app/customers`
- `apps/staff-terminal/src/app/tx`
- `apps/staff-terminal/src/app/approvals`
- `apps/staff-terminal/src/app/audit`
- `apps/staff-terminal/src/app/workflows`
- `apps/staff-terminal/src/app/api/auth`

수정:
- `apps/staff-terminal/src/app/layout.tsx`
  - `../components/terminal-ui.css` import 제거
  - metadata description에서 `prototype` 제거
- `apps/staff-terminal/package.json`
  - 필요 없는 의존성은 제거하지 않는다. 현재 Next/React/api-client/auth-client는 다른 코드 의존 확인 후만 제거한다.

검증:
- `rg "ApiBackedStaffPanel|manifest-renderer|terminal-ui|terminal-screens|workflow-routes|manifestLoader" apps/staff-terminal/src` 결과 0건
- `npm run next:staff-terminal:typecheck`
- `npm run next:staff-terminal:build`

### Phase 2. staff-terminal manifest 제거

삭제:
- `screen-manifests/staff-terminal/` 전체

수정:
- `scripts/validate-manifests.ts`가 staff-terminal manifest 존재를 필수로 보지 않게 한다.
- staff-terminal manifest 개수를 기대하는 테스트를 현재 iWorks 통합단말 기준으로 변경한다.
- 다른 채널 manifest는 보존한다:
  - customer-web
  - complaint-portal
  - admin-console
  - audit-console
  - ops-console
  - fds-aml-console

검증:
- `test -d screen-manifests/staff-terminal`가 실패해야 정상
- `npm run validate:manifests`
- `npm test` 중 manifest 관련 실패를 iWorks 기준으로 정리

### Phase 3. Playwright/E2E 전환

삭제:
- `apps/staff-terminal/e2e/staff-terminal-parity.spec.ts`

추가:
- `apps/staff-terminal/e2e/integrated-terminal.spec.ts`

새 E2E 필수 시나리오:
- `/` 로드 시 iWorks 통합단말이 표시된다.
- OS 타이틀바가 없다.
- 상단 모듈 active 상태는 항상 1개다.
- 미구현 모듈 클릭 시 X 아이콘 모달이 뜨고 active module은 유지된다.
- 좌측 업무메뉴에서 `[50841]`, `[50710]`, `[10607]` 등 같은 화면을 공유하는 항목도 각각 하나씩만 선택된다.
- `조회구분` 커스텀 select는 필드 바로 아래에서 열린다.
- 검색 아이콘 클릭 시 추가 조회 모달이 뜬다.
- 번호선택 입력은 숫자만 허용한다.
- 펀드 row 클릭 시 title/code가 해당 메뉴로 바뀐다.
- 흐름도 process node 클릭 시 해당 메뉴로 이동한다.
- status bar는 `/api/terminal-status` 기반 IP와 서버 시간을 표시한다.
- 브라우저 콘솔 error가 없다.

검증:
- `npx playwright test apps/staff-terminal/e2e/integrated-terminal.spec.ts`

### Phase 4. Node 레거시 전면 제거

삭제:
- `legacy-node-reference/` 전체

수정:
- `package.json`
  - `start: node runtime/server.mjs` 제거 또는 현재 공식 명령으로 교체
  - Node 레거시 실행을 전제로 하는 스크립트 제거
- `tests/*.test.mjs`
  - `legacy-node-reference` 존재를 기대하는 assertion 제거
  - Node oracle 기반 staff-terminal/runtime 기대값 제거
  - 목표 스택 테스트와 현재 iWorks 통합단말 boundary 테스트로 대체
- `scripts/*`
  - `legacy-node-reference` 경로를 읽는 검사 제거 또는 새 boundary check로 대체

검증:
- `rg "legacy-node-reference|runtime/server.mjs|Node reference|Node oracle|legacy oracle" package.json scripts tests docs PLAN.md BANKING_LAB_CODEX_PROMPT.md CODEX_FULL_REWRITE_PLAN.md` 결과는 역사 문서가 아닌 현재 정책 문맥에서 0건이어야 한다.
- `npm test`

### Phase 5. 문서 전면 정리

삭제 또는 대체:
- `design-qa.md`
- `docs/architecture/phase-3-staff-terminal.md`
- `docs/architecture/staff-terminal-workspace.md`
- `docs/architecture/manifest-renderer-v2.md` 중 staff-terminal 공식 구현 설명
- `docs/test-evidence/phase-3-staff-terminal.md`
- `docs/test-evidence/staff-terminal-renderer-e2e.md`
- `docs/demo-scenarios/phase-3-staff-terminal-demo.md`
- `docs/failure-drills/phase-3-staff-terminal-drill-plan.md`
- staff-terminal manifest/API-backed panel을 공식 구현처럼 설명하는 docs/codex 문서

추가 또는 대체:
- `docs/architecture/integrated-terminal.md`
- `docs/test-evidence/integrated-terminal.md`
- `docs/demo-scenarios/integrated-terminal-demo.md`
- `docs/adr/0009-current-integrated-terminal.md`

루트 정책 문서 수정:
- `PLAN.md`
- `BANKING_LAB_CODEX_PROMPT.md`
- `CODEX_FULL_REWRITE_PLAN.md`
- `AGENTS.md`가 tracked file로 존재하면 함께 수정

수정 내용:
- Node 레거시 oracle 정책 제거
- 직원 단말은 iWorks 통합단말이 공식 구현임을 명시
- staff-terminal manifest renderer 제거 사실 명시
- 다른 채널 manifest는 유지한다는 경계 명시
- 합성 데이터 원칙, 실제 금융/PII 금지, 미구현 모듈 X 모달 정책 명시

검증:
- `rg "DESIGN.md|design-qa|phase-3-staff-terminal|staff-terminal-renderer|ApiBackedStaffPanel|manifest-renderer|legacy-node-reference|Node oracle" docs PLAN.md BANKING_LAB_CODEX_PROMPT.md CODEX_FULL_REWRITE_PLAN.md`

### Phase 6. Boundary check 추가

추가:
- `scripts/check-integrated-terminal-boundary.ts`

검사 조건:
- `legacy-node-reference`가 존재하면 실패
- `screen-manifests/staff-terminal`이 존재하면 실패
- `apps/staff-terminal/src` 안에 old component 이름이 있으면 실패
- `docs`, `tests`, `scripts`, root markdown에 old staff terminal/Node oracle 문구가 남으면 실패
- `design-qa.md`, `DESIGN.md`가 있으면 실패
- `apps/staff-terminal/src/app`의 public page가 `/`, `/api/terminal-status` 외 old route를 포함하면 실패

`package.json`에 추가:
- `integrated-terminal:boundary-check`: `node --experimental-strip-types scripts/check-integrated-terminal-boundary.ts`

검증:
- `npm run integrated-terminal:boundary-check`

## 4. 전체 테스트 매트릭스

최소 필수:
- `npm run next:staff-terminal:typecheck`
- `npm run next:staff-terminal:build`
- `npm run integrated-terminal:boundary-check`
- `npx playwright test apps/staff-terminal/e2e/integrated-terminal.spec.ts`
- `npm test`

권장 추가:
- `npm run packages:typecheck`
- `npm run scripts:typecheck`
- `npm run validate:manifests`
- `npm run security:secrets-check`

브라우저 검증:
- 인앱 브라우저에서 `http://127.0.0.1:3002/` 새로고침
- 주요 클릭 시나리오 재확인
- console error 0건 확인

## 5. 예상 실패와 처리 지침

`npm test`가 Node 레거시 삭제로 실패하는 경우:
- 실패 테스트가 Node oracle 존재를 기대하면 테스트를 현재 정책 기준으로 변경한다.
- 실제 banking invariant 테스트 실패는 삭제하지 말고 원인 확인 후 보존한다.

`validate:manifests`가 staff-terminal manifest 삭제로 실패하는 경우:
- validator에서 staff-terminal을 필수 채널 목록에서 제거한다.
- 다른 채널 manifest validation은 유지한다.

Playwright가 기존 텍스트를 찾다가 실패하는 경우:
- 기존 spec를 복구하지 말고 iWorks 통합단말 기준 assertion으로 교체한다.

문서 검색에서 old 문구가 남는 경우:
- 역사 기록 보존 목적의 문구도 현재 정책과 충돌하면 제거하거나 “삭제된 과거 구현”으로 명확히 바꾼다.
- 공식 구현처럼 읽히는 표현은 모두 금지한다.

## 6. 금지 조건

- `IntegratedTerminalApp`을 old manifest renderer로 되돌리지 않는다.
- staff-terminal old route를 compatibility 용도로 남기지 않는다.
- staff-terminal manifest를 “미사용이지만 보존” 상태로 두지 않는다.
- Node 레거시를 oracle, reference, fallback, demo 용도로 남기지 않는다.
- 다른 채널 앱과 backend banking core를 cleanup 명목으로 삭제하지 않는다.
- 실제 고객정보, 실제 금융기관 API, 실제 KYC, 실제 결제망 연동을 추가하지 않는다.
- 테스트를 삭제만 하고 대체 검증 없이 종료하지 않는다.
- `commit-convention`을 건너뛰지 않는다.

## 7. 종료 조건

작업은 아래가 모두 만족될 때만 완료다.

- `apps/staff-terminal`은 현재 iWorks 통합단말만 공식 UI로 제공한다.
- `screen-manifests/staff-terminal`이 없다.
- `legacy-node-reference`가 없다.
- old staff terminal 컴포넌트/route/test/doc 참조가 없다.
- 새 integrated-terminal E2E가 통과한다.
- boundary check가 통과한다.
- `typecheck`, `build`, `npm test`가 통과한다.
- 인앱 브라우저에서 현재 화면이 정상 동작한다.
