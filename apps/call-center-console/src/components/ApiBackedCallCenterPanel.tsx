"use client";

import { useEffect, useState } from "react";
import {
  createBankingApiClient,
  type CallCenterCustomerSummaryDto,
  type CallCenterInteractionDto
} from "@banking-lab/api-client";
import {
  createOidcAuthorizationUrl,
  createPkcePair,
  createSimulatorBearerToken
} from "@banking-lab/auth-client";

type SearchState =
  | { readonly status: "offline" }
  | { readonly status: "loading" }
  | { readonly status: "loaded"; readonly auditEventId: string; readonly customer: CallCenterCustomerSummaryDto }
  | { readonly status: "empty"; readonly auditEventId: string }
  | { readonly status: "failed"; readonly message: string };

type WorkflowState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | {
      readonly status: "completed";
      readonly interaction: CallCenterInteractionDto;
      readonly noteRedactionApplied: boolean;
      readonly notePiiPatternCount: number;
      readonly aftercallTaskStatus: string;
      readonly escalationType: string;
      readonly complaintCaseId: string;
      readonly historyCount: number;
    }
  | { readonly status: "failed"; readonly message: string };

type OidcIntent = "agent" | "manager";

type KeycloakLoginState =
  | { readonly status: "offline" }
  | { readonly status: "idle" }
  | { readonly status: "redirecting" }
  | { readonly status: "exchanging" }
  | {
      readonly status: "loaded";
      readonly subject: string;
      readonly tokenType: string;
      readonly bearerToken: string;
      readonly auditEventId?: string;
      readonly customer?: CallCenterCustomerSummaryDto;
    }
  | { readonly status: "failed"; readonly message: string };

const apiBaseUrl = process.env.NEXT_PUBLIC_BANKING_API_BASE_URL ?? "";
const keycloakBaseUrl = process.env.NEXT_PUBLIC_BANKING_KEYCLOAK_BASE_URL ?? "";
const searchReason = "Browser CALL-101 synthetic customer search smoke";
const workflowReason = "Browser CALL-102 through CALL-106 synthetic workflow smoke";
const keycloakAgentSearchReason = "Browser CALL-101 live Keycloak agent search smoke";
const keycloakManagerSearchReason = "Browser CALL-101 live Keycloak manager authorization smoke";
const oidcStateKey = "bankingLab.callCenter.oidcState";
const oidcVerifierKey = "bankingLab.callCenter.oidcCodeVerifier";
const oidcRedirectKey = "bankingLab.callCenter.oidcRedirectUri";
const oidcIntentKey = "bankingLab.callCenter.oidcIntent";

export function ApiBackedCallCenterPanel() {
  const [searchState, setSearchState] = useState<SearchState>(() => (apiBaseUrl ? { status: "loading" } : { status: "offline" }));
  const [workflowState, setWorkflowState] = useState<WorkflowState>({ status: "idle" });
  const [keycloakAgentState, setKeycloakAgentState] = useState<KeycloakLoginState>(() =>
    apiBaseUrl && keycloakBaseUrl ? { status: "idle" } : { status: "offline" }
  );
  const [keycloakManagerState, setKeycloakManagerState] = useState<KeycloakLoginState>(() =>
    apiBaseUrl && keycloakBaseUrl ? { status: "idle" } : { status: "offline" }
  );
  const [keycloakWorkflowState, setKeycloakWorkflowState] = useState<WorkflowState>({ status: "idle" });

  const setKeycloakLoginState = (intent: string | null, state: KeycloakLoginState) => {
    if (intent === "manager") {
      setKeycloakManagerState(state);
      return;
    }
    setKeycloakAgentState(state);
  };

  useEffect(() => {
    if (!apiBaseUrl) {
      return;
    }
    let cancelled = false;

    callCenterAgentClient()
      .searchCallCenterCustomers("SYN-CUS", searchReason)
      .then((response) => {
        if (cancelled) {
          return;
        }
        const customer = response.items[0];
        setSearchState(customer ? { status: "loaded", auditEventId: response.auditEventId, customer } : { status: "empty", auditEventId: response.auditEventId });
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setSearchState({ status: "failed", message: error instanceof Error ? error.message : "Unknown call-center API failure" });
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!apiBaseUrl || !keycloakBaseUrl) {
      return;
    }
    const params = new URLSearchParams(window.location.search);
    const code = params.get("code");
    const returnedState = params.get("state");
    if (!code || !returnedState) {
      return;
    }
    const expectedState = window.sessionStorage.getItem(oidcStateKey);
    const codeVerifier = window.sessionStorage.getItem(oidcVerifierKey);
    const redirectUri = window.sessionStorage.getItem(oidcRedirectKey) ?? `${window.location.origin}${window.location.pathname}`;
    const intent = window.sessionStorage.getItem(oidcIntentKey);
    if (!expectedState || !codeVerifier || returnedState !== expectedState || !isOidcIntent(intent)) {
      setKeycloakLoginState(intent, { status: "failed", message: "Keycloak state verification failed" });
      clearOidcSession();
      return;
    }

    let cancelled = false;
    setKeycloakLoginState(intent, { status: "exchanging" });
    fetch("/api/auth/keycloak-token", {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        code,
        codeVerifier,
        redirectUri
      })
    })
      .then(async (response) => {
        const body = await response.json() as {
          accessToken?: string;
          tokenType?: string;
          error?: { message?: string };
        };
        if (!response.ok || !body.accessToken) {
          throw new Error(body.error?.message ?? "Keycloak token exchange failed");
        }
        const tokenType = body.tokenType ?? "Bearer";
        const bearerToken = `${tokenType} ${body.accessToken}`;
        const client = createBankingApiClient({ baseUrl: apiBaseUrl, bearerToken });
        const responseReason = intent === "agent" ? keycloakAgentSearchReason : keycloakManagerSearchReason;
        const search = await client.searchCallCenterCustomers("SYN-CUS", responseReason);
        const customer = search.items[0];
        if (!customer) {
          throw new Error("No synthetic call-center customer returned for Keycloak smoke");
        }
        if (!cancelled) {
          setKeycloakLoginState(intent, {
            status: "loaded",
            subject: intent === "agent" ? "call-agent01" : "call-manager01",
            tokenType,
            bearerToken,
            auditEventId: search.auditEventId,
            customer
          });
          clearOidcSession();
          window.history.replaceState(null, "", window.location.pathname);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setKeycloakLoginState(intent, { status: "failed", message: error instanceof Error ? error.message : "Unknown Keycloak call-center failure" });
          clearOidcSession();
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  async function runKeycloakLoginSmoke(intent: OidcIntent) {
    if (!apiBaseUrl || !keycloakBaseUrl) {
      return;
    }
    const currentState = intent === "agent" ? keycloakAgentState : keycloakManagerState;
    if (currentState.status === "redirecting" || currentState.status === "exchanging") {
      return;
    }
    setKeycloakLoginState(intent, { status: "redirecting" });
    try {
      const redirectUri = `${window.location.origin}${window.location.pathname}`;
      const stateValue = globalThis.crypto.randomUUID();
      const pkce = await createPkcePair();
      window.sessionStorage.setItem(oidcStateKey, stateValue);
      window.sessionStorage.setItem(oidcVerifierKey, pkce.codeVerifier);
      window.sessionStorage.setItem(oidcRedirectKey, redirectUri);
      window.sessionStorage.setItem(oidcIntentKey, intent);
      window.location.assign(
        createOidcAuthorizationUrl({
          issuerBaseUrl: keycloakBaseUrl,
          realm: "banking-lab",
          clientId: "call-center-console",
          redirectUri,
          state: stateValue,
          codeChallenge: pkce.codeChallenge,
          prompt: intent === "manager" ? "login" : undefined,
          loginHint: intent === "agent" ? "call-agent01" : "call-manager01"
        })
      );
    } catch (error: unknown) {
      clearOidcSession();
      setKeycloakLoginState(intent, { status: "failed", message: error instanceof Error ? error.message : "Unknown Keycloak redirect failure" });
    }
  }

  const runWorkflowSmoke = async () => {
    if (!apiBaseUrl || searchState.status !== "loaded") {
      return;
    }
    setWorkflowState({ status: "running" });
    try {
      const agentClient = callCenterAgentClient();
      const managerClient = callCenterManagerClient();
      const customerId = searchState.customer.customerId;
      const started = await agentClient.startCallCenterInteraction({
        customerId,
        channel: "PHONE",
        contactReasonCode: "BALANCE_INQUIRY",
        requestedBy: "call-agent01",
        requestedByRole: "CALL_CENTER_AGENT",
        assignedTo: "call-agent01",
        reason: workflowReason,
        metadata: {
          syntheticOnly: true,
          screenPath: "CALL-102"
        }
      });
      const note = await agentClient.addCallCenterNote(started.item.interactionId, {
        requestedBy: "call-agent01",
        requestedByRole: "CALL_CENTER_AGENT",
        reason: "Browser CALL-103 redacted synthetic note smoke",
        noteBody: "Synthetic lab note mentions 010-1111-2222 and lab-only account-like 123456789012."
      });
      const aftercall = await agentClient.createCallCenterAftercallTask(started.item.interactionId, {
        requestedBy: "call-agent01",
        requestedByRole: "CALL_CENTER_AGENT",
        reason: "Browser CALL-104 synthetic aftercall task smoke",
        taskType: "FOLLOW_UP",
        assignedTo: "call-agent02",
        metadata: {
          syntheticOnly: true
        }
      });
      const escalation = await managerClient.escalateCallCenterInteraction(started.item.interactionId, {
        requestedBy: "call-manager01",
        requestedByRole: "CALL_CENTER_MANAGER",
        reason: "Browser CALL-106 synthetic complaint escalation smoke",
        escalationType: "COMPLAINT",
        complaintCategory: "ACCOUNT_ACCESS",
        complaintDescription: "Synthetic complaint converted from call-center browser smoke with 010-2222-3333 redacted.",
        metadata: {
          syntheticOnly: true
        }
      });
      await agentClient.closeCallCenterInteraction(started.item.interactionId, {
        requestedBy: "call-agent01",
        requestedByRole: "CALL_CENTER_AGENT",
        reason: "Browser CALL-102 synthetic close smoke"
      });
      const history = await agentClient.callCenterCustomerHistory(customerId, "Browser CALL-105 synthetic history smoke");
      const finalInteraction = await agentClient.callCenterInteraction(started.item.interactionId, "Browser CALL-102 synthetic detail smoke");

      setWorkflowState({
        status: "completed",
        interaction: finalInteraction.item,
        noteRedactionApplied: note.note.redactionApplied,
        notePiiPatternCount: note.note.piiPatternCount,
        aftercallTaskStatus: aftercall.task.status,
        escalationType: escalation.escalation.escalationType,
        complaintCaseId: escalation.escalation.complaintCaseId ?? "none",
        historyCount: history.items.length
      });
    } catch (error: unknown) {
      setWorkflowState({ status: "failed", message: error instanceof Error ? error.message : "Unknown call-center workflow failure" });
    }
  };

  const runKeycloakWorkflowSmoke = async () => {
    if (
      !apiBaseUrl ||
      keycloakAgentState.status !== "loaded" ||
      keycloakManagerState.status !== "loaded" ||
      keycloakWorkflowState.status === "running"
    ) {
      return;
    }
    setKeycloakWorkflowState({ status: "running" });
    try {
      const agentClient = callCenterAgentClient(keycloakAgentState.bearerToken);
      const managerClient = callCenterManagerClient(keycloakManagerState.bearerToken);
      const customerId = keycloakAgentState.customer?.customerId ?? (searchState.status === "loaded" ? searchState.customer.customerId : "SYN-CUS-001");
      const started = await agentClient.startCallCenterInteraction({
        customerId,
        channel: "PHONE",
        contactReasonCode: "BALANCE_INQUIRY",
        requestedBy: "call-agent01",
        requestedByRole: "CALL_CENTER_AGENT",
        assignedTo: "call-agent01",
        reason: "Browser CALL-102 live Keycloak workflow smoke",
        metadata: {
          syntheticOnly: true,
          screenPath: "CALL-102",
          authPath: "keycloak"
        }
      });
      const note = await agentClient.addCallCenterNote(started.item.interactionId, {
        requestedBy: "call-agent01",
        requestedByRole: "CALL_CENTER_AGENT",
        reason: "Browser CALL-103 live Keycloak redacted note smoke",
        noteBody: "Synthetic Keycloak call-center note mentions 010-3333-4444 and account-like 987654321098."
      });
      const aftercall = await agentClient.createCallCenterAftercallTask(started.item.interactionId, {
        requestedBy: "call-agent01",
        requestedByRole: "CALL_CENTER_AGENT",
        reason: "Browser CALL-104 live Keycloak aftercall smoke",
        taskType: "FOLLOW_UP",
        assignedTo: "call-agent02",
        metadata: {
          syntheticOnly: true,
          authPath: "keycloak"
        }
      });
      const escalation = await managerClient.escalateCallCenterInteraction(started.item.interactionId, {
        requestedBy: "call-manager01",
        requestedByRole: "CALL_CENTER_MANAGER",
        reason: "Browser CALL-106 live Keycloak complaint escalation smoke",
        escalationType: "COMPLAINT",
        complaintCategory: "ACCOUNT_ACCESS",
        complaintDescription: "Synthetic Keycloak complaint converted from call-center smoke with 010-4444-5555 redacted.",
        metadata: {
          syntheticOnly: true,
          authPath: "keycloak"
        }
      });
      await agentClient.closeCallCenterInteraction(started.item.interactionId, {
        requestedBy: "call-agent01",
        requestedByRole: "CALL_CENTER_AGENT",
        reason: "Browser CALL-102 live Keycloak close smoke"
      });
      const history = await agentClient.callCenterCustomerHistory(customerId, "Browser CALL-105 live Keycloak history smoke");
      const finalInteraction = await agentClient.callCenterInteraction(started.item.interactionId, "Browser CALL-102 live Keycloak detail smoke");

      setKeycloakWorkflowState({
        status: "completed",
        interaction: finalInteraction.item,
        noteRedactionApplied: note.note.redactionApplied,
        notePiiPatternCount: note.note.piiPatternCount,
        aftercallTaskStatus: aftercall.task.status,
        escalationType: escalation.escalation.escalationType,
        complaintCaseId: escalation.escalation.complaintCaseId ?? "none",
        historyCount: history.items.length
      });
    } catch (error: unknown) {
      setKeycloakWorkflowState({ status: "failed", message: error instanceof Error ? error.message : "Unknown Keycloak call-center workflow failure" });
    }
  };

  return (
    <section className="api-panel" aria-label="API-backed call-center workflow">
      <h2>Spring API call-center smoke</h2>
      <dl data-testid="api-backed-call-center-search">
        <div>
          <dt>Search state</dt>
          <dd>{searchStatusText(searchState)}</dd>
        </div>
        <div>
          <dt>Customer</dt>
          <dd>{searchState.status === "loaded" ? searchState.customer.customerId : "none"}</dd>
        </div>
        <div>
          <dt>PII policy</dt>
          <dd>masked by default</dd>
        </div>
      </dl>

      <div className="api-actions" data-testid="api-backed-call-center-workflow">
        <button type="button" onClick={runWorkflowSmoke} disabled={searchState.status !== "loaded" || workflowState.status === "running"}>
          Run call-center workflow smoke
        </button>
        <dl>
          <div>
            <dt>Workflow state</dt>
            <dd>{workflowStatusText(workflowState)}</dd>
          </div>
          <div>
            <dt>Interaction</dt>
            <dd>{workflowState.status === "completed" ? workflowState.interaction.interactionId : "not run"}</dd>
          </div>
          <div>
            <dt>Final status</dt>
            <dd>{workflowState.status === "completed" ? workflowState.interaction.status : "not run"}</dd>
          </div>
          <div>
            <dt>Redaction</dt>
            <dd>
              {workflowState.status === "completed"
                ? `${workflowState.noteRedactionApplied ? "applied" : "not applied"} / ${workflowState.notePiiPatternCount} patterns`
                : "not run"}
            </dd>
          </div>
          <div>
            <dt>Escalation</dt>
            <dd>{workflowState.status === "completed" ? `${workflowState.escalationType} ${workflowState.complaintCaseId}` : "not run"}</dd>
          </div>
          <div>
            <dt>History count</dt>
            <dd>{workflowState.status === "completed" ? workflowState.historyCount : "not run"}</dd>
          </div>
        </dl>
      </div>

      <div className="api-actions" data-testid="api-backed-call-center-keycloak-login">
        <button
          type="button"
          onClick={() => void runKeycloakLoginSmoke("agent")}
          disabled={!apiBaseUrl || !keycloakBaseUrl || keycloakAgentState.status === "redirecting" || keycloakAgentState.status === "exchanging"}
        >
          Sign in call-center agent with Keycloak
        </button>
        <button
          type="button"
          onClick={() => void runKeycloakLoginSmoke("manager")}
          disabled={!apiBaseUrl || !keycloakBaseUrl || keycloakManagerState.status === "redirecting" || keycloakManagerState.status === "exchanging"}
        >
          Sign in call-center manager with Keycloak
        </button>
        <button
          type="button"
          onClick={() => void runKeycloakWorkflowSmoke()}
          disabled={
            keycloakAgentState.status !== "loaded" ||
            keycloakManagerState.status !== "loaded" ||
            keycloakWorkflowState.status === "running"
          }
        >
          Run Keycloak call-center workflow smoke
        </button>
        <dl>
          <div>
            <dt>Agent token</dt>
            <dd>{keycloakLoginLabel(keycloakAgentState, "agent")}</dd>
          </div>
          <div>
            <dt>Manager token</dt>
            <dd>{keycloakLoginLabel(keycloakManagerState, "manager")}</dd>
          </div>
          <div>
            <dt>Keycloak workflow</dt>
            <dd>{keycloakWorkflowStatusText(keycloakWorkflowState)}</dd>
          </div>
          <div>
            <dt>Keycloak interaction</dt>
            <dd>{keycloakWorkflowState.status === "completed" ? keycloakWorkflowState.interaction.interactionId : "not run"}</dd>
          </div>
          <div>
            <dt>Keycloak final status</dt>
            <dd>{keycloakWorkflowState.status === "completed" ? keycloakWorkflowState.interaction.status : "not run"}</dd>
          </div>
          <div>
            <dt>Keycloak redaction</dt>
            <dd>
              {keycloakWorkflowState.status === "completed"
                ? `${keycloakWorkflowState.noteRedactionApplied ? "applied" : "not applied"} / ${keycloakWorkflowState.notePiiPatternCount} patterns`
                : "not run"}
            </dd>
          </div>
          <div>
            <dt>Keycloak escalation</dt>
            <dd>
              {keycloakWorkflowState.status === "completed"
                ? `${keycloakWorkflowState.escalationType} ${keycloakWorkflowState.complaintCaseId}`
                : "not run"}
            </dd>
          </div>
        </dl>
      </div>
    </section>
  );
}

function callCenterAgentClient(bearerToken?: string) {
  return createBankingApiClient({
    baseUrl: apiBaseUrl,
    bearerToken: bearerToken ?? createSimulatorBearerToken({
      subject: "call-agent01",
      roles: ["CALL_CENTER_AGENT"]
    })
  });
}

function callCenterManagerClient(bearerToken?: string) {
  return createBankingApiClient({
    baseUrl: apiBaseUrl,
    bearerToken: bearerToken ?? createSimulatorBearerToken({
      subject: "call-manager01",
      roles: ["CALL_CENTER_MANAGER"]
    })
  });
}

function searchStatusText(state: SearchState): string {
  switch (state.status) {
    case "offline":
      return "offline - configure NEXT_PUBLIC_BANKING_API_BASE_URL";
    case "loading":
      return "loading";
    case "loaded":
      return "customers loaded";
    case "empty":
      return "no synthetic customers returned";
    case "failed":
      return state.message;
  }
}

function workflowStatusText(state: WorkflowState): string {
  switch (state.status) {
    case "idle":
      return "not run";
    case "running":
      return "running";
    case "completed":
      return "call-center workflow completed";
    case "failed":
      return state.message;
  }
}

function keycloakLoginLabel(state: KeycloakLoginState, actor: OidcIntent): string {
  switch (state.status) {
    case "offline":
      return "offline - configure API and Keycloak URLs";
    case "idle":
      return "not signed in";
    case "redirecting":
      return `redirecting ${actor} to Keycloak`;
    case "exchanging":
      return `exchanging ${actor} authorization code`;
    case "loaded":
      return actor === "agent"
        ? `Keycloak call-center agent loaded (${state.tokenType}) ${state.customer?.customerId ?? "no customer"}`
        : `Keycloak call-center manager loaded (${state.tokenType}) ${state.subject}`;
    case "failed":
      return state.message;
  }
}

function keycloakWorkflowStatusText(state: WorkflowState): string {
  switch (state.status) {
    case "idle":
      return "not run";
    case "running":
      return "running";
    case "completed":
      return "Keycloak call-center workflow completed";
    case "failed":
      return state.message;
  }
}

function isOidcIntent(value: string | null): value is OidcIntent {
  return value === "agent" || value === "manager";
}

function clearOidcSession() {
  window.sessionStorage.removeItem(oidcStateKey);
  window.sessionStorage.removeItem(oidcVerifierKey);
  window.sessionStorage.removeItem(oidcRedirectKey);
  window.sessionStorage.removeItem(oidcIntentKey);
}
