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
import { loadCustomerWebManifests } from "../lib/manifestLoader";

type WorkflowTone = "neutral" | "success" | "warning" | "critical";

type WorkflowState = {
  readonly label: string;
  readonly value: string;
  readonly detail: string;
  readonly tone?: WorkflowTone;
};

type CustomerWorkflowRoute = {
  readonly key: string;
  readonly href: string;
  readonly title: string;
  readonly eyebrow: string;
  readonly screenIds: readonly string[];
  readonly apiMethods: readonly string[];
  readonly controls: readonly string[];
  readonly states: readonly WorkflowState[];
  readonly demoSeed?: readonly string[];
};

export type CustomerWorkflowRouteKey =
  | "login"
  | "accounts"
  | "accountDetail"
  | "transferNew"
  | "transferResult"
  | "complaints"
  | "complaintDetail"
  | "cards"
  | "cardDetail"
  | "loans"
  | "payments"
  | "notifications"
  | "security";

const commonTransferStates: readonly WorkflowState[] = [
  { label: "Loading", value: "SUBMITTING", detail: "Form command is in flight." },
  { label: "Success", value: "POSTED", detail: "Balanced ledger posting completed.", tone: "success" },
  { label: "Replay", value: "REPLAYED", detail: "Duplicate idempotency key returns the first result.", tone: "success" },
  { label: "Held", value: "HELD", detail: "FDS hold blocks ledger posting until review.", tone: "warning" },
  { label: "Validation", value: "FAILED", detail: "Structured validation error is rendered.", tone: "critical" },
  { label: "Authorization", value: "BLOCKED", detail: "Ownership or step-up failure is visible.", tone: "critical" },
  { label: "Unexpected", value: "UNEXPECTED_FAILURE", detail: "Unknown failure keeps retry context.", tone: "critical" }
];

export const customerWorkflowRoutes: Record<CustomerWorkflowRouteKey, CustomerWorkflowRoute> = {
  login: {
    key: "login",
    href: "/login",
    title: "Login and Session",
    eyebrow: "AuthBoundary",
    screenIds: ["CWB-401"],
    apiMethods: ["createOidcAuthorizationUrl", "keycloak-token exchange", "customerAccessHistory"],
    controls: ["SessionBanner", "StepUpRequiredPanel", "StructuredErrorPanel"],
    states: [
      { label: "Signed out", value: "RELOGIN_REQUIRED", detail: "No active browser token.", tone: "warning" },
      { label: "Redirect", value: "OIDC_REDIRECTING", detail: "Authorization code flow started." },
      { label: "Refresh", value: "TOKEN_REFRESH_REQUIRED", detail: "Expired token asks for login restart.", tone: "warning" },
      { label: "Step-up", value: "STEP_UP_REQUIRED", detail: "MFA/WebAuthn challenge is surfaced.", tone: "critical" },
      { label: "Authorized", value: "CUSTOMER_SESSION_ACTIVE", detail: "Customer claim supplies customerId.", tone: "success" }
    ]
  },
  accounts: {
    key: "accounts",
    href: "/accounts",
    title: "Accounts",
    eyebrow: "AccountSelector",
    screenIds: ["CWB-101"],
    apiMethods: ["customerAccountDetail"],
    controls: ["AccountSelector", "AuditReferencePanel", "StructuredErrorPanel"],
    states: [
      { label: "Loading", value: "ACCOUNTS_LOADING", detail: "Account candidates are loading." },
      { label: "Loaded", value: "MASKED_ACCOUNTS_LOADED", detail: "Masked account aliases are selectable.", tone: "success" },
      { label: "Authorization", value: "CUSTOMER_OWNERSHIP_DENIED", detail: "Wrong customer claim is blocked.", tone: "critical" },
      { label: "Unexpected", value: "ACCOUNT_READ_FAILED", detail: "Unexpected read failure is visible.", tone: "critical" }
    ],
    demoSeed: ["synthetic demo customer", "masked account aliases only"]
  },
  accountDetail: {
    key: "account-detail",
    href: "/accounts/[accountId]",
    title: "Account Detail",
    eyebrow: "Account detail",
    screenIds: ["CWB-102", "CWB-103"],
    apiMethods: ["customerAccountDetail", "customerTransactions", "balanceCertificate", "transactionConfirmation"],
    controls: ["AccountSelector", "AuditReferencePanel", "StructuredErrorPanel"],
    states: [
      { label: "Loading", value: "ACCOUNT_DETAIL_LOADING", detail: "Detail and transaction tabs are loading." },
      { label: "Loaded", value: "MASKED_DETAIL_LOADED", detail: "Masked account and balance projection loaded.", tone: "success" },
      { label: "Audit", value: "AUDIT_REFERENCE_CAPTURED", detail: "Statement/certificate references are visible.", tone: "success" },
      { label: "Authorization", value: "CUSTOMER_OWNERSHIP_DENIED", detail: "Cross-customer account lookup is blocked.", tone: "critical" }
    ],
    demoSeed: ["route parameter supplies accountId"]
  },
  transferNew: {
    key: "transfer-new",
    href: "/transfers/new",
    title: "New Transfer",
    eyebrow: "Command Template",
    screenIds: ["CWB-201"],
    apiMethods: ["requestCustomerTransfer"],
    controls: ["MoneyInput", "AccountSelector", "IdempotencyResultPanel", "FdsHoldStatusPanel", "StructuredErrorPanel"],
    states: commonTransferStates,
    demoSeed: ["synthetic demo source account only when no API session is configured"]
  },
  transferResult: {
    key: "transfer-result",
    href: "/transfers/[resultId]",
    title: "Transfer Result",
    eyebrow: "Result state",
    screenIds: ["CWB-202", "CWB-203"],
    apiMethods: ["customerTransfers"],
    controls: ["IdempotencyResultPanel", "FdsHoldStatusPanel", "StructuredErrorPanel"],
    states: commonTransferStates,
    demoSeed: ["route parameter supplies resultId"]
  },
  complaints: {
    key: "complaints",
    href: "/complaints",
    title: "Complaint Entry",
    eyebrow: "Case Template",
    screenIds: ["CWB-301", "CWB-302"],
    apiMethods: ["complaintTypeGuide", "requestCustomerComplaint", "customerComplaints"],
    controls: ["StructuredErrorPanel", "AuditReferencePanel"],
    states: [
      { label: "Loading", value: "COMPLAINT_TYPES_LOADING", detail: "Complaint type guide is loading." },
      { label: "Submitted", value: "RECEIVED", detail: "Case timeline starts with customer submission.", tone: "success" },
      { label: "Validation", value: "REQUEST_VALIDATION_FAILED", detail: "Missing subject/detail is structured.", tone: "critical" },
      { label: "Unexpected", value: "COMPLAINT_SUBMIT_FAILED", detail: "Failure keeps form context.", tone: "critical" }
    ],
    demoSeed: ["synthetic source references only"]
  },
  complaintDetail: {
    key: "complaint-detail",
    href: "/complaints/[caseId]",
    title: "Complaint Detail",
    eyebrow: "Case timeline",
    screenIds: ["CWB-302", "CWB-303"],
    apiMethods: ["customerComplaints", "confirmCustomerComplaint", "submitCustomerComplaintMaterial", "reopenCustomerComplaint"],
    controls: ["ApprovalStatusTimeline", "AuditReferencePanel", "StructuredErrorPanel"],
    states: [
      { label: "Open", value: "RECEIVED", detail: "Case is visible with SLA and owner." },
      { label: "Waiting", value: "WAITING_APPROVAL", detail: "Staff answer is under checker review.", tone: "warning" },
      { label: "Confirmed", value: "CLOSED", detail: "Customer confirmation closes the case.", tone: "success" },
      { label: "Authorization", value: "CUSTOMER_OWNERSHIP_DENIED", detail: "Other-customer case access is blocked.", tone: "critical" }
    ],
    demoSeed: ["route parameter supplies caseId"]
  },
  cards: {
    key: "cards",
    href: "/cards",
    title: "Cards",
    eyebrow: "Card workflow",
    screenIds: ["CWB-601", "CWB-602", "CWB-603", "CWB-604", "CWB-605", "CWB-606"],
    apiMethods: ["issueCard", "simulate3ds", "authorizeCard", "captureCard", "reportLostCard"],
    controls: ["StructuredErrorPanel", "AuditReferencePanel"],
    states: [
      { label: "Issued", value: "ACTIVE", detail: "Synthetic card lifecycle state.", tone: "success" },
      { label: "Challenge", value: "THREEDS_REQUIRED", detail: "3DS simulator challenge visible.", tone: "warning" },
      { label: "Blocked", value: "LOST_REPORTED", detail: "Lost-card block state is visible.", tone: "critical" },
      { label: "Unexpected", value: "CARD_COMMAND_FAILED", detail: "Failure panel keeps retry context.", tone: "critical" }
    ],
    demoSeed: ["synthetic cards only"]
  },
  cardDetail: {
    key: "card-detail",
    href: "/cards/[cardId]",
    title: "Card Detail",
    eyebrow: "Card detail",
    screenIds: ["CWB-602", "CWB-603", "CWB-604", "CWB-606"],
    apiMethods: ["authorizeCard", "captureCard", "cancelCardAuthorization", "reportLostCard"],
    controls: ["AuditReferencePanel", "StructuredErrorPanel"],
    states: [
      { label: "Loading", value: "CARD_DETAIL_LOADING", detail: "Card route state is loading." },
      { label: "Authorized", value: "AUTHORIZED", detail: "Authorization hold is visible.", tone: "success" },
      { label: "Captured", value: "CAPTURED", detail: "Balanced capture posting is visible.", tone: "success" },
      { label: "Blocked", value: "CARD_BLOCKED", detail: "Loss or authorization failure is visible.", tone: "critical" }
    ],
    demoSeed: ["route parameter supplies cardId"]
  },
  loans: {
    key: "loans",
    href: "/loans",
    title: "Loans",
    eyebrow: "Loan workflow",
    screenIds: ["CWB-501", "CWB-502", "CWB-503", "CWB-504"],
    apiMethods: ["requestLoanApplication", "loanDetail", "repayLoan", "prepayLoan"],
    controls: ["MoneyInput", "ApprovalStatusTimeline", "StructuredErrorPanel"],
    states: [
      { label: "Submitted", value: "APPLICATION_SUBMITTED", detail: "Application awaits review.", tone: "warning" },
      { label: "Executed", value: "LOAN_EXECUTED", detail: "Approved loan is posted with ledger entries.", tone: "success" },
      { label: "Repayment", value: "REPAYMENT_POSTED", detail: "Repayment command is visible.", tone: "success" },
      { label: "Validation", value: "LOAN_VALIDATION_FAILED", detail: "Invalid repayment/prepayment is structured.", tone: "critical" }
    ],
    demoSeed: ["synthetic loan products only"]
  },
  payments: {
    key: "payments",
    href: "/payments",
    title: "Payments",
    eyebrow: "Payment workflow",
    screenIds: ["CWB-701", "CWB-702", "CWB-703"],
    apiMethods: ["createPaymentInstruction", "getPaymentInstruction", "createAutopayAgreement"],
    controls: ["MoneyInput", "IdempotencyResultPanel", "StructuredErrorPanel"],
    states: [
      { label: "Created", value: "PAYMENT_CREATED", detail: "Instruction persisted before settlement.", tone: "success" },
      { label: "Replay", value: "PAYMENT_REPLAYED", detail: "Duplicate idempotency key returns original instruction.", tone: "success" },
      { label: "Held", value: "PAYMENT_PENDING_REVIEW", detail: "Pending settlement is visible.", tone: "warning" },
      { label: "Failed", value: "PAYMENT_FAILED", detail: "Structured payment failure is visible.", tone: "critical" }
    ],
    demoSeed: ["synthetic billers and autopay only"]
  },
  notifications: {
    key: "notifications",
    href: "/notifications",
    title: "Notifications",
    eyebrow: "Preference workflow",
    screenIds: ["CWB-801", "CWB-802"],
    apiMethods: ["listCustomerNotificationPreferences", "upsertCustomerNotificationPreference", "listCustomerNotificationDeliveries"],
    controls: ["StructuredErrorPanel", "AuditReferencePanel"],
    states: [
      { label: "Loaded", value: "PREFERENCES_LOADED", detail: "Masked channel preferences are visible.", tone: "success" },
      { label: "Updated", value: "PREFERENCE_UPDATED", detail: "Self-service preference change is audited.", tone: "success" },
      { label: "History", value: "DELIVERY_HISTORY_MASKED", detail: "Delivery messages remain masked.", tone: "success" },
      { label: "Authorization", value: "CUSTOMER_OWNERSHIP_DENIED", detail: "Cross-customer history is blocked.", tone: "critical" }
    ],
    demoSeed: ["synthetic notification endpoints only"]
  },
  security: {
    key: "security",
    href: "/security",
    title: "Security",
    eyebrow: "Access history",
    screenIds: ["CWB-401"],
    apiMethods: ["customerAccessHistory"],
    controls: ["SessionBanner", "AuditReferencePanel", "StepUpRequiredPanel"],
    states: [
      { label: "Loaded", value: "ACCESS_HISTORY_LOADED", detail: "Customer-owned access history is visible.", tone: "success" },
      { label: "Step-up", value: "STEP_UP_REQUIRED", detail: "Sensitive operation needs stronger auth.", tone: "warning" },
      { label: "Expired", value: "TOKEN_REFRESH_REQUIRED", detail: "Expired session asks for relogin.", tone: "warning" },
      { label: "Denied", value: "AUTHORIZATION_DENIED", detail: "Structured authorization failure is visible.", tone: "critical" }
    ],
    demoSeed: ["synthetic access history only"]
  }
};

export const customerWorkflowRouteSummaries = Object.values(customerWorkflowRoutes).map((route) => ({
  href: route.href,
  title: route.title,
  screenIds: route.screenIds
}));

export async function CustomerWorkflowRoutePage({
  routeKey,
  routeParam
}: {
  readonly routeKey: CustomerWorkflowRouteKey;
  readonly routeParam?: string;
}) {
  const route = customerWorkflowRoutes[routeKey];
  const manifests = await loadCustomerWebManifests();
  const selectedManifests = manifests.filter((manifest) => route.screenIds.includes(manifest.screenId));
  const resolvedTitle = routeParam ? `${route.title}: ${routeParam}` : route.title;

  return (
    <ChannelShell appId="customer-web" eyebrow="Customer workflow" title={resolvedTitle} status="Synthetic lab · route workflow">
      <WorkflowRouteNav activeHref={route.href} />

      <ChannelMetricGrid>
        <ChannelMetric label="Session source" value="token claim" detail="customerId from OIDC or simulator claim" />
        <ChannelMetric label="Structured errors" value="visible" detail="validation, auth, unexpected" />
        <ChannelMetric label="Demo seed" value={route.demoSeed ? "labelled" : "not required"} detail="synthetic-only boundary" />
      </ChannelMetricGrid>

      <ChannelPanel
        title={route.title}
        eyebrow={route.eyebrow}
        meta={<ChannelBadge tone={route.demoSeed ? "critical" : "neutral"}>{route.demoSeed ? "synthetic demo fallback" : "session sourced"}</ChannelBadge>}
      >
        <ChannelActionRow items={route.controls} />
        <div className="workflow-form-grid" aria-label="Route workflow inputs">
          <label>
            Customer ID
            <input readOnly value="from token claim" />
          </label>
          <label>
            Route parameter
            <input readOnly value={routeParam ?? "selected from API result"} />
          </label>
          <label>
            Idempotency key
            <input readOnly value={route.key.includes("transfer") || route.key === "payments" ? "generated per command" : "not applicable"} />
          </label>
        </div>
      </ChannelPanel>

      <ChannelPanel title="Workflow State Coverage" eyebrow="Loading · success · replay · blocked">
        <WorkflowStateGrid states={route.states} />
      </ChannelPanel>

      <ChannelPanel title="API And Manifest Contract" eyebrow="Target stack">
        <ChannelTable>
          <thead>
            <tr>
              <th>API client path</th>
              <th>Manifest screens</th>
              <th>Control</th>
            </tr>
          </thead>
          <tbody>
            {route.apiMethods.map((method, index) => (
              <tr key={method}>
                <td>{method}</td>
                <td>{route.screenIds.join(", ")}</td>
                <td>{route.controls[index % route.controls.length]}</td>
              </tr>
            ))}
          </tbody>
        </ChannelTable>
      </ChannelPanel>

      <ChannelCardGrid density="wide">
        <ChannelCard screenId="SessionBanner" title="Session State" meta="token lifecycle">
          <ChannelDefinitionList
            items={[
              { term: "Customer source", detail: "OIDC customerId claim or labelled synthetic demo seed" },
              { term: "Refresh", detail: "TOKEN_REFRESH_REQUIRED" },
              { term: "Step-up", detail: "STEP_UP_REQUIRED" }
            ]}
          />
        </ChannelCard>
        <ChannelCard screenId="StructuredErrorPanel" title="Structured Error" meta="domain envelope">
          <ChannelDefinitionList
            items={[
              { term: "Validation", detail: "REQUEST_VALIDATION_FAILED" },
              { term: "Authorization", detail: "AUTHORIZATION_DENIED" },
              { term: "Unexpected", detail: "UNEXPECTED_FAILURE" }
            ]}
          />
        </ChannelCard>
        <ChannelCard screenId="AuditReferencePanel" title="Audit Reference" meta="masked by default">
          <ChannelDefinitionList
            items={[
              { term: "PII", detail: "masked by default" },
              { term: "Evidence", detail: selectedManifests.map((manifest) => manifest.audit.eventTypes?.join(", ") ?? manifest.audit.maskingPolicy).join(" · ") || "manifest audit metadata" },
              { term: "Synthetic", detail: route.demoSeed?.join(" · ") ?? "no demo seed required" }
            ]}
          />
        </ChannelCard>
      </ChannelCardGrid>
    </ChannelShell>
  );
}

function WorkflowRouteNav({ activeHref }: { readonly activeHref: string }) {
  return (
    <ChannelPanel title="Workflow Routes" eyebrow="route split">
      <nav className="workflow-route-nav" aria-label="Customer workflow routes">
        {customerWorkflowRouteSummaries.map((route) => (
          <a href={route.href.replace("[accountId]", "ACC-SELECTED").replace("[resultId]", "TRF-RESULT").replace("[caseId]", "CMP-CASE").replace("[cardId]", "CARD-SELECTED")} key={route.href} aria-current={route.href === activeHref ? "page" : undefined}>
            <strong>{route.title}</strong>
            <span className="workflow-route-note">{route.screenIds.join(", ")}</span>
          </a>
        ))}
      </nav>
    </ChannelPanel>
  );
}

function WorkflowStateGrid({ states }: { readonly states: readonly WorkflowState[] }) {
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
