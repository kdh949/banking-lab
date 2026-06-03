import type { ManifestField, ScreenManifest } from "../../../../packages/screen-engine/src/types";
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
  type TerminalTreeGroup
} from "./terminal-ui";
import { sideTools, taskTabs, topModules } from "./terminal-screens";

type StaffManifestScreenRendererProps = {
  readonly manifest: ScreenManifest;
};

type InquiryManifest = ScreenManifest & {
  readonly type: "INQUIRY";
  readonly query: NonNullable<ScreenManifest["query"]>;
  readonly resultTable: NonNullable<ScreenManifest["resultTable"]>;
};

const fallbackReasonField: ManifestField = {
  name: "reason",
  label: "Lookup Reason",
  type: "textarea",
  required: true
};

const sampleValues: Record<string, string> = {
  accountId: "SYN-ACC-001",
  accountNo: "110-***-123456",
  availableBalanceMinor: "1,250,000",
  businessDate: "2026-06-04",
  customerGrade: "STANDARD",
  customerId: "SYN-CUS-001",
  ledgerBalanceMinor: "1,250,000",
  maskedAccountNo: "110-***-123456",
  maskedName: "K** D***",
  maskedPhone: "010-****-1001",
  posting: "balanced",
  riskGrade: "LOW",
  status: "ACTIVE",
  transactionId: "TX-SYN-0001",
  transactionType: "INTERNAL_TRANSFER"
};

export function StaffManifestScreenRenderer({ manifest }: StaffManifestScreenRendererProps) {
  if (manifest.type === "INQUIRY" && manifest.query && manifest.resultTable) {
    return <InquiryScreenRenderer manifest={manifest as InquiryManifest} />;
  }

  return <UnsupportedTemplateScreen manifest={manifest} />;
}

function InquiryScreenRenderer({ manifest }: { readonly manifest: InquiryManifest }) {
  const fields = manifest.query.fields || [];
  const reasonFields = resolveReasonFields(manifest, fields);
  const searchFields = fields.filter((field) => !isReasonField(field));
  const columns = manifest.resultTable.columns || [];
  const auditEvents = manifest.audit.eventTypes || [];

  return (
    <TerminalShell
      topbar={<TerminalTopbar brand="INZENT Banking" modules={modulesForManifest(manifest)} />}
      miniSidebar={<TerminalMiniSidebar tools={sideTools} />}
      contextSidebar={<TerminalContextSidebar kind="tree" groups={treeGroupsForManifest(manifest)} operator={operatorForManifest(manifest)} />}
    >
      <TerminalWorkspace
        tabs={[{ title: `[${manifest.transactionCode || manifest.screenId}] ${manifest.title}`, active: true }]}
        taskTabs={taskTabs}
        statusBar={<TerminalStatusBar connection="manifest renderer" />}
      >
        <TerminalBody className="manifest-terminal-body" rightRail={<ManifestRightRail manifest={manifest} reasonFields={reasonFields} />}>
          <div className="manifest-work-area">
            <section className="manifest-screen-title" aria-label="Manifest screen identity">
              <div>
                <span>{manifest.screenId}</span>
                <h1>{manifest.title}</h1>
              </div>
              <div className="manifest-title-meta">
                <span>{manifest.transactionCode || "NO-TCODE"}</span>
                <span>{manifest.domain}</span>
                <span>{manifest.layout.template}</span>
              </div>
            </section>

            <TerminalPanel
              title="Search Panel"
              icon="search"
              className="manifest-panel manifest-search-panel"
              action={<EndpointBadge endpoint={manifest.query.endpoint} />}
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
                    조회
                  </TerminalButton>
                  {(manifest.actions || []).map((action) => (
                    <TerminalButton variant="panelAction" icon={action.type === "command" ? "settings" : "open_in_new"} key={action.id}>
                      {action.label}
                    </TerminalButton>
                  ))}
                </div>
              </form>
            </TerminalPanel>

            <TerminalPanel
              title="Result Table"
              icon="description"
              className="manifest-panel manifest-result-panel"
              action={<span>{manifest.audit.piiAccess ? "PII masked" : "non-PII"}</span>}
            >
              <DenseTable columns={columns} rows={[buildPreviewRow(columns, manifest)]} ariaLabel={`${manifest.title} result table`} />
              <p className="manifest-table-note">Manifest-declared preview only. Live API calls are intentionally outside this slice.</p>
            </TerminalPanel>

            <div className="manifest-detail-grid">
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
                    <dt>Tabbed workspace</dt>
                    <dd>{manifest.layout.tabbed ? "enabled" : "disabled"}</dd>
                  </div>
                </dl>
              </TerminalPanel>
              <TerminalPanel title="Action Bar" icon="play_arrow" className="manifest-panel">
                <div className="manifest-action-list">
                  {(manifest.actions || []).map((action) => (
                    <div key={action.id}>
                      <strong>{action.label}</strong>
                      <span>{action.type || "action"} / {action.target || "no target"}</span>
                    </div>
                  ))}
                  {(manifest.actions || []).length === 0 ? <span className="manifest-muted">No actions declared</span> : null}
                </div>
              </TerminalPanel>
            </div>
          </div>
        </TerminalBody>
      </TerminalWorkspace>
    </TerminalShell>
  );
}

function UnsupportedTemplateScreen({ manifest }: { readonly manifest: ScreenManifest }) {
  return (
    <TerminalShell
      topbar={<TerminalTopbar brand="INZENT Banking" modules={modulesForManifest(manifest)} />}
      miniSidebar={<TerminalMiniSidebar tools={sideTools} />}
      contextSidebar={<TerminalContextSidebar kind="tree" groups={treeGroupsForManifest(manifest)} operator={operatorForManifest(manifest)} />}
    >
      <TerminalWorkspace
        tabs={[{ title: `[${manifest.transactionCode || manifest.screenId}] ${manifest.title}`, active: true }]}
        taskTabs={taskTabs}
        statusBar={<TerminalStatusBar connection="manifest renderer" />}
      >
        <TerminalBody className="manifest-terminal-body">
          <TerminalPanel title="Template Not Implemented" icon="description" className="manifest-panel">
            <div className="manifest-empty-state">
              <strong>{manifest.type}</strong>
              <span>{manifest.layout.template} renderer is declared but not implemented in this slice.</span>
            </div>
          </TerminalPanel>
        </TerminalBody>
      </TerminalWorkspace>
    </TerminalShell>
  );
}

function ManifestFieldControl({ field }: { readonly field: ManifestField }) {
  const fieldType = mapFieldType(field.type);
  const label = `${field.label || field.name}${field.required ? " *" : ""}`;

  return (
    <TerminalField
      label={label}
      fieldType={fieldType}
      name={field.name}
      required={field.required === true}
      placeholder={placeholderForField(field)}
      options={fieldType === "select" ? ["ALL", "ACTIVE", "PENDING", "CLOSED"] : undefined}
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
        <span>Staff sensitive lookup cannot proceed without a declared reason.</span>
      </div>
      <div className="manifest-field-grid">
        {fields.map((field) => (
          <ManifestFieldControl field={{ ...field, required: true }} key={field.name} />
        ))}
      </div>
    </div>
  );
}

function ManifestRightRail({
  manifest,
  reasonFields
}: {
  readonly manifest: InquiryManifest;
  readonly reasonFields: readonly ManifestField[];
}) {
  const maskingFields = (manifest.query.fields || []).filter((field) => field.mask);
  const auditEvents = manifest.audit.eventTypes || [];

  return (
    <RightRail>
      <TerminalPanel title="Audit Panel" icon="receipt" className="manifest-rail-panel manifest-audit-panel">
        <dl className="manifest-definition-list">
          <div>
            <dt>Audit</dt>
            <dd>{manifest.audit.enabled ? "enabled" : "disabled"}</dd>
          </div>
          <div>
            <dt>Events</dt>
            <dd>{auditEvents.length > 0 ? auditEvents.join(", ") : "missing"}</dd>
          </div>
          <div>
            <dt>Reason</dt>
            <dd>{reasonFields.length > 0 ? "required" : "not declared"}</dd>
          </div>
          <div>
            <dt>Roles</dt>
            <dd>{manifest.requiredRoles.join(", ")}</dd>
          </div>
        </dl>
      </TerminalPanel>
      <TerminalPanel title="Masking Panel" icon="receipt" className="manifest-rail-panel manifest-masking-panel">
        <dl className="manifest-definition-list">
          <div>
            <dt>Policy</dt>
            <dd>{manifest.audit.maskingPolicy}</dd>
          </div>
          <div>
            <dt>Default</dt>
            <dd>{manifest.audit.piiAccess ? "masked" : "plain"}</dd>
          </div>
        </dl>
        <div className="manifest-mask-list">
          {maskingFields.map((field) => (
            <span key={field.name}>{field.label || field.name}: {field.mask}</span>
          ))}
          {maskingFields.length === 0 ? <span>No field masks declared</span> : null}
        </div>
      </TerminalPanel>
      <TerminalPanel title="Template Coverage" icon="grid_view" className="manifest-rail-panel">
        <div className="manifest-coverage-list">
          {["Search Panel", "Result Table", "Detail Panel", "Reason Required Panel", "Audit Panel", "Status Bar", "Action Bar"].map((item) => (
            <span key={item}>{item}</span>
          ))}
        </div>
      </TerminalPanel>
    </RightRail>
  );
}

function EndpointBadge({ endpoint }: { readonly endpoint?: string }) {
  return <span className="manifest-endpoint">{endpoint || "No endpoint declared"}</span>;
}

function buildPreviewRow(columns: readonly string[], manifest: InquiryManifest): TableRow {
  return columns.map((column) => maskedPreview(column, manifest));
}

function maskedPreview(column: string, manifest: InquiryManifest) {
  const normalized = column.toLowerCase();
  if (sampleValues[column]) {
    return sampleValues[column];
  }
  if (normalized.includes("masked")) {
    return "****";
  }
  if (manifest.audit.piiAccess && (normalized.includes("name") || normalized.includes("phone") || normalized.includes("account"))) {
    return "****";
  }
  return "SYNTHETIC";
}

function resolveReasonFields(manifest: InquiryManifest, fields: readonly ManifestField[]) {
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
  if (fieldType === "date") {
    return "date";
  }
  if (fieldType === "amount") {
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
    return "Enter lookup reason";
  }
  return field.label || field.name;
}

function modulesForManifest(manifest: ScreenManifest) {
  const activeModule = activeModuleForDomain(manifest.domain);
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
  if (domain === "customer") {
    return "고객";
  }
  if (domain === "account" || domain === "ledger") {
    return "수신";
  }
  return "수신";
}

function operatorForManifest(manifest: ScreenManifest) {
  return {
    initials: "BL",
    name: "차세대담당자",
    role: manifest.requiredRoles[0] || "STAFF",
    branch: "Synthetic Branch"
  };
}

function treeGroupsForManifest(manifest: ScreenManifest): readonly TerminalTreeGroup[] {
  return [
    {
      label: "수신",
      open: manifest.domain === "account" || manifest.domain === "ledger",
      children: [
        { code: "ACC101", label: "계좌 조회", selected: manifest.screenId === "ACC-101" },
        { code: "LED101", label: "원장 거래내역 조회", selected: manifest.screenId === "LED-101" }
      ]
    },
    {
      label: "여신",
      open: manifest.domain === "loan" || manifest.domain === "credit",
      children: [
        { code: "LON101", label: "대출 원장 조회" },
        { code: "LON201", label: "상환/연체 계산" }
      ]
    },
    {
      label: "외환",
      open: manifest.domain === "fx" || manifest.domain === "foreign-exchange",
      children: [
        { code: "FX101", label: "환율 조회" },
        { code: "FX201", label: "환전/송금 계산" }
      ]
    },
    {
      label: "고객",
      open: manifest.domain === "customer",
      children: [
        { code: "CST001", label: "고객 통합 조회", selected: manifest.screenId === "CST-001" },
        { code: "CST002", label: "고객 상세 조회", selected: manifest.screenId === "CST-002" }
      ]
    },
    {
      label: "감사/정정",
      open: manifest.domain === "audit" || manifest.domain === "approval",
      children: [
        { code: "AUD001", label: "감사 로그 조회", selected: manifest.screenId === "AUD-001" },
        { code: "APR001", label: "승인함", selected: manifest.screenId === "APR-001" }
      ]
    }
  ];
}
