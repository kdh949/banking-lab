import type { ScreenManifest } from "../../../../packages/screen-engine/src/types";
import { ApiBackedStaffPanel } from "../components/ApiBackedStaffPanel";
import {
  DenseTable,
  Panel,
  StatusBar,
  TaskTabs,
  TerminalMiniSidebar,
  TerminalTopbar,
  TerminalTreeSidebar,
  WorkspaceTabs
} from "../components/terminal-ui";
import { loadChannelManifests } from "../lib/manifestLoader";

const topModules = [
  { label: "수신", icon: "account_balance_wallet", active: true },
  { label: "여신", icon: "real_estate_agent" },
  { label: "외환", icon: "currency_exchange" },
  { label: "고객", icon: "group" },
  { label: "CRM", icon: "support_agent" },
  { label: "신용카드", icon: "credit_card" }
] as const;

const sideTools = [
  { label: "업무메뉴", icon: "menu" },
  { label: "즐겨찾기", icon: "star" },
  { label: "워크플로우", icon: "account_tree", active: true },
  { label: "탑리스트", icon: "leaderboard" },
  { label: "날짜계산기", icon: "calendar_today" },
  { label: "일정", icon: "event" },
  { label: "오피스", icon: "business_center" }
] as const;

const taskTabs = ["업무포털", "신규", "입금", "출금", "해지", "정산", "등록/해제", "조회", "통장/증명서"] as const;

const noticeRows = [
  ["No.", "제목", "담당부서", "게시일"],
  ["5", ">>> [LAB차세대] 통합단말 시행관련 문서 <<<", "채널운영", "2026.06.04"],
  ["4", "전자금융 통제 및 고객확인 시뮬레이션 안내", "고객업무", "2026.06.03"],
  ["3", "내부통제 점검 화면 reason-code 입력 기준", "감사", "2026.06.02"],
  ["2", "업무포털 주요 공지: 합성 데이터 운영 원칙", "IT기획", "2026.06.01"],
  ["1", "개인정보 마스킹 기본 적용 및 승인 절차", "보안", "2026.05.31"]
] as const;

const faqRows = [
  ["5", "[수신] 통합단말 조회에서 마스킹 해제 승인 요청"],
  ["4", "[고객] 고객상세 화면 업무사유 입력 방법"],
  ["3", "[원장] 거래내역 재처리와 역분개 확인 절차"],
  ["2", "[민원] 답변 승인 반려 시 재상신 흐름"],
  ["1", "[FDS] 의심거래 보류 해제 시 체크리스트"]
] as const;

const manualRows = [
  ["업무매뉴얼", "계좌개설 사후점검 화면 사용법"],
  ["업무매뉴얼", "통합고객조회 표준 처리 기준"],
  ["상품설명서", "합성 예금상품 약관 예시"],
  ["업무매뉴얼", "감사 로그 확인 및 증적 제출"],
  ["업무매뉴얼", "승인함 maker-checker 운영"]
] as const;

const newScreenRows = [
  ["S5801", "외환이자수수료 조회", "반영"],
  ["CST002", "마스킹 고객상세", "반영"],
  ["APR001", "승인함", "반영"],
  ["FDS201", "이상거래 케이스", "검토"],
  ["CMP201", "민원 답변", "검토"]
] as const;

const insideProducts = [
  "합성 예금 패키지",
  "디지털 입출금 계좌",
  "비대면 예금 전환",
  "수표/어음 처리 시뮬레이터"
] as const;

const workflowEvents = [
  "CST-002 masked customer detail loaded",
  "APR-001 declared maker-checker approval",
  "FDS case routed to workflow timeline",
  "Exception/retry panel ready"
] as const;

function domainLabel(domain: string): string {
  const labels: Record<string, string> = {
    account: "계좌",
    approval: "승인",
    audit: "감사",
    complaint: "민원",
    customer: "고객",
    ledger: "원장",
    "staff-workstation": "통합단말"
  };
  return labels[domain] ?? domain;
}

function typeLabel(type: ScreenManifest["type"]): string {
  const labels: Record<ScreenManifest["type"], string> = {
    CASE: "Case",
    COMMAND: "Command",
    DASHBOARD: "Dashboard",
    INQUIRY: "Inquiry",
    PARAMETER: "Parameter"
  };
  return labels[type];
}

function endpointOf(manifest: ScreenManifest): string {
  return manifest.query?.endpoint || manifest.api?.command || manifest.actions?.[0]?.target || "declared in workflow";
}

function buildSourceTree(manifests: ScreenManifest[], activeManifest: ScreenManifest | undefined) {
  const syntheticFolders = [
    {
      label: "수신",
      children: [
        { code: "11", label: "수신기본(신규,해지)" },
        { code: "15", label: "계약공통" },
        { code: "21", label: "수신기본(입출금)" },
        { code: "23", label: "수표/어음", selected: true },
        { code: "31", label: "제신고" }
      ]
    },
    {
      label: "통합단말",
      children: manifests
        .filter((manifest) => ["staff-workstation", "customer", "approval"].includes(manifest.domain))
        .slice(0, 6)
        .map((manifest) => ({
          code: manifest.transactionCode ?? manifest.screenId,
          label: manifest.title,
          selected: manifest.screenId === activeManifest?.screenId
        }))
    },
    {
      label: "감사/리스크",
      children: manifests
        .filter((manifest) => ["audit", "complaint", "ledger"].includes(manifest.domain))
        .slice(0, 5)
        .map((manifest) => ({
          code: manifest.transactionCode ?? manifest.screenId,
          label: manifest.title,
          selected: false
        }))
    }
  ];

  return syntheticFolders;
}

export default async function StaffTerminalPage() {
  const manifests = await loadChannelManifests();
  const reasonRequired = manifests.filter((manifest) => manifest.audit.reasonRequired).length;
  const makerChecker = manifests.filter((manifest) => manifest.approval?.required).length;
  const piiScreens = manifests.filter((manifest) => manifest.audit.piiAccess).length;
  const activeManifest = manifests.find((manifest) => manifest.screenId === "CST-002") ?? manifests[0];
  const manifestRows = manifests.slice(0, 6).map((manifest) => [
    manifest.transactionCode ?? manifest.screenId,
    domainLabel(manifest.domain),
    typeLabel(manifest.type),
    endpointOf(manifest)
  ]);

  return (
    <main className="bank-terminal">
      <TerminalTopbar brand="INZENT Banking" modules={topModules} />
      <div className="terminal-frame">
        <TerminalMiniSidebar tools={sideTools} />
        <TerminalTreeSidebar
          groups={buildSourceTree(manifests, activeManifest)}
          operator={{ initials: "BL", name: "branch01", role: "BRANCH_STAFF", branch: "Synthetic Branch" }}
        />

        <section className="workspace">
          <WorkspaceTabs
            activeTitle="[20000] 수신_네비게이션"
            secondaryTitle={`[${activeManifest?.transactionCode ?? "S5801"}] ${
              activeManifest?.title ?? "외환이자수수료"
            }`}
          />
          <TaskTabs tabs={taskTabs} />

          <div className="terminal-body">
            <section className="main-canvas" aria-label="Manifest-driven integrated terminal">
              <div className="source-bento">
                <Panel title="수신업무" icon="account_balance_wallet" className="panel-quick">
                  <div className="quick-grid">
                    {["신규", "입금", "출금", "해지", "정산", "등록/해제", "조회", "통장/증명서"].map((label) => (
                      <button type="button" key={label}>
                        {label}
                      </button>
                    ))}
                  </div>
                  <DenseTable
                    columns={["거래코드", "업무", "유형", "API"]}
                    rows={manifestRows}
                  />
                </Panel>

                <Panel title="수신_중요공지" icon="article" className="panel-notice">
                  <DenseTable columns={noticeRows[0]} rows={noticeRows.slice(1)} />
                </Panel>

                <Panel title="FAQ BEST 5" icon="support_agent" className="panel-small">
                  <DenseTable columns={["No.", "질문"]} rows={faqRows} />
                </Panel>

                <Panel title="매뉴얼" icon="description" className="panel-small">
                  <DenseTable columns={["구분", "제목"]} rows={manualRows} />
                </Panel>

                <Panel title="신규화면/개선사항" icon="leaderboard" className="panel-small">
                  <DenseTable columns={["코드", "화면명", "상태"]} rows={newScreenRows} />
                </Panel>

                <section className="manifest-evidence-strip" aria-label="Staff terminal control evidence">
                  <h1>Transaction-code workspace</h1>
                  <label className="terminal-command">
                    <span>Transaction code</span>
                    <input value={manifests[0]?.transactionCode || ""} readOnly aria-label="Transaction code" />
                  </label>
                  <div className="metric-card">
                    <span className="metric">{manifests.length}</span>
                    <span className="metric-label">screens</span>
                  </div>
                  <div className="metric-card">
                    <span className="metric">{reasonRequired}</span>
                    <span className="metric-label">reason-required</span>
                  </div>
                  <div className="metric-card">
                    <span className="metric">{makerChecker}</span>
                    <span className="metric-label">maker-checker</span>
                  </div>
                  <p>Masked by default</p>
                  <p>Business reason required</p>
                  <p>APR-001 declared</p>
                </section>

                <ApiBackedStaffPanel />
              </div>
            </section>

            <aside className="inside-view" aria-label="Inside view and API smoke">
              <div className="inside-header">
                <strong>인사이드뷰</strong>
                <span>MY INFO</span>
              </div>
              <div className="inside-tabs" role="tablist" aria-label="Inside view tabs">
                {["특이사항", "거래성향", "메모"].map((tab, index) => (
                  <button className={index === 0 ? "is-active" : ""} type="button" key={tab}>
                    {tab}
                  </button>
                ))}
              </div>
              <Panel title="고객/거래 통제" compact>
                <dl className="compact-definition">
                  <div>
                    <dt>Masking</dt>
                    <dd>기본 마스킹</dd>
                  </div>
                  <div>
                    <dt>Reason</dt>
                    <dd>업무사유 필수</dd>
                  </div>
                  <div>
                    <dt>Approval</dt>
                    <dd>Maker-checker</dd>
                  </div>
                </dl>
              </Panel>
              <Panel title="상품추천" compact>
                <ul className="inside-list">
                  {insideProducts.map((product) => (
                    <li key={product}>{product}</li>
                  ))}
                </ul>
              </Panel>
              <Panel title="Audit log panel" compact>
                <dl className="compact-definition">
                  <div>
                    <dt>PII screens</dt>
                    <dd>{piiScreens}</dd>
                  </div>
                  <div>
                    <dt>reason-required</dt>
                    <dd>{reasonRequired}</dd>
                  </div>
                </dl>
              </Panel>
              <Panel title="Workflow timeline" compact>
                <ol className="timeline">
                  {workflowEvents.map((event) => (
                    <li key={event}>{event}</li>
                  ))}
                </ol>
              </Panel>
              <Panel title="Exception/retry panel" compact>
                <DenseTable
                  columns={["상태", "처리"]}
                  rows={[
                    ["SERIALIZABLE retry", "ready"],
                    ["Duplicate idempotency key", "guarded"],
                    ["PII unmask step-up", "manager"]
                  ]}
                />
              </Panel>
            </aside>
          </div>
        </section>
      </div>
      <StatusBar
        left="화면명: 수신_네비게이션 | 합성 데이터 전용"
        right={`screens ${manifests.length} / reason ${reasonRequired} / approvals ${makerChecker}`}
      />
    </main>
  );
}
