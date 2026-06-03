import { ApiBackedStaffPanel } from "../components/ApiBackedStaffPanel";
import {
  TaskTabs,
  TerminalIcon,
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

const treeGroups = [
  {
    label: "수신",
    children: [
      { code: "11", label: "수신기본(신규,해지)" },
      { code: "15", label: "계약공통" },
      { code: "21", label: "수신기본(입출금)" },
      { code: "23", label: "수표/어음", selected: true },
      { code: "24", label: "자기앞수표" },
      { code: "25", label: "기타별단" }
    ]
  },
  { label: "여신", open: false, children: [] },
  { label: "외환", open: false, children: [] }
] as const;

const quickMenuItems = [
  "11. 수신기본(신규,해지,조회,기타)",
  "15. 계약공통",
  "21. 수신기본(입출금,조회,기타)거래",
  "24. 자기앞수표",
  "25. 기타별단",
  "S2. 수신정산"
] as const;

const notices = [
  [">>> [POST차세대] POST 차세도시행관련 문서 <<<", "IT금융개발부", "2014-09-25", true],
  ["일부 급여이체 기업의 타행이체 고객 적극 유치", "개인고객부", "2014-09-30"],
  ["「주택청약(종합)저축」 금리변경 안내 <시행일 14. 10. 1>", "개인고객부", "2014-09-30"],
  ["국민주택기금 대출고객에 대한 해피콜 실시 요청", "개인고객부", "2014-09-30"],
  ["「POST차세대시스템」전환 시 개인고객 응대 유의사항", "개인고객부", "2014-09-30"]
] as const;

const manuals = [
  "[POST차세대 변경업무 메뉴얼] 수신업무",
  "[POST차세대 시스템 화면구성] 이렇게 좋아져요~!(수신)"
] as const;

const newScreens = [
  ["[23601]", "수표어음교부"],
  ["[23602]", "수표어음사고등록"],
  ["[23608]", "당좌/가당 부도등록"],
  ["[23805]", "어음발행정보조회"],
  ["[23808]", "수표어음 교부계좌 조회"],
  ["[23809]", "수표어음 적정교부량조회"]
] as const;

export default async function StaffTerminalPage() {
  const manifests = await loadChannelManifests();
  const reasonRequired = manifests.filter((manifest) => manifest.audit.reasonRequired).length;
  const makerChecker = manifests.filter((manifest) => manifest.approval?.required).length;

  return (
    <main className="bank-terminal">
      <TerminalTopbar brand="INZENT Banking" modules={topModules} />
      <div className="terminal-frame">
        <TerminalMiniSidebar tools={sideTools} />
        <TerminalTreeSidebar groups={treeGroups} />

        <section className="workspace">
          <WorkspaceTabs activeTitle="[20000] 수신_네비게이션" secondaryTitle="[S5801] 외환이자수수료" />
          <TaskTabs tabs={taskTabs} />

          <div className="terminal-body">
            <section className="main-canvas" aria-label="Manifest-driven integrated terminal">
              <div className="source-bento">
                <article className="terminal-panel panel-quick">
                  <div className="panel-header">
                    <strong>
                      <TerminalIcon name="grid_view" />
                      수신업무
                    </strong>
                  </div>
                  <div className="quick-menu-body">
                    <div className="quick-menu-box">
                      <div className="quick-menu-title">
                        <TerminalIcon name="play_arrow" />
                        수신업무 중간화면
                      </div>
                      {quickMenuItems.slice(0, 3).map((item) => (
                        <button type="button" key={item}>
                          {item}
                        </button>
                      ))}
                      <button className="is-current" type="button">
                        <span>23. 수표/어음</span>
                        <TerminalIcon name="arrow_forward" />
                      </button>
                      {quickMenuItems.slice(3).map((item) => (
                        <button type="button" key={item}>
                          {item}
                        </button>
                      ))}
                    </div>
                    <div className="number-select">
                      <span>번호선택</span>
                      <input aria-label="번호선택" />
                    </div>
                  </div>
                </article>

                <article className="terminal-panel panel-notice">
                  <div className="panel-header split">
                    <strong>
                      <TerminalIcon name="campaign" />
                      수신_중요공지
                    </strong>
                    <button type="button">더보기 &gt;</button>
                  </div>
                  <div className="notice-body">
                    <div className="notice-title">
                      <TerminalIcon name="play_arrow" />
                      수신업무 공지사항
                    </div>
                    <div className="notice-table" role="table" aria-label="수신업무 공지사항">
                      <div className="notice-row notice-head" role="row">
                        <div>순번</div>
                        <div>제목</div>
                        <div>등록부서</div>
                        <div>등록일시</div>
                      </div>
                      {notices.map(([title, department, date, featured], index) => (
                        <div className={index % 2 === 0 ? "notice-row" : "notice-row is-alt"} role="row" key={title}>
                          <div>{index + 1}</div>
                          <div className={featured ? "is-featured" : ""}>{title}</div>
                          <div>{department}</div>
                          <div>{date}</div>
                        </div>
                      ))}
                    </div>
                  </div>
                </article>

                <article className="terminal-panel panel-small">
                  <div className="panel-header tertiary">
                    <strong>
                      <TerminalIcon name="help" />
                      자주묻는 질문
                    </strong>
                  </div>
                  <div className="simple-panel-body">
                    <div className="simple-title">제 목</div>
                    <div className="empty-message">등록된 게시물이 없습니다.</div>
                  </div>
                </article>

                <article className="terminal-panel panel-small">
                  <div className="panel-header tertiary">
                    <strong>
                      <TerminalIcon name="menu_book" />
                      알면 편한 단말 메뉴얼
                    </strong>
                  </div>
                  <div className="simple-panel-body">
                    <div className="simple-title">제 목</div>
                    <div className="manual-list">
                      {manuals.map((manual) => (
                        <button type="button" key={manual}>
                          {manual}
                        </button>
                      ))}
                    </div>
                  </div>
                </article>

                <article className="terminal-panel panel-small panel-new-screen">
                  <div className="panel-header split">
                    <strong>
                      <TerminalIcon name="fiber_new" />
                      신규화면 공지
                    </strong>
                    <span>포스트차세대</span>
                  </div>
                  <div className="new-screen-body">
                    <table>
                      <tbody>
                        {newScreens.map(([code, label]) => (
                          <tr key={code}>
                            <td>{code}</td>
                            <td>{label}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </article>

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
                <button type="button" aria-label="Close inside view">
                  <TerminalIcon name="close" />
                </button>
              </div>
              <div className="inside-summary">
                <div className="inside-stat-grid">
                  {["여신연체", "카드연체", "자점손실", "전점손실"].map((label) => (
                    <button type="button" key={label}>
                      {label}
                    </button>
                  ))}
                </div>
                <div className="inside-quick-tabs">
                  {["특이사항", "거래성향", "메모"].map((label) => (
                    <button type="button" key={label}>
                      {label}
                    </button>
                  ))}
                </div>
              </div>
              <div className="inside-panel-stack">
                <div className="inside-mini-panel">
                  <div className="inside-mini-header">
                    <strong>
                      <TerminalIcon name="inventory_2" />
                      내상품
                    </strong>
                    <span>
                      <button type="button">상담</button>
                      <button className="is-primary" type="button">
                        상세
                      </button>
                    </span>
                  </div>
                  <div className="inside-select-row">
                    <select defaultValue="우측 클릭 후 선택하세요" aria-label="내상품">
                      <option>우측 클릭 후 선택하세요</option>
                    </select>
                  </div>
                </div>
                <div className="inside-mini-panel recommendation">
                  <div className="inside-mini-header">
                    <strong>
                      <TerminalIcon name="thumb_up" />
                      추천상품
                    </strong>
                    <span>
                      <button type="button">상세</button>
                      <button type="button">탐색</button>
                    </span>
                  </div>
                  <div className="inside-table-title">추천상품명</div>
                  <div className="inside-fill" />
                </div>
                <div className="inside-mini-panel marketing">
                  <div className="inside-mini-header">
                    <strong>
                      <TerminalIcon name="campaign" />
                      수행마케팅
                    </strong>
                    <span>
                      <button type="button">반응등록</button>
                    </span>
                  </div>
                  <div className="inside-table-title">마케팅내용</div>
                  <div className="inside-fill" />
                </div>
              </div>
            </aside>
          </div>

          <footer className="status-bar">
            <div>
              <span>
                <TerminalIcon name="computer" />
                10.1.91.174
              </span>
              <span className="status-chip">정상 연결</span>
            </div>
            <div>
              <span>
                <TerminalIcon name="print" />
                프린터
              </span>
              <span>
                <TerminalIcon name="keyboard" />
                핀패드
              </span>
              <span>
                <TerminalIcon name="receipt" />
                즉발기
              </span>
              <strong>2023-10-24 13:37:24</strong>
            </div>
          </footer>
        </section>
      </div>
    </main>
  );
}
