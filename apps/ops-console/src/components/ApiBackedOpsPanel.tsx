"use client";

import { useEffect, useState } from "react";
import {
  BankingApiError,
  createBankingApiClient,
  type EodClosingMonitorDto,
  type LedgerProjectionDriftRunResponse,
  type LedgerProjectionRebuildRequestResponse,
  type LedgerProjectionRebuildRunResponse,
  type ParameterChangeRequestResponse,
  type ParameterListResponse,
  type PaymentOutboxDispatchResponse,
  type ReconciliationItemDto,
  type StaffApprovalExecutionResponse
} from "@banking-lab/api-client";
import { createOidcAuthorizationUrl, createPkcePair, createSimulatorBearerToken } from "@banking-lab/auth-client";

type ApiState =
  | { readonly status: "offline" }
  | { readonly status: "loading" }
  | { readonly status: "loaded"; readonly item: ReconciliationItemDto }
  | { readonly status: "failed"; readonly message: string };

type CommandState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | { readonly status: "adjusted"; readonly approvalId: string; readonly execution: StaffApprovalExecutionResponse }
  | { readonly status: "failed"; readonly message: string };

type EodMonitorState =
  | { readonly status: "offline" }
  | { readonly status: "loading" }
  | { readonly status: "loaded"; readonly monitor: EodClosingMonitorDto }
  | { readonly status: "failed"; readonly message: string };

type WorkflowFailureState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | { readonly status: "rejected"; readonly error: StructuredErrorSummary }
  | { readonly status: "failed"; readonly message: string };

type PaymentOutboxDispatchState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | { readonly status: "loaded"; readonly result: PaymentOutboxDispatchResponse; readonly reason: string }
  | { readonly status: "failed"; readonly message: string };

type ReconciliationParameterState =
  | { readonly status: "offline" }
  | { readonly status: "loading" }
  | {
      readonly status: "loaded";
      readonly auditEventId: string;
      readonly parameterKey: string;
      readonly currentValue: string;
      readonly currentVersionId: string;
      readonly scheduledCount: number;
    }
  | { readonly status: "failed"; readonly message: string };

type ReconciliationParameterCommandState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | {
      readonly status: "requested";
      readonly requestId: string;
      readonly approvalId: string;
      readonly parameterKey: string;
      readonly requestedValue: string;
      readonly effectiveFrom: string;
      readonly requestStatus: string;
    }
  | { readonly status: "failed"; readonly message: string };

type LedgerProjectionWorkflowState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | {
      readonly status: "completed";
      readonly drift: LedgerProjectionDriftRunResponse;
      readonly request: LedgerProjectionRebuildRequestResponse;
      readonly run: LedgerProjectionRebuildRunResponse;
    }
  | { readonly status: "failed"; readonly message: string };

type OperatorKeycloakLoginState =
  | { readonly status: "offline" }
  | { readonly status: "idle" }
  | { readonly status: "redirecting" }
  | { readonly status: "exchanging" }
  | { readonly status: "loaded"; readonly item: ReconciliationItemDto; readonly tokenType: string; readonly bearerToken: string }
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
      readonly status: "adjusted";
      readonly approvalId: string;
      readonly maker: string;
      readonly checker: string;
      readonly execution: StaffApprovalExecutionResponse;
    }
  | { readonly status: "failed"; readonly message: string };

type OidcIntent = "operator" | "checker";

interface StructuredErrorSummary {
  readonly code: string;
  readonly domain: string;
  readonly statusCode: number;
  readonly route: string;
  readonly message: string;
}

const apiBaseUrl = process.env.NEXT_PUBLIC_BANKING_API_BASE_URL ?? "";
const paymentApiBaseUrl = process.env.NEXT_PUBLIC_BANKING_PAYMENT_API_BASE_URL || apiBaseUrl;
const keycloakBaseUrl = process.env.NEXT_PUBLIC_BANKING_KEYCLOAK_BASE_URL ?? "";
const oidcStateKey = "bankingLabOpsOidcState";
const oidcVerifierKey = "bankingLabOpsOidcVerifier";
const oidcRedirectKey = "bankingLabOpsOidcRedirectUri";
const oidcIntentKey = "bankingLabOpsOidcIntent";
const storedOperatorLoginKey = "bankingLabOpsKeycloakOperatorLogin";
const adjustmentItemId = "REC-SYN-CMD-001";
const reconciliationFailureItemId = "REC-SYN-FAIL-001";
const eodMonitorBusinessDate = "2026-02-03";
const projectionSmokeAccountId = "ACC-SYN-CORR-TO";

export function ApiBackedOpsPanel() {
  const [state, setState] = useState<ApiState>(() => (apiBaseUrl ? { status: "loading" } : { status: "offline" }));
  const [eodState, setEodState] = useState<EodMonitorState>(() => (apiBaseUrl ? { status: "loading" } : { status: "offline" }));
  const [commandState, setCommandState] = useState<CommandState>({ status: "idle" });
  const [workflowFailureState, setWorkflowFailureState] = useState<WorkflowFailureState>({ status: "idle" });
  const [paymentDispatchState, setPaymentDispatchState] = useState<PaymentOutboxDispatchState>({ status: "idle" });
  const [reconciliationParameterState, setReconciliationParameterState] = useState<ReconciliationParameterState>(() =>
    apiBaseUrl ? { status: "loading" } : { status: "offline" }
  );
  const [reconciliationParameterCommandState, setReconciliationParameterCommandState] = useState<ReconciliationParameterCommandState>({ status: "idle" });
  const [ledgerProjectionState, setLedgerProjectionState] = useState<LedgerProjectionWorkflowState>({ status: "idle" });
  const [keycloakOperatorState, setKeycloakOperatorState] = useState<OperatorKeycloakLoginState>(() =>
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
        subject: "ops01",
        roles: ["OPS_OPERATOR"]
      })
    });

    client
      .reconciliationItems()
      .then((response) => {
        if (!cancelled) {
          const item = response.items.find((candidate) => candidate.itemId === "REC-SYN-001") ?? response.items[0];
          setState(item ? { status: "loaded", item } : { status: "failed", message: "No reconciliation items returned" });
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
        subject: "ops01",
        roles: ["OPS_OPERATOR"]
      })
    });

    client
      .eodMonitor(eodMonitorBusinessDate)
      .then((monitor) => {
        if (!cancelled) {
          setEodState({ status: "loaded", monitor });
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setEodState({ status: "failed", message: error instanceof Error ? error.message : "Unknown EOD API failure" });
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
    reconciliationParameterClient()
      .reconciliationParameters("Browser OPS-301 parameter read smoke")
      .then((response) => {
        if (!cancelled) {
          setReconciliationParameterState(toReconciliationParameterState(response));
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setReconciliationParameterState({
            status: "failed",
            message: error instanceof Error ? error.message : "Unknown reconciliation parameter API failure"
          });
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
    const storedOperatorLogin = restoreOperatorLogin();
    if (storedOperatorLogin) {
      setKeycloakOperatorState(storedOperatorLogin);
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
      setKeycloakOperatorState({ status: "failed", message: "Keycloak state verification failed" });
      setKeycloakCheckerState({ status: "failed", message: "Keycloak state verification failed" });
      clearOidcSession();
      return;
    }

    let cancelled = false;
    if (intent === "operator") {
      setKeycloakOperatorState({ status: "exchanging" });
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
        if (intent === "operator") {
          const client = createBankingApiClient({ baseUrl: apiBaseUrl, bearerToken });
          const responseBody = await client.reconciliationItems();
          const item = responseBody.items.find((candidate) => candidate.itemId === "REC-SYN-001") ?? responseBody.items[0];
          if (!item) {
            throw new Error("No reconciliation items returned for Keycloak operator");
          }
          if (!cancelled) {
            const loaded: OperatorKeycloakLoginState = {
              status: "loaded",
              item,
              tokenType,
              bearerToken
            };
            storeOperatorLogin(loaded);
            setKeycloakOperatorState(loaded);
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
          if (intent === "operator") {
            setKeycloakOperatorState({ status: "failed", message });
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
    if (intent === "operator" && (keycloakOperatorState.status === "redirecting" || keycloakOperatorState.status === "exchanging")) {
      return;
    }
    if (intent === "checker" && (keycloakCheckerState.status === "redirecting" || keycloakCheckerState.status === "exchanging")) {
      return;
    }

    if (intent === "operator") {
      setKeycloakOperatorState({ status: "redirecting" });
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
          clientId: "ops-console",
          redirectUri,
          state: stateValue,
          codeChallenge: pkce.codeChallenge,
          prompt: intent === "checker" ? "login" : undefined,
          loginHint: intent === "checker" ? "manager01" : "ops01"
        })
      );
    } catch (error: unknown) {
      clearOidcSession();
      const message = error instanceof Error ? error.message : "Unknown Keycloak redirect failure";
      if (intent === "operator") {
        setKeycloakOperatorState({ status: "failed", message });
      } else {
        setKeycloakCheckerState({ status: "failed", message });
      }
    }
  }

  const runReconciliationAdjustmentSmoke = async () => {
    if (!apiBaseUrl || commandState.status === "running") {
      return;
    }
    setCommandState({ status: "running" });
    try {
      const opsClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "ops01",
          roles: ["OPS_OPERATOR"]
        })
      });
      const managerClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "manager01",
          roles: ["BRANCH_MANAGER"]
        })
      });
      const request = await opsClient.requestReconciliationAdjustment(adjustmentItemId, {
        requestedBy: "ops01",
        requestedByRole: "OPS_OPERATOR",
        reason: "Browser reconciliation adjustment smoke",
        accountId: "ACC-SYN-001-001",
        direction: "CREDIT",
        amountMinor: 15000,
        idempotencyKey: "REC-SYN-CMD-ADJ-001"
      });
      const execution = await managerClient.approveStaffApproval(request.approval.approvalId, {
        approvedBy: "manager01",
        approvedByRole: "BRANCH_MANAGER",
        screenId: "OPS-201"
      });
      setCommandState({ status: "adjusted", approvalId: request.approval.approvalId, execution });
    } catch (error: unknown) {
      setCommandState({ status: "failed", message: error instanceof Error ? error.message : "Unknown API failure" });
    }
  };

  const runReconciliationWorkflowFailureSmoke = async () => {
    if (!apiBaseUrl || workflowFailureState.status === "running") {
      return;
    }
    setWorkflowFailureState({ status: "running" });
    try {
      const opsClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "ops01",
          roles: ["OPS_OPERATOR"]
        })
      });
      await opsClient.requestReconciliationAdjustment(reconciliationFailureItemId, {
        requestedBy: "ops01",
        requestedByRole: "OPS_OPERATOR",
        reason: "Browser reconciliation workflow failure smoke",
        accountId: "ACC-SYN-001-001",
        direction: "CREDIT",
        amountMinor: 15000,
        idempotencyKey: "REC-SYN-FAIL-ADJ-001"
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

  const runPaymentOutboxDispatchSmoke = async () => {
    if (!paymentApiBaseUrl || paymentDispatchState.status === "running") {
      return;
    }
    setPaymentDispatchState({ status: "running" });
    try {
      const client = createBankingApiClient({
        baseUrl: paymentApiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "ops01",
          roles: ["OPS_OPERATOR"]
        })
      });
      const reason = "Browser OPS-404 payment outbox dispatch smoke";
      const result = await client.dispatchNextPaymentLedgerPosting({
        requestedBy: "ops01",
        reason,
        deadLetterThreshold: 3
      });
      if (!result.syntheticOnly) {
        setPaymentDispatchState({ status: "failed", message: "payment outbox dispatch returned a non-synthetic result" });
        return;
      }
      setPaymentDispatchState({ status: "loaded", result, reason });
    } catch (error: unknown) {
      setPaymentDispatchState({ status: "failed", message: error instanceof Error ? error.message : "Unknown payment outbox dispatch failure" });
    }
  };

  const runReconciliationParameterChangeSmoke = async () => {
    if (!apiBaseUrl || reconciliationParameterCommandState.status === "running") {
      return;
    }
    setReconciliationParameterCommandState({ status: "running" });
    try {
      const current = reconciliationParameterState.status === "loaded"
        ? reconciliationParameterState
        : toReconciliationParameterState(
            await reconciliationParameterClient().reconciliationParameters("Browser OPS-301 parameter command read")
          );
      if (current.status !== "loaded") {
        setReconciliationParameterCommandState({ status: "failed", message: "OPS autoMatchToleranceMinor parameter was not loaded" });
        return;
      }
      const requestedValue = nextNumericParameterValue(current.currentValue, 100);
      const effectiveFrom = tomorrowIsoDate();
      const response = await reconciliationParameterClient(true).requestReconciliationParameterChange({
        parameterKey: "autoMatchToleranceMinor",
        scheduledValue: requestedValue,
        effectiveFrom,
        rollbackPlan: "Create a future synthetic rollback version from the prior auto-match tolerance",
        requestedBy: "ops01",
        requestedByRole: "OPS_MANAGER",
        reason: "Browser OPS-301 parameter change smoke",
        idempotencyKey: `OPS-PARAM-${globalThis.crypto.randomUUID()}`
      });
      setReconciliationParameterCommandState(toReconciliationParameterCommandState(response));
    } catch (error: unknown) {
      setReconciliationParameterCommandState({
        status: "failed",
        message: error instanceof Error ? error.message : "Unknown reconciliation parameter failure"
      });
    }
  };

  const runLedgerProjectionWorkflowSmoke = async () => {
    if (!apiBaseUrl || ledgerProjectionState.status === "running") {
      return;
    }
    setLedgerProjectionState({ status: "running" });
    try {
      const drift = await projectionClient("ops01", ["OPS_OPERATOR"]).startLedgerProjectionDriftRun({
        requestedBy: "ops01",
        requestedByRole: "OPS_OPERATOR",
        reason: "Browser OPS-LEDGER-101 projection drift smoke",
        idempotencyKey: `OPS-LEDGER-DRIFT-${globalThis.crypto.randomUUID()}`,
        accountId: projectionSmokeAccountId,
        currency: "KRW"
      });
      const request = await projectionClient("ops01", ["OPS_OPERATOR"], true).requestLedgerProjectionRebuild({
        requestedBy: "ops01",
        requestedByRole: "OPS_OPERATOR",
        reason: "Browser OPS-LEDGER-102 projection rebuild request smoke",
        idempotencyKey: `OPS-LEDGER-REQUEST-${globalThis.crypto.randomUUID()}`,
        accountId: projectionSmokeAccountId,
        currency: "KRW",
        driftRunId: drift.item.runId
      });
      await projectionClient("manager01", ["OPS_MANAGER"], true).approveLedgerProjectionRebuildRequest(request.item.requestId, {
        approvedBy: "manager01",
        approvedByRole: "OPS_MANAGER",
        screenId: "OPS-LEDGER-102"
      });
      const run = await projectionClient("ops01", ["OPS_OPERATOR"], true).executeLedgerProjectionRebuild(request.item.requestId, {
        executedBy: "ops01",
        executedByRole: "OPS_OPERATOR",
        reason: "Browser OPS-LEDGER-103 projection rebuild execution smoke",
        idempotencyKey: `OPS-LEDGER-EXEC-${globalThis.crypto.randomUUID()}`
      });
      setLedgerProjectionState({ status: "completed", drift, request, run });
    } catch (error: unknown) {
      setLedgerProjectionState({ status: "failed", message: error instanceof Error ? error.message : "Unknown ledger projection workflow failure" });
    }
  };

  const runKeycloakReconciliationAdjustmentSmoke = async () => {
    if (
      !apiBaseUrl ||
      keycloakOperatorState.status !== "loaded" ||
      keycloakCheckerState.status !== "loaded" ||
      keycloakCommandState.status === "running" ||
      keycloakCommandState.status === "adjusted"
    ) {
      return;
    }
    setKeycloakCommandState({ status: "running" });
    try {
      const opsClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: keycloakOperatorState.bearerToken
      });
      const managerClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: keycloakCheckerState.bearerToken
      });
      const request = await opsClient.requestReconciliationAdjustment(adjustmentItemId, {
        requestedBy: "ops01",
        requestedByRole: "OPS_OPERATOR",
        reason: "Browser Keycloak reconciliation adjustment smoke",
        accountId: "ACC-SYN-001-001",
        direction: "CREDIT",
        amountMinor: 15000,
        idempotencyKey: "REC-SYN-OIDC-ADJ-001"
      });
      const execution = await managerClient.approveStaffApproval(request.approval.approvalId, {
        approvedBy: "manager01",
        approvedByRole: "BRANCH_MANAGER",
        screenId: "OPS-201"
      });
      if (!execution.executed || !execution.reconciliationItem || !execution.ledgerTransaction) {
        setKeycloakCommandState({ status: "failed", message: "Keycloak approval completed without reconciliation execution" });
        return;
      }
      setKeycloakCommandState({
        status: "adjusted",
        approvalId: execution.item.approvalId,
        maker: request.approval.requestedBy,
        checker: execution.item.approvedBy ?? "manager01",
        execution
      });
    } catch (error: unknown) {
      setKeycloakCommandState({ status: "failed", message: error instanceof Error ? error.message : "Unknown Keycloak command failure" });
    }
  };

  const runKeycloakReconciliationWorkflowFailureSmoke = async () => {
    if (!apiBaseUrl || keycloakOperatorState.status !== "loaded" || keycloakWorkflowFailureState.status === "running") {
      return;
    }
    setKeycloakWorkflowFailureState({ status: "running" });
    try {
      const opsClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: keycloakOperatorState.bearerToken
      });
      await opsClient.requestReconciliationAdjustment(reconciliationFailureItemId, {
        requestedBy: "ops01",
        requestedByRole: "OPS_OPERATOR",
        reason: "Browser Keycloak reconciliation workflow failure smoke",
        accountId: "ACC-SYN-001-001",
        direction: "CREDIT",
        amountMinor: 15000,
        idempotencyKey: "REC-SYN-OIDC-FAIL-ADJ-001"
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
  };

  return (
    <section className="api-panel" aria-label="API-backed reconciliation item">
      <h2>API-backed Reconciliation</h2>
      <dl data-testid="api-backed-ops-reconciliation">
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
              <dt>Item</dt>
              <dd>{state.item.itemId}</dd>
            </div>
            <div>
              <dt>Workflow</dt>
              <dd>{state.item.status}</dd>
            </div>
            <div>
              <dt>Mismatch</dt>
              <dd>
                {state.item.mismatchType}: {state.item.amountMinor} {state.item.currency}
              </dd>
            </div>
            <div>
              <dt>Feed</dt>
              <dd>{state.item.feedFileId ?? "synthetic feed pending"}</dd>
            </div>
            <div>
              <dt>Reason</dt>
              <dd>{state.item.detectedReason}</dd>
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
      <div className="api-actions" data-testid="api-backed-eod-monitor">
        <h3>OPS-101</h3>
        <dl>
          <div>
            <dt>Business date</dt>
            <dd>{eodState.status === "loaded" ? eodState.monitor.businessDate : eodMonitorBusinessDate}</dd>
          </div>
          <div>
            <dt>Status</dt>
            <dd>{eodMonitorLabel(eodState)}</dd>
          </div>
          {eodState.status === "loaded" ? (
            <>
              <div>
                <dt>Steps</dt>
                <dd>{eodState.monitor.steps.length}</dd>
              </div>
              <div>
                <dt>Ledger hash</dt>
                <dd>{eodState.monitor.ledgerTotalHash ?? "pending"}</dd>
              </div>
            </>
          ) : null}
          {eodState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{eodState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
      <div className="api-actions" data-testid="api-backed-reconciliation-command">
        <button type="button" onClick={runReconciliationAdjustmentSmoke} disabled={!apiBaseUrl || commandState.status === "running"}>
          Run reconciliation adjustment smoke
        </button>
        <dl>
          <div>
            <dt>Command</dt>
            <dd>{commandLabel(commandState)}</dd>
          </div>
          {commandState.status === "adjusted" ? (
            <>
              <div>
                <dt>Approval</dt>
                <dd>{commandState.approvalId}</dd>
              </div>
              <div>
                <dt>Item</dt>
                <dd>{commandState.execution.reconciliationItem?.itemId}</dd>
              </div>
              <div>
                <dt>Workflow</dt>
                <dd>{commandState.execution.reconciliationItem?.status}</dd>
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
      <div className="api-actions" data-testid="api-backed-reconciliation-workflow-failure">
        <button
          type="button"
          onClick={runReconciliationWorkflowFailureSmoke}
          disabled={!apiBaseUrl || workflowFailureState.status === "running"}
        >
          Run reconciliation failure smoke
        </button>
        <dl>
          <div>
            <dt>Failure</dt>
            <dd>{workflowFailureLabel(workflowFailureState)}</dd>
          </div>
          {workflowFailureState.status === "rejected" ? (
            <>
              <div>
                <dt>Item</dt>
                <dd>{reconciliationFailureItemId}</dd>
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
      <div className="api-actions" data-testid="api-backed-reconciliation-parameters">
        <button
          type="button"
          onClick={runReconciliationParameterChangeSmoke}
          disabled={!apiBaseUrl || reconciliationParameterCommandState.status === "running"}
        >
          Run reconciliation parameter change smoke
        </button>
        <dl>
          <div>
            <dt>OPS-301</dt>
            <dd>{reconciliationParameterLabel(reconciliationParameterState)}</dd>
          </div>
          {reconciliationParameterState.status === "loaded" ? (
            <>
              <div>
                <dt>Parameter</dt>
                <dd>{reconciliationParameterState.parameterKey}</dd>
              </div>
              <div>
                <dt>Current</dt>
                <dd>{reconciliationParameterState.currentValue}</dd>
              </div>
              <div>
                <dt>Current version</dt>
                <dd>{reconciliationParameterState.currentVersionId}</dd>
              </div>
              <div>
                <dt>Scheduled</dt>
                <dd>{reconciliationParameterState.scheduledCount}</dd>
              </div>
              <div>
                <dt>Audit</dt>
                <dd>{reconciliationParameterState.auditEventId}</dd>
              </div>
            </>
          ) : null}
          {reconciliationParameterState.status === "failed" ? (
            <div>
              <dt>Parameter error</dt>
              <dd>{reconciliationParameterState.message}</dd>
            </div>
          ) : null}
          <div>
            <dt>Command</dt>
            <dd>{reconciliationParameterCommandLabel(reconciliationParameterCommandState)}</dd>
          </div>
          {reconciliationParameterCommandState.status === "requested" ? (
            <>
              <div>
                <dt>Request</dt>
                <dd>{reconciliationParameterCommandState.requestId}</dd>
              </div>
              <div>
                <dt>Approval</dt>
                <dd>{reconciliationParameterCommandState.approvalId}</dd>
              </div>
              <div>
                <dt>Requested value</dt>
                <dd>{reconciliationParameterCommandState.requestedValue}</dd>
              </div>
              <div>
                <dt>Effective from</dt>
                <dd>{reconciliationParameterCommandState.effectiveFrom}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>{reconciliationParameterCommandState.requestStatus}</dd>
              </div>
            </>
          ) : null}
          {reconciliationParameterCommandState.status === "failed" ? (
            <div>
              <dt>Command error</dt>
              <dd>{reconciliationParameterCommandState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
      <div className="api-actions" data-testid="api-backed-ledger-projection-workflow">
        <button
          type="button"
          onClick={runLedgerProjectionWorkflowSmoke}
          disabled={!apiBaseUrl || ledgerProjectionState.status === "running"}
        >
          Run projection rebuild smoke
        </button>
        <dl>
          <div>
            <dt>OPS-LEDGER</dt>
            <dd>{ledgerProjectionWorkflowLabel(ledgerProjectionState)}</dd>
          </div>
          {ledgerProjectionState.status === "completed" ? (
            <>
              <div>
                <dt>Business type</dt>
                <dd>LEDGER_PROJECTION_REBUILD</dd>
              </div>
              <div>
                <dt>Account</dt>
                <dd>{projectionSmokeAccountId}</dd>
              </div>
              <div>
                <dt>Drift run</dt>
                <dd>{ledgerProjectionState.drift.item.runId}</dd>
              </div>
              <div>
                <dt>Drift items</dt>
                <dd>{ledgerProjectionState.drift.item.driftItemCount}</dd>
              </div>
              <div>
                <dt>Request</dt>
                <dd>{ledgerProjectionState.request.item.requestId}</dd>
              </div>
              <div>
                <dt>Approval</dt>
                <dd>{ledgerProjectionState.request.approval.approvalId}</dd>
              </div>
              <div>
                <dt>Rebuild run</dt>
                <dd>{ledgerProjectionState.run.item.runId}</dd>
              </div>
              <div>
                <dt>Evidence</dt>
                <dd>{ledgerProjectionState.run.item.rebuiltItemCount} projection rows rebuilt</dd>
              </div>
              <div>
                <dt>Source hash</dt>
                <dd>{ledgerProjectionState.run.item.afterSourceHash.slice(0, 16)}</dd>
              </div>
              <div>
                <dt>Projection hash</dt>
                <dd>{ledgerProjectionState.run.item.afterProjectionHash.slice(0, 16)}</dd>
              </div>
            </>
          ) : null}
          {ledgerProjectionState.status === "failed" ? (
            <div>
              <dt>Projection error</dt>
              <dd>{ledgerProjectionState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
      <div className="api-actions" data-testid="api-backed-payment-outbox-dispatch">
        <button type="button" onClick={runPaymentOutboxDispatchSmoke} disabled={!paymentApiBaseUrl || paymentDispatchState.status === "running"}>
          Run payment outbox dispatch smoke
        </button>
        <dl>
          <div>
            <dt>Payment dispatch</dt>
            <dd>{paymentDispatchLabel(paymentDispatchState)}</dd>
          </div>
          {paymentDispatchState.status === "loaded" ? (
            <>
              <div>
                <dt>Result</dt>
                <dd>{paymentDispatchState.result.status}</dd>
              </div>
              <div>
                <dt>Outbox event</dt>
                <dd>{paymentDispatchState.result.outboxEventId ?? "none"}</dd>
              </div>
              <div>
                <dt>Instruction</dt>
                <dd>{paymentDispatchState.result.paymentInstructionId ?? "none"}</dd>
              </div>
              <div>
                <dt>Ledger</dt>
                <dd>{paymentDispatchState.result.ledgerTransactionId ?? "none"}</dd>
              </div>
              <div>
                <dt>Retry count</dt>
                <dd>{paymentDispatchState.result.retryCount}</dd>
              </div>
              <div>
                <dt>Reason</dt>
                <dd>{paymentDispatchState.reason}</dd>
              </div>
            </>
          ) : null}
          {paymentDispatchState.status === "failed" ? (
            <div>
              <dt>Payment dispatch error</dt>
              <dd>{paymentDispatchState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
      <div className="api-actions" data-testid="api-backed-ops-keycloak-login">
        <button
          type="button"
          onClick={() => void runKeycloakLoginSmoke("operator")}
          disabled={
            !apiBaseUrl ||
            !keycloakBaseUrl ||
            keycloakOperatorState.status === "redirecting" ||
            keycloakOperatorState.status === "exchanging"
          }
        >
          Sign in ops operator with Keycloak
        </button>
        <dl>
          <div>
            <dt>Operator Login</dt>
            <dd>{keycloakOperatorLabel(keycloakOperatorState)}</dd>
          </div>
          {keycloakOperatorState.status === "loaded" ? (
            <>
              <div>
                <dt>Token</dt>
                <dd>{keycloakOperatorState.tokenType}</dd>
              </div>
              <div>
                <dt>Item</dt>
                <dd>{keycloakOperatorState.item.itemId}</dd>
              </div>
              <div>
                <dt>Workflow</dt>
                <dd>{keycloakOperatorState.item.status}</dd>
              </div>
              <div>
                <dt>Mismatch</dt>
                <dd>
                  {keycloakOperatorState.item.amountMinor} {keycloakOperatorState.item.currency}
                </dd>
              </div>
            </>
          ) : null}
          {keycloakOperatorState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{keycloakOperatorState.message}</dd>
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
          Sign in reconciliation checker with Keycloak
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
          onClick={runKeycloakReconciliationAdjustmentSmoke}
          disabled={
            !apiBaseUrl ||
            keycloakOperatorState.status !== "loaded" ||
            keycloakCheckerState.status !== "loaded" ||
            keycloakCommandState.status === "running" ||
            keycloakCommandState.status === "adjusted"
          }
        >
          Run Keycloak reconciliation adjustment smoke
        </button>
        <dl>
          <div>
            <dt>Keycloak Command</dt>
            <dd>{keycloakCommandLabel(keycloakCommandState)}</dd>
          </div>
          {keycloakCommandState.status === "adjusted" ? (
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
                <dt>Item</dt>
                <dd>{keycloakCommandState.execution.reconciliationItem?.itemId}</dd>
              </div>
              <div>
                <dt>Workflow</dt>
                <dd>{keycloakCommandState.execution.reconciliationItem?.status}</dd>
              </div>
              <div>
                <dt>Ledger</dt>
                <dd>{keycloakCommandState.execution.ledgerTransaction?.value.id}</dd>
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
          onClick={runKeycloakReconciliationWorkflowFailureSmoke}
          disabled={!apiBaseUrl || keycloakOperatorState.status !== "loaded" || keycloakWorkflowFailureState.status === "running"}
        >
          Run Keycloak reconciliation failure smoke
        </button>
        <dl>
          <div>
            <dt>Keycloak Failure</dt>
            <dd>{workflowFailureLabel(keycloakWorkflowFailureState)}</dd>
          </div>
          {keycloakWorkflowFailureState.status === "rejected" ? (
            <>
              <div>
                <dt>Item</dt>
                <dd>{reconciliationFailureItemId}</dd>
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
    return "item loaded";
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
  if (state.status === "adjusted") {
    return "reconciliation adjustment approved";
  }
  return "failed";
}

function eodMonitorLabel(state: EodMonitorState): string {
  if (state.status === "offline") {
    return "API URL not configured";
  }
  if (state.status === "loading") {
    return "loading";
  }
  if (state.status === "loaded") {
    return state.monitor.status;
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

function paymentDispatchLabel(state: PaymentOutboxDispatchState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "loaded") {
    return "payment outbox dispatch recorded";
  }
  return "failed";
}

function reconciliationParameterLabel(state: ReconciliationParameterState): string {
  if (state.status === "offline") {
    return "API URL not configured";
  }
  if (state.status === "loading") {
    return "loading";
  }
  if (state.status === "loaded") {
    return "reconciliation parameters loaded";
  }
  return "failed";
}

function reconciliationParameterCommandLabel(state: ReconciliationParameterCommandState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "requested") {
    return "reconciliation parameter change requested";
  }
  return "failed";
}

function ledgerProjectionWorkflowLabel(state: LedgerProjectionWorkflowState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "completed") {
    return "projection rebuild completed";
  }
  return "failed";
}

function keycloakOperatorLabel(state: OperatorKeycloakLoginState): string {
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
    return "Keycloak reconciliation item loaded";
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
    return "Keycloak reconciliation checker loaded";
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
  if (state.status === "adjusted") {
    return "Keycloak reconciliation adjusted";
  }
  return "failed";
}

function toReconciliationParameterState(response: ParameterListResponse): ReconciliationParameterState {
  const parameter = response.items.find((item) => item.parameterKey === "autoMatchToleranceMinor");
  if (!parameter) {
    return { status: "failed", message: "autoMatchToleranceMinor parameter missing" };
  }
  return {
    status: "loaded",
    auditEventId: response.auditEventId,
    parameterKey: parameter.parameterKey,
    currentValue: parameter.currentValue,
    currentVersionId: parameter.currentVersionId,
    scheduledCount: parameter.scheduled.length
  };
}

function toReconciliationParameterCommandState(response: ParameterChangeRequestResponse): ReconciliationParameterCommandState {
  return {
    status: "requested",
    requestId: response.item.requestId,
    approvalId: response.approval?.approvalId ?? "approval-missing",
    parameterKey: response.item.parameterKey,
    requestedValue: response.item.requestedValue,
    effectiveFrom: response.item.effectiveFrom,
    requestStatus: response.item.status
  };
}

function nextNumericParameterValue(currentValue: string, increment: number): number {
  const parsed = Number.parseInt(currentValue, 10);
  if (!Number.isFinite(parsed)) {
    throw new Error("OPS autoMatchToleranceMinor parameter is not numeric");
  }
  return parsed + increment;
}

function tomorrowIsoDate(): string {
  const value = new Date();
  value.setUTCDate(value.getUTCDate() + 1);
  return value.toISOString().slice(0, 10);
}

function reconciliationParameterClient(stepUp = false) {
  const nowEpochSeconds = Math.floor(Date.now() / 1000);
  return createBankingApiClient({
    baseUrl: apiBaseUrl,
    bearerToken: createSimulatorBearerToken({
      subject: "ops01",
      roles: ["OPS_MANAGER"],
      ...(stepUp
        ? {
            authTimeEpochSeconds: nowEpochSeconds,
            issuedAtEpochSeconds: nowEpochSeconds,
            authenticationMethods: ["mfa"],
            assuranceLevel: "banking-lab-step-up"
          }
        : {})
    })
  });
}

function projectionClient(subject: string, roles: readonly string[], stepUp = false) {
  const nowEpochSeconds = Math.floor(Date.now() / 1000);
  return createBankingApiClient({
    baseUrl: apiBaseUrl,
    bearerToken: createSimulatorBearerToken({
      subject,
      roles,
      ...(stepUp
        ? {
            authTimeEpochSeconds: nowEpochSeconds,
            issuedAtEpochSeconds: nowEpochSeconds,
            authenticationMethods: ["mfa"],
            assuranceLevel: "banking-lab-step-up"
          }
        : {})
    })
  });
}

function isOidcIntent(value: string | null): value is OidcIntent {
  return value === "operator" || value === "checker";
}

function clearOidcSession(): void {
  window.sessionStorage.removeItem(oidcStateKey);
  window.sessionStorage.removeItem(oidcVerifierKey);
  window.sessionStorage.removeItem(oidcRedirectKey);
  window.sessionStorage.removeItem(oidcIntentKey);
}

function storeOperatorLogin(state: OperatorKeycloakLoginState): void {
  if (state.status !== "loaded") {
    return;
  }
  window.sessionStorage.setItem(
    storedOperatorLoginKey,
    JSON.stringify({
      item: state.item,
      tokenType: state.tokenType,
      bearerToken: state.bearerToken
    })
  );
}

function restoreOperatorLogin(): OperatorKeycloakLoginState | null {
  const stored = window.sessionStorage.getItem(storedOperatorLoginKey);
  if (!stored) {
    return null;
  }
  try {
    const parsed = JSON.parse(stored) as {
      item?: ReconciliationItemDto;
      tokenType?: string;
      bearerToken?: string;
    };
    if (!parsed.item || !parsed.bearerToken) {
      return null;
    }
    return {
      status: "loaded",
      item: parsed.item,
      tokenType: parsed.tokenType ?? "Bearer",
      bearerToken: parsed.bearerToken
    };
  } catch {
    return null;
  }
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
