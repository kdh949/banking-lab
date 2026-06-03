import type { ScreenManifest } from "../../../../packages/screen-engine/src/types";
import { ApiBackedStaffPanel } from "../components/ApiBackedStaffPanel";
import { loadChannelManifests } from "../lib/manifestLoader";

type DomainGroup = {
  label: string;
  manifests: ScreenManifest[];
};

const topModules = [
  { label: "수신", icon: "account_balance_wallet", active: true },
  { label: "여신", icon: "real_estate_agent", active: false },
  { label: "외환", icon: "currency_exchange", active: false },
  { label: "고객", icon: "group", active: false },
  { label: "CRM", icon: "support_agent", active: false },
  { label: "신용카드", icon: "credit_card", active: false }
] as const;

const sideTools = [
  { label: "업무메뉴", icon: "menu", active: false },
  { label: "즐겨찾기", icon: "star", active: false },
  { label: "워크플로우", icon: "account_tree", active: true },
  { label: "탑리스트", icon: "leaderboard", active: false },
  { label: "날짜계산기", icon: "calendar_today", active: false },
  { label: "일정", icon: "event", active: false },
  { label: "오피스", icon: "business_center", active: false }
] as const;

const taskTabs = ["업무포털", "고객조회", "계좌조회", "원장내역", "정보변경", "승인", "감사", "민원"];

const notices = [
  ["1", "Synthetic PII masking policy review", "보안통제", "2026-06-04"],
  ["2", "Staff access reason-required workflow", "채널운영", "2026-06-04"],
  ["3", "Temporal case retry drill window", "플랫폼", "2026-06-03"],
  ["4", "Maker-checker separation evidence refresh", "감사", "2026-06-03"],
  ["5", "Ledger projection reconciliation smoke", "원장", "2026-06-02"]
] as const;

const workflowEvents = [
  "CST-002 masked customer detail loaded",
  "CST-103 waiting maker-checker approval",
  "CMP-201 answer draft requires checker",
  "FDS/AML console cross-channel case visible"
] as const;

const exceptions = [
  ["SERIALIZABLE retry", "ready", "green"],
  ["Duplicate idempotency key", "guarded", "blue"],
  ["PII unmask step-up", "manager only", "amber"]
] as const;

function list(value: readonly string[] | undefined | null): string {
  return Array.isArray(value) && value.length > 0 ? value.join(", ") : "none";
}

function endpointOf(manifest: ScreenManifest): string {
  return manifest.query?.endpoint || manifest.api?.command || manifest.actions?.[0]?.target || "declared in workflow";
}

function fieldCount(manifest: ScreenManifest): number {
  return manifest.query?.fields?.length || manifest.fields?.length || manifest.sections?.length || manifest.widgets?.length || 0;
}

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

function groupByDomain(manifests: ScreenManifest[]): DomainGroup[] {
  const groups = new Map<string, ScreenManifest[]>();
  for (const manifest of manifests) {
    const existing = groups.get(manifest.domain) ?? [];
    existing.push(manifest);
    groups.set(manifest.domain, existing);
  }
  return [...groups.entries()]
    .sort(([left], [right]) => domainLabel(left).localeCompare(domainLabel(right)))
    .map(([domain, items]) => ({
      label: domainLabel(domain),
      manifests: items.sort((left, right) => left.screenId.localeCompare(right.screenId))
    }));
}

function statusPill(manifest: ScreenManifest): string {
  if (manifest.approval?.makerChecker) {
    return "maker-checker";
  }
  if (manifest.audit.reasonRequired) {
    return "reason-required";
  }
  if (manifest.type === "CASE") {
    return "workflow";
  }
  return "manifest";
}

export default async function StaffTerminalPage() {
  const manifests = await loadChannelManifests();
  const reasonRequired = manifests.filter((manifest) => manifest.audit.reasonRequired).length;
  const makerChecker = manifests.filter((manifest) => manifest.approval?.required).length;
  const piiScreens = manifests.filter((manifest) => manifest.audit.piiAccess).length;
  const activeManifest = manifests.find((manifest) => manifest.screenId === "CST-002") ?? manifests[0];
  const dashboardManifest = manifests.find((manifest) => manifest.screenId === "WRK-001");
  const domainGroups = groupByDomain(manifests);

  return (
    <main className="bank-terminal">
      <header className="global-topbar">
        <div className="brand-block">
          <div className="brand-title">Banking Lab</div>
          <label className="global-search">
            <span className="material-symbols-outlined" aria-hidden="true">search</span>
            <input aria-label="Integrated search" placeholder="통합검색" />
          </label>
        </div>
        <nav className="module-nav" aria-label="Primary banking modules">
          {topModules.map((module) => (
            <button className={module.active ? "module-tab is-active" : "module-tab"} type="button" key={module.label}>
              <span className="material-symbols-outlined" aria-hidden="true">{module.icon}</span>
              <span>{module.label}</span>
            </button>
          ))}
        </nav>
        <div className="top-actions" aria-label="Operator actions">
          <button type="button" aria-label="Help desk">
            <span className="material-symbols-outlined" aria-hidden="true">support_agent</span>
          </button>
          <button type="button" aria-label="Settings">
            <span className="material-symbols-outlined" aria-hidden="true">settings</span>
          </button>
          <button className="finish-button" type="button">
            <span className="material-symbols-outlined" aria-hidden="true">logout</span>
            완료
          </button>
        </div>
      </header>

      <div className="terminal-frame">
        <aside className="mini-sidebar" aria-label="Staff utility navigation">
          {sideTools.map((tool) => (
            <button className={tool.active ? "mini-tool is-active" : "mini-tool"} type="button" key={tool.label}>
              <span className="material-symbols-outlined" aria-hidden="true">{tool.icon}</span>
              <span>{tool.label}</span>
            </button>
          ))}
        </aside>

        <aside className="context-sidebar" aria-label="Role-aware menu">
          <div className="sidebar-header">
            <strong>업무 메뉴 트리</strong>
            <span className="material-symbols-outlined" aria-hidden="true">push_pin</span>
          </div>
          <div className="operator-card">
            <div className="operator-avatar" aria-hidden="true">BL</div>
            <div>
              <strong>branch01</strong>
              <span>BRANCH_STAFF</span>
              <span>지점: Synthetic Branch</span>
            </div>
          </div>
          <button className="operator-button" type="button">내 권한 보기</button>
          <div className="tree-list">
            {domainGroups.map((group) => (
              <div className="tree-group" key={group.label}>
                <div className="tree-folder">
                  <span className="material-symbols-outlined" aria-hidden="true">arrow_drop_down</span>
                  <span className="material-symbols-outlined folder-icon" aria-hidden="true">folder_open</span>
                  {group.label}
                </div>
                <div className="tree-children">
                  {group.manifests.map((manifest) => (
                    <div
                      className={manifest.screenId === activeManifest?.screenId ? "tree-item is-selected" : "tree-item"}
                      key={manifest.screenId}
                    >
                      <span className="material-symbols-outlined" aria-hidden="true">description</span>
                      <span>{manifest.screenId}</span>
                      <strong>{manifest.transactionCode ?? manifest.screenId}</strong>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </aside>

        <section className="workspace">
          <div className="window-tabs" aria-label="Open terminal tabs">
            <button className="window-tab is-active" type="button">
              <span className="tab-dot" aria-hidden="true" />
              [WRK001] 통합단말_업무포털
              <span className="material-symbols-outlined" aria-hidden="true">close</span>
            </button>
            <button className="window-tab" type="button">
              <span className="tab-dot muted" aria-hidden="true" />
              [{activeManifest?.transactionCode ?? "CST002"}] {activeManifest?.title ?? "Customer Detail"}
              <span className="material-symbols-outlined" aria-hidden="true">close</span>
            </button>
          </div>

          <div className="task-tabs" aria-label="Task tabs">
            {taskTabs.map((tab, index) => (
              <button className={index === 0 ? "task-tab is-active" : "task-tab"} type="button" key={tab}>
                {tab}
              </button>
            ))}
          </div>

          <div className="terminal-body">
            <section className="main-canvas" aria-label="Manifest-driven integrated terminal">
              <div className="command-strip" aria-label="Staff terminal controls">
                <div className="command-title">
                  <p className="eyebrow">Synthetic staff terminal</p>
                  <h1>Transaction-code workspace</h1>
                </div>
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
              </div>

              <section className="control-grid" aria-label="Core staff terminal controls">
                <article className="terminal-panel customer-context">
                  <div className="panel-header">
                    <span className="material-symbols-outlined" aria-hidden="true">badge</span>
                    <strong>Customer Context</strong>
                  </div>
                  <dl className="compact-definition">
                    <div>
                      <dt>Customer</dt>
                      <dd>SYN-CUS-001</dd>
                    </div>
                    <div>
                      <dt>PII exposure</dt>
                      <dd>Masked by default</dd>
                    </div>
                    <div>
                      <dt>Lookup control</dt>
                      <dd>Business reason required</dd>
                    </div>
                    <div>
                      <dt>Approval inbox</dt>
                      <dd>APR-001 declared</dd>
                    </div>
                  </dl>
                </article>

                <article className="terminal-panel">
                  <div className="panel-header">
                    <span className="material-symbols-outlined" aria-hidden="true">account_tree</span>
                    <strong>Workflow timeline</strong>
                  </div>
                  <ol className="timeline-list">
                    {workflowEvents.map((event) => (
                      <li key={event}>
                        <span aria-hidden="true" />
                        {event}
                      </li>
                    ))}
                  </ol>
                </article>

                <article className="terminal-panel">
                  <div className="panel-header">
                    <span className="material-symbols-outlined" aria-hidden="true">policy</span>
                    <strong>Audit log panel</strong>
                  </div>
                  <div className="audit-summary">
                    <span>{piiScreens} masked PII screens</span>
                    <span>{reasonRequired} reason-required lookups</span>
                    <span>Append-only hash chain visible</span>
                  </div>
                </article>

                <article className="terminal-panel">
                  <div className="panel-header">
                    <span className="material-symbols-outlined" aria-hidden="true">sync_problem</span>
                    <strong>Exception/retry panel</strong>
                  </div>
                  <div className="exception-list">
                    {exceptions.map(([label, value, tone]) => (
                      <div className={`exception-row tone-${tone}`} key={label}>
                        <span>{label}</span>
                        <strong>{value}</strong>
                      </div>
                    ))}
                  </div>
                </article>
              </section>

              <section className="bento-grid" aria-label="Operational workspace">
                <article className="terminal-panel quick-menu">
                  <div className="panel-header">
                    <span className="material-symbols-outlined" aria-hidden="true">grid_view</span>
                    <strong>{dashboardManifest?.title ?? "Integrated Workstation Dashboard"}</strong>
                  </div>
                  <div className="quick-menu-list">
                    {manifests.slice(0, 7).map((manifest) => (
                      <div className={manifest.screenId === activeManifest?.screenId ? "quick-menu-item is-selected" : "quick-menu-item"} key={manifest.screenId}>
                        <span>{manifest.transactionCode ?? manifest.screenId}</span>
                        <strong>{manifest.title}</strong>
                        <em>{typeLabel(manifest.type)}</em>
                      </div>
                    ))}
                  </div>
                </article>

                <article className="terminal-panel notice-table">
                  <div className="panel-header split">
                    <span>
                      <span className="material-symbols-outlined" aria-hidden="true">campaign</span>
                      Synthetic control notices
                    </span>
                    <button type="button">더보기 &gt;</button>
                  </div>
                  <div className="dense-table">
                    <div className="table-head">
                      <span>순번</span>
                      <span>제목</span>
                      <span>등록부서</span>
                      <span>등록일시</span>
                    </div>
                    {notices.map(([number, title, owner, date]) => (
                      <div className="table-row" key={number}>
                        <span>{number}</span>
                        <strong>{title}</strong>
                        <span>{owner}</span>
                        <span>{date}</span>
                      </div>
                    ))}
                  </div>
                </article>

                <article className="terminal-panel manifest-table">
                  <div className="panel-header">
                    <span className="material-symbols-outlined" aria-hidden="true">view_list</span>
                    <strong>Manifest-rendered staff screens</strong>
                  </div>
                  <div className="manifest-rows">
                    {manifests.map((manifest) => (
                      <article className="screen-card" key={manifest.screenId}>
                        <div>
                          <span className="screen-code">{manifest.transactionCode || manifest.screenId}</span>
                          <h2>{manifest.title}</h2>
                        </div>
                        <div className="screen-meta-grid">
                          <div>
                            <span>Roles</span>
                            <strong>{list(manifest.requiredRoles)}</strong>
                          </div>
                          <div>
                            <span>Audit</span>
                            <strong>{manifest.audit.reasonRequired ? "reason required" : "standard"}</strong>
                          </div>
                          <div>
                            <span>Masking</span>
                            <strong>{manifest.audit.maskingPolicy}</strong>
                          </div>
                          <div>
                            <span>Approval</span>
                            <strong>{manifest.approval?.required ? "maker-checker" : "not required"}</strong>
                          </div>
                        </div>
                        <div className="detail-row">
                          <span>{manifest.type}</span>
                          <span>{manifest.domain}</span>
                          <span>{fieldCount(manifest)} manifest elements</span>
                          <strong>{statusPill(manifest)}</strong>
                        </div>
                        <p className="endpoint">{endpointOf(manifest)}</p>
                      </article>
                    ))}
                  </div>
                </article>
              </section>
            </section>

            <aside className="inside-view" aria-label="Inside view and API smoke">
              <div className="inside-header">
                <strong>Inside View</strong>
                <span className="material-symbols-outlined" aria-hidden="true">close</span>
              </div>
              <div className="inside-content">
                <section className="inside-widget">
                  <div className="inside-tabs">
                    <button type="button">특이사항</button>
                    <button type="button">거래성향</button>
                    <button type="button">메모</button>
                  </div>
                  <dl className="compact-definition">
                    <div>
                      <dt>Active customer</dt>
                      <dd>SYN-CUS-001</dd>
                    </div>
                    <div>
                      <dt>Account</dt>
                      <dd>LAB-***-0001</dd>
                    </div>
                  </dl>
                </section>
                <section className="inside-widget">
                  <div className="panel-header compact">
                    <span className="material-symbols-outlined" aria-hidden="true">inventory_2</span>
                    <strong>내상품</strong>
                  </div>
                  <select aria-label="Synthetic product context">
                    <option>우측 클릭 후 선택하세요</option>
                  </select>
                </section>
                <ApiBackedStaffPanel />
              </div>
            </aside>
          </div>

          <footer className="status-bar" aria-label="Terminal status">
            <div>
              <span className="material-symbols-outlined" aria-hidden="true">computer</span>
              10.1.91.174
              <strong>정상 연결</strong>
            </div>
            <div>
              <span>프린터 ready</span>
              <span>핀패드 simulator</span>
              <span>즉발기 disabled</span>
              <time dateTime="2026-06-04T01:37:24+09:00">2026-06-04 01:37:24</time>
            </div>
          </footer>
        </section>
      </div>
    </main>
  );
}
