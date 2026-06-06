import {
  ChannelActionRow,
  ChannelBadge,
  ChannelCard,
  ChannelCardGrid,
  ChannelDefinitionList,
  ChannelMetric,
  ChannelMetricGrid,
  ChannelPanel,
  ChannelShell,
  ChannelTable
} from "../../../../packages/channel-ui/src";
import type { ScreenManifest } from "../../../../packages/screen-engine/src/types";
import { loadChannelManifests } from "../lib/manifestLoader";
import { staffWorkflowRouteSummaries } from "./workflow-route-summaries";

type StaffWorkflowState = {
  readonly label: string;
  readonly value: string;
  readonly detail: string;
  readonly tone?: "neutral" | "success" | "warning" | "critical";
};

type StaffWorkflowRoute = {
  readonly key: string;
  readonly href: string;
  readonly title: string;
  readonly eyebrow: string;
  readonly screenIds: readonly string[];
  readonly apiMethods: readonly string[];
  readonly controls: readonly string[];
  readonly states: readonly StaffWorkflowState[];
  readonly reasonRequired: boolean;
  readonly highRisk?: boolean;
};

export type StaffWorkflowRouteKey = "tx" | "customers" | "accounts" | "approvals" | "audit" | "workflows";

const reasonRequiredStates: readonly StaffWorkflowState[] = [
  { label: "Loading", value: "LOOKUP_RUNNING", detail: "Lookup waits for reason and token." },
  { label: "Success", value: "MASKED_RESULT_LOADED", detail: "Masked result and audit reference are visible.", tone: "success" },
  { label: "Reason", value: "POLICY_REASON_REQUIRED", detail: "Missing business reason is blocked.", tone: "critical" },
  { label: "Authorization", value: "AUTHORIZATION_DENIED", detail: "Role or step-up failure is structured.", tone: "critical" },
  { label: "Unexpected", value: "LOOKUP_UNEXPECTED_FAILURE", detail: "Unexpected failure keeps lookup context.", tone: "critical" }
];

const approvalStates: readonly StaffWorkflowState[] = [
  { label: "Requested", value: "APPROVAL_REQUESTED", detail: "Maker request is pending checker review.", tone: "warning" },
  { label: "Self-check", value: "MAKER_CHECKER_SEPARATION_REQUIRED", detail: "Maker self-approval is blocked.", tone: "critical" },
  { label: "Approved", value: "APPROVED", detail: "Checker approval is audit-linked.", tone: "success" },
  { label: "Rejected", value: "REJECTED", detail: "Checker rejection keeps reason and status.", tone: "critical" },
  { label: "Executed", value: "EXECUTED", detail: "Approved command result is visible.", tone: "success" }
];

export const staffWorkflowRoutes: Record<StaffWorkflowRouteKey, StaffWorkflowRoute> = {
  tx: {
    key: "tx",
    href: "/tx/[transactionCode]",
    title: "Transaction Code",
    eyebrow: "manifest-backed route",
    screenIds: ["WRK-001"],
    apiMethods: ["manifest renderer"],
    controls: ["Transaction code input", "ReasonInput", "StructuredErrorPanel"],
    states: reasonRequiredStates,
    reasonRequired: true
  },
  customers: {
    key: "customers",
    href: "/customers/[customerId]",
    title: "Customer Lookup",
    eyebrow: "Inquiry Template",
    screenIds: ["CST-001", "CST-002", "CST-003", "CST-104"],
    apiMethods: ["staffCustomerDetail", "unmaskStaffCustomer"],
    controls: ["CustomerSelector", "ReasonInput", "StepUpRequiredPanel", "AuditReferencePanel"],
    states: [
      ...reasonRequiredStates,
      { label: "Unmask", value: "PRIVILEGED_UNMASK_TIMEBOXED", detail: "High-risk unmask requires reason and audit.", tone: "warning" }
    ],
    reasonRequired: true,
    highRisk: true
  },
  accounts: {
    key: "accounts",
    href: "/accounts/[accountId]",
    title: "Account Operations",
    eyebrow: "Command Template",
    screenIds: ["ACC-101", "ACC-102", "ACC-103", "ACC-104", "LIM-101", "LIM-102", "FEE-102"],
    apiMethods: ["requestAccountHold", "requestAccountHoldRelease", "staffTransferLimits", "requestTransferLimitChange", "requestFeeWaiver"],
    controls: ["AccountSelector", "ReasonInput", "ApprovalStatusTimeline", "AuditReferencePanel"],
    states: approvalStates,
    reasonRequired: true,
    highRisk: true
  },
  approvals: {
    key: "approvals",
    href: "/approvals",
    title: "Approval Inbox",
    eyebrow: "maker-checker",
    screenIds: ["APR-001"],
    apiMethods: ["staffApprovals", "staffApproval", "approveStaffApproval", "rejectStaffApproval"],
    controls: ["ApprovalStatusTimeline", "ReasonInput", "StructuredErrorPanel"],
    states: approvalStates,
    reasonRequired: true,
    highRisk: true
  },
  audit: {
    key: "audit",
    href: "/audit",
    title: "Audit Events",
    eyebrow: "AuditReferencePanel",
    screenIds: ["AUD-001"],
    apiMethods: ["auditEvents"],
    controls: ["ReasonInput", "AuditReferencePanel", "StructuredErrorPanel"],
    states: [
      { label: "Loading", value: "AUDIT_EVENTS_LOADING", detail: "Audit event list is loading." },
      { label: "Loaded", value: "HASH_CHAIN_VISIBLE", detail: "Hash-chain evidence is visible.", tone: "success" },
      { label: "Authorization", value: "AUDITOR_ROLE_REQUIRED", detail: "Non-auditor route is denied.", tone: "critical" },
      { label: "Unexpected", value: "AUDIT_READ_FAILED", detail: "Unexpected read failure is structured.", tone: "critical" }
    ],
    reasonRequired: true
  },
  workflows: {
    key: "workflows",
    href: "/workflows/[businessReferenceId]",
    title: "Workflow Timeline",
    eyebrow: "Case timeline",
    screenIds: ["WRK-003"],
    apiMethods: ["staffWorkflowTimeline"],
    controls: ["ReasonInput", "ApprovalStatusTimeline", "AuditReferencePanel"],
    states: [
      { label: "Loading", value: "WORKFLOW_TIMELINE_LOADING", detail: "Timeline is loading with reason." },
      { label: "Requested", value: "REQUESTED", detail: "Maker event is visible.", tone: "warning" },
      { label: "Approved", value: "APPROVED", detail: "Checker event is visible.", tone: "success" },
      { label: "Rejected", value: "REJECTED", detail: "Terminal rejection event is visible.", tone: "critical" },
      { label: "Unexpected", value: "WORKFLOW_READ_FAILED", detail: "Unexpected read failure is structured.", tone: "critical" }
    ],
    reasonRequired: true
  }
};

export async function StaffWorkflowRoutePage({
  routeKey,
  routeParam
}: {
  readonly routeKey: StaffWorkflowRouteKey;
  readonly routeParam?: string;
}) {
  const route = staffWorkflowRoutes[routeKey];
  const manifests = await loadChannelManifests();
  const selectedManifests = selectManifests(route, manifests, routeParam);
  const selectedScreenIds = selectedManifests.map((manifest) => manifest.screenId);
  const resolvedTitle = routeParam ? `${route.title}: ${routeParam}` : route.title;

  return (
    <ChannelShell appId="staff-terminal" eyebrow="Staff workflow" title={resolvedTitle} status="Synthetic lab · route workflow">
      <StaffWorkflowRouteNav activeHref={route.href} />

      <ChannelMetricGrid>
        <ChannelMetric label="Business reason" value={route.reasonRequired ? "required" : "standard"} detail="lookup and command audit" />
        <ChannelMetric label="Maker-checker" value={route.highRisk ? "required" : "as declared"} detail="high-risk operations only" />
        <ChannelMetric label="Structured errors" value="visible" detail="reason, auth, validation, unexpected" />
      </ChannelMetricGrid>

      <ChannelPanel
        title={route.title}
        eyebrow={route.eyebrow}
        meta={<ChannelBadge tone={route.highRisk ? "critical" : "neutral"}>{route.highRisk ? "high risk" : "controlled"}</ChannelBadge>}
      >
        <ChannelActionRow items={route.controls} />
        <div className="workflow-form-grid" aria-label="Staff workflow inputs">
          <label>
            Target
            <input readOnly value={routeParam ?? "selected from result table"} />
          </label>
          <label>
            Lookup Reason
            <input readOnly value={route.reasonRequired ? "required before API call" : "not required"} />
          </label>
          <label>
            Checker
            <input readOnly value={route.highRisk ? "independent approver required" : "not applicable"} />
          </label>
        </div>
      </ChannelPanel>

      <ChannelPanel title="Workflow State Coverage" eyebrow="reason · approval · audit">
        <StaffWorkflowStateGrid states={route.states} />
      </ChannelPanel>

      <ChannelPanel title="API And Manifest Contract" eyebrow="target stack">
        <ChannelTable>
          <thead>
            <tr>
              <th>API client path</th>
              <th>Manifest screens</th>
              <th>Audit event</th>
            </tr>
          </thead>
          <tbody>
            {route.apiMethods.map((method, index) => (
              <tr key={method}>
                <td>{method}</td>
                <td>{selectedScreenIds.join(", ") || route.screenIds.join(", ")}</td>
                <td>{selectedManifests[index % Math.max(selectedManifests.length, 1)]?.audit.eventTypes?.join(", ") ?? "manifest audit metadata"}</td>
              </tr>
            ))}
          </tbody>
        </ChannelTable>
      </ChannelPanel>

      <ChannelCardGrid density="wide">
        <ChannelCard screenId="ReasonInput" title="Reason Required Lookup" meta="audit before access">
          <ChannelDefinitionList
            items={[
              { term: "Policy", detail: route.reasonRequired ? "reason required" : "standard route" },
              { term: "Masked", detail: "PII masked by default" },
              { term: "Failure", detail: "POLICY_REASON_REQUIRED" }
            ]}
          />
        </ChannelCard>
        <ChannelCard screenId="ApprovalStatusTimeline" title="Approval Workflow" meta="maker-checker">
          <ChannelDefinitionList
            items={[
              { term: "Maker", detail: route.highRisk ? "requester cannot approve" : "not required unless manifest declares it" },
              { term: "Checker", detail: route.highRisk ? "independent checker required" : "as declared" },
              { term: "Audit", detail: "request, approve, reject, execute events" }
            ]}
          />
        </ChannelCard>
        <ChannelCard screenId="StructuredErrorPanel" title="Structured Error Surface" meta="domain envelope">
          <ChannelDefinitionList
            items={[
              { term: "Reason", detail: "POLICY_REASON_REQUIRED" },
              { term: "Authorization", detail: "AUTHORIZATION_DENIED" },
              { term: "Unexpected", detail: "UNEXPECTED_FAILURE" }
            ]}
          />
        </ChannelCard>
      </ChannelCardGrid>
    </ChannelShell>
  );
}

function selectManifests(route: StaffWorkflowRoute, manifests: readonly ScreenManifest[], routeParam?: string) {
  if (route.key === "tx" && routeParam) {
    const normalized = normalizeTransactionCode(routeParam);
    const matched = manifests.find(
      (manifest) => normalizeTransactionCode(manifest.transactionCode ?? "") === normalized || normalizeTransactionCode(manifest.screenId) === normalized
    );
    return matched ? [matched] : [];
  }

  const screenIds: readonly string[] = route.screenIds;
  return manifests.filter((manifest) => screenIds.includes(manifest.screenId));
}

function normalizeTransactionCode(value: string) {
  return value.toUpperCase().replace(/[^A-Z0-9]/g, "");
}

function StaffWorkflowRouteNav({ activeHref }: { readonly activeHref: string }) {
  return (
    <ChannelPanel title="Workflow Routes" eyebrow="route split">
      <nav className="workflow-route-nav" aria-label="Staff workflow routes">
        {staffWorkflowRouteSummaries.map((route) => (
          <a
            href={route.href.replace("[transactionCode]", "CST001").replace("[customerId]", "SYN-CUS-001").replace("[accountId]", "ACC-SYN-001-001").replace("[businessReferenceId]", "TX-SYN-CORR-001")}
            key={route.href}
            aria-current={route.href === activeHref ? "page" : undefined}
          >
            <strong>{route.title}</strong>
            <span className="workflow-route-note">{route.screenIds.join(", ")}</span>
          </a>
        ))}
      </nav>
    </ChannelPanel>
  );
}

function StaffWorkflowStateGrid({ states }: { readonly states: readonly StaffWorkflowState[] }) {
  return (
    <div className="workflow-state-grid">
      {states.map((state) => (
        <article className={`workflow-state workflow-state-${state.tone ?? "neutral"}`} key={`${state.label}-${state.value}`}>
          <span>{state.label}</span>
          <strong>{state.value}</strong>
          <small>{state.detail}</small>
        </article>
      ))}
    </div>
  );
}
