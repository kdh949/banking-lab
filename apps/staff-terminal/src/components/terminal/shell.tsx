import { useEffect, useState } from "react";
import { menuTree, topModules, utilityItems } from "./registry";
import { MaterialIcon, Panel } from "./primitives";
import type { MenuTarget, RightMode, ScreenKey, SideMode, TopModule, WorkspaceScreen } from "./types";

export function TerminalHeader({
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

export function UtilityRail({ activeMode, onModeChange }: { readonly activeMode: SideMode; readonly onModeChange: (mode: SideMode) => void }) {
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

export function SideDrawer({
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
                <button className={activeMenuCode === item.code ? "is-selected" : ""} type="button" key={item.code} onClick={() => onMenuSelect(item)}>
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

export function ScreenToolbar({ active }: { readonly active: WorkspaceScreen }) {
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

export function BusinessTabs({ tabs, activeIndex = 0 }: { readonly tabs: readonly string[]; readonly activeIndex?: number }) {
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

export function RightRail({
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
          <button type="button" onClick={() => onModeChange("manual")}>
            ×
          </button>
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
      <button type="button" className="manual-button">
        업무 매뉴얼
      </button>
      <button type="button" className="manual-button">
        필요서류목록 확인
      </button>
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

export function StatusBar() {
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
