import type { ChangeEventHandler, ReactNode } from "react";

export type IconName =
  | "account_balance_wallet"
  | "account_tree"
  | "add"
  | "arrow_drop_down"
  | "arrow_forward"
  | "arrow_right"
  | "article"
  | "business_center"
  | "calendar_month"
  | "calendar_today"
  | "call"
  | "campaign"
  | "close"
  | "computer"
  | "credit_card"
  | "currency_exchange"
  | "description"
  | "event"
  | "fiber_new"
  | "folder"
  | "folder_open"
  | "grid_view"
  | "group"
  | "help"
  | "inventory_2"
  | "keyboard"
  | "leaderboard"
  | "logout"
  | "mail"
  | "menu"
  | "menu_book"
  | "notifications"
  | "open_in_new"
  | "person"
  | "play_arrow"
  | "print"
  | "push_pin"
  | "real_estate_agent"
  | "receipt"
  | "school"
  | "search"
  | "settings"
  | "star"
  | "support_agent"
  | "thumb_up";

export type TerminalNavItem = {
  readonly label: string;
  readonly icon: IconName;
  readonly active?: boolean;
};

export type TerminalTreeItem = {
  readonly code: string;
  readonly label: string;
  readonly selected?: boolean;
};

export type TerminalTreeGroup = {
  readonly label: string;
  readonly open?: boolean;
  readonly children: readonly TerminalTreeItem[];
};

export type TerminalProfile = {
  readonly initials: string;
  readonly name: string;
  readonly role: string;
  readonly branch: string;
  readonly extension: string;
  readonly phone?: string;
};

export type TerminalButtonVariant =
  | "topAction"
  | "moduleTab"
  | "miniTool"
  | "taskTab"
  | "panelAction"
  | "statusAction"
  | "segment";

export type TerminalContextSidebarProps =
  | {
      readonly kind: "tree";
      readonly groups: readonly TerminalTreeGroup[];
      readonly operator?: { readonly initials: string; readonly name: string; readonly role: string; readonly branch: string };
    }
  | {
      readonly kind: "profile";
      readonly profile: TerminalProfile;
      readonly widgets?: ReactNode;
    };

export type WorkspaceTab = {
  readonly title: string;
  readonly active?: boolean;
};

export type StatusDevice = {
  readonly label: string;
  readonly icon: IconName;
};

export type TableRow = readonly ReactNode[];

function cx(...classes: Array<string | false | null | undefined>) {
  return classes.filter(Boolean).join(" ");
}

export function TerminalIcon({
  name,
  className = "",
  size
}: {
  readonly name: IconName;
  readonly className?: string;
  readonly size?: 12 | 14 | 16 | 18 | 20 | 24;
}) {
  return (
    <span className={cx("material-symbols-outlined terminal-icon", size ? `terminal-icon-${size}` : "", className)} aria-hidden="true">
      {name}
    </span>
  );
}

export function TerminalButton({
  variant,
  icon,
  active = false,
  children,
  className = "",
  type = "button",
  "aria-label": ariaLabel
}: {
  readonly variant: TerminalButtonVariant;
  readonly icon?: IconName;
  readonly active?: boolean;
  readonly children?: ReactNode;
  readonly className?: string;
  readonly type?: "button" | "submit" | "reset";
  readonly "aria-label"?: string;
}) {
  return (
    <button className={cx("terminal-button", `terminal-button-${variant}`, active && "is-active", className)} type={type} aria-label={ariaLabel}>
      {icon ? <TerminalIcon name={icon} /> : null}
      {children ? <span>{children}</span> : null}
    </button>
  );
}

export function TerminalField({
  label,
  fieldType,
  options = [],
  className = "",
  value,
  defaultValue,
  placeholder,
  readOnly,
  ariaLabel,
  onChange
}: {
  readonly label: string;
  readonly fieldType: "text" | "search" | "select" | "date" | "amount" | "readonly";
  readonly options?: readonly string[];
  readonly className?: string;
  readonly value?: string | number;
  readonly defaultValue?: string | number;
  readonly placeholder?: string;
  readonly readOnly?: boolean;
  readonly ariaLabel?: string;
  readonly onChange?: ChangeEventHandler<HTMLInputElement>;
}) {
  const sharedClassName = cx("terminal-field-control", `terminal-field-${fieldType}`);

  return (
    <label className={cx("terminal-field", className)}>
      <span>{label}</span>
      {fieldType === "select" ? (
        <select className={sharedClassName} defaultValue={(defaultValue as string | undefined) ?? options[0]} aria-label={ariaLabel ?? label}>
          {options.map((option) => (
            <option key={option}>{option}</option>
          ))}
        </select>
      ) : (
        <input
          className={sharedClassName}
          defaultValue={defaultValue}
          placeholder={placeholder}
          readOnly={fieldType === "readonly" || readOnly}
          type={fieldType === "amount" ? "text" : fieldType}
          value={value}
          onChange={onChange}
          aria-label={ariaLabel ?? label}
        />
      )}
    </label>
  );
}

export function TerminalShell({
  topbar,
  miniSidebar,
  contextSidebar,
  children
}: {
  readonly topbar: ReactNode;
  readonly miniSidebar: ReactNode;
  readonly contextSidebar: ReactNode;
  readonly children: ReactNode;
}) {
  return (
    <main className="bank-terminal">
      {topbar}
      <div className="terminal-frame">
        {miniSidebar}
        {contextSidebar}
        {children}
      </div>
    </main>
  );
}

export function TerminalWorkspace({
  tabs,
  taskTabs,
  children,
  footer,
  statusBar
}: {
  readonly tabs: readonly WorkspaceTab[];
  readonly taskTabs: readonly string[];
  readonly children: ReactNode;
  readonly footer?: ReactNode;
  readonly statusBar: ReactNode;
}) {
  return (
    <section className="workspace">
      <WorkspaceTabs tabs={tabs} />
      <TaskTabs tabs={taskTabs} />
      {children}
      {footer}
      {statusBar}
    </section>
  );
}

export function TerminalTopbar({
  brand,
  modules,
  searchPlaceholder = "통합검색"
}: {
  readonly brand: string;
  readonly modules: readonly TerminalNavItem[];
  readonly searchPlaceholder?: string;
}) {
  return (
    <header className="global-topbar">
      <div className="brand-block">
        <div className="brand-title">{brand}</div>
        <label className="global-search">
          <TerminalIcon name="search" size={18} />
          <input aria-label="Integrated search" placeholder={searchPlaceholder} />
        </label>
      </div>
      <nav className="module-nav" aria-label="Primary banking modules">
        {modules.map((module) => (
          <TerminalButton variant="moduleTab" icon={module.icon} active={module.active} key={module.label}>
            {module.label}
          </TerminalButton>
        ))}
      </nav>
      <div className="top-actions" aria-label="Operator actions">
        <TerminalButton variant="topAction" icon="support_agent" aria-label="Help desk" />
        <TerminalButton variant="topAction" icon="settings" aria-label="Settings" />
        <div className="top-action-separator" aria-hidden="true" />
        <TerminalButton variant="topAction" icon="logout" className="finish-button">
          완료
        </TerminalButton>
      </div>
    </header>
  );
}

export function TerminalMiniSidebar({ tools }: { readonly tools: readonly TerminalNavItem[] }) {
  return (
    <aside className="mini-sidebar" aria-label="Staff utility navigation">
      {tools.map((tool) => (
        <TerminalButton variant="miniTool" icon={tool.icon} active={tool.active} key={tool.label}>
          {tool.label}
        </TerminalButton>
      ))}
    </aside>
  );
}

export function TerminalContextSidebar(props: TerminalContextSidebarProps) {
  if (props.kind === "profile") {
    return <TerminalProfileSidebar profile={props.profile}>{props.widgets}</TerminalProfileSidebar>;
  }
  return <TerminalTreeSidebar groups={props.groups} operator={props.operator} />;
}

export function TerminalTreeSidebar({
  groups,
  operator
}: {
  readonly groups: readonly TerminalTreeGroup[];
  readonly operator?: { readonly initials: string; readonly name: string; readonly role: string; readonly branch: string };
}) {
  return (
    <aside className="context-sidebar" aria-label="Role-aware menu">
      <div className="sidebar-header">
        <strong>업무 메뉴 트리</strong>
        <TerminalIcon name="push_pin" size={16} />
      </div>
      {operator ? (
        <>
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
        </>
      ) : null}
      <div className="tree-list">
        {groups.map((group) => (
          <div className="tree-group" key={group.label}>
            <div className={cx("tree-folder", group.open === false && "is-closed")}>
              <TerminalIcon name={group.open === false ? "arrow_right" : "arrow_drop_down"} size={16} />
              <TerminalIcon name={group.open === false ? "folder" : "folder_open"} size={16} className="folder-icon" />
              {group.label}
            </div>
            {group.open !== false ? (
              <div className="tree-children">
                {group.children.map((item) => (
                  <div className={cx("tree-item", item.selected && "is-selected")} key={`${group.label}-${item.code}`}>
                    <TerminalIcon name="description" size={16} />
                    <span>{item.code}.</span>
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

export function TerminalProfileSidebar({ profile, children }: { readonly profile: TerminalProfile; readonly children?: ReactNode }) {
  return (
    <aside className="context-sidebar profile-sidebar" aria-label="Synthetic profile menu">
      <ProfileCard profile={profile} />
      <button className="operator-button" type="button">
        내 정보 관리
      </button>
      {children}
    </aside>
  );
}

export function ProfileCard({ profile }: { readonly profile: TerminalProfile }) {
  return (
    <section className="profile-card">
      <div className="profile-photo" aria-hidden="true">
        {profile.initials}
      </div>
      <div className="profile-meta">
        <strong>{profile.name}</strong>
        <span>직급: {profile.role}</span>
        <span>지점: {profile.branch}</span>
        <span>내선: {profile.extension}</span>
        {profile.phone ? (
          <span className="profile-phone">
            <TerminalIcon name="call" size={12} />
            {profile.phone}
          </span>
        ) : null}
      </div>
    </section>
  );
}

export function WorkspaceTabs({
  tabs,
  activeTitle,
  secondaryTitle
}: {
  readonly tabs?: readonly WorkspaceTab[];
  readonly activeTitle?: string;
  readonly secondaryTitle?: string;
}) {
  const resolvedTabs = tabs ?? [
    { title: activeTitle ?? "", active: true },
    { title: secondaryTitle ?? "", active: false }
  ];

  return (
    <div className="window-tabs" aria-label="Open terminal tabs">
      {resolvedTabs.map((tab, index) => (
        <button className={cx("window-tab", (tab.active ?? index === 0) && "is-active")} type="button" key={tab.title}>
          <span className={cx("tab-dot", !(tab.active ?? index === 0) && "muted")} aria-hidden="true" />
          {tab.title}
          <TerminalIcon name="close" size={14} />
        </button>
      ))}
    </div>
  );
}

export function TaskTabs({ tabs, activeIndex = 0 }: { readonly tabs: readonly string[]; readonly activeIndex?: number }) {
  return (
    <div className="task-tabs" aria-label="Task tabs">
      {tabs.map((tab, index) => (
        <TerminalButton variant="taskTab" active={index === activeIndex} key={tab}>
          {tab}
        </TerminalButton>
      ))}
    </div>
  );
}

export function TerminalPanel({
  title,
  icon,
  action,
  className = "",
  compact = false,
  headerVariant = "primary",
  tabs,
  children
}: {
  readonly title: string;
  readonly icon?: IconName;
  readonly action?: ReactNode;
  readonly className?: string;
  readonly compact?: boolean;
  readonly headerVariant?: "primary" | "tertiary" | "plain";
  readonly tabs?: readonly string[];
  readonly children: ReactNode;
}) {
  if (tabs) {
    return (
      <article className={cx("terminal-panel", compact && "is-compact", className)}>
        <PanelTabs tabs={tabs} />
        {children}
      </article>
    );
  }

  return (
    <article className={cx("terminal-panel", compact && "is-compact", className)}>
      <div className={cx("panel-header", Boolean(action) && "split", headerVariant === "tertiary" && "tertiary", headerVariant === "plain" && "plain")}>
        <strong>
          {icon ? <TerminalIcon name={icon} size={16} /> : null}
          {title}
        </strong>
        {action ? <div className="panel-action">{action}</div> : null}
      </div>
      {children}
    </article>
  );
}

export const Panel = TerminalPanel;

export function PanelTabs({ tabs, activeIndex = 0 }: { readonly tabs: readonly string[]; readonly activeIndex?: number }) {
  return (
    <div className="panel-tabs" role="tablist">
      {tabs.map((tab, index) => (
        <button className={cx(index === activeIndex && "is-active")} type="button" role="tab" aria-selected={index === activeIndex} key={tab}>
          {tab}
        </button>
      ))}
    </div>
  );
}

export function DenseTable({
  columns,
  rows,
  ariaLabel
}: {
  readonly columns: readonly string[];
  readonly rows: readonly TableRow[];
  readonly ariaLabel?: string;
}) {
  return (
    <table className="dense-table" aria-label={ariaLabel}>
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

export function TerminalBentoGrid({ children, className = "" }: { readonly children: ReactNode; readonly className?: string }) {
  return <div className={cx("source-bento", className)}>{children}</div>;
}

export function TerminalBody({
  children,
  rightRail,
  className = ""
}: {
  readonly children: ReactNode;
  readonly rightRail?: ReactNode;
  readonly className?: string;
}) {
  return (
    <div className={cx("terminal-body", className)}>
      <section className="main-canvas" aria-label="Manifest-driven integrated terminal">
        {children}
      </section>
      {rightRail}
    </div>
  );
}

export function InsideView({
  title = "인사이드뷰",
  stats,
  tabs,
  children
}: {
  readonly title?: string;
  readonly stats: readonly string[];
  readonly tabs: readonly string[];
  readonly children: ReactNode;
}) {
  return (
    <aside className="inside-view" aria-label="Inside view and API smoke">
      <div className="inside-header">
        <strong>{title}</strong>
        <button type="button" aria-label="Close inside view">
          <TerminalIcon name="close" size={16} />
        </button>
      </div>
      <div className="inside-summary">
        <div className="inside-stat-grid">
          {stats.map((label) => (
            <button type="button" key={label}>
              {label}
            </button>
          ))}
        </div>
        <div className="inside-quick-tabs">
          {tabs.map((label) => (
            <button type="button" key={label}>
              {label}
            </button>
          ))}
        </div>
      </div>
      <div className="inside-panel-stack">{children}</div>
    </aside>
  );
}

export function InsideMiniPanel({
  title,
  icon,
  actions,
  className = "",
  children
}: {
  readonly title: string;
  readonly icon: IconName;
  readonly actions?: readonly string[];
  readonly className?: string;
  readonly children: ReactNode;
}) {
  return (
    <div className={cx("inside-mini-panel", className)}>
      <div className="inside-mini-header">
        <strong>
          <TerminalIcon name={icon} size={14} />
          {title}
        </strong>
        {actions ? (
          <span>
            {actions.map((action, index) => (
              <button className={index === actions.length - 1 ? "is-primary" : ""} type="button" key={action}>
                {action}
              </button>
            ))}
          </span>
        ) : null}
      </div>
      {children}
    </div>
  );
}

export function AlertWidget({
  title,
  icon = "mail",
  columns
}: {
  readonly title: string;
  readonly icon?: IconName;
  readonly columns: readonly (readonly [string, string, "primary" | "danger"][])[];
}) {
  return (
    <section className="profile-widget alert-widget">
      <WidgetHeader title={title} icon={icon} />
      <div className="alert-grid">
        {columns.map((column, columnIndex) => (
          <div className={cx(columnIndex > 0 && "is-divided")} key={columnIndex}>
            {column.map(([label, value, tone]) => (
              <div className="alert-row" key={label}>
                <span>{label}</span>
                <strong className={`tone-${tone}`}>{value}</strong>
              </div>
            ))}
          </div>
        ))}
      </div>
    </section>
  );
}

export function ProgressWidget({
  title,
  icon = "school",
  items
}: {
  readonly title: string;
  readonly icon?: IconName;
  readonly items: readonly { readonly label: string; readonly value: number }[];
}) {
  return (
    <section className="profile-widget progress-widget">
      <WidgetHeader title={title} icon={icon} />
      <div className="progress-stack">
        {items.map((item) => (
          <div className="progress-item" key={item.label}>
            <div>
              <span>{item.label}</span>
              <span>{item.value}%</span>
            </div>
            <div className="progress-track" aria-hidden="true">
              <span style={{ width: `${item.value}%` }} />
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

export function MiniCalendar({
  month,
  days,
  activeDay
}: {
  readonly month: string;
  readonly days: readonly string[];
  readonly activeDay: string;
}) {
  return (
    <section className="profile-widget calendar-widget">
      <div className="widget-header">
        <strong>
          <TerminalIcon name="calendar_month" size={16} />
          일정
        </strong>
        <div className="calendar-mode-buttons">
          {["일", "주", "월"].map((mode, index) => (
            <button className={index === 0 ? "is-active" : ""} type="button" key={mode}>
              {mode}
            </button>
          ))}
        </div>
      </div>
      <div className="mini-calendar">
        <div className="mini-calendar-month">{month}</div>
        <div className="mini-calendar-grid">
          {["일", "월", "화", "수", "목", "금", "토", ...days].map((day, index) => (
            <span className={cx(index === 0 && "is-sunday", index === 6 && "is-saturday", day === activeDay && "is-active")} key={`${day}-${index}`}>
              {day}
            </span>
          ))}
        </div>
      </div>
    </section>
  );
}

function WidgetHeader({ title, icon }: { readonly title: string; readonly icon: IconName }) {
  return (
    <div className="widget-header">
      <strong>
        <TerminalIcon name={icon} size={16} />
        {title}
      </strong>
      <TerminalIcon name="open_in_new" size={14} className="widget-open-icon" />
    </div>
  );
}

export function NoticeList({
  items,
  withBadge = false
}: {
  readonly items: readonly { readonly label: string; readonly date?: string }[];
  readonly withBadge?: boolean;
}) {
  return (
    <div className="portal-list">
      {items.map((item) => (
        <div className="portal-list-row" key={item.label}>
          {withBadge ? <span className="new-badge">N</span> : null}
          <span>{item.label}</span>
          {item.date ? <time>{item.date}</time> : null}
        </div>
      ))}
    </div>
  );
}

export function PartnerGrid({ items }: { readonly items: readonly string[] }) {
  return (
    <div className="partner-grid">
      {items.map((item) => (
        <button type="button" key={item}>
          <TerminalIcon name="play_arrow" size={16} />
          {item}
        </button>
      ))}
    </div>
  );
}

export function RightRail({ children }: { readonly children: ReactNode }) {
  return <aside className="right-rail">{children}</aside>;
}

export function FooterLinkBar({
  contact,
  links,
  copyright
}: {
  readonly contact: readonly string[];
  readonly links: readonly string[];
  readonly copyright: string;
}) {
  return (
    <footer className="terminal-footer-links">
      <div className="terminal-footer-contact">
        {contact.map((line) => (
          <span key={line}>{line}</span>
        ))}
      </div>
      <div className="terminal-footer-nav">
        {links.map((link) => (
          <a className={link === "개인정보처리방침" ? "is-strong" : ""} href="#" key={link}>
            {link}
          </a>
        ))}
        <label>
          <span>LANGUAGE</span>
          <select defaultValue="한국어">
            <option>한국어</option>
          </select>
        </label>
      </div>
      <div className="terminal-footer-copy">
        <span>{copyright}</span>
        <div className="synthetic-card-marks" aria-label="Synthetic payment marks">
          <span>VISA</span>
          <span>MC</span>
        </div>
      </div>
    </footer>
  );
}

export function TerminalStatusBar({
  host = "10.1.91.174",
  connection = "정상 연결",
  devices = [
    { icon: "print", label: "프린터" },
    { icon: "keyboard", label: "핀패드" },
    { icon: "receipt", label: "즉발기" }
  ],
  timestamp = "2023-10-24 13:37:24"
}: {
  readonly host?: string;
  readonly connection?: string;
  readonly devices?: readonly StatusDevice[];
  readonly timestamp?: string;
}) {
  return (
    <footer className="status-bar">
      <div>
        <span>
          <TerminalIcon name="computer" size={14} />
          {host}
        </span>
        <span className="status-chip">{connection}</span>
      </div>
      <div>
        {devices.map((device) => (
          <span key={device.label}>
            <TerminalIcon name={device.icon} size={14} />
            {device.label}
          </span>
        ))}
        <strong>{timestamp}</strong>
      </div>
    </footer>
  );
}

export function StatusBar({ left, right }: { readonly left: ReactNode; readonly right: ReactNode }) {
  return (
    <footer className="status-bar">
      <span>{left}</span>
      <span>{right}</span>
    </footer>
  );
}
