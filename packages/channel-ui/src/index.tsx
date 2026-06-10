import type { ReactNode } from "react";

export type ChannelAppId =
  | "customer-web"
  | "staff-terminal"
  | "complaint-portal"
  | "call-center-console"
  | "ops-console"
  | "audit-console"
  | "fds-aml-console"
  | "admin-console";

export interface ChannelShellProps {
  readonly appId: ChannelAppId;
  readonly eyebrow: string;
  readonly title: string;
  readonly status: string;
  readonly children: ReactNode;
}

export interface ChannelPanelProps {
  readonly title: string;
  readonly eyebrow?: string;
  readonly meta?: ReactNode;
  readonly className?: string;
  readonly children: ReactNode;
}

export interface ChannelCardProps {
  readonly screenId: string;
  readonly title: string;
  readonly meta?: ReactNode;
  readonly children: ReactNode;
}

export interface ChannelMetricProps {
  readonly label: string;
  readonly value: ReactNode;
  readonly detail?: ReactNode;
}

export interface ChannelDefinitionItem {
  readonly term: ReactNode;
  readonly detail: ReactNode;
}

export function ChannelShell({ appId, eyebrow, title, status, children }: ChannelShellProps) {
  return (
    <main className={`channel-root channel-app-${appId}`}>
      <div className="channel-shell" data-channel-shell={appId}>
        <aside className="channel-sidebar" aria-label={`${eyebrow} navigation`}>
          <div className="channel-brand">
            <span className="channel-brand-mark">BL</span>
            <div>
              <strong>Banking Lab</strong>
              <small>{eyebrow}</small>
            </div>
          </div>
          <nav className="channel-nav" aria-label="Channel sections">
            <span className="is-active">Manifest</span>
            <span>API</span>
            <span>Controls</span>
            <span>Evidence</span>
          </nav>
        </aside>

        <section className="channel-workspace">
          <header className="channel-topbar">
            <div>
              <p className="channel-eyebrow">{eyebrow}</p>
              <h1>{title}</h1>
            </div>
            <span className="channel-status">{status}</span>
          </header>
          <div className="channel-content">{children}</div>
        </section>
      </div>
    </main>
  );
}

export function ChannelPanel({ title, eyebrow, meta, className, children }: ChannelPanelProps) {
  const classes = className ? `channel-panel ${className}` : "channel-panel";

  return (
    <section className={classes}>
      <div className="channel-panel-header">
        <div>
          {eyebrow ? <p className="channel-panel-eyebrow">{eyebrow}</p> : null}
          <h2>{title}</h2>
        </div>
        {meta ? <div className="channel-panel-meta">{meta}</div> : null}
      </div>
      <div className="channel-panel-body">{children}</div>
    </section>
  );
}

export function ChannelMetricGrid({ children }: { readonly children: ReactNode }) {
  return <section className="channel-metric-grid">{children}</section>;
}

export function ChannelMetric({ label, value, detail }: ChannelMetricProps) {
  return (
    <article className="channel-metric">
      <span>{label}</span>
      <strong>{value}</strong>
      {detail ? <small>{detail}</small> : null}
    </article>
  );
}

export function ChannelSplit({ aside, children }: { readonly aside: ReactNode; readonly children: ReactNode }) {
  return (
    <section className="channel-split">
      <aside>{aside}</aside>
      <div>{children}</div>
    </section>
  );
}

export function ChannelCardGrid({ children, density = "regular" }: { readonly children: ReactNode; readonly density?: "regular" | "wide" }) {
  return <section className={`channel-card-grid channel-card-grid-${density}`}>{children}</section>;
}

export function ChannelCard({ screenId, title, meta, children }: ChannelCardProps) {
  return (
    <article className="channel-card">
      <div className="channel-card-heading">
        <span>{screenId}</span>
        <h2>{title}</h2>
        {meta ? <p>{meta}</p> : null}
      </div>
      {children}
    </article>
  );
}

export function ChannelDefinitionList({ items }: { readonly items: readonly ChannelDefinitionItem[] }) {
  return (
    <dl className="channel-definition-list">
      {items.map((item, index) => (
        <div key={index}>
          <dt>{item.term}</dt>
          <dd>{item.detail}</dd>
        </div>
      ))}
    </dl>
  );
}

export function ChannelWorkflow({ states, label = "workflow timeline" }: { readonly states: readonly string[]; readonly label?: string }) {
  return (
    <div className="channel-chip-row">
      <span>{label}</span>
      {states.map((state) => (
        <span key={state}>{state}</span>
      ))}
    </div>
  );
}

export function ChannelActionRow({ items }: { readonly items: readonly ReactNode[] }) {
  if (items.length === 0) {
    return null;
  }

  return (
    <div className="channel-chip-row">
      {items.map((item, index) => (
        <span key={index}>{item}</span>
      ))}
    </div>
  );
}

export function ChannelBadge({ children, tone = "neutral" }: { readonly children: ReactNode; readonly tone?: "neutral" | "critical" }) {
  return <span className={`channel-badge channel-badge-${tone}`}>{children}</span>;
}

export function ChannelTable({ children }: { readonly children: ReactNode }) {
  return (
    <div className="channel-table-wrap">
      <table className="channel-table">{children}</table>
    </div>
  );
}
