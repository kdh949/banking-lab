"use client";

import { useMemo, useState, type FormEvent, type ReactNode } from "react";
import type { ManifestField, ScreenManifest } from "../../../../packages/screen-engine/src/types";
import { ApiBackedStaffPanel } from "./ApiBackedStaffPanel";
import {
  DenseTable,
  RightRail,
  TerminalBody,
  TerminalButton,
  TerminalContextSidebar,
  TerminalField,
  TerminalMiniSidebar,
  TerminalPanel,
  TerminalShell,
  TerminalStatusBar,
  TerminalTopbar,
  TerminalWorkspace,
  type TableRow,
  type TerminalFieldType,
  type TerminalTreeGroup,
  type WorkspaceTab
} from "./terminal-ui";
import { sideTools, taskTabs, topModules } from "./terminal-screens";

type StaffIntegratedWorkspaceProps = {
  readonly manifests: readonly ScreenManifest[];
  readonly initialScreen?: string | null;
};

type StaffManifestScreenRendererProps = {
  readonly manifest: ScreenManifest;
  readonly manifests?: readonly ScreenManifest[];
};

type RendererStatus = "api-backed" | "declared-only";

const dashboardScreenId = "WRK-001";
const fallbackReasonField: ManifestField = {
  name: "reason",
  label: "Lookup Reason",
  type: "textarea",
  required: true
};

const sampleValues: Record<string, string> = {
  accountId: "ACC-SYN-001-001",
  accountNo: "LAB-***-0001",
  amountMinor: "12000",
  approvalId: "APR-SYN-001",
  availableBalanceMinor: "1,250,000",
  businessDate: "2026-06-04",
  caseId: "CASE-SYN-001",
  customerGrade: "STANDARD",
  customerId: "SYN-CUS-001",
  eventType: "CUSTOMER_DETAIL_VIEW",
  failureCode: "REQUEST_VALIDATION_FAILED",
  itemId: "REC-SYN-001",
  ledgerBalanceMinor: "1,250,000",
  maskedAccountNo: "LAB-***-0001",
  maskedName: "K** D***",
  maskedPhone: "010-****-1001",
  owner: "ops01",
  payloadHash: "hash:synthetic",
  posting: "balanced",
  previousEventHash: "hash:previous",
  riskGrade: "LOW",
  status: "ACTIVE",
  transactionId: "TX-SYN-0001",
  transactionType: "INTERNAL_TRANSFER",
  transferReferenceId: "TRF-SYN-001"
};

const apiBackedEndpointFragments = [
  "/api/staff/customers/",
  "/api/staff/pii/unmask",
  "/api/staff/approvals/",
  "/api/staff/complaints",
  "/api/staff/fds-cases",
  "/api/staff/aml-cases",
  "/api/ops/reconciliation-items",
  "/api/audit/events"
] as const;

export function StaffIntegratedWorkspace({ manifests, initialScreen }: StaffIntegratedWorkspaceProps) {
  const sortedManifests = useMemo(() => [...manifests].sort(compareManifest), [manifests]);
  const initialManifest = findManifest(sortedManifests, initialScreen) ?? sortedManifests.find((manifest) => manifest.screenId === dashboardScreenId) ?? sortedManifests[0];
  const [activeScreenId, setActiveScreenId] = useState(initialManifest?.screenId ?? dashboardScreenId);
  const [openScreenIds, setOpenScreenIds] = useState<readonly string[]>(() => {
    const dashboard = sortedManifests.find((manifest) => manifest.screenId === dashboardScreenId)?.screenId;
    const initial = initialManifest?.screenId;
    return [...new Set([dashboard, initial].filter((value): value is string => Boolean(value)))];
  });

  const activeManifest = sortedManifests.find((manifest) => manifest.screenId === activeScreenId) ?? initialManifest;
  const tabs = openScreenIds
    .map((screenId) => sortedManifests.find((manifest) => manifest.screenId === screenId))
    .filter((manifest): manifest is ScreenManifest => Boolean(manifest))
    .map((manifest) => tabForManifest(manifest, activeManifest?.screenId));

  const openManifest = (manifest: ScreenManifest) => {
    setOpenScreenIds((current) => current.includes(manifest.screenId) ? current : [...current, manifest.screenId]);
    setActiveScreenId(manifest.screenId);
  };

  return (
    <TerminalShell
      topbar={<TerminalTopbar brand="INZENT Banking" modules={modulesForManifest(activeManifest)} />}
      miniSidebar={<TerminalMiniSidebar tools={sideTools} />}
      contextSidebar={<TerminalContextSidebar kind="tree" groups={treeGroupsForManifest(activeManifest, sortedManifests)} operator={operatorForManifest(activeManifest)} />}
    >
      <TerminalWorkspace
        tabs={tabs}
        taskTabs={taskTabs}
        statusBar={<TerminalStatusBar connection={apiStatusForManifest(activeManifest).label} />}
        onTabSelect={(tab) => {
          if (tab.screenId) {
            setActiveScreenId(tab.screenId);
          }
        }}
      >
        <TerminalBody
          className="manifest-terminal-body"
          rightRail={<RendererRightRail manifest={activeManifest} allManifests={sortedManifests} />}
        >
          <div className="manifest-work-area">
            <TransactionCodeLauncher
              manifests={sortedManifests}
              activeManifest={activeManifest}
              onOpenManifest={openManifest}
            />
            <ScreenRenderer manifest={activeManifest} allManifests={sortedManifests} onOpenManifest={openManifest} />
          </div>
        </TerminalBody>
      </TerminalWorkspace>
    </TerminalShell>
  );
}

export function StaffManifestScreenRenderer({ manifest, manifests = [manifest] }: StaffManifestScreenRendererProps) {
  return <StaffIntegratedWorkspace manifests={manifests} initialScreen={manifest.screenId} />;
}

export function TransactionCodeLauncher({
  manifests,
  activeManifest,
  onOpenManifest
}: {
  readonly manifests: readonly ScreenManifest[];
  readonly activeManifest: ScreenManifest;
  readonly onOpenManifest: (manifest: ScreenManifest) => void;
}) {
  const [query, setQuery] = useState(activeManifest.transactionCode ?? activeManifest.screenId);
  const results = useMemo(() => searchManifests(manifests, query).slice(0, 8), [manifests, query]);

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const exact = findManifest(manifests, query) ?? results[0];
    if (exact) {
      onOpenManifest(exact);
    }
  };

  return (
    <TerminalPanel title="Transaction Code Launcher" icon="keyboard" className="manifest-panel launcher-panel">
      <form className="transaction-launcher" onSubmit={submit} aria-label="Transaction code launcher">
        <label className="terminal-command">
          <span>Transaction code search</span>
          <input
            aria-label="Transaction code search"
            value={query}
            onChange={(event) => setQuery(event.currentTarget.value)}
            placeholder="CST001, Customer, Approval"
          />
        </label>
        <TerminalButton variant="panelAction" icon="search" type="submit">
          Open
        </TerminalButton>
      </form>
      <div className="launcher-results" aria-label="Transaction code or screen name search results">
        {results.map((manifest) => (
          <button
            className={manifest.screenId === activeManifest.screenId ? "is-active" : ""}
            type="button"
            key={manifest.screenId}
            onClick={() => onOpenManifest(manifest)}
          >
            <span>{manifest.transactionCode}</span>
            <strong>{manifest.title}</strong>
            <em>{manifest.type} / {manifest.domain}</em>
          </button>
        ))}
      </div>
    </TerminalPanel>
  );
}

export function ScreenRenderer({
  manifest,
  allManifests,
  onOpenManifest
}: {
  readonly manifest: ScreenManifest;
  readonly allManifests: readonly ScreenManifest[];
  readonly onOpenManifest: (manifest: ScreenManifest) => void;
}) {
  if (manifest.type === "INQUIRY") {
    return <InquiryScreenRenderer manifest={manifest} onOpenManifest={onOpenManifest} allManifests={allManifests} />;
  }
  if (manifest.type === "COMMAND") {
    return <CommandScreenRenderer manifest={manifest} />;
  }
  if (manifest.type === "CASE") {
    return <CaseScreenRenderer manifest={manifest} onOpenManifest={onOpenManifest} allManifests={allManifests} />;
  }
  if (manifest.type === "PARAMETER") {
    return <ParameterScreenRenderer manifest={manifest} />;
  }
  return <DashboardScreenRenderer manifest={manifest} allManifests={allManifests} onOpenManifest={onOpenManifest} />;
}

export function InquiryScreenRenderer({
  manifest,
  allManifests,
  onOpenManifest
}: {
  readonly manifest: ScreenManifest;
  readonly allManifests: readonly ScreenManifest[];
  readonly onOpenManifest: (manifest: ScreenManifest) => void;
}) {
  const fields = manifest.query?.fields || [];
  const reasonFields = resolveReasonFields(manifest, fields);
  const searchFields = fields.filter((field) => !isReasonField(field));
  const columns = manifest.resultTable?.columns?.length ? manifest.resultTable.columns : ["resultId", "status", "createdAt"];

  return (
    <>
      <ScreenHeader manifest={manifest} />
      <SearchPanel manifest={manifest} searchFields={searchFields} reasonFields={reasonFields} />
      <DataTable manifest={manifest} columns={columns} />
      <div className="manifest-detail-grid">
        <DetailPanel manifest={manifest} />
        <ActionPanel manifest={manifest} allManifests={allManifests} onOpenManifest={onOpenManifest} />
      </div>
      <AuditTimeline manifest={manifest} />
      <StructuredErrorView manifest={manifest} />
    </>
  );
}

export function CommandScreenRenderer({ manifest }: { readonly manifest: ScreenManifest }) {
  const status = apiStatusForManifest(manifest);
  return (
    <>
      <ScreenHeader manifest={manifest} />
      <div className="manifest-command-grid">
        <TerminalPanel title="Command Form" icon="settings" className="manifest-panel">
          <FormRenderer fields={manifest.fields || []} reasonRequired={manifest.audit.reasonRequired} />
          <div className="manifest-validation-list" aria-label="Validation messages">
            <span>Required fields are validated before submission.</span>
            <span>{manifest.audit.reasonRequired ? "Business reason is mandatory." : "Business reason is optional."}</span>
            <span>{manifest.approval?.required ? "Maker-checker approval request is created before execution." : "Direct execution allowed by manifest."}</span>
          </div>
        </TerminalPanel>
        <TerminalPanel title="Before After Snapshot" icon="article" className="manifest-panel">
          <dl className="manifest-definition-list">
            <div>
              <dt>Before</dt>
              <dd>Masked synthetic current state</dd>
            </div>
            <div>
              <dt>After</dt>
              <dd>Pending command payload preview</dd>
            </div>
            <div>
              <dt>API</dt>
              <dd>{manifest.api?.command ?? "declared-only / not API-backed yet"}</dd>
            </div>
          </dl>
        </TerminalPanel>
      </div>
      <TerminalPanel title="Command Action" icon="play_arrow" className="manifest-panel">
        <div className="command-action-panel">
          <div>
            <strong>{status.label}</strong>
            <span>{status.status === "api-backed" ? "Connectivity is retained through @banking-lab/api-client smoke flows." : "No fake success is rendered for this declared-only screen."}</span>
          </div>
          <TerminalButton variant="panelAction" icon="play_arrow" type="button" className={status.status === "declared-only" ? "is-disabled" : ""}>
            {status.status === "api-backed" ? "API-backed command path declared" : "Execution unavailable"}
          </TerminalButton>
        </div>
      </TerminalPanel>
      <ApprovalPanel manifest={manifest} />
      <StructuredErrorView manifest={manifest} />
    </>
  );
}

export function CaseScreenRenderer({
  manifest,
  allManifests,
  onOpenManifest
}: {
  readonly manifest: ScreenManifest;
  readonly allManifests: readonly ScreenManifest[];
  readonly onOpenManifest: (manifest: ScreenManifest) => void;
}) {
  return (
    <>
      <ScreenHeader manifest={manifest} />
      <div className="manifest-case-grid">
        <TerminalPanel title="Case Status" icon="article" className="manifest-panel">
          <dl className="manifest-definition-list">
            <div>
              <dt>Workflow</dt>
              <dd>{manifest.workflow?.name ?? "declared-only / not API-backed yet"}</dd>
            </div>
            <div>
              <dt>Current Status</dt>
              <dd>{manifest.workflow?.states?.[0] ?? "OPEN"}</dd>
            </div>
            <div>
              <dt>Owner</dt>
              <dd>{ownerForManifest(manifest)}</dd>
            </div>
            <div>
              <dt>SLA</dt>
              <dd>{manifest.sla?.enabled ? `${manifest.sla.targetHours} hours` : "not tracked"}</dd>
            </div>
          </dl>
        </TerminalPanel>
        <TerminalPanel title="Comments And Attachments" icon="description" className="manifest-panel">
          <div className="manifest-comment-list">
            <span>Internal comment stream: synthetic-only preview.</span>
            <span>Attachments: simulator metadata only, no real customer files.</span>
          </div>
        </TerminalPanel>
      </div>
      <AuditTimeline manifest={manifest} />
      <ApprovalPanel manifest={manifest} />
      <ActionPanel manifest={manifest} allManifests={allManifests} onOpenManifest={onOpenManifest} />
    </>
  );
}

export function ParameterScreenRenderer({ manifest }: { readonly manifest: ScreenManifest }) {
  return (
    <>
      <ScreenHeader manifest={manifest} />
      <div className="manifest-parameter-grid">
        <TerminalPanel title="Current Value" icon="description" className="manifest-panel">
          <dl className="manifest-definition-list">
            <div>
              <dt>Namespace</dt>
              <dd>{manifest.parameter?.namespace ?? "missing"}</dd>
            </div>
            <div>
              <dt>Current Endpoint</dt>
              <dd>{manifest.parameter?.currentValueEndpoint ?? "declared-only / not API-backed yet"}</dd>
            </div>
            <div>
              <dt>Keys</dt>
              <dd>{manifest.parameter?.keys?.join(", ") ?? "none"}</dd>
            </div>
          </dl>
        </TerminalPanel>
        <TerminalPanel title="Scheduled Value" icon="settings" className="manifest-panel">
          <FormRenderer fields={manifest.fields || []} reasonRequired={manifest.audit.reasonRequired} />
        </TerminalPanel>
      </div>
      <TerminalPanel title="Change History And Rollback" icon="receipt" className="manifest-panel">
        <DenseTable
          columns={["parameterKey", "scheduledValue", "effectiveAt", "approvalStatus", "rollbackPlan"]}
          rows={[["syntheticParameter", "pending", "2026-06-04T09:00:00Z", manifest.approval?.required ? "WAITING_APPROVAL" : "NOT_REQUIRED", "restore previous value"]]}
          ariaLabel={`${manifest.title} parameter history`}
        />
      </TerminalPanel>
      <ApprovalPanel manifest={manifest} />
      <StructuredErrorView manifest={manifest} />
    </>
  );
}

export function DashboardScreenRenderer({
  manifest,
  allManifests,
  onOpenManifest
}: {
  readonly manifest: ScreenManifest;
  readonly allManifests: readonly ScreenManifest[];
  readonly onOpenManifest: (manifest: ScreenManifest) => void;
}) {
  const reasonRequired = allManifests.filter((screen) => screen.audit.reasonRequired).length;
  const makerChecker = allManifests.filter((screen) => screen.approval?.required).length;
  const byType = countBy(allManifests, (screen) => screen.type);

  return (
    <>
      <ScreenHeader manifest={manifest} />
      <section className="manifest-evidence-strip" aria-label="Staff terminal control evidence">
        <h1>Transaction-code workspace</h1>
        <div className="metric-card">
          <span className="metric">{allManifests.length}</span>
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
      <div className="manifest-dashboard-grid">
        <TerminalPanel title="Template Coverage" icon="grid_view" className="manifest-panel">
          <DenseTable
            columns={["template", "count"]}
            rows={Object.entries(byType).map(([type, count]) => [type, count])}
            ariaLabel="Template coverage"
          />
        </TerminalPanel>
        <TerminalPanel title="Role Aware Menu" icon="menu" className="manifest-panel">
          <div className="screen-shortcut-list">
            {allManifests.slice(0, 12).map((screen) => (
              <button type="button" key={screen.screenId} onClick={() => onOpenManifest(screen)}>
                <span>{screen.transactionCode}</span>
                <strong>{screen.title}</strong>
              </button>
            ))}
          </div>
        </TerminalPanel>
      </div>
      <ApiBackedStaffPanel />
    </>
  );
}

export function CustomerContextPanel({ manifest }: { readonly manifest: ScreenManifest }) {
  if (!manifest.layout.customerContext) {
    return (
      <TerminalPanel title="Customer Context" icon="person" className="manifest-rail-panel">
        <div className="manifest-empty-state">
          <strong>No customer context</strong>
          <span>This operational screen does not require customer-scoped state.</span>
        </div>
      </TerminalPanel>
    );
  }

  return (
    <TerminalPanel title="Customer Context" icon="person" className="manifest-rail-panel">
      <dl className="manifest-definition-list">
        <div>
          <dt>Customer</dt>
          <dd>SYN-CUS-001</dd>
        </div>
        <div>
          <dt>Name</dt>
          <dd><MaskedValue value="Kim Doe" maskedValue="K** D***" masked /></dd>
        </div>
        <div>
          <dt>Phone</dt>
          <dd><MaskedValue value="010-0000-1001" maskedValue="010-****-1001" masked /></dd>
        </div>
        <div>
          <dt>Risk</dt>
          <dd>LOW / synthetic</dd>
        </div>
      </dl>
    </TerminalPanel>
  );
}

export function SearchPanel({
  manifest,
  searchFields,
  reasonFields
}: {
  readonly manifest: ScreenManifest;
  readonly searchFields: readonly ManifestField[];
  readonly reasonFields: readonly ManifestField[];
}) {
  return (
    <TerminalPanel
      title="Search Conditions"
      icon="search"
      className="manifest-panel manifest-search-panel"
      action={<EndpointBadge endpoint={manifest.query?.endpoint} />}
    >
      <form className="manifest-search-form" aria-label={`${manifest.title} search form`}>
        <div className="manifest-field-grid">
          {searchFields.map((field) => (
            <ManifestFieldControl field={field} key={field.name} />
          ))}
        </div>
        <ReasonRequiredPanel fields={reasonFields} reasonRequired={manifest.audit.reasonRequired} />
        <div className="manifest-action-bar" aria-label="Manifest actions">
          <TerminalButton variant="panelAction" icon="search" type="submit">
            Search
          </TerminalButton>
        </div>
      </form>
    </TerminalPanel>
  );
}

export function DataTable({ manifest, columns }: { readonly manifest: ScreenManifest; readonly columns: readonly string[] }) {
  return (
    <TerminalPanel
      title="Result Table"
      icon="description"
      className="manifest-panel manifest-result-panel"
      action={<span>{manifest.audit.piiAccess ? "PII masked" : "non-PII"}</span>}
    >
      <DenseTable columns={columns} rows={[buildPreviewRow(columns, manifest)]} ariaLabel={`${manifest.title} result table`} />
      <p className="manifest-table-note">{apiStatusForManifest(manifest).label}</p>
    </TerminalPanel>
  );
}

export function DetailPanel({ manifest }: { readonly manifest: ScreenManifest }) {
  return (
    <TerminalPanel title="Detail Panel" icon="article" className="manifest-panel">
      <dl className="manifest-definition-list">
        <div>
          <dt>Template</dt>
          <dd>{manifest.layout.template}</dd>
        </div>
        <div>
          <dt>Customer context</dt>
          <dd>{manifest.layout.customerContext ? "enabled" : "disabled"}</dd>
        </div>
        <div>
          <dt>Masking</dt>
          <dd>{manifest.audit.maskingPolicy}</dd>
        </div>
        <div>
          <dt>API status</dt>
          <dd>{apiStatusForManifest(manifest).label}</dd>
        </div>
      </dl>
    </TerminalPanel>
  );
}

export function FormRenderer({ fields, reasonRequired }: { readonly fields: readonly ManifestField[]; readonly reasonRequired: boolean }) {
  const resolvedFields = fields.length > 0 ? fields : reasonRequired ? [fallbackReasonField] : [];
  return (
    <form className="manifest-search-form" aria-label="Manifest command form">
      <div className="manifest-field-grid">
        {resolvedFields.map((field) => (
          <ManifestFieldControl field={field} key={field.name} />
        ))}
      </div>
      {reasonRequired && !resolvedFields.some(isReasonField) ? (
        <ReasonRequiredPanel fields={[fallbackReasonField]} reasonRequired />
      ) : null}
    </form>
  );
}

export function ActionPanel({
  manifest,
  allManifests,
  onOpenManifest
}: {
  readonly manifest: ScreenManifest;
  readonly allManifests: readonly ScreenManifest[];
  readonly onOpenManifest: (manifest: ScreenManifest) => void;
}) {
  return (
    <TerminalPanel title="Action Panel" icon="play_arrow" className="manifest-panel">
      <div className="manifest-action-list">
        {(manifest.actions || []).map((action) => {
          const targetManifest = findManifest(allManifests, action.target);
          return (
            <button type="button" key={action.id} onClick={() => targetManifest ? onOpenManifest(targetManifest) : undefined}>
              <strong>{action.label}</strong>
              <span>{action.type || "action"} / {action.target || "no target"}</span>
            </button>
          );
        })}
        {(manifest.actions || []).length === 0 ? <span className="manifest-muted">No actions declared</span> : null}
      </div>
    </TerminalPanel>
  );
}

export function ApprovalPanel({ manifest }: { readonly manifest: ScreenManifest }) {
  return (
    <TerminalPanel title="Maker Checker Approval" icon="account_tree" className="manifest-panel approval-panel">
      <dl className="manifest-definition-list">
        <div>
          <dt>Required</dt>
          <dd>{manifest.approval?.required ? "required" : "not required"}</dd>
        </div>
        <div>
          <dt>Separation</dt>
          <dd>{manifest.approval?.makerChecker ? "maker-checker enforced" : "not applicable"}</dd>
        </div>
        <div>
          <dt>Approver</dt>
          <dd>{manifest.approval?.approverRole ?? "none"}</dd>
        </div>
        <div>
          <dt>Business type</dt>
          <dd>{manifest.approval?.businessTypes?.join(", ") ?? "none"}</dd>
        </div>
      </dl>
    </TerminalPanel>
  );
}

export function AuditTimeline({ manifest }: { readonly manifest: ScreenManifest }) {
  const states = manifest.audit.eventTypes?.length ? manifest.audit.eventTypes : manifest.workflow?.states?.length ? manifest.workflow.states.slice(0, 6) : ["OPENED", "VALIDATED", "AUDIT_RECORDED"];
  return (
    <TerminalPanel title="Audit Timeline" icon="receipt" className="manifest-panel manifest-audit-panel">
      <ol className="renderer-timeline">
        {states.map((state, index) => (
          <li key={`${manifest.screenId}-${state}`}>
            <span>{String(index + 1).padStart(2, "0")}</span>
            <strong>{state}</strong>
            <em>{manifest.audit.enabled ? "audit event expected" : "audit disabled"}</em>
          </li>
        ))}
      </ol>
    </TerminalPanel>
  );
}

export function MaskedValue({
  value,
  maskedValue,
  masked
}: {
  readonly value: string;
  readonly maskedValue: string;
  readonly masked: boolean;
}) {
  return <span className="masked-value">{masked ? maskedValue : value}</span>;
}

export function StructuredErrorView({ manifest }: { readonly manifest: ScreenManifest }) {
  const code = manifest.audit.reasonRequired ? "POLICY_REASON_REQUIRED" : manifest.approval?.makerChecker ? "MAKER_CHECKER_SELF_APPROVAL_REJECTED" : "AUTHORIZATION_POLICY_VIOLATION";
  const policy = manifest.audit.reasonRequired ? "REASON_REQUIRED" : manifest.approval?.makerChecker ? "MAKER_CHECKER_SEPARATION_OF_DUTIES" : "RBAC_ABAC";
  return (
    <TerminalPanel title="Structured Error Surface" icon="description" className="manifest-panel structured-error-panel">
      <dl className="manifest-definition-list">
        <div>
          <dt>Code</dt>
          <dd>{code}</dd>
        </div>
        <div>
          <dt>Policy</dt>
          <dd>{policy}</dd>
        </div>
        <div>
          <dt>Route</dt>
          <dd>{manifest.query?.endpoint ?? manifest.api?.command ?? "declared-only / not API-backed yet"}</dd>
        </div>
        <div>
          <dt>Docs</dt>
          <dd>docs/migration/structured-api-error-contract.md</dd>
        </div>
      </dl>
    </TerminalPanel>
  );
}

function RendererRightRail({
  manifest,
  allManifests
}: {
  readonly manifest: ScreenManifest;
  readonly allManifests: readonly ScreenManifest[];
}) {
  return (
    <RightRail>
      <CustomerContextPanel manifest={manifest} />
      <TerminalPanel title="Masking State" icon="receipt" className="manifest-rail-panel">
        <dl className="manifest-definition-list">
          <div>
            <dt>Policy</dt>
            <dd>{manifest.audit.maskingPolicy}</dd>
          </div>
          <div>
            <dt>Default</dt>
            <dd>{manifest.audit.piiAccess ? "masked" : "plain"}</dd>
          </div>
          <div>
            <dt>Unmask</dt>
            <dd>{manifest.audit.piiAccess ? "reason and privileged role required" : "not applicable"}</dd>
          </div>
        </dl>
      </TerminalPanel>
      <ApprovalPanel manifest={manifest} />
      <TerminalPanel title="Catalog Counts" icon="grid_view" className="manifest-rail-panel">
        <dl className="manifest-definition-list">
          <div>
            <dt>Total</dt>
            <dd>{allManifests.length}</dd>
          </div>
          <div>
            <dt>Reason</dt>
            <dd>{allManifests.filter((screen) => screen.audit.reasonRequired).length}</dd>
          </div>
          <div>
            <dt>Approval</dt>
            <dd>{allManifests.filter((screen) => screen.approval?.required).length}</dd>
          </div>
        </dl>
      </TerminalPanel>
    </RightRail>
  );
}

function ScreenHeader({ manifest }: { readonly manifest: ScreenManifest }) {
  const status = apiStatusForManifest(manifest);
  return (
    <section className="manifest-screen-title" aria-label="Manifest screen identity">
      <div>
        <span>{manifest.screenId}</span>
        <h1>{manifest.title}</h1>
      </div>
      <div className="manifest-title-meta">
        <span>{manifest.transactionCode || "NO-TCODE"}</span>
        <span>{manifest.type}</span>
        <span>{manifest.domain}</span>
        <span>{status.label}</span>
      </div>
    </section>
  );
}

function ManifestFieldControl({ field }: { readonly field: ManifestField }) {
  const fieldType = mapFieldType(field.type);
  const label = `${field.label || field.name}${field.required ? " *" : ""}`;
  const fieldWithOptions = field as ManifestField & { readonly options?: readonly string[] };

  return (
    <TerminalField
      label={label}
      fieldType={fieldType}
      name={field.name}
      required={field.required === true}
      placeholder={placeholderForField(field)}
      options={fieldWithOptions.options ?? (fieldType === "select" ? ["ALL", "ACTIVE", "PENDING", "CLOSED"] : undefined)}
      rows={fieldType === "textarea" ? 4 : undefined}
      ariaLabel={field.label || field.name}
    />
  );
}

function ReasonRequiredPanel({
  fields,
  reasonRequired
}: {
  readonly fields: readonly ManifestField[];
  readonly reasonRequired: boolean;
}) {
  if (!reasonRequired) {
    return (
      <div className="manifest-reason-panel is-optional">
        <strong>Business reason</strong>
        <span>Not required for this screen.</span>
      </div>
    );
  }

  return (
    <div className="manifest-reason-panel">
      <div>
        <strong>Business reason required</strong>
        <span>Staff sensitive lookup and high-risk commands cannot proceed without a declared reason.</span>
      </div>
      <div className="manifest-field-grid">
        {(fields.length > 0 ? fields : [fallbackReasonField]).map((field) => (
          <ManifestFieldControl field={{ ...field, required: true }} key={field.name} />
        ))}
      </div>
    </div>
  );
}

function EndpointBadge({ endpoint }: { readonly endpoint?: string }) {
  return <span className="manifest-endpoint">{endpoint || "declared-only / not API-backed yet"}</span>;
}

function buildPreviewRow(columns: readonly string[], manifest: ScreenManifest): TableRow {
  return columns.map((column) => maskedPreview(column, manifest));
}

function maskedPreview(column: string, manifest: ScreenManifest): ReactNode {
  const normalized = column.toLowerCase();
  if (sampleValues[column]) {
    return sampleValues[column];
  }
  if (normalized.includes("masked")) {
    return "****";
  }
  if (manifest.audit.piiAccess && (normalized.includes("name") || normalized.includes("phone") || normalized.includes("account") || normalized.includes("customer"))) {
    return "****";
  }
  if (normalized.includes("status")) {
    return "PENDING";
  }
  return "SYNTHETIC";
}

function resolveReasonFields(manifest: ScreenManifest, fields: readonly ManifestField[]) {
  const explicit = fields.filter((field) => isReasonField(field));
  if (explicit.length > 0) {
    return explicit;
  }
  return manifest.audit.reasonRequired ? [fallbackReasonField] : [];
}

function isReasonField(field: ManifestField) {
  const name = field.name.toLowerCase();
  const label = (field.label || "").toLowerCase();
  return name.includes("reason") || label.includes("reason") || label.includes("사유");
}

function mapFieldType(fieldType: string | undefined): TerminalFieldType {
  if (fieldType === "textarea") {
    return "textarea";
  }
  if (fieldType === "select") {
    return "select";
  }
  if (fieldType === "date" || fieldType === "datetime") {
    return "date";
  }
  if (fieldType === "amount" || fieldType === "money" || fieldType === "number") {
    return "amount";
  }
  if (fieldType?.includes("search")) {
    return "search";
  }
  return "text";
}

function placeholderForField(field: ManifestField) {
  if (field.mask) {
    return `${field.mask} masked by default`;
  }
  if (isReasonField(field)) {
    return "Enter business reason";
  }
  return field.label || field.name;
}

function compareManifest(a: ScreenManifest, b: ScreenManifest) {
  return (a.transactionCode || a.screenId).localeCompare(b.transactionCode || b.screenId);
}

function findManifest(manifests: readonly ScreenManifest[], value: string | null | undefined) {
  if (!value) {
    return undefined;
  }
  const normalized = normalizeSearch(value);
  return manifests.find((manifest) => normalizeSearch(manifest.screenId) === normalized || normalizeSearch(manifest.transactionCode) === normalized);
}

function searchManifests(manifests: readonly ScreenManifest[], query: string) {
  const normalized = normalizeSearch(query);
  if (!normalized) {
    return manifests.slice(0, 8);
  }
  return manifests.filter((manifest) => {
    const haystack = [
      manifest.screenId,
      manifest.transactionCode,
      manifest.title,
      manifest.domain,
      manifest.type
    ].map(normalizeSearch).join(" ");
    return haystack.includes(normalized);
  });
}

function normalizeSearch(value: string | null | undefined) {
  return (value || "").replace(/[^a-zA-Z0-9가-힣]/g, "").toUpperCase();
}

function tabForManifest(manifest: ScreenManifest, activeScreenId: string | undefined): WorkspaceTab {
  return {
    title: `[${manifest.transactionCode || manifest.screenId}] ${manifest.title}`,
    screenId: manifest.screenId,
    active: manifest.screenId === activeScreenId
  };
}

function apiStatusForManifest(manifest: ScreenManifest): { readonly status: RendererStatus; readonly label: string } {
  const endpoint = [
    manifest.query?.endpoint,
    manifest.api?.command,
    ...(manifest.actions || []).map((action) => action.target)
  ].filter((value): value is string => Boolean(value)).join(" ");
  const apiBacked = apiBackedEndpointFragments.some((fragment) => endpoint.includes(fragment));
  return apiBacked
    ? { status: "api-backed", label: "API-backed via @banking-lab/api-client" }
    : { status: "declared-only", label: "declared-only / not API-backed yet" };
}

function countBy<T extends string>(values: readonly ScreenManifest[], selector: (manifest: ScreenManifest) => T) {
  return values.reduce<Record<T, number>>((accumulator, manifest) => {
    const key = selector(manifest);
    accumulator[key] = (accumulator[key] || 0) + 1;
    return accumulator;
  }, {} as Record<T, number>);
}

function modulesForManifest(manifest: ScreenManifest | undefined) {
  const activeModule = activeModuleForDomain(manifest?.domain ?? "");
  return topModules.map((module) => ({
    ...module,
    active: module.label === activeModule
  }));
}

function activeModuleForDomain(domain: string) {
  if (domain === "loan" || domain === "credit") {
    return "여신";
  }
  if (domain === "fx" || domain === "foreign-exchange") {
    return "외환";
  }
  if (domain === "customer" || domain === "complaint") {
    return "고객";
  }
  if (domain === "account" || domain === "ledger" || domain === "transfer" || domain === "limit" || domain === "fee") {
    return "수신";
  }
  return "CRM";
}

function operatorForManifest(manifest: ScreenManifest | undefined) {
  return {
    initials: "BL",
    name: "차세대담당자",
    role: manifest?.requiredRoles[0] || "STAFF",
    branch: "Synthetic Branch"
  };
}

function ownerForManifest(manifest: ScreenManifest) {
  if (manifest.domain === "complaint") {
    return "complaint01";
  }
  if (manifest.domain === "fds") {
    return "fds01";
  }
  if (manifest.domain === "aml") {
    return "aml01";
  }
  if (manifest.domain === "reconciliation") {
    return "ops01";
  }
  return "branch01";
}

function treeGroupsForManifest(manifest: ScreenManifest | undefined, manifests: readonly ScreenManifest[]): readonly TerminalTreeGroup[] {
  const childrenForDomain = (domain: string) => manifests
    .filter((screen) => screen.domain === domain)
    .slice(0, 8)
    .map((screen) => ({
      code: screen.transactionCode || screen.screenId,
      label: screen.title,
      selected: screen.screenId === manifest?.screenId
    }));

  return [
    {
      label: "고객",
      open: ["customer", "complaint"].includes(manifest?.domain ?? ""),
      children: [...childrenForDomain("customer"), ...childrenForDomain("complaint")]
    },
    {
      label: "수신",
      open: ["account", "ledger", "transfer", "limit", "fee"].includes(manifest?.domain ?? ""),
      children: [
        ...childrenForDomain("account"),
        ...childrenForDomain("ledger"),
        ...childrenForDomain("transfer"),
        ...childrenForDomain("limit"),
        ...childrenForDomain("fee")
      ].slice(0, 10)
    },
    {
      label: "리스크/정산",
      open: ["fds", "aml", "reconciliation"].includes(manifest?.domain ?? ""),
      children: [
        ...childrenForDomain("fds"),
        ...childrenForDomain("aml"),
        ...childrenForDomain("reconciliation")
      ].slice(0, 10)
    },
    {
      label: "감사/승인",
      open: ["audit", "approval"].includes(manifest?.domain ?? ""),
      children: [
        ...childrenForDomain("audit"),
        ...childrenForDomain("approval")
      ].slice(0, 8)
    }
  ];
}
