"use client";

import { useEffect, useState } from "react";
import { BankingApiError, createBankingApiClient, type ComplaintCaseDto, type ComplaintTypeGuideDto } from "@banking-lab/api-client";
import { createOidcAuthorizationUrl, createPkcePair, createSimulatorBearerToken } from "@banking-lab/auth-client";

type ApiState =
  | { readonly status: "offline" }
  | { readonly status: "loading" }
  | { readonly status: "loaded"; readonly complaint: ComplaintCaseDto }
  | { readonly status: "failed"; readonly message: string };

type CommandState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | { readonly status: "approved"; readonly approvalId: string; readonly complaint: ComplaintCaseDto }
  | { readonly status: "failed"; readonly message: string };

type WorkflowFailureState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | { readonly status: "rejected"; readonly error: StructuredErrorSummary }
  | { readonly status: "failed"; readonly message: string };

type TypeGuideState =
  | { readonly status: "offline" }
  | { readonly status: "loading" }
  | { readonly status: "loaded"; readonly items: readonly ComplaintTypeGuideDto[] }
  | { readonly status: "failed"; readonly message: string };

type MaterialSmokeState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | { readonly status: "submitted"; readonly materialId: string; readonly caseId: string; readonly workflow: string }
  | { readonly status: "failed"; readonly message: string };

type ReopenSmokeState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | { readonly status: "reopened"; readonly reopenRequestId: string; readonly caseId: string; readonly workflow: string }
  | { readonly status: "failed"; readonly message: string };

type HandlerKeycloakLoginState =
  | { readonly status: "offline" }
  | { readonly status: "idle" }
  | { readonly status: "redirecting" }
  | { readonly status: "exchanging" }
  | { readonly status: "loaded"; readonly complaint: ComplaintCaseDto; readonly tokenType: string; readonly bearerToken: string }
  | { readonly status: "failed"; readonly message: string };

type CheckerKeycloakLoginState =
  | { readonly status: "offline" }
  | { readonly status: "idle" }
  | { readonly status: "redirecting" }
  | { readonly status: "exchanging" }
  | { readonly status: "loaded"; readonly subject: string; readonly tokenType: string; readonly bearerToken: string }
  | { readonly status: "failed"; readonly message: string };

type KeycloakCommandState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | {
      readonly status: "approved";
      readonly approvalId: string;
      readonly maker: string;
      readonly checker: string;
      readonly complaint: ComplaintCaseDto;
    }
  | { readonly status: "failed"; readonly message: string };

type OidcIntent = "handler" | "checker";

interface StructuredErrorSummary {
  readonly code: string;
  readonly domain: string;
  readonly statusCode: number;
  readonly route: string;
  readonly message: string;
}

const apiBaseUrl = process.env.NEXT_PUBLIC_BANKING_API_BASE_URL ?? "";
const keycloakBaseUrl = process.env.NEXT_PUBLIC_BANKING_KEYCLOAK_BASE_URL ?? "";
const oidcStateKey = "bankingLabComplaintOidcState";
const oidcVerifierKey = "bankingLabComplaintOidcVerifier";
const oidcRedirectKey = "bankingLabComplaintOidcRedirectUri";
const oidcIntentKey = "bankingLabComplaintOidcIntent";
const storedHandlerLoginKey = "bankingLabComplaintKeycloakHandlerLogin";

export function ApiBackedComplaintPanel() {
  const [state, setState] = useState<ApiState>(() => (apiBaseUrl ? { status: "loading" } : { status: "offline" }));
  const [commandState, setCommandState] = useState<CommandState>({ status: "idle" });
  const [workflowFailureState, setWorkflowFailureState] = useState<WorkflowFailureState>({ status: "idle" });
  const [typeGuideState, setTypeGuideState] = useState<TypeGuideState>(() => (apiBaseUrl ? { status: "loading" } : { status: "offline" }));
  const [materialSmokeState, setMaterialSmokeState] = useState<MaterialSmokeState>({ status: "idle" });
  const [reopenSmokeState, setReopenSmokeState] = useState<ReopenSmokeState>({ status: "idle" });
  const [keycloakHandlerState, setKeycloakHandlerState] = useState<HandlerKeycloakLoginState>(() =>
    apiBaseUrl && keycloakBaseUrl ? { status: "idle" } : { status: "offline" }
  );
  const [keycloakCheckerState, setKeycloakCheckerState] = useState<CheckerKeycloakLoginState>(() =>
    apiBaseUrl && keycloakBaseUrl ? { status: "idle" } : { status: "offline" }
  );
  const [keycloakCommandState, setKeycloakCommandState] = useState<KeycloakCommandState>({ status: "idle" });
  const [keycloakWorkflowFailureState, setKeycloakWorkflowFailureState] = useState<WorkflowFailureState>({ status: "idle" });

  useEffect(() => {
    if (!apiBaseUrl) {
      return;
    }
    let cancelled = false;
    const client = createBankingApiClient({
      baseUrl: apiBaseUrl,
      bearerToken: createSimulatorBearerToken({
        subject: "complaint01",
        roles: ["COMPLAINT_HANDLER"]
      })
    });

    client
      .complaintCases()
      .then((items) => {
        if (!cancelled) {
          const complaint = items.find((item) => item.caseId === "CMP-SYN-001") ?? items[0];
          setState(complaint ? { status: "loaded", complaint } : { status: "failed", message: "No complaint cases returned" });
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setState({ status: "failed", message: error instanceof Error ? error.message : "Unknown API failure" });
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!apiBaseUrl) {
      return;
    }
    let cancelled = false;
    const client = createBankingApiClient({
      baseUrl: apiBaseUrl,
      bearerToken: createSimulatorBearerToken({
        subject: "customer01",
        roles: ["CUSTOMER"],
        customerId: "SYN-CUS-001"
      })
    });

    client
      .complaintTypeGuide()
      .then((response) => {
        if (!cancelled) {
          setTypeGuideState({ status: "loaded", items: response.items });
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setTypeGuideState({ status: "failed", message: error instanceof Error ? error.message : "Unknown complaint type guide failure" });
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
    const storedHandlerLogin = restoreHandlerLogin();
    if (storedHandlerLogin) {
      setKeycloakHandlerState(storedHandlerLogin);
    }
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
      setKeycloakHandlerState({ status: "failed", message: "Keycloak state verification failed" });
      setKeycloakCheckerState({ status: "failed", message: "Keycloak state verification failed" });
      clearOidcSession();
      return;
    }

    let cancelled = false;
    if (intent === "handler") {
      setKeycloakHandlerState({ status: "exchanging" });
    } else {
      setKeycloakCheckerState({ status: "exchanging" });
    }

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
        if (intent === "handler") {
          const client = createBankingApiClient({ baseUrl: apiBaseUrl, bearerToken });
          const items = await client.complaintCases();
          const complaint = items.find((item) => item.caseId === "CMP-SYN-001") ?? items[0];
          if (!complaint) {
            throw new Error("No complaint cases returned for Keycloak handler");
          }
          if (!cancelled) {
            const loaded: HandlerKeycloakLoginState = {
              status: "loaded",
              complaint,
              tokenType,
              bearerToken
            };
            storeHandlerLogin(loaded);
            setKeycloakHandlerState(loaded);
          }
        } else if (!cancelled) {
          setKeycloakCheckerState({
            status: "loaded",
            subject: "manager01",
            tokenType,
            bearerToken
          });
        }
        if (!cancelled) {
          clearOidcSession();
          window.history.replaceState(null, "", window.location.pathname);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          const message = error instanceof Error ? error.message : "Unknown Keycloak login failure";
          if (intent === "handler") {
            setKeycloakHandlerState({ status: "failed", message });
          } else {
            setKeycloakCheckerState({ status: "failed", message });
          }
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
    if (intent === "handler" && (keycloakHandlerState.status === "redirecting" || keycloakHandlerState.status === "exchanging")) {
      return;
    }
    if (intent === "checker" && (keycloakCheckerState.status === "redirecting" || keycloakCheckerState.status === "exchanging")) {
      return;
    }

    if (intent === "handler") {
      setKeycloakHandlerState({ status: "redirecting" });
    } else {
      setKeycloakCheckerState({ status: "redirecting" });
    }

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
          clientId: "complaint-portal",
          redirectUri,
          state: stateValue,
          codeChallenge: pkce.codeChallenge,
          prompt: intent === "checker" ? "login" : undefined,
          loginHint: intent === "checker" ? "manager01" : "complaint01"
        })
      );
    } catch (error: unknown) {
      clearOidcSession();
      const message = error instanceof Error ? error.message : "Unknown Keycloak redirect failure";
      if (intent === "handler") {
        setKeycloakHandlerState({ status: "failed", message });
      } else {
        setKeycloakCheckerState({ status: "failed", message });
      }
    }
  }

  async function runCommandSmoke() {
    if (!apiBaseUrl || commandState.status === "running" || commandState.status === "approved") {
      return;
    }
    setCommandState({ status: "running" });
    try {
      const handlerClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "complaint01",
          roles: ["COMPLAINT_HANDLER"]
        })
      });
      const managerClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "manager01",
          roles: ["BRANCH_MANAGER"]
        })
      });
      const draft = await handlerClient.draftComplaintAnswer("CMP-SYN-CMD-001", {
        actorId: "complaint01",
        requestedByRole: "COMPLAINT_HANDLER",
        reason: "Browser maker-checker smoke",
        body: "Synthetic answer approved from browser channel."
      });
      const approval = await managerClient.approveStaffApproval(draft.approval.approvalId, {
        approvedBy: "manager01",
        approvedByRole: "BRANCH_MANAGER",
        screenId: "CMP-201"
      });
      if (!approval.executed || !approval.complaint) {
        setCommandState({ status: "failed", message: "Approval completed without complaint execution" });
        return;
      }
      setCommandState({
        status: "approved",
        approvalId: approval.item.approvalId,
        complaint: approval.complaint
      });
    } catch (error: unknown) {
      setCommandState({ status: "failed", message: error instanceof Error ? error.message : "Unknown command failure" });
    }
  }

  async function runWorkflowFailureSmoke() {
    if (!apiBaseUrl || workflowFailureState.status === "running") {
      return;
    }
    setWorkflowFailureState({ status: "running" });
    try {
      const handlerClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "complaint01",
          roles: ["COMPLAINT_HANDLER"]
        })
      });
      await handlerClient.draftComplaintAnswer("CMP-SYN-FAIL-001", {
        actorId: "complaint01",
        requestedByRole: "COMPLAINT_HANDLER",
        reason: "Browser workflow failure smoke",
        body: "Synthetic duplicate answer draft should be rejected."
      });
      setWorkflowFailureState({ status: "failed", message: "workflow failure smoke unexpectedly succeeded" });
    } catch (error: unknown) {
      const structuredError = parseStructuredError(error);
      if (structuredError.code !== "WORKFLOW_STATE_VIOLATION") {
        setWorkflowFailureState({ status: "failed", message: error instanceof Error ? error.message : "Unknown workflow failure" });
        return;
      }
      setWorkflowFailureState({ status: "rejected", error: structuredError });
    }
  }

  async function runMaterialSmoke() {
    if (!apiBaseUrl || materialSmokeState.status === "running" || materialSmokeState.status === "submitted") {
      return;
    }
    setMaterialSmokeState({ status: "running" });
    try {
      const client = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "customer01",
          roles: ["CUSTOMER"],
          customerId: "SYN-CUS-001"
        })
      });
      const response = await client.submitCustomerComplaintMaterial("CMP-SYN-001", {
        customerId: "SYN-CUS-001",
        materialType: "CUSTOMER_STATEMENT",
        fileName: "synthetic-statement.pdf",
        description: "Synthetic metadata only; no real attachment bytes.",
        reason: "Browser complaint material smoke"
      });
      setMaterialSmokeState({
        status: "submitted",
        materialId: response.material.materialId,
        caseId: response.item.caseId,
        workflow: response.item.status
      });
    } catch (error: unknown) {
      setMaterialSmokeState({ status: "failed", message: error instanceof Error ? error.message : "Unknown material smoke failure" });
    }
  }

  async function runReopenSmoke() {
    if (!apiBaseUrl || reopenSmokeState.status === "running" || reopenSmokeState.status === "reopened") {
      return;
    }
    setReopenSmokeState({ status: "running" });
    try {
      const client = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "customer01",
          roles: ["CUSTOMER"],
          customerId: "SYN-CUS-001"
        })
      });
      const response = await client.reopenCustomerComplaint("CMP-SYN-CLOSED-001", {
        customerId: "SYN-CUS-001",
        reopenReason: "Synthetic customer disagrees with the closure outcome.",
        reason: "Browser complaint reopen smoke"
      });
      setReopenSmokeState({
        status: "reopened",
        reopenRequestId: response.reopenRequest.reopenRequestId,
        caseId: response.item.caseId,
        workflow: response.item.status
      });
    } catch (error: unknown) {
      setReopenSmokeState({ status: "failed", message: error instanceof Error ? error.message : "Unknown reopen smoke failure" });
    }
  }

  async function runKeycloakCommandSmoke() {
    if (
      !apiBaseUrl ||
      keycloakHandlerState.status !== "loaded" ||
      keycloakCheckerState.status !== "loaded" ||
      keycloakCommandState.status === "running" ||
      keycloakCommandState.status === "approved"
    ) {
      return;
    }
    setKeycloakCommandState({ status: "running" });
    try {
      const handlerClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: keycloakHandlerState.bearerToken
      });
      const managerClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: keycloakCheckerState.bearerToken
      });
      const draft = await handlerClient.draftComplaintAnswer("CMP-SYN-CMD-001", {
        actorId: "complaint01",
        requestedByRole: "COMPLAINT_HANDLER",
        reason: "Browser Keycloak complaint answer smoke",
        body: "Synthetic answer approved from browser Keycloak channel."
      });
      const approval = await managerClient.approveStaffApproval(draft.approval.approvalId, {
        approvedBy: "manager01",
        approvedByRole: "BRANCH_MANAGER",
        screenId: "CMP-201"
      });
      if (!approval.executed || !approval.complaint) {
        setKeycloakCommandState({ status: "failed", message: "Keycloak approval completed without complaint execution" });
        return;
      }
      setKeycloakCommandState({
        status: "approved",
        approvalId: approval.item.approvalId,
        maker: draft.approval.requestedBy,
        checker: approval.item.approvedBy ?? "manager01",
        complaint: approval.complaint
      });
    } catch (error: unknown) {
      setKeycloakCommandState({ status: "failed", message: error instanceof Error ? error.message : "Unknown Keycloak command failure" });
    }
  }

  async function runKeycloakWorkflowFailureSmoke() {
    if (!apiBaseUrl || keycloakHandlerState.status !== "loaded" || keycloakWorkflowFailureState.status === "running") {
      return;
    }
    setKeycloakWorkflowFailureState({ status: "running" });
    try {
      const handlerClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: keycloakHandlerState.bearerToken
      });
      await handlerClient.draftComplaintAnswer("CMP-SYN-FAIL-001", {
        actorId: "complaint01",
        requestedByRole: "COMPLAINT_HANDLER",
        reason: "Browser Keycloak complaint workflow failure smoke",
        body: "Synthetic duplicate answer draft should be rejected with a Keycloak token."
      });
      setKeycloakWorkflowFailureState({ status: "failed", message: "Keycloak workflow failure smoke unexpectedly succeeded" });
    } catch (error: unknown) {
      const structuredError = parseStructuredError(error);
      if (structuredError.code !== "WORKFLOW_STATE_VIOLATION") {
        setKeycloakWorkflowFailureState({ status: "failed", message: error instanceof Error ? error.message : "Unknown Keycloak workflow failure" });
        return;
      }
      setKeycloakWorkflowFailureState({ status: "rejected", error: structuredError });
    }
  }

  return (
    <section className="api-panel" aria-label="API-backed complaint case">
      <h2>API-backed Complaint</h2>
      <dl data-testid="api-backed-complaint-case">
        <div>
          <dt>Spring API</dt>
          <dd>{apiBaseUrl || "not configured"}</dd>
        </div>
        <div>
          <dt>Status</dt>
          <dd>{statusLabel(state)}</dd>
        </div>
        {state.status === "loaded" ? (
          <>
            <div>
              <dt>Case</dt>
              <dd>{state.complaint.caseId}</dd>
            </div>
            <div>
              <dt>Customer</dt>
              <dd>{state.complaint.customerId}</dd>
            </div>
            <div>
              <dt>Workflow</dt>
              <dd>{state.complaint.status}</dd>
            </div>
          </>
        ) : null}
        {state.status === "failed" ? (
          <div>
            <dt>Error</dt>
            <dd>{state.message}</dd>
          </div>
        ) : null}
      </dl>
      <div className="api-actions" data-testid="api-backed-complaint-command">
        <button type="button" onClick={runCommandSmoke} disabled={!apiBaseUrl || commandState.status === "running" || commandState.status === "approved"}>
          Run approval smoke
        </button>
        <dl>
          <div>
            <dt>Command</dt>
            <dd>{commandStatusLabel(commandState)}</dd>
          </div>
          {commandState.status === "approved" ? (
            <>
              <div>
                <dt>Approval</dt>
                <dd>{commandState.approvalId}</dd>
              </div>
              <div>
                <dt>Case</dt>
                <dd>{commandState.complaint.caseId}</dd>
              </div>
              <div>
                <dt>Workflow</dt>
                <dd>{commandState.complaint.status}</dd>
              </div>
            </>
          ) : null}
          {commandState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{commandState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
      <div className="api-actions" data-testid="api-backed-complaint-workflow-failure">
        <button type="button" onClick={runWorkflowFailureSmoke} disabled={!apiBaseUrl || workflowFailureState.status === "running"}>
          Run workflow failure smoke
        </button>
        <dl>
          <div>
            <dt>Failure</dt>
            <dd>{workflowFailureLabel(workflowFailureState)}</dd>
          </div>
          {workflowFailureState.status === "rejected" ? (
            <>
              <div>
                <dt>Case</dt>
                <dd>CMP-SYN-FAIL-001</dd>
              </div>
              <div>
                <dt>Code</dt>
                <dd>{workflowFailureState.error.code}</dd>
              </div>
              <div>
                <dt>Domain</dt>
                <dd>{workflowFailureState.error.domain}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>{workflowFailureState.error.statusCode}</dd>
              </div>
              <div>
                <dt>Route</dt>
                <dd>{workflowFailureState.error.route}</dd>
              </div>
            </>
          ) : null}
          {workflowFailureState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{workflowFailureState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
      <div className="api-actions" data-testid="api-backed-complaint-self-service">
        <button type="button" onClick={runMaterialSmoke} disabled={!apiBaseUrl || materialSmokeState.status === "running" || materialSmokeState.status === "submitted"}>
          Run material smoke
        </button>
        <button type="button" onClick={runReopenSmoke} disabled={!apiBaseUrl || reopenSmokeState.status === "running" || reopenSmokeState.status === "reopened"}>
          Run reopen smoke
        </button>
        <dl>
          <div>
            <dt>Type Guide</dt>
            <dd>{typeGuideLabel(typeGuideState)}</dd>
          </div>
          {typeGuideState.status === "loaded" ? (
            <>
              <div>
                <dt>Types</dt>
                <dd>{typeGuideState.items.map((item) => `${item.category}:${item.slaHours}h`).join(", ")}</dd>
              </div>
              <div>
                <dt>Required Materials</dt>
                <dd>{typeGuideState.items[0]?.requiredMaterials.join(", ") ?? "none"}</dd>
              </div>
            </>
          ) : null}
          {typeGuideState.status === "failed" ? (
            <div>
              <dt>Type Guide Error</dt>
              <dd>{typeGuideState.message}</dd>
            </div>
          ) : null}
          <div>
            <dt>Material</dt>
            <dd>{materialSmokeLabel(materialSmokeState)}</dd>
          </div>
          {materialSmokeState.status === "submitted" ? (
            <>
              <div>
                <dt>Material ID</dt>
                <dd>{materialSmokeState.materialId}</dd>
              </div>
              <div>
                <dt>Material Case</dt>
                <dd>{materialSmokeState.caseId}</dd>
              </div>
              <div>
                <dt>Material Workflow</dt>
                <dd>{materialSmokeState.workflow}</dd>
              </div>
            </>
          ) : null}
          {materialSmokeState.status === "failed" ? (
            <div>
              <dt>Material Error</dt>
              <dd>{materialSmokeState.message}</dd>
            </div>
          ) : null}
          <div>
            <dt>Reopen</dt>
            <dd>{reopenSmokeLabel(reopenSmokeState)}</dd>
          </div>
          {reopenSmokeState.status === "reopened" ? (
            <>
              <div>
                <dt>Reopen Request</dt>
                <dd>{reopenSmokeState.reopenRequestId}</dd>
              </div>
              <div>
                <dt>Reopen Case</dt>
                <dd>{reopenSmokeState.caseId}</dd>
              </div>
              <div>
                <dt>Reopen Workflow</dt>
                <dd>{reopenSmokeState.workflow}</dd>
              </div>
            </>
          ) : null}
          {reopenSmokeState.status === "failed" ? (
            <div>
              <dt>Reopen Error</dt>
              <dd>{reopenSmokeState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
      <div className="api-actions" data-testid="api-backed-complaint-keycloak-login">
        <button
          type="button"
          onClick={() => void runKeycloakLoginSmoke("handler")}
          disabled={!apiBaseUrl || !keycloakBaseUrl || keycloakHandlerState.status === "redirecting" || keycloakHandlerState.status === "exchanging"}
        >
          Sign in complaint handler with Keycloak
        </button>
        <dl>
          <div>
            <dt>Handler login</dt>
            <dd>{keycloakHandlerLabel(keycloakHandlerState)}</dd>
          </div>
          {keycloakHandlerState.status === "loaded" ? (
            <>
              <div>
                <dt>Handler token</dt>
                <dd>{keycloakHandlerState.tokenType}</dd>
              </div>
              <div>
                <dt>Case</dt>
                <dd>{keycloakHandlerState.complaint.caseId}</dd>
              </div>
              <div>
                <dt>Customer</dt>
                <dd>{keycloakHandlerState.complaint.customerId}</dd>
              </div>
              <div>
                <dt>Workflow</dt>
                <dd>{keycloakHandlerState.complaint.status}</dd>
              </div>
            </>
          ) : null}
          {keycloakHandlerState.status === "failed" ? (
            <div>
              <dt>Handler login error</dt>
              <dd>{keycloakHandlerState.message}</dd>
            </div>
          ) : null}
        </dl>
        <button
          type="button"
          onClick={() => void runKeycloakLoginSmoke("checker")}
          disabled={
            !apiBaseUrl ||
            !keycloakBaseUrl ||
            keycloakHandlerState.status !== "loaded" ||
            keycloakCheckerState.status === "redirecting" ||
            keycloakCheckerState.status === "exchanging"
          }
        >
          Sign in complaint checker with Keycloak
        </button>
        <dl>
          <div>
            <dt>Checker login</dt>
            <dd>{keycloakCheckerLabel(keycloakCheckerState)}</dd>
          </div>
          {keycloakCheckerState.status === "loaded" ? (
            <>
              <div>
                <dt>Checker</dt>
                <dd>{keycloakCheckerState.subject}</dd>
              </div>
              <div>
                <dt>Checker token</dt>
                <dd>{keycloakCheckerState.tokenType}</dd>
              </div>
            </>
          ) : null}
          {keycloakCheckerState.status === "failed" ? (
            <div>
              <dt>Checker login error</dt>
              <dd>{keycloakCheckerState.message}</dd>
            </div>
          ) : null}
        </dl>
        <button
          type="button"
          onClick={runKeycloakCommandSmoke}
          disabled={
            !apiBaseUrl ||
            keycloakHandlerState.status !== "loaded" ||
            keycloakCheckerState.status !== "loaded" ||
            keycloakCommandState.status === "running" ||
            keycloakCommandState.status === "approved"
          }
        >
          Run Keycloak answer approval smoke
        </button>
        <dl>
          <div>
            <dt>Keycloak command</dt>
            <dd>{keycloakCommandLabel(keycloakCommandState)}</dd>
          </div>
          {keycloakCommandState.status === "approved" ? (
            <>
              <div>
                <dt>Approval</dt>
                <dd>{keycloakCommandState.approvalId}</dd>
              </div>
              <div>
                <dt>Maker</dt>
                <dd>{keycloakCommandState.maker}</dd>
              </div>
              <div>
                <dt>Checker</dt>
                <dd>{keycloakCommandState.checker}</dd>
              </div>
              <div>
                <dt>Case</dt>
                <dd>{keycloakCommandState.complaint.caseId}</dd>
              </div>
              <div>
                <dt>Workflow</dt>
                <dd>{keycloakCommandState.complaint.status}</dd>
              </div>
            </>
          ) : null}
          {keycloakCommandState.status === "failed" ? (
            <div>
              <dt>Keycloak command error</dt>
              <dd>{keycloakCommandState.message}</dd>
            </div>
          ) : null}
        </dl>
        <button
          type="button"
          onClick={runKeycloakWorkflowFailureSmoke}
          disabled={!apiBaseUrl || keycloakHandlerState.status !== "loaded" || keycloakWorkflowFailureState.status === "running"}
        >
          Run Keycloak workflow failure smoke
        </button>
        <dl>
          <div>
            <dt>Keycloak failure</dt>
            <dd>{keycloakWorkflowFailureLabel(keycloakWorkflowFailureState)}</dd>
          </div>
          {keycloakWorkflowFailureState.status === "rejected" ? (
            <>
              <div>
                <dt>Case</dt>
                <dd>CMP-SYN-FAIL-001</dd>
              </div>
              <div>
                <dt>Code</dt>
                <dd>{keycloakWorkflowFailureState.error.code}</dd>
              </div>
              <div>
                <dt>Domain</dt>
                <dd>{keycloakWorkflowFailureState.error.domain}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>{keycloakWorkflowFailureState.error.statusCode}</dd>
              </div>
              <div>
                <dt>Route</dt>
                <dd>{keycloakWorkflowFailureState.error.route}</dd>
              </div>
            </>
          ) : null}
          {keycloakWorkflowFailureState.status === "failed" ? (
            <div>
              <dt>Keycloak failure error</dt>
              <dd>{keycloakWorkflowFailureState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
    </section>
  );
}

function statusLabel(state: ApiState): string {
  if (state.status === "offline") {
    return "API URL not configured";
  }
  if (state.status === "loading") {
    return "loading";
  }
  if (state.status === "loaded") {
    return "case loaded";
  }
  return "failed";
}

function workflowFailureLabel(state: WorkflowFailureState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "rejected") {
    return "workflow state rejected";
  }
  return "failed";
}

function typeGuideLabel(state: TypeGuideState): string {
  if (state.status === "offline") {
    return "API URL not configured";
  }
  if (state.status === "loading") {
    return "loading complaint types";
  }
  if (state.status === "loaded") {
    return "complaint types loaded";
  }
  return "failed";
}

function materialSmokeLabel(state: MaterialSmokeState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "submitted") {
    return "material submitted";
  }
  return "failed";
}

function reopenSmokeLabel(state: ReopenSmokeState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "reopened") {
    return "complaint reopened";
  }
  return "failed";
}

function keycloakHandlerLabel(state: HandlerKeycloakLoginState): string {
  if (state.status === "offline") {
    return "Keycloak URL not configured";
  }
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "redirecting") {
    return "redirecting";
  }
  if (state.status === "exchanging") {
    return "exchanging";
  }
  if (state.status === "loaded") {
    return "Keycloak complaint case loaded";
  }
  return "failed";
}

function keycloakCheckerLabel(state: CheckerKeycloakLoginState): string {
  if (state.status === "offline") {
    return "Keycloak URL not configured";
  }
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "redirecting") {
    return "redirecting";
  }
  if (state.status === "exchanging") {
    return "exchanging";
  }
  if (state.status === "loaded") {
    return "Keycloak complaint checker loaded";
  }
  return "failed";
}

function keycloakCommandLabel(state: KeycloakCommandState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "approved") {
    return "Keycloak answer approved";
  }
  return "failed";
}

function keycloakWorkflowFailureLabel(state: WorkflowFailureState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "rejected") {
    return "Keycloak workflow state rejected";
  }
  return "failed";
}

function parseStructuredError(error: unknown): StructuredErrorSummary {
  if (error instanceof BankingApiError) {
    try {
      const parsed = JSON.parse(error.body) as {
        error?: {
          code?: string;
          domain?: string;
          statusCode?: number;
          route?: string;
          message?: string;
        };
      };
      return {
        code: parsed.error?.code ?? error.name,
        domain: parsed.error?.domain ?? "unknown",
        statusCode: parsed.error?.statusCode ?? error.status,
        route: parsed.error?.route ?? "unknown",
        message: parsed.error?.message ?? error.message
      };
    } catch {
      return {
        code: error.name,
        domain: "unknown",
        statusCode: error.status,
        route: "unknown",
        message: error.message
      };
    }
  }
  return {
    code: error instanceof Error ? error.name : "unknown_error",
    domain: "unknown",
    statusCode: 0,
    route: "unknown",
    message: error instanceof Error ? error.message : "Unknown workflow failure"
  };
}

function commandStatusLabel(state: CommandState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "approved") {
    return "answer approved";
  }
  return "failed";
}

function isOidcIntent(value: string | null): value is OidcIntent {
  return value === "handler" || value === "checker";
}

function clearOidcSession(): void {
  window.sessionStorage.removeItem(oidcStateKey);
  window.sessionStorage.removeItem(oidcVerifierKey);
  window.sessionStorage.removeItem(oidcRedirectKey);
  window.sessionStorage.removeItem(oidcIntentKey);
}

function storeHandlerLogin(state: Extract<HandlerKeycloakLoginState, { status: "loaded" }>): void {
  window.sessionStorage.setItem(storedHandlerLoginKey, JSON.stringify(state));
}

function restoreHandlerLogin(): HandlerKeycloakLoginState | null {
  const raw = window.sessionStorage.getItem(storedHandlerLoginKey);
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as HandlerKeycloakLoginState;
    return parsed.status === "loaded" ? parsed : null;
  } catch {
    window.sessionStorage.removeItem(storedHandlerLoginKey);
    return null;
  }
}
