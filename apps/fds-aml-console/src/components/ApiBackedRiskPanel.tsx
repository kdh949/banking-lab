"use client";

import { useEffect, useState } from "react";
import {
  BankingApiError,
  createBankingApiClient,
  type AmlCaseDto,
  type FdsCaseDto,
  type StaffApprovalExecutionResponse
} from "@banking-lab/api-client";
import { createOidcAuthorizationUrl, createPkcePair, createSimulatorBearerToken } from "@banking-lab/auth-client";

type ApiState =
  | { readonly status: "offline" }
  | { readonly status: "loading" }
  | { readonly status: "loaded"; readonly fdsCase: FdsCaseDto; readonly amlCase: AmlCaseDto }
  | { readonly status: "failed"; readonly message: string };

type CommandState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | { readonly status: "released"; readonly approvalId: string; readonly execution: StaffApprovalExecutionResponse }
  | { readonly status: "failed"; readonly message: string };

type BlockCommandState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | { readonly status: "blocked"; readonly approvalId: string; readonly execution: StaffApprovalExecutionResponse }
  | { readonly status: "failed"; readonly message: string };

type AmlCommandState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | { readonly status: "closed"; readonly approvalId: string; readonly execution: StaffApprovalExecutionResponse }
  | { readonly status: "failed"; readonly message: string };

type WorkflowFailureState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | { readonly status: "rejected"; readonly error: StructuredErrorSummary }
  | { readonly status: "failed"; readonly message: string };

type ReviewerKeycloakLoginState =
  | { readonly status: "offline" }
  | { readonly status: "idle" }
  | { readonly status: "redirecting" }
  | { readonly status: "exchanging" }
  | { readonly status: "loaded"; readonly fdsCase: FdsCaseDto; readonly amlCase: AmlCaseDto; readonly tokenType: string; readonly bearerToken: string }
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
      readonly maker: string;
      readonly checker: string;
      readonly fdsReleaseApprovalId: string;
      readonly fdsBlockApprovalId: string;
      readonly amlClosureApprovalId: string;
      readonly fdsRelease: StaffApprovalExecutionResponse;
      readonly fdsBlock: StaffApprovalExecutionResponse;
      readonly amlClosure: StaffApprovalExecutionResponse;
    }
  | { readonly status: "failed"; readonly message: string };

type OidcIntent = "reviewer" | "checker";

interface StructuredErrorSummary {
  readonly code: string;
  readonly domain: string;
  readonly statusCode: number;
  readonly route: string;
  readonly message: string;
}

const apiBaseUrl = process.env.NEXT_PUBLIC_BANKING_API_BASE_URL ?? "";
const keycloakBaseUrl = process.env.NEXT_PUBLIC_BANKING_KEYCLOAK_BASE_URL ?? "";
const oidcStateKey = "bankingLabRiskOidcState";
const oidcVerifierKey = "bankingLabRiskOidcVerifier";
const oidcRedirectKey = "bankingLabRiskOidcRedirectUri";
const oidcIntentKey = "bankingLabRiskOidcIntent";
const storedReviewerLoginKey = "bankingLabRiskKeycloakReviewerLogin";
const fdsReleaseCommandCaseId = "FDS-SYN-CMD-001";
const fdsBlockCommandCaseId = "FDS-SYN-BLOCK-CMD-001";
const fdsFailureCaseId = "FDS-SYN-FAIL-001";
const amlCommandCaseId = "AML-SYN-CMD-001";
const amlFailureCaseId = "AML-SYN-FAIL-001";
const keycloakFdsReleaseCommandCaseId = "FDS-SYN-OIDC-REL-001";
const keycloakFdsBlockCommandCaseId = "FDS-SYN-OIDC-BLOCK-001";
const keycloakAmlCommandCaseId = "AML-SYN-OIDC-CLOSE-001";

export function ApiBackedRiskPanel() {
  const [state, setState] = useState<ApiState>(() => (apiBaseUrl ? { status: "loading" } : { status: "offline" }));
  const [commandState, setCommandState] = useState<CommandState>({ status: "idle" });
  const [blockCommandState, setBlockCommandState] = useState<BlockCommandState>({ status: "idle" });
  const [amlCommandState, setAmlCommandState] = useState<AmlCommandState>({ status: "idle" });
  const [workflowFailureState, setWorkflowFailureState] = useState<WorkflowFailureState>({ status: "idle" });
  const [amlWorkflowFailureState, setAmlWorkflowFailureState] = useState<WorkflowFailureState>({ status: "idle" });
  const [keycloakReviewerState, setKeycloakReviewerState] = useState<ReviewerKeycloakLoginState>(() =>
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
        subject: "risk01",
        roles: ["FDS_REVIEWER", "AML_REVIEWER"]
      })
    });

    Promise.all([client.fdsCases(), client.amlCases()])
      .then(([fdsCases, amlCases]) => {
        if (!cancelled) {
          const fdsCase = fdsCases.find((item) => item.caseId === "FDS-SYN-001") ?? fdsCases[0];
          const amlCase = amlCases.find((item) => item.caseId === "AML-SYN-001") ?? amlCases[0];
          setState(
            fdsCase && amlCase
              ? { status: "loaded", fdsCase, amlCase }
              : { status: "failed", message: "No FDS/AML cases returned" }
          );
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
    if (!apiBaseUrl || !keycloakBaseUrl) {
      return;
    }
    const storedReviewerLogin = restoreReviewerLogin();
    if (storedReviewerLogin) {
      setKeycloakReviewerState(storedReviewerLogin);
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
      setKeycloakReviewerState({ status: "failed", message: "Keycloak state verification failed" });
      setKeycloakCheckerState({ status: "failed", message: "Keycloak state verification failed" });
      clearOidcSession();
      return;
    }

    let cancelled = false;
    if (intent === "reviewer") {
      setKeycloakReviewerState({ status: "exchanging" });
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
        if (intent === "reviewer") {
          const client = createBankingApiClient({ baseUrl: apiBaseUrl, bearerToken });
          const [fdsCases, amlCases] = await Promise.all([client.fdsCases(), client.amlCases()]);
          const fdsCase = fdsCases.find((item) => item.caseId === "FDS-SYN-001") ?? fdsCases[0];
          const amlCase = amlCases.find((item) => item.caseId === "AML-SYN-001") ?? amlCases[0];
          if (!fdsCase || !amlCase) {
            throw new Error("No FDS/AML cases returned for Keycloak reviewer");
          }
          if (!cancelled) {
            const loaded: ReviewerKeycloakLoginState = {
              status: "loaded",
              fdsCase,
              amlCase,
              tokenType,
              bearerToken
            };
            storeReviewerLogin(loaded);
            setKeycloakReviewerState(loaded);
          }
        } else if (!cancelled) {
          setKeycloakCheckerState({
            status: "loaded",
            subject: "compliance01",
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
          if (intent === "reviewer") {
            setKeycloakReviewerState({ status: "failed", message });
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
    if (intent === "reviewer" && (keycloakReviewerState.status === "redirecting" || keycloakReviewerState.status === "exchanging")) {
      return;
    }
    if (intent === "checker" && (keycloakCheckerState.status === "redirecting" || keycloakCheckerState.status === "exchanging")) {
      return;
    }

    if (intent === "reviewer") {
      setKeycloakReviewerState({ status: "redirecting" });
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
          clientId: "fds-aml-console",
          redirectUri,
          state: stateValue,
          codeChallenge: pkce.codeChallenge,
          prompt: intent === "checker" ? "login" : undefined,
          loginHint: intent === "checker" ? "compliance01" : "risk01"
        })
      );
    } catch (error: unknown) {
      clearOidcSession();
      const message = error instanceof Error ? error.message : "Unknown Keycloak redirect failure";
      if (intent === "reviewer") {
        setKeycloakReviewerState({ status: "failed", message });
      } else {
        setKeycloakCheckerState({ status: "failed", message });
      }
    }
  }

  const runFdsReleaseSmoke = async () => {
    if (!apiBaseUrl || commandState.status === "running") {
      return;
    }
    setCommandState({ status: "running" });
    try {
      const reviewerClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "fds01",
          roles: ["FDS_REVIEWER"]
        })
      });
      const managerClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "manager01",
          roles: ["BRANCH_MANAGER"]
        })
      });
      const request = await reviewerClient.requestFdsRelease(fdsReleaseCommandCaseId, {
        actorId: "fds01",
        requestedByRole: "FDS_REVIEWER",
        reason: "Browser FDS release smoke"
      });
      const execution = await managerClient.approveStaffApproval(request.approval.approvalId, {
        approvedBy: "manager01",
        approvedByRole: "BRANCH_MANAGER",
        screenId: "FDS-201"
      });
      setCommandState({ status: "released", approvalId: request.approval.approvalId, execution });
    } catch (error: unknown) {
      setCommandState({ status: "failed", message: error instanceof Error ? error.message : "Unknown API failure" });
    }
  };

  const runFdsBlockSmoke = async () => {
    if (!apiBaseUrl || blockCommandState.status === "running") {
      return;
    }
    setBlockCommandState({ status: "running" });
    try {
      const reviewerClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "fds01",
          roles: ["FDS_REVIEWER"]
        })
      });
      const managerClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "manager01",
          roles: ["BRANCH_MANAGER"]
        })
      });
      const request = await reviewerClient.requestFdsBlock(fdsBlockCommandCaseId, {
        actorId: "fds01",
        requestedByRole: "FDS_REVIEWER",
        reason: "Browser FDS block smoke"
      });
      const execution = await managerClient.approveStaffApproval(request.approval.approvalId, {
        approvedBy: "manager01",
        approvedByRole: "BRANCH_MANAGER",
        screenId: "FDS-201"
      });
      setBlockCommandState({ status: "blocked", approvalId: request.approval.approvalId, execution });
    } catch (error: unknown) {
      setBlockCommandState({ status: "failed", message: error instanceof Error ? error.message : "Unknown API failure" });
    }
  };

  const runAmlClosureSmoke = async () => {
    if (!apiBaseUrl || amlCommandState.status === "running") {
      return;
    }
    setAmlCommandState({ status: "running" });
    try {
      const reviewerClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "aml01",
          roles: ["AML_REVIEWER"]
        })
      });
      const managerClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "compliance01",
          roles: ["COMPLIANCE_MANAGER"]
        })
      });
      const request = await reviewerClient.requestAmlClosure(amlCommandCaseId, {
        actorId: "aml01",
        requestedByRole: "AML_REVIEWER",
        reason: "Browser AML closure smoke",
        disposition: "STR_SIMULATED",
        reportReferenceId: "STR-SYN-CMD-001"
      });
      const execution = await managerClient.approveStaffApproval(request.approval.approvalId, {
        approvedBy: "compliance01",
        approvedByRole: "COMPLIANCE_MANAGER",
        screenId: "AML-201"
      });
      setAmlCommandState({ status: "closed", approvalId: request.approval.approvalId, execution });
    } catch (error: unknown) {
      setAmlCommandState({ status: "failed", message: error instanceof Error ? error.message : "Unknown API failure" });
    }
  };

  const runFdsWorkflowFailureSmoke = async () => {
    if (!apiBaseUrl || workflowFailureState.status === "running") {
      return;
    }
    setWorkflowFailureState({ status: "running" });
    try {
      const reviewerClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "fds01",
          roles: ["FDS_REVIEWER"]
        })
      });
      await reviewerClient.requestFdsRelease(fdsFailureCaseId, {
        actorId: "fds01",
        requestedByRole: "FDS_REVIEWER",
        reason: "Browser FDS workflow failure smoke"
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
  };

  const runAmlWorkflowFailureSmoke = async () => {
    if (!apiBaseUrl || amlWorkflowFailureState.status === "running") {
      return;
    }
    setAmlWorkflowFailureState({ status: "running" });
    try {
      const reviewerClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "aml01",
          roles: ["AML_REVIEWER"]
        })
      });
      await reviewerClient.requestAmlClosure(amlFailureCaseId, {
        actorId: "aml01",
        requestedByRole: "AML_REVIEWER",
        reason: "Browser AML workflow failure smoke",
        disposition: "STR_SIMULATED",
        reportReferenceId: "STR-SYN-FAIL-001"
      });
      setAmlWorkflowFailureState({ status: "failed", message: "workflow failure smoke unexpectedly succeeded" });
    } catch (error: unknown) {
      const structuredError = parseStructuredError(error);
      if (structuredError.code !== "WORKFLOW_STATE_VIOLATION") {
        setAmlWorkflowFailureState({ status: "failed", message: error instanceof Error ? error.message : "Unknown workflow failure" });
        return;
      }
      setAmlWorkflowFailureState({ status: "rejected", error: structuredError });
    }
  };

  const runKeycloakRiskCommandSmoke = async () => {
    if (
      !apiBaseUrl ||
      keycloakReviewerState.status !== "loaded" ||
      keycloakCheckerState.status !== "loaded" ||
      keycloakCommandState.status === "running" ||
      keycloakCommandState.status === "approved"
    ) {
      return;
    }
    setKeycloakCommandState({ status: "running" });
    try {
      const reviewerClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: keycloakReviewerState.bearerToken
      });
      const checkerClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: keycloakCheckerState.bearerToken
      });
      const releaseRequest = await reviewerClient.requestFdsRelease(keycloakFdsReleaseCommandCaseId, {
        actorId: "risk01",
        requestedByRole: "FDS_REVIEWER",
        reason: "Browser Keycloak FDS release smoke"
      });
      const fdsRelease = await checkerClient.approveStaffApproval(releaseRequest.approval.approvalId, {
        approvedBy: "compliance01",
        approvedByRole: "BRANCH_MANAGER",
        screenId: "FDS-201"
      });
      const blockRequest = await reviewerClient.requestFdsBlock(keycloakFdsBlockCommandCaseId, {
        actorId: "risk01",
        requestedByRole: "FDS_REVIEWER",
        reason: "Browser Keycloak FDS block smoke"
      });
      const fdsBlock = await checkerClient.approveStaffApproval(blockRequest.approval.approvalId, {
        approvedBy: "compliance01",
        approvedByRole: "BRANCH_MANAGER",
        screenId: "FDS-201"
      });
      const amlRequest = await reviewerClient.requestAmlClosure(keycloakAmlCommandCaseId, {
        actorId: "risk01",
        requestedByRole: "AML_REVIEWER",
        reason: "Browser Keycloak AML closure smoke",
        disposition: "STR_SIMULATED",
        reportReferenceId: "STR-SYN-OIDC-CMD-001"
      });
      const amlClosure = await checkerClient.approveStaffApproval(amlRequest.approval.approvalId, {
        approvedBy: "compliance01",
        approvedByRole: "COMPLIANCE_MANAGER",
        screenId: "AML-201"
      });
      if (!fdsRelease.executed || !fdsRelease.fdsCase || !fdsRelease.ledgerTransaction) {
        setKeycloakCommandState({ status: "failed", message: "Keycloak FDS release did not execute with a ledger transaction" });
        return;
      }
      if (!fdsBlock.executed || !fdsBlock.fdsCase || fdsBlock.ledgerTransaction) {
        setKeycloakCommandState({ status: "failed", message: "Keycloak FDS block did not preserve the no-posting control" });
        return;
      }
      if (!amlClosure.executed || !amlClosure.amlCase) {
        setKeycloakCommandState({ status: "failed", message: "Keycloak AML closure did not execute" });
        return;
      }
      setKeycloakCommandState({
        status: "approved",
        maker: "risk01",
        checker: "compliance01",
        fdsReleaseApprovalId: releaseRequest.approval.approvalId,
        fdsBlockApprovalId: blockRequest.approval.approvalId,
        amlClosureApprovalId: amlRequest.approval.approvalId,
        fdsRelease,
        fdsBlock,
        amlClosure
      });
    } catch (error: unknown) {
      setKeycloakCommandState({ status: "failed", message: error instanceof Error ? error.message : "Unknown Keycloak risk command failure" });
    }
  };

  const runKeycloakRiskWorkflowFailureSmoke = async () => {
    if (!apiBaseUrl || keycloakReviewerState.status !== "loaded" || keycloakWorkflowFailureState.status === "running") {
      return;
    }
    setKeycloakWorkflowFailureState({ status: "running" });
    try {
      const reviewerClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: keycloakReviewerState.bearerToken
      });
      const errors: StructuredErrorSummary[] = [];
      try {
        await reviewerClient.requestFdsRelease(fdsFailureCaseId, {
          actorId: "risk01",
          requestedByRole: "FDS_REVIEWER",
          reason: "Browser Keycloak FDS workflow failure smoke"
        });
        setKeycloakWorkflowFailureState({ status: "failed", message: "Keycloak FDS workflow failure smoke unexpectedly succeeded" });
        return;
      } catch (error: unknown) {
        errors.push(parseStructuredError(error));
      }
      try {
        await reviewerClient.requestAmlClosure(amlFailureCaseId, {
          actorId: "risk01",
          requestedByRole: "AML_REVIEWER",
          reason: "Browser Keycloak AML workflow failure smoke",
          disposition: "STR_SIMULATED",
          reportReferenceId: "STR-SYN-OIDC-FAIL-001"
        });
        setKeycloakWorkflowFailureState({ status: "failed", message: "Keycloak AML workflow failure smoke unexpectedly succeeded" });
        return;
      } catch (error: unknown) {
        errors.push(parseStructuredError(error));
      }
      if (errors.some((error) => error.code !== "WORKFLOW_STATE_VIOLATION")) {
        setKeycloakWorkflowFailureState({ status: "failed", message: "Keycloak workflow failure smoke returned an unexpected structured error" });
        return;
      }
      setKeycloakWorkflowFailureState({ status: "rejected", error: errors[0] });
    } catch (error: unknown) {
      setKeycloakWorkflowFailureState({ status: "failed", message: error instanceof Error ? error.message : "Unknown Keycloak workflow failure" });
    }
  };

  return (
    <section className="api-panel" aria-label="API-backed FDS AML cases">
      <h2>API-backed Risk Cases</h2>
      <dl data-testid="api-backed-risk-cases">
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
              <dt>FDS case</dt>
              <dd>{state.fdsCase.caseId}</dd>
            </div>
            <div>
              <dt>FDS workflow</dt>
              <dd>{state.fdsCase.status}</dd>
            </div>
            <div>
              <dt>AML case</dt>
              <dd>{state.amlCase.caseId}</dd>
            </div>
            <div>
              <dt>AML workflow</dt>
              <dd>{state.amlCase.status}</dd>
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
      <div className="api-actions" data-testid="api-backed-fds-command">
        <button type="button" onClick={runFdsReleaseSmoke} disabled={!apiBaseUrl || commandState.status === "running"}>
          Run FDS release smoke
        </button>
        <dl>
          <div>
            <dt>Command</dt>
            <dd>{commandLabel(commandState)}</dd>
          </div>
          {commandState.status === "released" ? (
            <>
              <div>
                <dt>Approval</dt>
                <dd>{commandState.approvalId}</dd>
              </div>
              <div>
                <dt>Case</dt>
                <dd>{commandState.execution.fdsCase?.caseId}</dd>
              </div>
              <div>
                <dt>Workflow</dt>
                <dd>{commandState.execution.fdsCase?.status}</dd>
              </div>
              <div>
                <dt>Ledger</dt>
                <dd>{commandState.execution.ledgerTransaction?.value.id}</dd>
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
      <div className="api-actions" data-testid="api-backed-fds-block-command">
        <button type="button" onClick={runFdsBlockSmoke} disabled={!apiBaseUrl || blockCommandState.status === "running"}>
          Run FDS block smoke
        </button>
        <dl>
          <div>
            <dt>Command</dt>
            <dd>{blockCommandLabel(blockCommandState)}</dd>
          </div>
          {blockCommandState.status === "blocked" ? (
            <>
              <div>
                <dt>Approval</dt>
                <dd>{blockCommandState.approvalId}</dd>
              </div>
              <div>
                <dt>Case</dt>
                <dd>{blockCommandState.execution.fdsCase?.caseId}</dd>
              </div>
              <div>
                <dt>Workflow</dt>
                <dd>{blockCommandState.execution.fdsCase?.status}</dd>
              </div>
              <div>
                <dt>Ledger</dt>
                <dd>not posted</dd>
              </div>
            </>
          ) : null}
          {blockCommandState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{blockCommandState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
      <div className="api-actions" data-testid="api-backed-aml-command">
        <button type="button" onClick={runAmlClosureSmoke} disabled={!apiBaseUrl || amlCommandState.status === "running"}>
          Run AML closure smoke
        </button>
        <dl>
          <div>
            <dt>Command</dt>
            <dd>{amlCommandLabel(amlCommandState)}</dd>
          </div>
          {amlCommandState.status === "closed" ? (
            <>
              <div>
                <dt>Approval</dt>
                <dd>{amlCommandState.approvalId}</dd>
              </div>
              <div>
                <dt>Case</dt>
                <dd>{amlCommandState.execution.amlCase?.caseId}</dd>
              </div>
              <div>
                <dt>Workflow</dt>
                <dd>{amlCommandState.execution.amlCase?.status}</dd>
              </div>
              <div>
                <dt>Disposition</dt>
                <dd>STR_SIMULATED</dd>
              </div>
            </>
          ) : null}
          {amlCommandState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{amlCommandState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
      <div className="api-actions" data-testid="api-backed-fds-workflow-failure">
        <button type="button" onClick={runFdsWorkflowFailureSmoke} disabled={!apiBaseUrl || workflowFailureState.status === "running"}>
          Run FDS failure smoke
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
                <dd>{fdsFailureCaseId}</dd>
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
      <div className="api-actions" data-testid="api-backed-aml-workflow-failure">
        <button type="button" onClick={runAmlWorkflowFailureSmoke} disabled={!apiBaseUrl || amlWorkflowFailureState.status === "running"}>
          Run AML failure smoke
        </button>
        <dl>
          <div>
            <dt>Failure</dt>
            <dd>{workflowFailureLabel(amlWorkflowFailureState)}</dd>
          </div>
          {amlWorkflowFailureState.status === "rejected" ? (
            <>
              <div>
                <dt>Case</dt>
                <dd>{amlFailureCaseId}</dd>
              </div>
              <div>
                <dt>Code</dt>
                <dd>{amlWorkflowFailureState.error.code}</dd>
              </div>
              <div>
                <dt>Domain</dt>
                <dd>{amlWorkflowFailureState.error.domain}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>{amlWorkflowFailureState.error.statusCode}</dd>
              </div>
              <div>
                <dt>Route</dt>
                <dd>{amlWorkflowFailureState.error.route}</dd>
              </div>
            </>
          ) : null}
          {amlWorkflowFailureState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{amlWorkflowFailureState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
      <div className="api-actions" data-testid="api-backed-risk-keycloak-login">
        <button
          type="button"
          onClick={() => void runKeycloakLoginSmoke("reviewer")}
          disabled={
            !apiBaseUrl ||
            !keycloakBaseUrl ||
            keycloakReviewerState.status === "redirecting" ||
            keycloakReviewerState.status === "exchanging"
          }
        >
          Sign in risk reviewer with Keycloak
        </button>
        <dl>
          <div>
            <dt>Reviewer Login</dt>
            <dd>{keycloakReviewerLabel(keycloakReviewerState)}</dd>
          </div>
          {keycloakReviewerState.status === "loaded" ? (
            <>
              <div>
                <dt>Token</dt>
                <dd>{keycloakReviewerState.tokenType}</dd>
              </div>
              <div>
                <dt>FDS case</dt>
                <dd>{keycloakReviewerState.fdsCase.caseId}</dd>
              </div>
              <div>
                <dt>FDS workflow</dt>
                <dd>{keycloakReviewerState.fdsCase.status}</dd>
              </div>
              <div>
                <dt>AML case</dt>
                <dd>{keycloakReviewerState.amlCase.caseId}</dd>
              </div>
              <div>
                <dt>AML workflow</dt>
                <dd>{keycloakReviewerState.amlCase.status}</dd>
              </div>
            </>
          ) : null}
          {keycloakReviewerState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{keycloakReviewerState.message}</dd>
            </div>
          ) : null}
        </dl>
        <button
          type="button"
          onClick={() => void runKeycloakLoginSmoke("checker")}
          disabled={
            !apiBaseUrl ||
            !keycloakBaseUrl ||
            keycloakCheckerState.status === "redirecting" ||
            keycloakCheckerState.status === "exchanging"
          }
        >
          Sign in risk checker with Keycloak
        </button>
        <dl>
          <div>
            <dt>Checker Login</dt>
            <dd>{keycloakCheckerLabel(keycloakCheckerState)}</dd>
          </div>
          {keycloakCheckerState.status === "loaded" ? (
            <>
              <div>
                <dt>Checker</dt>
                <dd>{keycloakCheckerState.subject}</dd>
              </div>
              <div>
                <dt>Checker Token</dt>
                <dd>{keycloakCheckerState.tokenType}</dd>
              </div>
            </>
          ) : null}
          {keycloakCheckerState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{keycloakCheckerState.message}</dd>
            </div>
          ) : null}
        </dl>
        <button
          type="button"
          onClick={runKeycloakRiskCommandSmoke}
          disabled={
            !apiBaseUrl ||
            keycloakReviewerState.status !== "loaded" ||
            keycloakCheckerState.status !== "loaded" ||
            keycloakCommandState.status === "running" ||
            keycloakCommandState.status === "approved"
          }
        >
          Run Keycloak risk approval smoke
        </button>
        <dl>
          <div>
            <dt>Keycloak Command</dt>
            <dd>{keycloakCommandLabel(keycloakCommandState)}</dd>
          </div>
          {keycloakCommandState.status === "approved" ? (
            <>
              <div>
                <dt>Maker</dt>
                <dd>{keycloakCommandState.maker}</dd>
              </div>
              <div>
                <dt>Checker</dt>
                <dd>{keycloakCommandState.checker}</dd>
              </div>
              <div>
                <dt>FDS release approval</dt>
                <dd>{keycloakCommandState.fdsReleaseApprovalId}</dd>
              </div>
              <div>
                <dt>FDS release case</dt>
                <dd>{keycloakCommandState.fdsRelease.fdsCase?.caseId}</dd>
              </div>
              <div>
                <dt>FDS release workflow</dt>
                <dd>{keycloakCommandState.fdsRelease.fdsCase?.status}</dd>
              </div>
              <div>
                <dt>FDS release ledger</dt>
                <dd>{keycloakCommandState.fdsRelease.ledgerTransaction?.value.id}</dd>
              </div>
              <div>
                <dt>FDS block approval</dt>
                <dd>{keycloakCommandState.fdsBlockApprovalId}</dd>
              </div>
              <div>
                <dt>FDS block case</dt>
                <dd>{keycloakCommandState.fdsBlock.fdsCase?.caseId}</dd>
              </div>
              <div>
                <dt>FDS block workflow</dt>
                <dd>{keycloakCommandState.fdsBlock.fdsCase?.status}</dd>
              </div>
              <div>
                <dt>FDS block ledger</dt>
                <dd>not posted</dd>
              </div>
              <div>
                <dt>AML approval</dt>
                <dd>{keycloakCommandState.amlClosureApprovalId}</dd>
              </div>
              <div>
                <dt>AML case</dt>
                <dd>{keycloakCommandState.amlClosure.amlCase?.caseId}</dd>
              </div>
              <div>
                <dt>AML workflow</dt>
                <dd>{keycloakCommandState.amlClosure.amlCase?.status}</dd>
              </div>
              <div>
                <dt>Disposition</dt>
                <dd>STR_SIMULATED</dd>
              </div>
            </>
          ) : null}
          {keycloakCommandState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{keycloakCommandState.message}</dd>
            </div>
          ) : null}
        </dl>
        <button
          type="button"
          onClick={runKeycloakRiskWorkflowFailureSmoke}
          disabled={!apiBaseUrl || keycloakReviewerState.status !== "loaded" || keycloakWorkflowFailureState.status === "running"}
        >
          Run Keycloak risk failure smoke
        </button>
        <dl>
          <div>
            <dt>Keycloak Failure</dt>
            <dd>{workflowFailureLabel(keycloakWorkflowFailureState)}</dd>
          </div>
          {keycloakWorkflowFailureState.status === "rejected" ? (
            <>
              <div>
                <dt>FDS case</dt>
                <dd>{fdsFailureCaseId}</dd>
              </div>
              <div>
                <dt>AML case</dt>
                <dd>{amlFailureCaseId}</dd>
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
              <dt>Error</dt>
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
    return "risk cases loaded";
  }
  return "failed";
}

function amlCommandLabel(state: AmlCommandState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "closed") {
    return "AML closure approved";
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

function blockCommandLabel(state: BlockCommandState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "blocked") {
    return "FDS block approved";
  }
  return "failed";
}

function commandLabel(state: CommandState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "released") {
    return "FDS release approved";
  }
  return "failed";
}

function keycloakReviewerLabel(state: ReviewerKeycloakLoginState): string {
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
    return "exchanging token";
  }
  if (state.status === "loaded") {
    return "Keycloak risk cases loaded";
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
    return "exchanging token";
  }
  if (state.status === "loaded") {
    return "Keycloak risk checker loaded";
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
    return "Keycloak risk approvals completed";
  }
  return "failed";
}

function isOidcIntent(value: string | null): value is OidcIntent {
  return value === "reviewer" || value === "checker";
}

function clearOidcSession(): void {
  window.sessionStorage.removeItem(oidcStateKey);
  window.sessionStorage.removeItem(oidcVerifierKey);
  window.sessionStorage.removeItem(oidcRedirectKey);
  window.sessionStorage.removeItem(oidcIntentKey);
}

function storeReviewerLogin(state: ReviewerKeycloakLoginState): void {
  if (state.status !== "loaded") {
    return;
  }
  window.sessionStorage.setItem(
    storedReviewerLoginKey,
    JSON.stringify({
      fdsCase: state.fdsCase,
      amlCase: state.amlCase,
      tokenType: state.tokenType,
      bearerToken: state.bearerToken
    })
  );
}

function restoreReviewerLogin(): ReviewerKeycloakLoginState | null {
  const stored = window.sessionStorage.getItem(storedReviewerLoginKey);
  if (!stored) {
    return null;
  }
  try {
    const parsed = JSON.parse(stored) as {
      fdsCase?: FdsCaseDto;
      amlCase?: AmlCaseDto;
      tokenType?: string;
      bearerToken?: string;
    };
    if (!parsed.fdsCase || !parsed.amlCase || !parsed.bearerToken) {
      return null;
    }
    return {
      status: "loaded",
      fdsCase: parsed.fdsCase,
      amlCase: parsed.amlCase,
      tokenType: parsed.tokenType ?? "Bearer",
      bearerToken: parsed.bearerToken
    };
  } catch {
    return null;
  }
}
