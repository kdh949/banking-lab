import type { ReactNode } from "react";

type IconName =
  | "account_balance_wallet"
  | "account_tree"
  | "arrow_drop_down"
  | "article"
  | "business_center"
  | "calendar_today"
  | "close"
  | "credit_card"
  | "currency_exchange"
  | "description"
  | "event"
  | "folder_open"
  | "group"
  | "leaderboard"
  | "logout"
  | "menu"
  | "notifications"
  | "person"
  | "push_pin"
  | "real_estate_agent"
  | "search"
  | "settings"
  | "star"
  | "support_agent";

type NavItem = {
  label: string;
  icon: IconName;
  active?: boolean;
};

type TreeItem = {
  code: string;
  label: string;
  selected?: boolean;
};

type TreeGroup = {
  label: string;
  open?: boolean;
  children: TreeItem[];
};

type TableRow = readonly ReactNode[];

export function TerminalIcon({ name, className = "" }: { name: IconName; className?: string }) {
  return (
    <span className={`material-symbols-outlined terminal-icon ${className}`} aria-hidden="true">
      {name}
    </span>
  );
}

export function TerminalTopbar({
  brand,
  modules,
  searchPlaceholder = "통합검색"
}: {
  brand: string;
  modules: readonly NavItem[];
  searchPlaceholder?: string;
}) {
  return (
    <header className="global-topbar">
      <div className="brand-block">
        <div className="brand-title">{brand}</div>
        <label className="global-search">
          <TerminalIcon name="search" />
          <input aria-label="Integrated search" placeholder={searchPlaceholder} />
        </label>
      </div>
      <nav className="module-nav" aria-label="Primary banking modules">
        {modules.map((module) => (
          <button className={module.active ? "module-tab is-active" : "module-tab"} type="button" key={module.label}>
            <TerminalIcon name={module.icon} />
            <span>{module.label}</span>
          </button>
        ))}
      </nav>
      <div className="top-actions" aria-label="Operator actions">
        <button type="button" aria-label="Help desk">
          <TerminalIcon name="support_agent" />
        </button>
        <button type="button" aria-label="Settings">
          <TerminalIcon name="settings" />
        </button>
        <button className="finish-button" type="button">
          <TerminalIcon name="logout" />
          완료
        </button>
      </div>
    </header>
  );
}

export function TerminalMiniSidebar({ tools }: { tools: readonly NavItem[] }) {
  return (
    <aside className="mini-sidebar" aria-label="Staff utility navigation">
      {tools.map((tool) => (
        <button className={tool.active ? "mini-tool is-active" : "mini-tool"} type="button" key={tool.label}>
          <TerminalIcon name={tool.icon} />
          <span>{tool.label}</span>
        </button>
      ))}
    </aside>
  );
}

export function TerminalTreeSidebar({
  groups,
  operator
}: {
  groups: readonly TreeGroup[];
  operator: { initials: string; name: string; role: string; branch: string };
}) {
  return (
    <aside className="context-sidebar" aria-label="Role-aware menu">
      <div className="sidebar-header">
        <strong>업무 메뉴 트리</strong>
        <TerminalIcon name="push_pin" />
      </div>
      <div className="operator-card">
        <div className="operator-avatar" aria-hidden="true">
          {operator.initials}
        </div>
        <div>
          <strong>{operator.name}</strong>
          <span>{operator.role}</span>
          <span>{operator.branch}</span>
        </div>
      </div>
      <button className="operator-button" type="button">
        내 권한 보기
      </button>
      <div className="tree-list">
        {groups.map((group) => (
          <div className="tree-group" key={group.label}>
            <div className="tree-folder">
              <TerminalIcon name="arrow_drop_down" />
              <TerminalIcon name="folder_open" className="folder-icon" />
              {group.label}
            </div>
            {group.open !== false ? (
              <div className="tree-children">
                {group.children.map((item) => (
                  <div className={item.selected ? "tree-item is-selected" : "tree-item"} key={`${group.label}-${item.code}`}>
                    <TerminalIcon name="description" />
                    <span>{item.code}</span>
                    <strong>{item.label}</strong>
                  </div>
                ))}
              </div>
            ) : null}
          </div>
        ))}
      </div>
    </aside>
  );
}

export function TerminalProfileSidebar() {
  return (
    <aside className="context-sidebar profile-sidebar" aria-label="Synthetic profile menu">
      <div className="profile-card">
        <div className="profile-photo" aria-hidden="true">
          BL
        </div>
        <div className="profile-meta">
          <strong>차세대담당자</strong>
          <span>직급: STAFF_L1</span>
          <span>부점: Synthetic Branch</span>
          <span>내선: 0000-0000</span>
        </div>
      </div>
      <button className="operator-button" type="button">
        내 정보 관리
      </button>
      <Panel title="알림" icon="notifications" compact>
        <ul className="sidebar-bullets">
          <li>결재 요청 3건</li>
          <li>교육 이수 75%</li>
          <li>오늘 일정 4건</li>
        </ul>
      </Panel>
    </aside>
  );
}

export function WorkspaceTabs({ activeTitle, secondaryTitle }: { activeTitle: string; secondaryTitle: string }) {
  return (
    <div className="window-tabs" aria-label="Open terminal tabs">
      <button className="window-tab is-active" type="button">
        <span className="tab-dot" aria-hidden="true" />
        {activeTitle}
        <TerminalIcon name="close" />
      </button>
      <button className="window-tab" type="button">
        <span className="tab-dot muted" aria-hidden="true" />
        {secondaryTitle}
        <TerminalIcon name="close" />
      </button>
    </div>
  );
}

export function TaskTabs({ tabs }: { tabs: readonly string[] }) {
  return (
    <div className="task-tabs" aria-label="Task tabs">
      {tabs.map((tab, index) => (
        <button className={index === 0 ? "task-tab is-active" : "task-tab"} type="button" key={tab}>
          {tab}
        </button>
      ))}
    </div>
  );
}

export function Panel({
  title,
  icon,
  action,
  className = "",
  compact = false,
  children
}: {
  title: string;
  icon?: IconName;
  action?: ReactNode;
  className?: string;
  compact?: boolean;
  children: ReactNode;
}) {
  return (
    <article className={`terminal-panel ${compact ? "is-compact" : ""} ${className}`}>
      <div className="panel-header">
        {icon ? <TerminalIcon name={icon} /> : null}
        <strong>{title}</strong>
        {action ? <div className="panel-action">{action}</div> : null}
      </div>
      {children}
    </article>
  );
}

export function DenseTable({ columns, rows }: { columns: readonly string[]; rows: readonly TableRow[] }) {
  return (
    <table className="dense-table">
      <thead>
        <tr>
          {columns.map((column) => (
            <th key={column}>{column}</th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((row, rowIndex) => (
          <tr key={rowIndex}>
            {row.map((cell, cellIndex) => (
              <td key={`${rowIndex}-${cellIndex}`}>{cell}</td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function StatusBar({ left, right }: { left: ReactNode; right: ReactNode }) {
  return (
    <footer className="status-bar">
      <span>{left}</span>
      <span>{right}</span>
    </footer>
  );
}
