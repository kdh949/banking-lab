"use client";

import { useEffect, useMemo, useState, type ReactNode } from "react";

type IconName =
  | "account_balance"
  | "account_balance_wallet"
  | "add"
  | "arrow_drop_down"
  | "arrow_forward"
  | "article"
  | "badge"
  | "bar_chart"
  | "business_center"
  | "calendar_month"
  | "call"
  | "campaign"
  | "check"
  | "close"
  | "credit_card"
  | "currency_exchange"
  | "description"
  | "docs"
  | "domain"
  | "edit_square"
  | "folder"
  | "folder_open"
  | "grid_view"
  | "groups"
  | "help"
  | "history"
  | "home_work"
  | "keyboard_double_arrow_right"
  | "language"
  | "manage_search"
  | "menu_book"
  | "monitoring"
  | "more_horiz"
  | "payments"
  | "person_search"
  | "print"
  | "query_stats"
  | "receipt_long"
  | "search"
  | "settings"
  | "star"
  | "support_agent"
  | "sync_alt"
  | "task_alt"
  | "widgets"
  | "workspaces";

type ScreenKey = "portal" | "deposit" | "fee" | "inheritance" | "fund";
type SideMode = "menu" | "workflow" | "none";
type RightMode = "manual" | "marketing";

type TopModule = {
  readonly label: string;
  readonly icon: IconName;
  readonly screen?: ScreenKey;
  readonly implemented?: boolean;
};

type MenuTarget = {
  readonly code: string;
  readonly label: string;
  readonly screen?: ScreenKey;
  readonly moduleLabel?: string;
  readonly implemented?: boolean;
};

type UtilityItem = {
  readonly label: string;
  readonly icon: IconName;
  readonly mode?: SideMode;
};

type WorkspaceScreen = {
  readonly key: ScreenKey;
  readonly code: string;
  readonly title: string;
  readonly module: string;
};

type PanelProps = {
  readonly title?: string;
  readonly icon?: IconName;
  readonly tabs?: readonly string[];
  readonly actions?: ReactNode;
  readonly className?: string;
  readonly children: ReactNode;
};

type TableColumn = {
  readonly key: string;
  readonly label: string;
  readonly width?: string;
};

type TableRow = Record<string, ReactNode>;

const topModules: readonly TopModule[] = [
  { label: "수신", icon: "account_balance_wallet", screen: "deposit" },
  { label: "여신", icon: "home_work", screen: "portal" },
  { label: "여신종합", icon: "manage_search", implemented: false },
  { label: "외환", icon: "currency_exchange", screen: "fee" },
  { label: "고객", icon: "groups", screen: "inheritance" },
  { label: "CRM", icon: "campaign", implemented: false },
  { label: "방카", icon: "business_center", implemented: false },
  { label: "펀드", icon: "account_balance", screen: "fund" },
  { label: "신용카드", icon: "credit_card", implemented: false },
  { label: "전자", icon: "payments", implemented: false },
  { label: "대행", icon: "sync_alt", implemented: false },
  { label: "재무", icon: "domain", implemented: false },
  { label: "기타", icon: "query_stats", implemented: false }
];

const utilityItems: readonly UtilityItem[] = [
  { label: "즐겨찾기", icon: "star", mode: "none" },
  { label: "탑리스트", icon: "task_alt", mode: "none" },
  { label: "워크플로우", icon: "workspaces", mode: "workflow" },
  { label: "업무메뉴", icon: "widgets", mode: "menu" },
  { label: "저널보기", icon: "docs", mode: "none" },
  { label: "고객포털", icon: "language", mode: "none" },
  { label: "계산기", icon: "receipt_long", mode: "none" },
  { label: "날짜계산기", icon: "calendar_month", mode: "none" },
  { label: "일정", icon: "business_center", mode: "none" },
  { label: "오피스", icon: "article", mode: "none" },
  { label: "멀티프레임", icon: "grid_view", mode: "none" },
  { label: "트레이스", icon: "bar_chart", mode: "none" },
  { label: "히든보기", icon: "more_horiz", mode: "none" },
  { label: "스크립트", icon: "edit_square", mode: "none" }
];

const screens: readonly WorkspaceScreen[] = [
  { key: "portal", code: "SY-Starts.scn", title: "통합 포털", module: "SY-Starts..." },
  { key: "deposit", code: "20000", title: "수신_네비게이션", module: "20000" },
  { key: "fee", code: "S5801", title: "외환이자수수료내역조회", module: "S5801" },
  { key: "inheritance", code: "10607", title: "상속 및 양도 관리대장 조회/출력", module: "10607" },
  { key: "fund", code: "F0000", title: "펀드_네비게이션", module: "F0000" }
];

function defaultModuleForScreen(screen: ScreenKey) {
  if (screen === "fee") {
    return "외환";
  }
  if (screen === "inheritance") {
    return "고객";
  }
  if (screen === "fund") {
    return "펀드";
  }
  if (screen === "portal") {
    return "여신";
  }
  return "수신";
}

function defaultTargetForScreen(screen: ScreenKey, moduleLabel = defaultModuleForScreen(screen)): MenuTarget {
  const screenInfo = screens.find((item) => item.key === screen) ?? screens[1];
  return {
    code: screenInfo.code,
    label: screenInfo.title,
    moduleLabel,
    screen
  };
}

const depositTabs = ["업무포탈", "신규", "입금", "출금", "해지", "정산", "등록/해제", "조회", "통장/증명서/기타", "자기앞수표", "기타별단", "수신거래흐름도", "BPR흐름도"] as const;
const fundTabs = ["펀드흐름도", "펀드(공지)", "펀드상담", "신규", "입금/출금", "제신고/제변경", "판매사이동", "대고객발급/통장", "연금/사모펀드", "계좌정보"] as const;
const queryTabs = ["정산", "등록/해제", "조회", "통장/증명서/기타", "자기앞수표", "기타별단", "수신거래흐름도", "BPR흐름도"] as const;

const depositMenu = [
  "11. 수신기본(신규,해지,조회,기타)",
  "15. 계약공통",
  "21. 수신기본(입출금,조회,기타)거래",
  "23. 수표/어음",
  "24. 자기앞수표",
  "25. 기타별단",
  "S2. 수신정산"
] as const;

const notices = [
  [">>> [POST차세대] POST 차세대시행관련 문서 <<<", "IT금융개발부", "2014-09-25", "important"],
  ["일부 급여이체 기업의 타행이체 고객 적극 유치", "개인고객부", "2014-09-30", ""],
  ["「주택청약(종합)저축」 금리변경 안내<시행일 '14.10.1>", "개인고객부", "2014-09-30", ""],
  ["국민주택기금 대출고객에 대한 해피콜 실시 요청", "개인고객부", "2014-09-30", ""],
  ["「POST차세대시스템」전환 시 개인고객 응대 유의사항", "개인고객부", "2014-09-30", ""],
  ["제1종 국민주택채권 발행금리 인하 안내", "개인고객부", "2014-09-30", ""],
  ["주택청약저축 업무취급지침 개정<시행일 '14.10.1>", "개인고객부", "2014-09-30", ""]
] as const;

const newScreenRows = [
  ["[23601]", "수표어음교부"],
  ["[23602]", "수표어음사고등록"],
  ["[23608]", "당좌/가당 부도등록"],
  ["[23805]", "어음발행정보조회"],
  ["[23808]", "수표어음 교부계좌 조회"],
  ["[23809]", "수표어음 적정교부량조회"],
  ["[23810]", "당좌 교환결제현황"],
  ["[21680]", "계좌사고신고"],
  ["[21711]", "조건변경 등록/해제"],
  ["[23815]", "당좌가당 부도내역 조회"]
] as const;

const feeColumns: readonly TableColumn[] = [
  { key: "date", label: "거래년월일", width: "120px" },
  { key: "name", label: "거래명", width: "180px" },
  { key: "bl", label: "B/L", width: "48px" },
  { key: "lg", label: "L/G", width: "48px" },
  { key: "fee", label: "이자수수료명", width: "230px" },
  { key: "amount", label: "계산금액", width: "140px" },
  { key: "paid", label: "실거래금액", width: "140px" },
  { key: "discount", label: "감면금액", width: "120px" },
  { key: "remain", label: "미정리잔액", width: "120px" }
];

const inheritanceColumns: readonly TableColumn[] = [
  { key: "select", label: "선택", width: "50px" },
  { key: "date", label: "거래일", width: "110px" },
  { key: "time", label: "거래시각", width: "96px" },
  { key: "from", label: "양도인", width: "120px" },
  { key: "fromId", label: "양도인사업...", width: "140px" },
  { key: "fromCustomer", label: "양도인고객번호", width: "150px" },
  { key: "to", label: "양수인", width: "120px" },
  { key: "toCustomer", label: "양수인고객번호", width: "150px" }
];

const detailColumns: readonly TableColumn[] = [
  { key: "serial", label: "일련번호", width: "110px" },
  { key: "base", label: "부리대상금액", width: "140px" },
  { key: "calcStart", label: "계산시작일", width: "120px" },
  { key: "calcEnd", label: "계산종료일", width: "120px" },
  { key: "days", label: "이자계산일수", width: "130px" },
  { key: "rate", label: "적용이율", width: "110px" },
  { key: "fee", label: "수수료계산일/일수", width: "170px" },
  { key: "appliedFee", label: "적용수수료율", width: "140px" }
];

export function IntegratedTerminalApp() {
  const [activeScreen, setActiveScreen] = useState<ScreenKey>("deposit");
  const [activeModuleLabel, setActiveModuleLabel] = useState("수신");
  const [activeMenu, setActiveMenu] = useState<MenuTarget>(() => defaultTargetForScreen("deposit"));
  const [dialog, setDialog] = useState<{ readonly title: string; readonly message: string; readonly code?: string } | null>(null);
  const [sideMode, setSideMode] = useState<SideMode>("none");
  const [rightMode, setRightMode] = useState<RightMode>("manual");
  const [searchText, setSearchText] = useState("");
  const activeBase = screens.find((screen) => screen.key === activeScreen) ?? screens[1];
  const active: WorkspaceScreen = {
    ...activeBase,
    code: activeMenu.screen === activeScreen ? activeMenu.code : activeBase.code,
    title: activeMenu.screen === activeScreen ? activeMenu.label : activeBase.title,
    module: activeMenu.screen === activeScreen ? activeMenu.code : activeBase.module
  };
  const openTabs = useMemo(() => screens.filter((screen) => screen.key !== "portal" || activeScreen === "portal"), [activeScreen]);

  const showRightRail = activeScreen !== "portal" || rightMode === "marketing";
  const mainTabs = activeScreen === "fund" ? fundTabs : activeScreen === "fee" ? queryTabs : activeScreen === "inheritance" ? [] : depositTabs;

  const openUnavailableDialog = (target: Pick<MenuTarget, "code" | "label">) => {
    setDialog({
      code: target.code,
      title: target.code ? `[${target.code}] ${target.label}` : target.label,
      message: "아직 구현되지 않은 업무입니다. 화면 정의가 추가되면 이 메뉴로 연결됩니다."
    });
  };

  const selectScreen = (screen: ScreenKey, moduleLabel = defaultModuleForScreen(screen), target = defaultTargetForScreen(screen, moduleLabel)) => {
    setActiveScreen(screen);
    setActiveModuleLabel(moduleLabel);
    setActiveMenu(target);
  };

  const navigateToMenu = (target: MenuTarget) => {
    if (!target.screen || target.implemented === false) {
      openUnavailableDialog(target);
      return;
    }
    selectScreen(target.screen, target.moduleLabel ?? defaultModuleForScreen(target.screen), target);
  };

  return (
    <main className="iworks-root">
      <section className="iworks-window" aria-label="통합단말 프로토타입">
        <TerminalHeader
          activeScreen={activeScreen}
          activeModuleLabel={activeModuleLabel}
          searchText={searchText}
          onSearchText={setSearchText}
          onModuleChange={(module) => {
            if (!module.screen || module.implemented === false) {
              openUnavailableDialog({ code: "", label: `${module.label} 업무 모듈` });
              return;
            }
            selectScreen(module.screen, module.label);
            setSideMode(module.screen === "portal" ? "none" : sideMode);
          }}
        />
        <div className="iworks-workspace-tabs">
          {openTabs.map((tab) => (
            <button className={tab.key === activeScreen ? "is-active" : ""} type="button" key={tab.key} onClick={() => selectScreen(tab.key)}>
              <MaterialIcon name="folder_open" />
              <span>{tab.module}</span>
              <MaterialIcon name="close" />
            </button>
          ))}
        </div>
        <div className="iworks-body">
          <UtilityRail activeMode={sideMode} onModeChange={setSideMode} />
          <SideDrawer mode={sideMode} searchText={searchText} activeMenuCode={activeMenu.code} onMenuSelect={navigateToMenu} />
          <section className="iworks-main">
            <ScreenToolbar active={active} />
            {mainTabs.length > 0 ? <BusinessTabs tabs={mainTabs} activeIndex={activeScreen === "fund" ? 4 : 0} /> : null}
            <div className="iworks-content-row">
              <section className="iworks-content">
                {activeScreen === "portal" ? <PortalScreen /> : null}
                {activeScreen === "deposit" ? <DepositNavigationScreen onMenuSelect={navigateToMenu} /> : null}
                {activeScreen === "fee" ? <FeeInquiryScreen /> : null}
                {activeScreen === "inheritance" ? <InheritanceScreen /> : null}
                {activeScreen === "fund" ? <FundNavigationScreen onMenuSelect={navigateToMenu} /> : null}
              </section>
              {showRightRail ? <RightRail mode={rightMode} onModeChange={setRightMode} activeScreen={activeScreen} onMenuSelect={navigateToMenu} /> : null}
            </div>
          </section>
        </div>
        <StatusBar />
      </section>
      {dialog ? <AppDialog dialog={dialog} onClose={() => setDialog(null)} /> : null}
    </main>
  );
}

function TerminalHeader({
  activeScreen,
  activeModuleLabel,
  searchText,
  onSearchText,
  onModuleChange
}: {
  readonly activeScreen: ScreenKey;
  readonly activeModuleLabel: string;
  readonly searchText: string;
  readonly onSearchText: (value: string) => void;
  readonly onModuleChange: (module: TopModule) => void;
}) {
  return (
    <header className="iworks-header">
      <div className="iworks-brand">
        <div className="brand-emblem">N</div>
        <strong>INZENT</strong>
      </div>
      <label className="iworks-search">
        <MaterialIcon name="description" />
        <input value={searchText} onChange={(event) => onSearchText(event.target.value)} placeholder="통합검색" aria-label="통합검색" />
        <MaterialIcon name="search" />
      </label>
      <nav className="iworks-module-nav" aria-label="업무 모듈">
        {topModules.map((module) => (
          <button
            className={`${module.label === activeModuleLabel ? "is-active" : ""} ${module.implemented === false ? "is-unavailable" : ""}`}
            type="button"
            title={module.label}
            key={module.label}
            aria-current={module.screen === activeScreen && module.label === activeModuleLabel ? "page" : undefined}
            onClick={() => onModuleChange(module)}
          >
            <MaterialIcon name={module.icon} />
            <span>{module.label}</span>
          </button>
        ))}
      </nav>
      <div className="inz-brand">
        <strong>INZENT</strong>
        <span>Integrated UI system</span>
      </div>
    </header>
  );
}

function UtilityRail({ activeMode, onModeChange }: { readonly activeMode: SideMode; readonly onModeChange: (mode: SideMode) => void }) {
  return (
    <aside className="utility-rail" aria-label="공통 도구">
      <nav>
        {utilityItems.map((item) => (
          <button
            className={item.mode && item.mode === activeMode ? "is-active" : ""}
            type="button"
            title={item.label}
            key={item.label}
            onClick={() => onModeChange(item.mode && item.mode !== "none" ? (activeMode === item.mode ? "none" : item.mode) : "none")}
          >
            <MaterialIcon name={item.icon} />
            <span>{item.label}</span>
          </button>
        ))}
      </nav>
      <button className="rail-settings" type="button" title="설정">
        <MaterialIcon name="settings" />
      </button>
    </aside>
  );
}

function SideDrawer({
  mode,
  searchText,
  activeMenuCode,
  onMenuSelect
}: {
  readonly mode: SideMode;
  readonly searchText: string;
  readonly activeMenuCode: string;
  readonly onMenuSelect: (target: MenuTarget) => void;
}) {
  if (mode === "none") {
    return null;
  }

  if (mode === "workflow") {
    return (
      <aside className="side-drawer workflow-drawer" aria-label="워크플로우 관리창">
        <DrawerHeader title="워크플로우(사용자)" />
        <div className="drawer-section-title">워크플로우 관리창</div>
        <div className="workflow-buttons">
          <button type="button">화면추가</button>
          <button type="button">설명추가</button>
          <button type="button">삭제</button>
          <button type="button">저장</button>
          <button type="button">수정</button>
          <button type="button">새로고침</button>
        </div>
        <WorkflowDiagram onMenuSelect={onMenuSelect} />
      </aside>
    );
  }

  const filter = searchText.trim().toLowerCase();
  const items = menuTree.flatMap((group) => group.items.map((item) => ({ ...item, group: group.label })));
  const visible = filter ? items.filter((item) => `${item.code} ${item.label} ${item.group}`.toLowerCase().includes(filter)) : items;

  return (
    <aside className="side-drawer menu-drawer" aria-label="업무메뉴">
      <DrawerHeader title="업무메뉴" />
      <div className="drawer-path">[ 수신/펀드 ]</div>
      <div className="menu-accordion">
        {menuTree.map((group) => (
          <details open={group.open} key={group.label}>
            <summary>{group.label}</summary>
            <div className="tree-lines">
              {group.items.map((item) => (
                <button
                  className={activeMenuCode === item.code ? "is-selected" : ""}
                  type="button"
                  key={item.code}
                  onClick={() => onMenuSelect(item)}
                >
                  <MaterialIcon name="description" />
                  <span>[{item.code}]</span>
                  {item.label}
                </button>
              ))}
            </div>
          </details>
        ))}
      </div>
      {filter ? (
        <div className="search-result-list">
          <strong>검색결과</strong>
          {visible.slice(0, 8).map((item) => (
            <button className={activeMenuCode === item.code ? "is-selected" : ""} type="button" key={`${item.group}-${item.code}`} onClick={() => onMenuSelect(item)}>
              [{item.code}] {item.label}
            </button>
          ))}
        </div>
      ) : null}
    </aside>
  );
}

const menuTree = [
  {
    label: "정산",
    open: true,
    items: [
      { code: "S5801", label: "외환이자수수료내역조회", screen: "fee" as const, moduleLabel: "외환" },
      { code: "50841", label: "외환거래내역조회", screen: "fee" as const, moduleLabel: "외환" },
      { code: "50710", label: "미정수내역관리", screen: "fee" as const, moduleLabel: "외환" }
    ]
  },
  {
    label: "계약",
    open: true,
    items: [
      { code: "10601", label: "정보변경 전입/명의변경", screen: "inheritance" as const, moduleLabel: "고객" },
      { code: "10602", label: "세금우대 일반 상호전환", screen: "inheritance" as const, moduleLabel: "고객" },
      { code: "10607", label: "상속 및 양도 관리대장", screen: "inheritance" as const, moduleLabel: "고객" }
    ]
  },
  {
    label: "수신",
    open: false,
    items: [
      { code: "20000", label: "수신 네비게이션", screen: "deposit" as const, moduleLabel: "수신" },
      { code: "23601", label: "수표어음교부", screen: "deposit" as const, moduleLabel: "수신" }
    ]
  },
  {
    label: "펀드",
    open: false,
    items: [{ code: "F0000", label: "펀드 네비게이션", screen: "fund" as const, moduleLabel: "펀드" }]
  }
] as const;

function DrawerHeader({ title }: { readonly title: string }) {
  return (
    <div className="drawer-header">
      <strong>{title}</strong>
      <span aria-hidden="true">×</span>
    </div>
  );
}

function WorkflowDiagram({ onMenuSelect }: { readonly onMenuSelect: (target: MenuTarget) => void }) {
  const steps: ReadonlyArray<{ label: string; target?: MenuTarget; strong?: boolean }> = [
    { label: "시작" },
    { label: "오전일일업무" },
    { label: "외환이자수수료내역조회 [S5801]", target: { code: "S5801", label: "외환이자수수료내역조회", screen: "fee", moduleLabel: "외환" }, strong: true },
    { label: "상속 및 양도 관리대장 [10607]", target: { code: "10607", label: "상속 및 양도 관리대장", screen: "inheritance", moduleLabel: "고객" }, strong: true },
    { label: "종료" }
  ];

  return (
    <div className="workflow-flow">
      {steps.map((step, index) => (
        <div className="workflow-step-wrap" key={step.label}>
          <button className={step.strong ? "is-strong" : ""} type="button" onClick={() => step.target && onMenuSelect(step.target)}>
            {step.label}
          </button>
          {index < steps.length - 1 ? <span className="workflow-arrow">▼</span> : null}
        </div>
      ))}
    </div>
  );
}

function ScreenToolbar({ active }: { readonly active: WorkspaceScreen }) {
  return (
    <div className="screen-toolbar">
      <div className="screen-title">
        <button type="button" aria-label="즐겨찾기">
          <MaterialIcon name="star" />
        </button>
        <strong>
          [{active.code}] {active.title}
        </strong>
        <MaterialIcon name="sync_alt" />
        <MaterialIcon name="description" />
      </div>
      <div className="screen-actions">
        <button type="button">
          <MaterialIcon name="support_agent" /> IT 상담센터
        </button>
        <button className="incident" type="button">
          <MaterialIcon name="call" /> IT 기기장애
        </button>
        <button className="complete" type="button">
          완료
        </button>
        <button className="related" type="button">
          연계 / 연관 업무 바로가기
        </button>
      </div>
    </div>
  );
}

function BusinessTabs({ tabs, activeIndex = 0 }: { readonly tabs: readonly string[]; readonly activeIndex?: number }) {
  return (
    <nav className="business-tabs" aria-label="업무 탭">
      {tabs.map((tab, index) => (
        <button className={index === activeIndex ? "is-active" : ""} type="button" key={tab}>
          {tab}
        </button>
      ))}
    </nav>
  );
}

function PortalScreen() {
  return (
    <div className="portal-layout">
      <aside className="operator-profile">
        <div className="portrait-card">
          <div className="synthetic-portrait" aria-hidden="true">
            <span>BL</span>
          </div>
          <div>
            <strong>공승연 (B20100)</strong>
            <span>수신, 여신</span>
            <span>지점: 을지로2가</span>
            <span className="phone-line">
              <MaterialIcon name="call" /> 010-9974-2201
            </span>
          </div>
          <button type="button">내정보관리</button>
        </div>
        <MiniWidget title="메시지 알림" icon="description">
          <div className="mail-grid">
            <span>받은메일: <b>11</b></span>
            <span>결재건수: <b>7</b></span>
            <span>안읽은메일: <b>5</b></span>
            <span>결재반려건수: <b>0</b></span>
          </div>
        </MiniWidget>
        <MiniWidget title="사이버연수원" icon="support_agent">
          <Progress label="캠페인달성" value={81} />
          <Progress label="상품교육" value={60} />
          <Progress label="업무능력" value={64} />
        </MiniWidget>
        <MiniWidget title="일정" icon="calendar_month">
          <MiniCalendar />
        </MiniWidget>
        <MiniWidget title="금리정보" icon="monitoring">
          <Sparkline />
        </MiniWidget>
        <MiniWidget title="환율정보" icon="currency_exchange">
          <RateList />
        </MiniWidget>
      </aside>
      <section className="portal-grid">
        <Panel title="공지사항" className="portal-panel wide">
          <ListRows rows={["- [이벤트]나의 포인트 기부 행사", "- [FAQ] 이용안내 및 신청", "- [FAQ] 한눈에 보기 신청", "- 사이트맵 이용안내", "- [기타] 경제지표 발표 캘린더"]} dates={["15-06-08", "15-06-07", "15-06-07", "15-06-05", "15-06-02"]} />
        </Panel>
        <Panel title="보도자료" className="portal-panel">
          <ListRows rows={["저축은행 인수계약 체결", "이웃돕기 성금 10억원 기탁", "서울시 안심서비스 실시", "우리 백장대소 정기예금 판매", "스마트뱅킹 1천만 고객 돌파"]} badge />
        </Panel>
        <Panel title="To Do list" tabs={["영업 전", "영업 중", "영업 후"]} className="portal-panel wide short">
          <ListRows rows={["점별 관리대상 발생 조회", "할인이음관계인 관리대상발생 조회", "만기 도래 안내 조회", "연체 계약 목록 조회", "만기 도래 안내 조회"]} />
        </Panel>
        <Panel title="상품정보" tabs={["패키지", "예금/적금", "펀드/보험"]} className="portal-panel short">
          <ListRows rows={["상품패키지 (2012.06.04)", "상품패키지 (2012.05.23)", "상품패키지 (2012.05.15)", "글로벌 자산배분 입니다"]} dates={["15-06-04", "15-05-23", "15-05-15", "15-05-11"]} />
        </Panel>
        <Panel title="여신접수" tabs={["감정결과", "감정의뢰", "여신심사", "기표예정"]} className="portal-panel wide">
          <DataTable
            columns={[
              { key: "no", label: "No", width: "42px" },
              { key: "receipt", label: "접수번호" },
              { key: "name", label: "신청자" },
              { key: "product", label: "대출상품" },
              { key: "amount", label: "신청금액" },
              { key: "date", label: "접수일자" },
              { key: "status", label: "진행상태" }
            ]}
            rows={[
              { no: "5", receipt: "120621-C30-011", name: "한상대", product: "드림론", amount: "22,000,000", date: "2015-06-21", status: <span className="state-pill blue">접수</span> },
              { no: "4", receipt: "120618-C12-505", name: "이화성", product: "전세자금대출", amount: "51,000,000", date: "2015-06-18", status: <span className="state-pill">접수</span> },
              { no: "3", receipt: "120608-D01-220", name: "김학연", product: "전세자금대출", amount: "25,000,000", date: "2015-06-08", status: <span className="state-pill gray">대기</span> }
            ]}
          />
        </Panel>
        <Panel title="투자정보" className="portal-panel">
          <ListRows rows={["[증권사자료] 중국 금리인하 관련 보고서", "[보고서] Monthly Market Report", "[증권사자료] ELS 시장 동향분석", "[기타] 주요증시 휴장일 캘린더"]} dates={["15-06-08", "15-06-07", "15-06-07", "15-06-02"]} />
        </Panel>
        <Panel title="WM Research" tabs={["일간리포트", "정기리포트", "Issue분석", "펀드정보"]} className="portal-panel wide short">
          <ListRows rows={["Daily Market Research (2015.06.18)", "Daily Market Research (2015.06.17)", "Daily Market Research (2015.06.16)", "Daily Market Research (2015.06.15)"]} dates={["2015-06-18", "2015-06-17", "2015-06-16", "2015-06-15"]} />
        </Panel>
        <Panel title="WM Advisory" tabs={["세무정보", "부동산정보", "외환정보", "주요문서모음"]} className="portal-panel short">
          <ListRows rows={["★WM Sales Cafe 통합운영 안내★", "[소득] 오피스텔 임대소득 등록 가능", "[신탁] 금융소득종합과세 대상자인 경우", "토지보상자금 유치를 위한 제안서"]} dates={["2015-06-18", "2015-06-07", "2015-06-16", "2015-06-14"]} />
        </Panel>
      </section>
      <FooterBand />
    </div>
  );
}

function DepositNavigationScreen({ onMenuSelect }: { readonly onMenuSelect: (target: MenuTarget) => void }) {
  const menuTargets: readonly MenuTarget[] = [
    { code: "20000", label: "수신기본", screen: "deposit", moduleLabel: "수신" },
    { code: "15000", label: "계약공통", screen: "deposit", moduleLabel: "수신" },
    { code: "21000", label: "수신기본 거래", screen: "deposit", moduleLabel: "수신" },
    { code: "23601", label: "수표/어음", screen: "deposit", moduleLabel: "수신" },
    { code: "24000", label: "자기앞수표", screen: "deposit", moduleLabel: "수신" },
    { code: "25000", label: "기타별단", screen: "deposit", moduleLabel: "수신" },
    { code: "S200", label: "수신정산", screen: "deposit", moduleLabel: "수신" }
  ];

  return (
    <div className="deposit-screen">
      <section className="deposit-top-grid">
        <Panel title="수신업무" className="deposit-menu-panel">
          <div className="deposit-menu-box">
            <h2>
              <MaterialIcon name="keyboard_double_arrow_right" /> 수신업무 중간화면
            </h2>
            {depositMenu.map((item, index) => (
              <button className={item.startsWith("23.") ? "is-highlight" : ""} type="button" key={item} onClick={() => onMenuSelect(menuTargets[index])}>
                {item}
              </button>
            ))}
          </div>
        <label className="number-choice">
          번호선택
          <input
            aria-label="번호선택"
            inputMode="numeric"
            maxLength={4}
            pattern="[0-9]*"
            onChange={(event) => {
              event.currentTarget.value = event.currentTarget.value.replace(/\D/gu, "");
            }}
          />
        </label>
        </Panel>
        <Panel title="수신_중요공지" className="notice-panel">
          <NoticeTable />
        </Panel>
      </section>
      <section className="deposit-bottom-grid">
        <Panel title="자주묻는 질문" className="empty-board">
          <BoardHeader />
        </Panel>
        <Panel title="알면 편한 단말 메뉴얼" className="manual-board">
          <BoardHeader />
          <button type="button">[POST차세대 변경업무 메뉴얼]수신업무</button>
          <button type="button">[POST차세대 시스템 화면구성] 이렇게 좋아져요~!(수신)</button>
        </Panel>
        <Panel title="신규화면 공지" className="new-screen-board">
          <table>
            <tbody>
              {newScreenRows.map(([code, label]) => (
                <tr key={code}>
                  <td>
                    <button type="button" onClick={() => onMenuSelect({ code: code.replace(/\[|\]/gu, ""), label, screen: "deposit", moduleLabel: "수신" })}>
                      {code}
                    </button>
                  </td>
                  <td>
                    <button type="button" onClick={() => onMenuSelect({ code: code.replace(/\[|\]/gu, ""), label, screen: "deposit", moduleLabel: "수신" })}>
                      {label}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Panel>
      </section>
    </div>
  );
}

function FeeInquiryScreen() {
  return (
    <div className="query-screen">
      <SearchBox>
        <Field label="고객계좌번호" type="search" />
        <Field label="내부계약번호" />
        <Field label="이자수수료종류" type="select" value="%" />
        <Field label="거래상태" type="select" value="%-전체" emphasized />
        <Field label="입출금구분" type="select" value="-전체" />
        <RadioGroup label="출력구분" options={["화면", "단말", "레이저"]} />
        <Field label="조회기간" value="2005-01-01" type="date" emphasized />
      </SearchBox>
      <Panel title="외환이자수수료 발생내역" className="query-panel">
        <DataTable columns={feeColumns} rows={[]} minRows={9} />
      </Panel>
      <Panel title="외환이자수수료 상세내역" className="query-panel short">
        <DataTable columns={detailColumns} rows={[]} minRows={3} />
      </Panel>
      <Panel title="외환수수료이자 금리환율상세" className="query-panel short">
        <DataTable
          columns={[
            { key: "serial", label: "일련번호", width: "110px" },
            { key: "type", label: "금리환율구분", width: "150px" },
            { key: "rateId", label: "금리환율상세일련번호", width: "210px" },
            { key: "baseDate", label: "금리적용기준년월일", width: "180px" },
            { key: "shown", label: "표면적용율", width: "130px" },
            { key: "actual", label: "실적적용율", width: "130px" },
            { key: "condition", label: "상품조건코드", width: "150px" },
            { key: "contract", label: "내부계약번호", width: "170px" }
          ]}
          rows={[]}
          minRows={4}
        />
      </Panel>
    </div>
  );
}

function InheritanceScreen() {
  return (
    <div className="inheritance-screen">
      <div className="notice-strip">
        <p className="danger">※ 사망으로 인한 해지건은 계좌번호 혹은 변경전실명번호로만 조회 가능합니다.</p>
        <p>※ 제자번호는 양도 후 변경된 계좌번호를 입력하시기 바랍니다.</p>
        <p>※ 양도거래후 첨부순서&nbsp;&nbsp;① 고객정보등록표인자 ==&gt; ② 양도상속관리대장 출력</p>
      </div>
      <SearchBox compact>
        <Field label="조회구분" type="select" value="1-계좌번호" />
        <Field label="계좌번호" type="search" emphasized />
        <Field label="회차" type="search" />
        <Field label="양도인고객번호" value="고객 가져오기" type="readonly" />
        <Field label="고객명" type="readonly" />
        <Field label="전행고객번호" type="readonly" />
        <Field label="사업자번호/생일" type="readonly" />
      </SearchBox>
      <div className="inheritance-actions">
        <button type="button">고객정보등록표 인자</button>
        <button type="button">상속및양도관리대장 출력</button>
      </div>
      <DataTable columns={inheritanceColumns} rows={[]} minRows={15} />
    </div>
  );
}

function FundNavigationScreen({ onMenuSelect }: { readonly onMenuSelect: (target: MenuTarget) => void }) {
  const groups = [
    {
      title: "입금",
      rows: [
        ["[F2201] (MMF)펀드입금", "※ MMF계좌 입금 거래"],
        ["[F2202] (일반)펀드입금", "※ 일반펀드 계좌 입금 거래"],
        ["[F2203] 펀드대계좌입금", "※ 다계좌 입금 거래"]
      ]
    },
    {
      title: "출금\n해지신청",
      rows: [
        ["[F2301] 펀드판매/해지신청", "※ 펀드 판매/해지 신청"],
        ["[F2302] 펀드출금/해지(당일/별단출금)", "※ 개인MMF 당일출금 / 일반펀드 별단출금"],
        ["[F2303] 펀드인출가능금액(해지예정조회)", "※ 인출가능(해지예정) 금액 조회"],
        ["[F2304] 펀드예탁금이자정리(일괄지급)", "※ 원리금지급계좌 미등록 사유로 미지급된 예탁금이자 정리"],
        ["[F2305] 펀드예탁금이자환급", "※ 예탁금 이자 환급처리"],
        ["[F2306] 펀드판매 본부승인신청(구속성 관련)", "※ 기안결재 후, 본부 승인 신청 등록"],
        ["[F2307] 승인계좌 판매신청(구속성 관련)", "※ 구속성 관련 본부 승인 받은 계좌에 한해 17시 이후 판매신청"]
      ]
    },
    {
      title: "거래취소",
      rows: [
        ["[F2401] 펀드거래취소", "※ 취소대상 거래 조회 및 취소처리"],
        ["[F2402] 펀드본부승인신청등록(거래취소)", "※ 거래취소 본부 승인 신청 등록/조회"],
        ["[F2403] 본부승인신청등록(사모펀드해지)", "※ 해지하고자 하는 사모펀드가 해당 상품의 마지막 계좌일 경우 사용"]
      ]
    }
  ];

  return (
    <div className="fund-screen">
      <div className="fund-list">
        <SectionLabel title="입금/출금/해지신청" />
        {groups.map((group) => (
          <div className="fund-group" key={group.title}>
            <div className="fund-group-title">{group.title}</div>
            <div className="fund-group-rows">
              {group.rows.map(([code, note]) => (
                <button className="fund-row" type="button" key={code} onClick={() => onMenuSelect(fundTargetFromRow(code, note))}>
                  <span>{code}</span>
                  <span>{note}</span>
                </button>
              ))}
            </div>
          </div>
        ))}
      </div>
      <div className="flow-row">
        <FlowPanel title="입금/거래취소">
          <FlowChart
            lanes={[
              [
                { label: "입금", type: "process", target: fundTarget("F2201", "(MMF)펀드입금") },
                { label: "확인증 교부 및 안내", type: "process", muted: true }
              ],
              [
                { label: "거래취소", type: "process", target: fundTarget("F2401", "펀드거래취소") },
                { label: "취소가능여부", type: "decision" },
                { label: "기준시간 전", type: "decision" },
                { label: "거래취소", type: "process", target: fundTarget("F2401", "펀드거래취소") }
              ]
            ]}
            onMenuSelect={onMenuSelect}
          />
        </FlowPanel>
        <FlowPanel title="출금">
          <FlowChart
            lanes={[
              [
                { label: "인출가능금액조회", type: "process", target: fundTarget("F2303", "펀드인출가능금액") },
                { label: "출금", type: "process", target: fundTarget("F2302", "펀드출금/해지") },
                { label: "확인증 교부 및 안내", type: "process", muted: true },
                { label: "계좌거래내역조회", type: "process", target: fundTarget("F2501", "펀드계좌거래내역조회") }
              ]
            ]}
            onMenuSelect={onMenuSelect}
          />
        </FlowPanel>
        <FlowPanel title="펀드예탁금이자정리">
          <FlowChart
            lanes={[
              [
                { label: "별단미정리내역", type: "process", target: fundTarget("F2304", "펀드예탁금이자정리") },
                { label: "원리금지급계좌 등록유무", type: "decision" },
                { label: "예탁금이자정리", type: "decision" },
                { label: "예탁금이자정리", type: "process", target: fundTarget("F2304", "펀드예탁금이자정리") }
              ],
              [
                { label: "계좌개설", type: "process", target: fundTarget("F2101", "펀드계좌개설") },
                { label: "예탁금이자환급", type: "process", target: fundTarget("F2305", "펀드예탁금이자환급") }
              ]
            ]}
            onMenuSelect={onMenuSelect}
          />
        </FlowPanel>
      </div>
    </div>
  );
}

function fundTarget(code: string, label: string): MenuTarget {
  return { code, label, screen: "fund", moduleLabel: "펀드" };
}

function fundTargetFromRow(codeText: string, note: string): MenuTarget {
  const code = codeText.match(/\[([^\]]+)\]/u)?.[1] ?? codeText;
  const label = codeText.replace(/\[[^\]]+\]\s*/u, "").trim() || note.replace(/^※\s*/u, "");
  return fundTarget(code, label);
}

function RightRail({
  mode,
  onModeChange,
  activeScreen,
  onMenuSelect
}: {
  readonly mode: RightMode;
  readonly onModeChange: (mode: RightMode) => void;
  readonly activeScreen: ScreenKey;
  readonly onMenuSelect: (target: MenuTarget) => void;
}) {
  if (mode === "marketing") {
    return (
      <aside className="right-rail marketing-rail" aria-label="인사이드뷰">
        <div className="inside-title">
          <span>인사이드뷰</span>
          <button type="button" onClick={() => onModeChange("manual")}>×</button>
        </div>
        <BusinessTabs tabs={["여신연체", "카드연체", "자점손실", "전점손실", "특이사항", "거래성향", "메모"]} />
        <Panel title="내상품" actions={<button type="button">상세</button>}>
          <select aria-label="내상품">
            <option>우측 클릭 후 선택하세요</option>
          </select>
        </Panel>
        <Panel title="추천상품" tabs={["상세", "탐색", "관심상품"]}>
          <div className="empty-yellow">추천상품명</div>
        </Panel>
        <Panel title="수행마케팅" actions={<button type="button">반응등록</button>}>
          <div className="rail-empty">마케팅내용</div>
        </Panel>
        <Panel title="알림마케팅">
          <div className="rail-empty">정보명</div>
        </Panel>
        <Panel title="상품서비스(전점대상)" tabs={["인포뷰"]}>
          <div className="chip-grid">
            {["입출식", "적립식", "거치식", "여신", "신탁", "수익증권", "신용카드", "체크카드", "보험", "급여이체", "인터넷", "스마트"].map((chip) => (
              <span key={chip}>{chip}</span>
            ))}
          </div>
        </Panel>
      </aside>
    );
  }

  return (
    <aside className="right-rail" aria-label="업무 매뉴얼">
      <button className="rail-head" type="button" onClick={() => onModeChange("marketing")}>
        연계 / 연관 업무 바로가기
      </button>
      <button type="button" className="manual-button">업무 매뉴얼</button>
      <button type="button" className="manual-button">필요서류목록 확인</button>
      {activeScreen === "inheritance" ? (
        <button type="button" className="quick-link" onClick={() => onMenuSelect({ code: "10802", label: "계좌변경내역조회", screen: "inheritance", moduleLabel: "고객" })}>
          [10802] 계좌변경내역조회
        </button>
      ) : null}
      {activeScreen === "fee" ? (
        <>
          <button type="button" className="quick-link" onClick={() => onMenuSelect({ code: "50841", label: "외환거래내역조회", screen: "fee", moduleLabel: "외환" })}>
            [50841] 외환거래내역조회
          </button>
          <button type="button" className="quick-link" onClick={() => onMenuSelect({ code: "50710", label: "미정수내역관리", screen: "fee", moduleLabel: "외환" })}>
            [50710] 미정수내역관리
          </button>
        </>
      ) : null}
    </aside>
  );
}

function Panel({ title, icon, tabs, actions, className = "", children }: PanelProps) {
  return (
    <article className={`iworks-panel ${className}`}>
      {title || tabs ? (
        <div className="panel-heading">
          <div>
            {icon ? <MaterialIcon name={icon} /> : null}
            {title ? <strong>{title}</strong> : null}
            {tabs ? (
              <div className="panel-tabs">
                {tabs.map((tab, index) => (
                  <button className={index === 0 ? "is-active" : ""} type="button" key={tab}>
                    {tab}
                  </button>
                ))}
              </div>
            ) : null}
          </div>
          {actions ? <div className="panel-actions">{actions}</div> : null}
        </div>
      ) : null}
      <div className="panel-body">{children}</div>
    </article>
  );
}

function SearchBox({ children, compact = false }: { readonly children: ReactNode; readonly compact?: boolean }) {
  return <section className={`search-box ${compact ? "is-compact" : ""}`}>{children}</section>;
}

function Field({
  label,
  type = "text",
  value,
  emphasized = false
}: {
  readonly label: string;
  readonly type?: "text" | "search" | "select" | "date" | "readonly";
  readonly value?: string;
  readonly emphasized?: boolean;
}) {
  const [lookupOpen, setLookupOpen] = useState(false);
  const controlClass = emphasized ? "is-emphasis" : "";
  return (
    <div className="field">
      <span>{label}</span>
      {type === "select" ? (
        <TerminalSelect className={controlClass} label={label} options={selectOptionsForField(label, value)} value={value ?? "-전체"} />
      ) : (
        <div className={`input-wrap ${type === "search" ? "has-search" : ""}`}>
          <input aria-label={label} className={controlClass} defaultValue={value} readOnly={type === "readonly"} type={type === "date" ? "text" : "text"} />
          {type === "search" ? (
            <button className="lookup-icon-button" type="button" aria-label={`${label} 추가 조회`} onClick={() => setLookupOpen(true)}>
              <MaterialIcon name="search" />
            </button>
          ) : null}
        </div>
      )}
      {lookupOpen ? <LookupDialog label={label} onClose={() => setLookupOpen(false)} /> : null}
    </div>
  );
}

function TerminalSelect({ className, label, options, value }: { readonly className: string; readonly label: string; readonly options: readonly string[]; readonly value: string }) {
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState(value);

  return (
    <div className="terminal-select">
      <button className={`terminal-select-button ${className}`} type="button" aria-expanded={open} aria-label={label} onClick={() => setOpen((current) => !current)}>
        <span>{selected}</span>
        <MaterialIcon name="arrow_drop_down" />
      </button>
      {open ? (
        <div className="terminal-select-menu" role="listbox" aria-label={`${label} 선택`}>
          {options.map((option) => (
            <button
              className={option === selected ? "is-selected" : ""}
              type="button"
              role="option"
              aria-selected={option === selected}
              key={option}
              onClick={() => {
                setSelected(option);
                setOpen(false);
              }}
            >
              {option}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}

function selectOptionsForField(label: string, value?: string) {
  if (label === "조회구분") {
    return ["1-계좌번호", "2-변경전실명번호", "3-전행고객번호", "전체"];
  }
  if (label === "이자수수료종류") {
    return ["%", "전체", "이자", "수수료"];
  }
  if (label === "거래상태") {
    return ["%-전체", "정상", "취소", "미정리"];
  }
  if (label === "입출금구분") {
    return ["-전체", "입금", "출금"];
  }
  return Array.from(new Set([value ?? "-전체", "전체"]));
}

function LookupDialog({ label, onClose }: { readonly label: string; readonly onClose: () => void }) {
  const [keyword, setKeyword] = useState("");
  const rows = lookupRowsForField(label).filter((row) => `${row.code} ${row.name} ${row.detail}`.toLowerCase().includes(keyword.toLowerCase()));

  return (
    <div className="dialog-backdrop" role="presentation">
      <section className="terminal-dialog lookup-dialog" role="dialog" aria-modal="true" aria-labelledby="lookup-dialog-title">
        <header>
          <strong id="lookup-dialog-title">{label} 추가 조회</strong>
          <button type="button" aria-label="닫기" onClick={onClose}>
            <MaterialIcon name="close" />
          </button>
        </header>
        <div className="lookup-search-row">
          <span>검색어</span>
          <input autoFocus value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="번호 또는 이름 입력" />
          <button type="button">조회</button>
        </div>
        <div className="lookup-result-table">
          <div className="lookup-head">
            <span>구분</span>
            <span>명칭</span>
            <span>상세</span>
          </div>
          {rows.map((row) => (
            <button type="button" key={row.code} onClick={onClose}>
              <span>{row.code}</span>
              <span>{row.name}</span>
              <span>{row.detail}</span>
            </button>
          ))}
        </div>
        <footer>
          <button type="button" onClick={onClose}>선택</button>
          <button type="button" onClick={onClose}>닫기</button>
        </footer>
      </section>
    </div>
  );
}

function lookupRowsForField(label: string) {
  if (label.includes("고객")) {
    return [
      { code: "C-102391", name: "김우리", detail: "개인 / 생년월일 마스킹" },
      { code: "C-384020", name: "한상대", detail: "개인 / 실명확인 완료" },
      { code: "B-029410", name: "우리상사", detail: "법인 / 사업자번호 마스킹" }
    ];
  }
  if (label.includes("계좌") || label.includes("계약")) {
    return [
      { code: "1002-***-4421", name: "요구불 계좌", detail: "정상 / 본인확인 필요" },
      { code: "2001-***-0194", name: "펀드 계좌", detail: "정상 / 거래가능" },
      { code: "3004-***-7750", name: "외환 계약", detail: "미정리 수수료 있음" }
    ];
  }
  return [
    { code: "001", name: `${label} 기본조회`, detail: "현재 화면 조건으로 조회" },
    { code: "002", name: `${label} 상세조회`, detail: "추가 조건 입력 가능" }
  ];
}

function RadioGroup({ label, options }: { readonly label: string; readonly options: readonly string[] }) {
  return (
    <fieldset className="radio-group">
      <legend>{label}</legend>
      {options.map((option, index) => (
        <label key={option}>
          <input defaultChecked={index === 0} name={label} type="radio" /> {option}
        </label>
      ))}
    </fieldset>
  );
}

function DataTable({ columns, rows, minRows = 0 }: { readonly columns: readonly TableColumn[]; readonly rows: readonly TableRow[]; readonly minRows?: number }) {
  const filler = Array.from({ length: Math.max(0, minRows - rows.length) });
  return (
    <div className="data-table-scroll">
      <table className="data-table">
        <colgroup>
          {columns.map((column) => (
            <col style={column.width ? { width: column.width } : undefined} key={column.key} />
          ))}
        </colgroup>
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.key}>{column.label}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, rowIndex) => (
            <tr key={`row-${rowIndex}`}>
              {columns.map((column) => (
                <td key={column.key}>{row[column.key]}</td>
              ))}
            </tr>
          ))}
          {filler.map((_, index) => (
            <tr className="empty-row" key={`empty-${index}`}>
              {columns.map((column) => (
                <td key={column.key}>&nbsp;</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function NoticeTable() {
  return (
    <div className="notice-table">
      <div className="notice-caption">
        <MaterialIcon name="keyboard_double_arrow_right" /> 수신업무 공지사항
      </div>
      <DataTable
        columns={[
          { key: "no", label: "순번", width: "70px" },
          { key: "title", label: "제목" },
          { key: "dept", label: "등록부서", width: "120px" },
          { key: "date", label: "등록일시", width: "120px" }
        ]}
        rows={notices.map(([title, dept, date, important], index) => ({
          no: String(index + 1),
          title: <span className={important ? "important-notice" : ""}>{title}</span>,
          dept,
          date
        }))}
      />
    </div>
  );
}

function BoardHeader() {
  return (
    <div className="board-header">
      <strong>제&nbsp;&nbsp;&nbsp;&nbsp;목</strong>
    </div>
  );
}

function MiniWidget({ title, icon, children }: { readonly title: string; readonly icon: IconName; readonly children: ReactNode }) {
  return (
    <section className="mini-widget">
      <h3>
        <MaterialIcon name={icon} />
        {title}
      </h3>
      <div>{children}</div>
    </section>
  );
}

function Progress({ label, value }: { readonly label: string; readonly value: number }) {
  return (
    <div className="progress-row">
      <span>{label}</span>
      <div>
        <i style={{ width: `${value}%` }} />
      </div>
      <em>{value}%</em>
    </div>
  );
}

function MiniCalendar() {
  const days = Array.from({ length: 31 }, (_, index) => String(index + 1));
  return (
    <div className="mini-calendar">
      <div>12월</div>
      <div className="calendar-grid">
        {days.map((day) => (
          <span className={day === "28" ? "is-today" : ""} key={day}>
            {day}
          </span>
        ))}
      </div>
    </div>
  );
}

function Sparkline() {
  return (
    <div className="sparkline" aria-label="금리 추이">
      {[24, 25, 28, 30, 34, 35, 36, 38, 39, 40].map((height, index) => (
        <span style={{ height }} key={index} />
      ))}
    </div>
  );
}

function RateList() {
  return (
    <table className="rate-list">
      <tbody>
        {[
          ["USD", "1194.54", "1153.46"],
          ["GBP", "1845.37", "1773.37"],
          ["JPY100", "1508.26", "1456.38"],
          ["CNY", "197.29", "175.18"]
        ].map(([unit, buy, sell]) => (
          <tr key={unit}>
            <td>{unit}</td>
            <td>{buy}</td>
            <td>{sell}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function ListRows({ rows, dates, badge = false }: { readonly rows: readonly string[]; readonly dates?: readonly string[]; readonly badge?: boolean }) {
  return (
    <div className="list-rows">
      {rows.map((row, index) => (
        <div className="list-row" key={`${row}-${index}`}>
          <span>{badge ? <b>N</b> : null}{row}</span>
          {dates?.[index] ? <time>{dates[index]}</time> : null}
        </div>
      ))}
    </div>
  );
}

function FooterBand() {
  return (
    <footer className="portal-footer">
      <p>은행상담 1588-5000 1599-5000 (해외 82-2-2006-5000) | 고객의 말씀 080-365-5000</p>
      <p>은행소개 | 영업점안내 | 고객광장 | 개인정보처리방침 | 사고신고 | 전자민원접수 | 보안센터</p>
      <p>COPYRIGHTS WOORI BANK. ALL RIGHTS RESERVED. synthetic lab</p>
    </footer>
  );
}

function SectionLabel({ title }: { readonly title: string }) {
  return (
    <div className="section-label">
      <MaterialIcon name="keyboard_double_arrow_right" />
      {title}
    </div>
  );
}

function FlowPanel({ title, children }: { readonly title: string; readonly children: ReactNode }) {
  return (
    <section className="flow-panel">
      <SectionLabel title={title} />
      {children}
    </section>
  );
}

type FlowNode = {
  readonly label: string;
  readonly type: "process" | "decision";
  readonly muted?: boolean;
  readonly target?: MenuTarget;
};

function FlowChart({ lanes, onMenuSelect }: { readonly lanes: readonly (readonly FlowNode[])[]; readonly onMenuSelect: (target: MenuTarget) => void }) {
  return (
    <div className={`flow-chart lanes-${lanes.length}`}>
      {lanes.map((lane, laneIndex) => (
        <div className="flow-lane" key={laneIndex}>
          {lane.map((node, index) => {
            const className = `flow-node ${node.type} ${node.muted ? "is-muted" : ""}`;
            return (
              <div className="flow-node-wrap" key={`${node.label}-${index}`}>
                {node.target ? (
                  <button className={className} type="button" onClick={() => onMenuSelect(node.target!)}>
                    {node.label}
                  </button>
                ) : (
                  <div className={className}>{node.label}</div>
                )}
                {index < lane.length - 1 ? <div className="flow-arrow" /> : null}
              </div>
            );
          })}
        </div>
      ))}
    </div>
  );
}

function AppDialog({ dialog, onClose }: { readonly dialog: { readonly title: string; readonly message: string; readonly code?: string }; readonly onClose: () => void }) {
  return (
    <div className="dialog-backdrop" role="presentation">
      <section className="terminal-dialog unavailable-dialog" role="dialog" aria-modal="true" aria-labelledby="unavailable-dialog-title">
        <button className="dialog-close" type="button" aria-label="닫기" onClick={onClose}>
          <MaterialIcon name="close" />
        </button>
        <div className="dialog-x-mark">
          <MaterialIcon name="close" />
        </div>
        <strong id="unavailable-dialog-title">{dialog.title}</strong>
        <p>{dialog.message}</p>
        <button className="dialog-confirm" type="button" onClick={onClose}>
          확인
        </button>
      </section>
    </div>
  );
}

function StatusBar() {
  const [status, setStatus] = useState({
    clientIp: "확인 중",
    serverTime: "연결 중"
  });

  useEffect(() => {
    let active = true;
    let serverOffsetMs = 0;

    const formatServerTime = (serverTimeIso: string) => {
      const serverTime = new Date(serverTimeIso);
      if (Number.isNaN(serverTime.getTime())) {
        return "시간 확인 실패";
      }
      return formatDateTime(new Date(Date.now() + serverOffsetMs));
    };

    async function refreshStatus() {
      try {
        const response = await fetch("/api/terminal-status", { cache: "no-store" });
        const payload = (await response.json()) as { clientIp?: string; serverTimeIso?: string };
        if (!active) {
          return;
        }
        const serverTimeIso = payload.serverTimeIso ?? new Date().toISOString();
        serverOffsetMs = new Date(serverTimeIso).getTime() - Date.now();
        setStatus({
          clientIp: payload.clientIp ?? "IP 확인 불가",
          serverTime: formatServerTime(serverTimeIso)
        });
      } catch {
        if (active) {
          setStatus({
            clientIp: "IP 확인 불가",
            serverTime: "서버 연결 실패"
          });
        }
      }
    }

    refreshStatus();
    const timer = window.setInterval(() => {
      if (active) {
        setStatus((current) => ({
          ...current,
          serverTime: current.serverTime === "서버 연결 실패" || current.serverTime === "연결 중" ? current.serverTime : formatDateTime(new Date(Date.now() + serverOffsetMs))
        }));
      }
    }, 1000);

    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, []);

  return (
    <footer className="iworks-statusbar">
      <span>[-] {status.clientIp}</span>
      <span>프린터&nbsp;&nbsp;핀패드&nbsp;&nbsp;즐겨찾기</span>
      <time>{status.serverTime}</time>
    </footer>
  );
}

function formatDateTime(value: Date) {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, "0");
  const day = String(value.getDate()).padStart(2, "0");
  const hour = String(value.getHours()).padStart(2, "0");
  const minute = String(value.getMinutes()).padStart(2, "0");
  const second = String(value.getSeconds()).padStart(2, "0");
  return `${year}-${month}-${day} ${hour}:${minute}:${second}`;
}

function MaterialIcon({ name }: { readonly name: IconName }) {
  return (
    <span className="material-symbols-outlined iworks-icon" aria-hidden="true">
      {name}
    </span>
  );
}
