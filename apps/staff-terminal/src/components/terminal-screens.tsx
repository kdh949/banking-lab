import { ApiBackedStaffPanel } from "./ApiBackedStaffPanel";
import {
  AlertWidget,
  DenseTable,
  FooterLinkBar,
  InsideMiniPanel,
  InsideView,
  MiniCalendar,
  NoticeList,
  PanelTabs,
  PartnerGrid,
  ProgressWidget,
  RightRail,
  TerminalBentoGrid,
  TerminalBody,
  TerminalContextSidebar,
  TerminalField,
  TerminalIcon,
  TerminalMiniSidebar,
  TerminalPanel,
  TerminalShell,
  TerminalStatusBar,
  TerminalTopbar,
  TerminalWorkspace,
  type TerminalProfile
} from "./terminal-ui";

type ManifestEvidence = {
  readonly transactionCode: string;
  readonly screenCount: number;
  readonly reasonRequired: number;
  readonly makerChecker: number;
};

export const topModules = [
  { label: "수신", icon: "account_balance_wallet", active: true },
  { label: "여신", icon: "real_estate_agent" },
  { label: "외환", icon: "currency_exchange" },
  { label: "고객", icon: "group" },
  { label: "CRM", icon: "support_agent" },
  { label: "신용카드", icon: "credit_card" }
] as const;

export const sideTools = [
  { label: "업무메뉴", icon: "menu" },
  { label: "즐겨찾기", icon: "star" },
  { label: "워크플로우", icon: "account_tree", active: true },
  { label: "탑리스트", icon: "leaderboard" },
  { label: "날짜계산기", icon: "calendar_today" },
  { label: "일정", icon: "event" },
  { label: "오피스", icon: "business_center" }
] as const;

export const taskTabs = ["업무포털", "신규", "입금", "출금", "해지", "정산", "등록/해제", "조회", "통장/증명서"] as const;

export const workspaceTabs = [
  { title: "[20000] 수신_네비게이션", active: true },
  { title: "[S5801] 외환이자수수료" }
] as const;

export const treeGroups = [
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

const manuals = ["[POST차세대 변경업무 메뉴얼] 수신업무", "[POST차세대 시스템 화면구성] 이렇게 좋아져요~!(수신)"] as const;

const newScreens = [
  ["[23601]", "수표어음교부"],
  ["[23602]", "수표어음사고등록"],
  ["[23608]", "당좌/가당 부도등록"],
  ["[23805]", "어음발행정보조회"],
  ["[23808]", "수표어음 교부계좌 조회"],
  ["[23809]", "수표어음 적정교부량조회"]
] as const;

const syntheticProfile: TerminalProfile = {
  initials: "BL",
  name: "차세대담당자",
  role: "STAFF_L1",
  branch: "Synthetic Branch",
  extension: "0000-0000",
  phone: "010-0000-0000"
};

const portalNotices = [
  { label: "- [이벤트] 나의 포인트 기부 행사", date: "15-06-08" },
  { label: "- [FAQ] 이용안내 및 신청", date: "15-06-07" },
  { label: "- [FAQ] 한눈에 보기 신청", date: "15-06-07" }
] as const;

const portalTodos = ["- 점별 관리대상 발생 조회", "- 할인이음관계인 관리대상발생 조회", "- 만기 도래 안내 조회"] as const;

const creditRows = [["5", "120621-C30-011", "합성고객", "Synthetic Loan", "22,000,000", <span className="status-pill" key="status">접수</span>]] as const;

export function TerminalNavigationWorkbench({ evidence }: { readonly evidence: ManifestEvidence }) {
  return (
    <TerminalShell
      topbar={<TerminalTopbar brand="INZENT Banking" modules={topModules} />}
      miniSidebar={<TerminalMiniSidebar tools={sideTools} />}
      contextSidebar={<TerminalContextSidebar kind="tree" groups={treeGroups} />}
    >
      <TerminalWorkspace tabs={workspaceTabs} taskTabs={taskTabs} statusBar={<TerminalStatusBar />}>
        <TerminalBody rightRail={<NavigationInsideView />}>
          <TerminalBentoGrid>
            <NavigationQuickPanel />
            <NavigationNoticePanel />
            <SimpleBoardPanel title="자주묻는 질문" icon="help" emptyMessage="등록된 게시물이 없습니다." />
            <ManualPanel />
            <NewScreenPanel />
            <ManifestEvidenceStrip evidence={evidence} />
            <ApiBackedStaffPanel />
          </TerminalBentoGrid>
        </TerminalBody>
      </TerminalWorkspace>
    </TerminalShell>
  );
}

function NavigationQuickPanel() {
  return (
    <TerminalPanel title="수신업무" icon="grid_view" className="panel-quick">
      <div className="quick-menu-body">
        <div className="quick-menu-box">
          <div className="quick-menu-title">
            <TerminalIcon name="play_arrow" size={14} />
            수신업무 중간화면
          </div>
          {quickMenuItems.slice(0, 3).map((item) => (
            <button type="button" key={item}>
              {item}
            </button>
          ))}
          <button className="is-current" type="button">
            <span>23. 수표/어음</span>
            <TerminalIcon name="arrow_forward" size={14} />
          </button>
          {quickMenuItems.slice(3).map((item) => (
            <button type="button" key={item}>
              {item}
            </button>
          ))}
        </div>
        <TerminalField label="번호선택" fieldType="text" className="number-select" ariaLabel="번호선택" />
      </div>
    </TerminalPanel>
  );
}

function NavigationNoticePanel() {
  return (
    <TerminalPanel
      title="수신_중요공지"
      icon="campaign"
      className="panel-notice"
      action={<button type="button">더보기 &gt;</button>}
    >
      <div className="notice-body">
        <div className="notice-title">
          <TerminalIcon name="play_arrow" size={14} />
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
    </TerminalPanel>
  );
}

function SimpleBoardPanel({
  title,
  icon,
  emptyMessage
}: {
  readonly title: string;
  readonly icon: "help" | "menu_book";
  readonly emptyMessage: string;
}) {
  return (
    <TerminalPanel title={title} icon={icon} className="panel-small" headerVariant="tertiary">
      <div className="simple-panel-body">
        <div className="simple-title">제 목</div>
        <div className="empty-message">{emptyMessage}</div>
      </div>
    </TerminalPanel>
  );
}

function ManualPanel() {
  return (
    <TerminalPanel title="알면 편한 단말 메뉴얼" icon="menu_book" className="panel-small" headerVariant="tertiary">
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
    </TerminalPanel>
  );
}

function NewScreenPanel() {
  return (
    <TerminalPanel
      title="신규화면 공지"
      icon="fiber_new"
      className="panel-small panel-new-screen"
      action={<span>포스트차세대</span>}
    >
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
    </TerminalPanel>
  );
}

function NavigationInsideView() {
  return (
    <InsideView stats={["여신연체", "카드연체", "자점손실", "전점손실"]} tabs={["특이사항", "거래성향", "메모"]}>
      <InsideMiniPanel title="내상품" icon="inventory_2" actions={["상담", "상세"]}>
        <div className="inside-select-row">
          <select defaultValue="우측 클릭 후 선택하세요" aria-label="내상품">
            <option>우측 클릭 후 선택하세요</option>
          </select>
        </div>
      </InsideMiniPanel>
      <InsideMiniPanel title="추천상품" icon="thumb_up" actions={["상세", "탐색"]} className="recommendation">
        <div className="inside-table-title">추천상품명</div>
        <div className="inside-fill" />
      </InsideMiniPanel>
      <InsideMiniPanel title="수행마케팅" icon="campaign" actions={["반응등록"]} className="marketing">
        <div className="inside-table-title">마케팅내용</div>
        <div className="inside-fill" />
      </InsideMiniPanel>
    </InsideView>
  );
}

function ManifestEvidenceStrip({ evidence }: { readonly evidence: ManifestEvidence }) {
  return (
    <section className="manifest-evidence-strip" aria-label="Staff terminal control evidence">
      <h1>Transaction-code workspace</h1>
      <label className="terminal-command">
        <span>Transaction code</span>
        <input value={evidence.transactionCode} readOnly aria-label="Transaction code" />
      </label>
      <div className="metric-card">
        <span className="metric">{evidence.screenCount}</span>
        <span className="metric-label">screens</span>
      </div>
      <div className="metric-card">
        <span className="metric">{evidence.reasonRequired}</span>
        <span className="metric-label">reason-required</span>
      </div>
      <div className="metric-card">
        <span className="metric">{evidence.makerChecker}</span>
        <span className="metric-label">maker-checker</span>
      </div>
      <p>Masked by default</p>
      <p>Business reason required</p>
      <p>APR-001 declared</p>
    </section>
  );
}

export function TerminalPortalDashboard() {
  return (
    <TerminalShell
      topbar={<TerminalTopbar brand="INZENT Banking" modules={topModules} />}
      miniSidebar={<TerminalMiniSidebar tools={sideTools} />}
      contextSidebar={
        <TerminalContextSidebar
          kind="profile"
          profile={syntheticProfile}
          widgets={
            <>
              <AlertWidget
                title="메시지 알림"
                columns={[
                  [
                    ["받은메일:", "11", "danger"],
                    ["안읽은메일:", "5", "danger"]
                  ],
                  [
                    ["결재건수:", "7", "primary"],
                    ["결재반려건수:", "0", "danger"]
                  ]
                ]}
              />
              <ProgressWidget
                title="사이버연수원"
                items={[
                  { label: "캠페인달성", value: 81 },
                  { label: "상품교육", value: 60 }
                ]}
              />
              <MiniCalendar month="12월" activeDay="4" days={["27", "28", "29", "30", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"]} />
            </>
          }
        />
      }
    >
      <TerminalWorkspace
        tabs={workspaceTabs}
        taskTabs={taskTabs}
        footer={
          <FooterLinkBar
            contact={["은행상담 1588-5000 1599-5000 (해외 82-2-2006-5000)", "신규상담 (예적금 1599-8100/대출 1599-8300)"]}
            links={["은행소개", "영업점안내", "개인정보처리방침", "보안센터"]}
            copyright="COPYRIGHTS SYNTHETIC BANKING LAB. ALL RIGHTS RESERVED. pib091"
          />
        }
        statusBar={<TerminalStatusBar />}
      >
        <TerminalBody className="portal-body" rightRail={<PortalRightRail />}>
          <TerminalBentoGrid className="portal-grid">
            <TerminalPanel title="공지사항" className="portal-panel portal-panel-7" action={<TerminalIcon name="add" size={16} />}>
              <NoticeList items={portalNotices} />
            </TerminalPanel>
            <TerminalPanel title="우리평생파트너" className="portal-panel portal-panel-5">
              <PartnerGrid items={["가가호호", "수수료면제 0원하라"]} />
            </TerminalPanel>
            <TerminalPanel title="To Do list" className="portal-panel portal-panel-7" tabs={["To Do list", "영업 전", "영업 중", "영업 후"]}>
              <div className="portal-list">
                {portalTodos.map((todo) => (
                  <div className="portal-list-row" key={todo}>
                    <span>{todo}</span>
                  </div>
                ))}
              </div>
            </TerminalPanel>
            <TerminalPanel title="상품정보" className="portal-panel portal-panel-5" tabs={["상품정보", "예금/적금", "펀드/보험"]}>
              <NoticeList items={[{ label: "- Synthetic 상품패키지 (2012.06.04)", date: "15-06-04" }]} />
            </TerminalPanel>
            <TerminalPanel title="여신접수" className="portal-panel portal-panel-12 portal-table-panel">
              <PanelTabs tabs={["여신접수", "감정결과", "기표예정"]} />
              <DenseTable columns={["No", "접수번호", "신청자", "대출상품", "신청금액", "진행상태"]} rows={creditRows} ariaLabel="Synthetic credit reception" />
            </TerminalPanel>
          </TerminalBentoGrid>
        </TerminalBody>
      </TerminalWorkspace>
    </TerminalShell>
  );
}

function PortalRightRail() {
  return (
    <RightRail>
      <TerminalPanel title="보도자료" className="portal-rail-panel">
        <NoticeList
          withBadge
          items={[
            { label: "Synthetic Lab 인수계약 체결" },
            { label: "합성 사회공헌 기금 적립" }
          ]}
        />
      </TerminalPanel>
      <TerminalPanel title="투자정보" className="portal-rail-panel is-flex" tabs={["투자정보", "WM Advisory"]}>
        <NoticeList items={[{ label: "- [Synthetic 자료] 기준금리 시나리오 보고서", date: "15-06-08" }]} />
      </TerminalPanel>
    </RightRail>
  );
}
