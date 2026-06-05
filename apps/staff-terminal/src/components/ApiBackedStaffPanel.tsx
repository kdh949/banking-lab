"use client";

import { useEffect, useState } from "react";
import {
  BankingApiError,
  createBankingApiClient,
  type OperationalRetryQueueItemDto,
  type PaymentInstructionResponse,
  type StaffApprovalExecutionResponse,
  type StaffCustomerDetailDto,
  type StaffUnmaskResponse
} from "@banking-lab/api-client";
import { createOidcAuthorizationUrl, createPkcePair, createSimulatorBearerToken } from "@banking-lab/auth-client";

type ApiState =
  | { readonly status: "offline" }
  | { readonly status: "loading" }
  | { readonly status: "loaded"; readonly auditEventId: string; readonly customer: StaffCustomerDetailDto }
  | { readonly status: "failed"; readonly message: string };

type CommandState =
  | { readonly status: "disabled" }
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | {
      readonly status: "changed";
      readonly approvalId: string;
      readonly selfApprovalCode: string;
      readonly execution: StaffApprovalExecutionResponse;
    }
  | { readonly status: "failed"; readonly message: string };

type UnmaskState =
  | { readonly status: "disabled" }
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | {
      readonly status: "unmasked";
      readonly deniedCode?: string;
      readonly actor: string;
      readonly response: StaffUnmaskResponse;
    }
  | { readonly status: "failed"; readonly message: string };

type StaffKeycloakLoginState =
  | { readonly status: "offline" }
  | { readonly status: "idle" }
  | { readonly status: "redirecting" }
  | { readonly status: "exchanging" }
  | {
      readonly status: "loaded";
      readonly auditEventId: string;
      readonly customer: StaffCustomerDetailDto;
      readonly tokenType: string;
      readonly bearerToken: string;
    }
  | { readonly status: "failed"; readonly message: string };

type CheckerKeycloakLoginState =
  | { readonly status: "offline" }
  | { readonly status: "idle" }
  | { readonly status: "redirecting" }
  | { readonly status: "exchanging" }
  | { readonly status: "loaded"; readonly subject: string; readonly tokenType: string; readonly bearerToken: string }
  | { readonly status: "failed"; readonly message: string };

type WebAuthnKeycloakLoginState =
  | { readonly status: "offline" }
  | { readonly status: "idle" }
  | { readonly status: "redirecting" }
  | { readonly status: "exchanging" }
  | {
      readonly status: "loaded";
      readonly subject: string;
      readonly tokenType: string;
      readonly auditEventId: string;
      readonly customer: StaffCustomerDetailDto;
    }
  | { readonly status: "failed"; readonly message: string };

type KeycloakCommandState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | {
      readonly status: "changed";
      readonly approvalId: string;
      readonly maker: string;
      readonly checker: string;
      readonly execution: StaffApprovalExecutionResponse;
    }
  | { readonly status: "failed"; readonly message: string };

type KeycloakUnmaskState =
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | {
      readonly status: "unmasked";
      readonly actor: string;
      readonly response: StaffUnmaskResponse;
    }
  | { readonly status: "failed"; readonly message: string };

type PaymentInquiryState =
  | { readonly status: "disabled" }
  | { readonly status: "idle" }
  | { readonly status: "running" }
  | {
      readonly status: "loaded";
      readonly created: PaymentInstructionResponse;
      readonly inquiry: PaymentInstructionResponse;
      readonly reason: string;
    }
  | { readonly status: "failed"; readonly message: string };

type OperationalRetryQueueState =
  | { readonly status: "offline" }
  | { readonly status: "loading" }
  | { readonly status: "loaded"; readonly auditEventId: string; readonly items: readonly OperationalRetryQueueItemDto[] }
  | { readonly status: "failed"; readonly message: string };

type OidcIntent = "staff" | "checker" | "webauthn";

const apiBaseUrl = process.env.NEXT_PUBLIC_BANKING_API_BASE_URL ?? "";
const paymentApiBaseUrl = process.env.NEXT_PUBLIC_BANKING_PAYMENT_API_BASE_URL || apiBaseUrl;
const keycloakBaseUrl = process.env.NEXT_PUBLIC_BANKING_KEYCLOAK_BASE_URL ?? "";
const simulatorTokenSmokesEnabled = process.env.NEXT_PUBLIC_BANKING_SIMULATOR_TOKENS_ENABLED !== "false";
const reason = "API-backed channel parity smoke";
const changeCustomerId = "SYN-CUS-CMD-001";
const oidcStateKey = "bankingLabStaffOidcState";
const oidcVerifierKey = "bankingLabStaffOidcVerifier";
const oidcRedirectKey = "bankingLabStaffOidcRedirectUri";
const oidcIntentKey = "bankingLabStaffOidcIntent";
const storedStaffLoginKey = "bankingLabStaffKeycloakLogin";

export function ApiBackedStaffPanel() {
  const [state, setState] = useState<ApiState>(() =>
    apiBaseUrl && simulatorTokenSmokesEnabled ? { status: "loading" } : { status: "offline" }
  );
  const [commandState, setCommandState] = useState<CommandState>(() =>
    simulatorTokenSmokesEnabled ? { status: "idle" } : { status: "disabled" }
  );
  const [unmaskState, setUnmaskState] = useState<UnmaskState>(() =>
    simulatorTokenSmokesEnabled ? { status: "idle" } : { status: "disabled" }
  );
  const [keycloakStaffState, setKeycloakStaffState] = useState<StaffKeycloakLoginState>(() =>
    apiBaseUrl && keycloakBaseUrl ? { status: "idle" } : { status: "offline" }
  );
  const [keycloakCheckerState, setKeycloakCheckerState] = useState<CheckerKeycloakLoginState>(() =>
    apiBaseUrl && keycloakBaseUrl ? { status: "idle" } : { status: "offline" }
  );
  const [keycloakWebAuthnState, setKeycloakWebAuthnState] = useState<WebAuthnKeycloakLoginState>(() =>
    apiBaseUrl && keycloakBaseUrl ? { status: "idle" } : { status: "offline" }
  );
  const [keycloakCommandState, setKeycloakCommandState] = useState<KeycloakCommandState>({ status: "idle" });
  const [keycloakUnmaskState, setKeycloakUnmaskState] = useState<KeycloakUnmaskState>({ status: "idle" });
  const [paymentInquiryState, setPaymentInquiryState] = useState<PaymentInquiryState>(() =>
    simulatorTokenSmokesEnabled ? { status: "idle" } : { status: "disabled" }
  );
  const [retryQueueState, setRetryQueueState] = useState<OperationalRetryQueueState>(() =>
    apiBaseUrl && simulatorTokenSmokesEnabled ? { status: "loading" } : { status: "offline" }
  );

  useEffect(() => {
    if (!apiBaseUrl || !simulatorTokenSmokesEnabled) {
      return;
    }
    let cancelled = false;
    const client = createBankingApiClient({
      baseUrl: apiBaseUrl,
      bearerToken: createSimulatorBearerToken({
        subject: "branch01",
        roles: ["BRANCH_STAFF"]
      })
    });

    client
      .staffCustomerDetail("SYN-CUS-001", reason)
      .then((response) => {
        if (!cancelled) {
          setState({ status: "loaded", auditEventId: response.auditEventId, customer: response.item });
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
    const storedStaffLogin = restoreStaffLogin();
    if (storedStaffLogin) {
      setKeycloakStaffState(storedStaffLogin);
    }
  }, []);

  useEffect(() => {
    if (!apiBaseUrl || !simulatorTokenSmokesEnabled) {
      return;
    }
    let cancelled = false;
    const client = createBankingApiClient({
      baseUrl: apiBaseUrl,
      bearerToken: createSimulatorBearerToken({
        subject: "ops01",
        roles: ["OPS_MANAGER"]
      })
    });

    client
      .staffOperationalRetryQueue("API-backed operational retry queue smoke", "FAILED")
      .then((response) => {
        if (!cancelled) {
          setRetryQueueState({ status: "loaded", auditEventId: response.auditEventId, items: response.items });
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setRetryQueueState({ status: "failed", message: error instanceof Error ? error.message : "Unknown retry queue failure" });
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
      setKeycloakStaffState({ status: "failed", message: "Keycloak state verification failed" });
      setKeycloakCheckerState({ status: "failed", message: "Keycloak state verification failed" });
      setKeycloakWebAuthnState({ status: "failed", message: "Keycloak state verification failed" });
      clearOidcSession();
      return;
    }

    let cancelled = false;
    if (intent === "staff") {
      setKeycloakStaffState({ status: "exchanging" });
    } else if (intent === "checker") {
      setKeycloakCheckerState({ status: "exchanging" });
    } else {
      setKeycloakWebAuthnState({ status: "exchanging" });
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
        if (intent === "staff") {
          const client = createBankingApiClient({ baseUrl: apiBaseUrl, bearerToken });
          const responseBody = await client.staffCustomerDetail("SYN-CUS-001", "Browser Keycloak staff masked lookup smoke");
          if (!cancelled) {
            const loaded: StaffKeycloakLoginState = {
              status: "loaded",
              auditEventId: responseBody.auditEventId,
              customer: responseBody.item,
              tokenType,
              bearerToken
            };
            storeStaffLogin(loaded);
            setKeycloakStaffState(loaded);
          }
        } else if (intent === "checker" && !cancelled) {
          setKeycloakCheckerState({
            status: "loaded",
            subject: "manager01",
            tokenType,
            bearerToken
          });
        } else {
          const client = createBankingApiClient({ baseUrl: apiBaseUrl, bearerToken });
          const responseBody = await client.staffCustomerDetail("SYN-CUS-001", "Browser Keycloak WebAuthn staff step-up smoke");
          if (!cancelled) {
            setKeycloakWebAuthnState({
              status: "loaded",
              subject: "manager-webauthn01",
              tokenType,
              auditEventId: responseBody.auditEventId,
              customer: responseBody.item
            });
          }
        }
        if (!cancelled) {
          clearOidcSession();
          window.history.replaceState(null, "", window.location.pathname);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          const message = error instanceof Error ? error.message : "Unknown Keycloak login failure";
          if (intent === "staff") {
            setKeycloakStaffState({ status: "failed", message });
          } else if (intent === "checker") {
            setKeycloakCheckerState({ status: "failed", message });
          } else {
            setKeycloakWebAuthnState({ status: "failed", message });
          }
          clearOidcSession();
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const runKeycloakLoginSmoke = async (intent: OidcIntent) => {
    if (!apiBaseUrl || !keycloakBaseUrl) {
      return;
    }
    if (intent === "staff" && (keycloakStaffState.status === "redirecting" || keycloakStaffState.status === "exchanging")) {
      return;
    }
    if (intent === "checker" && (keycloakCheckerState.status === "redirecting" || keycloakCheckerState.status === "exchanging")) {
      return;
    }
    if (intent === "webauthn" && (keycloakWebAuthnState.status === "redirecting" || keycloakWebAuthnState.status === "exchanging")) {
      return;
    }

    if (intent === "staff") {
      setKeycloakStaffState({ status: "redirecting" });
    } else if (intent === "checker") {
      setKeycloakCheckerState({ status: "redirecting" });
    } else {
      setKeycloakWebAuthnState({ status: "redirecting" });
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
          clientId: "staff-terminal",
          redirectUri,
          state: stateValue,
          codeChallenge: pkce.codeChallenge,
          prompt: intent === "staff" ? undefined : "login",
          loginHint: keycloakLoginHint(intent)
        })
      );
    } catch (error: unknown) {
      clearOidcSession();
      const message = error instanceof Error ? error.message : "Unknown Keycloak redirect failure";
      if (intent === "staff") {
        setKeycloakStaffState({ status: "failed", message });
      } else if (intent === "checker") {
        setKeycloakCheckerState({ status: "failed", message });
      } else {
        setKeycloakWebAuthnState({ status: "failed", message });
      }
    }
  };

  const runCustomerChangeSmoke = async () => {
    if (!apiBaseUrl || !simulatorTokenSmokesEnabled || commandState.status === "running") {
      return;
    }
    setCommandState({ status: "running" });
    try {
      const branchClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "branch01",
          roles: ["BRANCH_STAFF"]
        })
      });
      const managerClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "manager01",
          roles: ["BRANCH_MANAGER"]
        })
      });
      const selfCheckerClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "branch01",
          roles: ["BRANCH_MANAGER"]
        })
      });
      const request = await branchClient.requestCustomerInfoChange(changeCustomerId, {
        requestedBy: "branch01",
        requestedByRole: "BRANCH_STAFF",
        reason: "Browser customer change smoke",
        afterSnapshot: {
          phone: "010-0000-1399",
          address: "Seoul Synthetic Browser Updated"
        }
      });

      let selfApprovalCode = "not_checked";
      try {
        await selfCheckerClient.approveStaffApproval(request.item.approvalId, {
          approvedBy: "branch01",
          approvedByRole: "BRANCH_MANAGER",
          screenId: "CST-103"
        });
        throw new Error("self approval unexpectedly succeeded");
      } catch (error: unknown) {
        selfApprovalCode = extractErrorCode(error);
        if (selfApprovalCode !== "MAKER_CHECKER_SELF_APPROVAL_REJECTED") {
          throw error;
        }
      }

      const execution = await managerClient.approveStaffApproval(request.item.approvalId, {
        approvedBy: "manager01",
        approvedByRole: "BRANCH_MANAGER",
        screenId: "CST-103"
      });
      setCommandState({
        status: "changed",
        approvalId: request.item.approvalId,
        selfApprovalCode,
        execution
      });
    } catch (error: unknown) {
      setCommandState({ status: "failed", message: error instanceof Error ? error.message : "Unknown API failure" });
    }
  };

  const runUnmaskSmoke = async () => {
    if (!apiBaseUrl || !simulatorTokenSmokesEnabled || unmaskState.status === "running") {
      return;
    }
    setUnmaskState({ status: "running" });
    try {
      const branchClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "branch01",
          roles: ["BRANCH_STAFF"]
        })
      });
      const managerClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "manager01",
          roles: ["BRANCH_MANAGER"]
        })
      });

      let deniedCode = "not_checked";
      try {
        await branchClient.unmaskStaffCustomer({
          customerId: "SYN-CUS-001",
          requestedBy: "branch01",
          actorRole: "BRANCH_STAFF",
          reason: "Browser privileged unmask denial smoke",
          screenId: "CST-002"
        });
        throw new Error("branch unmask unexpectedly succeeded");
      } catch (error: unknown) {
        deniedCode = extractErrorCode(error);
        if (deniedCode !== "AUTHORIZATION_POLICY_VIOLATION") {
          throw error;
        }
      }

      const response = await managerClient.unmaskStaffCustomer({
        customerId: "SYN-CUS-001",
        requestedBy: "manager01",
        actorRole: "BRANCH_MANAGER",
        reason: "Browser privileged unmask approval smoke",
        screenId: "CST-002"
      });
      setUnmaskState({
        status: "unmasked",
        deniedCode,
        actor: "manager01",
        response
      });
    } catch (error: unknown) {
      setUnmaskState({ status: "failed", message: error instanceof Error ? error.message : "Unknown unmask API failure" });
    }
  };

  const runPaymentInquirySmoke = async () => {
    if (!paymentApiBaseUrl || !simulatorTokenSmokesEnabled || paymentInquiryState.status === "running") {
      return;
    }
    setPaymentInquiryState({ status: "running" });
    try {
      const customerClient = createBankingApiClient({
        baseUrl: paymentApiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "customer01",
          roles: ["CUSTOMER"],
          customerId: "SYN-CUS-001"
        })
      });
      const staffClient = createBankingApiClient({
        baseUrl: paymentApiBaseUrl,
        bearerToken: createSimulatorBearerToken({
          subject: "branch01",
          roles: ["BRANCH_STAFF"]
        })
      });
      const runId = Date.now();
      const created = await customerClient.createPaymentInstruction({
        customerId: "SYN-CUS-001",
        debitAccountId: "ACC-SYN-001-001",
        billerId: "SYN-BILLER-UTIL-001",
        amountMinor: 8_000,
        currency: "KRW",
        idempotencyKey: `PAY101-CREATE-${runId}`,
        requestedBy: "customer01",
        requestedChannel: "CUSTOMER_WEB",
        reason: "Browser staff payment inquiry seed payment"
      });
      const lookupReason = "Browser PAY-101 payment instruction inquiry smoke";
      const inquiry = await staffClient.getPaymentInstruction(created.item.paymentInstructionId, lookupReason);
      if (!inquiry.auditEventId?.startsWith("PAU-") || inquiry.item.paymentInstructionId !== created.item.paymentInstructionId) {
        setPaymentInquiryState({ status: "failed", message: "payment instruction inquiry did not return audited PAY-101 result" });
        return;
      }
      setPaymentInquiryState({ status: "loaded", created, inquiry, reason: lookupReason });
    } catch (error: unknown) {
      setPaymentInquiryState({ status: "failed", message: error instanceof Error ? error.message : "Unknown payment inquiry failure" });
    }
  };

  const runKeycloakCustomerChangeSmoke = async () => {
    if (
      !apiBaseUrl ||
      keycloakStaffState.status !== "loaded" ||
      keycloakCheckerState.status !== "loaded" ||
      keycloakCommandState.status === "running"
    ) {
      return;
    }
    setKeycloakCommandState({ status: "running" });
    try {
      const branchClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: keycloakStaffState.bearerToken
      });
      const managerClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: keycloakCheckerState.bearerToken
      });
      const request = await branchClient.requestCustomerInfoChange(changeCustomerId, {
        requestedBy: "branch01",
        requestedByRole: "BRANCH_STAFF",
        reason: "Browser Keycloak staff customer change smoke",
        afterSnapshot: {
          phone: "010-0000-1499",
          address: "Seoul Synthetic Keycloak Updated"
        }
      });

      const execution = await managerClient.approveStaffApproval(request.item.approvalId, {
        approvedBy: "manager01",
        approvedByRole: "BRANCH_MANAGER",
        screenId: "CST-103"
      });
      setKeycloakCommandState({
        status: "changed",
        approvalId: request.item.approvalId,
        maker: request.item.requestedBy,
        checker: execution.item.approvedBy ?? "manager01",
        execution
      });
    } catch (error: unknown) {
      setKeycloakCommandState({ status: "failed", message: error instanceof Error ? error.message : "Unknown Keycloak staff command failure" });
    }
  };

  const runKeycloakUnmaskSmoke = async () => {
    if (!apiBaseUrl || keycloakCheckerState.status !== "loaded" || keycloakUnmaskState.status === "running") {
      return;
    }
    setKeycloakUnmaskState({ status: "running" });
    try {
      const managerClient = createBankingApiClient({
        baseUrl: apiBaseUrl,
        bearerToken: keycloakCheckerState.bearerToken
      });
      const response = await managerClient.unmaskStaffCustomer({
        customerId: "SYN-CUS-001",
        requestedBy: "manager01",
        actorRole: "BRANCH_MANAGER",
        reason: "Browser Keycloak privileged unmask smoke",
        screenId: "CST-002"
      });
      setKeycloakUnmaskState({
        status: "unmasked",
        actor: "manager01",
        response
      });
    } catch (error: unknown) {
      setKeycloakUnmaskState({ status: "failed", message: error instanceof Error ? error.message : "Unknown Keycloak unmask failure" });
    }
  };

  return (
    <section className="panel api-panel" aria-label="API-backed staff customer inquiry">
      <h2>API-backed Inquiry</h2>
      <dl data-testid="api-backed-staff-customer">
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
              <dt>Customer</dt>
              <dd>{state.customer.customerId}</dd>
            </div>
            <div>
              <dt>Masked phone</dt>
              <dd>{state.customer.maskedPhone}</dd>
            </div>
            <div>
              <dt>Audit event</dt>
              <dd>{state.auditEventId}</dd>
            </div>
          </>
        ) : null}
        {state.status === "failed" ? (
          <div>
            <dt>Error</dt>
            <dd>{state.message}</dd>
          </div>
        ) : null}
        <div>
          <dt>Reason</dt>
          <dd>{reason}</dd>
        </div>
      </dl>
      <div className="api-actions" data-testid="api-backed-staff-change-command">
        <button
          type="button"
          onClick={runCustomerChangeSmoke}
          disabled={!apiBaseUrl || !simulatorTokenSmokesEnabled || commandState.status === "running"}
        >
          Run customer change smoke
        </button>
        <dl>
          <div>
            <dt>Command</dt>
            <dd>{commandLabel(commandState)}</dd>
          </div>
          {commandState.status === "changed" ? (
            <>
              <div>
                <dt>Approval</dt>
                <dd>{commandState.approvalId}</dd>
              </div>
              <div>
                <dt>Self approval</dt>
                <dd>{commandState.selfApprovalCode}</dd>
              </div>
              <div>
                <dt>Customer</dt>
                <dd>{commandState.execution.customer?.customerId}</dd>
              </div>
              <div>
                <dt>Masked phone</dt>
                <dd>{commandState.execution.customer?.maskedPhone}</dd>
              </div>
              <div>
                <dt>Executed</dt>
                <dd>{commandState.execution.executed ? "customer change executed" : "not executed"}</dd>
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
      <div className="api-actions" data-testid="api-backed-staff-unmask-command">
        <button
          type="button"
          onClick={runUnmaskSmoke}
          disabled={!apiBaseUrl || !simulatorTokenSmokesEnabled || unmaskState.status === "running"}
        >
          Run privileged unmask smoke
        </button>
        <dl>
          <div>
            <dt>Unmask</dt>
            <dd>{unmaskLabel(unmaskState)}</dd>
          </div>
          {unmaskState.status === "unmasked" ? (
            <>
              <div>
                <dt>Denied branch role</dt>
                <dd>{unmaskState.deniedCode}</dd>
              </div>
              <div>
                <dt>Actor</dt>
                <dd>{unmaskState.actor}</dd>
              </div>
              <div>
                <dt>PII exposure</dt>
                <dd>{unmaskState.response.item.piiExposure}</dd>
              </div>
              <div>
                <dt>Phone</dt>
                <dd>{unmaskState.response.item.phone}</dd>
              </div>
              <div>
                <dt>TTL</dt>
                <dd>{unmaskState.response.expiresInSeconds}</dd>
              </div>
              <div>
                <dt>Audit event</dt>
                <dd>{unmaskState.response.auditEventId}</dd>
              </div>
            </>
          ) : null}
          {unmaskState.status === "failed" ? (
            <div>
              <dt>Unmask error</dt>
              <dd>{unmaskState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
      <div className="api-actions" data-testid="api-backed-staff-payment-inquiry">
        <button
          type="button"
          onClick={runPaymentInquirySmoke}
          disabled={!paymentApiBaseUrl || !simulatorTokenSmokesEnabled || paymentInquiryState.status === "running"}
        >
          Run payment inquiry smoke
        </button>
        <dl>
          <div>
            <dt>Payment inquiry</dt>
            <dd>{paymentInquiryLabel(paymentInquiryState)}</dd>
          </div>
          {paymentInquiryState.status === "loaded" ? (
            <>
              <div>
                <dt>Instruction</dt>
                <dd>{paymentInquiryState.inquiry.item.paymentInstructionId}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>{paymentInquiryState.inquiry.item.status}</dd>
              </div>
              <div>
                <dt>Biller</dt>
                <dd>{paymentInquiryState.inquiry.item.billerName}</dd>
              </div>
              <div>
                <dt>Debit account</dt>
                <dd>{paymentInquiryState.inquiry.item.debitAccountId}</dd>
              </div>
              <div>
                <dt>Audit event</dt>
                <dd>{paymentInquiryState.inquiry.auditEventId}</dd>
              </div>
              <div>
                <dt>Reason</dt>
                <dd>{paymentInquiryState.reason}</dd>
              </div>
            </>
          ) : null}
          {paymentInquiryState.status === "failed" ? (
            <div>
              <dt>Payment inquiry error</dt>
              <dd>{paymentInquiryState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
      <div className="api-actions" data-testid="api-backed-operational-retry-queue">
        <dl>
          <div>
            <dt>Retry queue</dt>
            <dd>{retryQueueLabel(retryQueueState)}</dd>
          </div>
          {retryQueueState.status === "loaded" ? (
            <>
              <div>
                <dt>Audit event</dt>
                <dd>{retryQueueState.auditEventId}</dd>
              </div>
              <div>
                <dt>Queue count</dt>
                <dd>{retryQueueState.items.length}</dd>
              </div>
              <div>
                <dt>First event</dt>
                <dd>{retryQueueState.items[0]?.outboxEventId ?? "none"}</dd>
              </div>
              <div>
                <dt>First status</dt>
                <dd>{retryQueueState.items[0]?.status ?? "none"}</dd>
              </div>
              <div>
                <dt>Retry eligible</dt>
                <dd>{retryQueueState.items[0]?.retryEligible ? "eligible" : "not eligible"}</dd>
              </div>
            </>
          ) : null}
          {retryQueueState.status === "failed" ? (
            <div>
              <dt>Retry queue error</dt>
              <dd>{retryQueueState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
      <div className="api-actions" data-testid="api-backed-staff-keycloak-login">
        <button
          type="button"
          onClick={() => void runKeycloakLoginSmoke("staff")}
          disabled={!apiBaseUrl || !keycloakBaseUrl || keycloakStaffState.status === "redirecting" || keycloakStaffState.status === "exchanging"}
        >
          Sign in staff with Keycloak
        </button>
        <dl>
          <div>
            <dt>Staff login</dt>
            <dd>{keycloakStaffLabel(keycloakStaffState)}</dd>
          </div>
          {keycloakStaffState.status === "loaded" ? (
            <>
              <div>
                <dt>Staff token</dt>
                <dd>{keycloakStaffState.tokenType}</dd>
              </div>
              <div>
                <dt>Customer</dt>
                <dd>{keycloakStaffState.customer.customerId}</dd>
              </div>
              <div>
                <dt>Masked phone</dt>
                <dd>{keycloakStaffState.customer.maskedPhone}</dd>
              </div>
              <div>
                <dt>Audit event</dt>
                <dd>{keycloakStaffState.auditEventId}</dd>
              </div>
            </>
          ) : null}
          {keycloakStaffState.status === "failed" ? (
            <div>
              <dt>Staff login error</dt>
              <dd>{keycloakStaffState.message}</dd>
            </div>
          ) : null}
        </dl>
        <button
          type="button"
          onClick={() => void runKeycloakLoginSmoke("checker")}
          disabled={
            !apiBaseUrl ||
            !keycloakBaseUrl ||
            keycloakStaffState.status !== "loaded" ||
            keycloakCheckerState.status === "redirecting" ||
            keycloakCheckerState.status === "exchanging"
          }
        >
          Sign in checker with Keycloak
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
          onClick={runKeycloakUnmaskSmoke}
          disabled={!apiBaseUrl || keycloakCheckerState.status !== "loaded" || keycloakUnmaskState.status === "running"}
        >
          Run Keycloak unmask smoke
        </button>
        <dl>
          <div>
            <dt>Keycloak unmask</dt>
            <dd>{keycloakUnmaskLabel(keycloakUnmaskState)}</dd>
          </div>
          {keycloakUnmaskState.status === "unmasked" ? (
            <>
              <div>
                <dt>Unmask actor</dt>
                <dd>{keycloakUnmaskState.actor}</dd>
              </div>
              <div>
                <dt>Unmask exposure</dt>
                <dd>{keycloakUnmaskState.response.item.piiExposure}</dd>
              </div>
              <div>
                <dt>Unmasked phone</dt>
                <dd>{keycloakUnmaskState.response.item.phone}</dd>
              </div>
              <div>
                <dt>Unmask TTL</dt>
                <dd>{keycloakUnmaskState.response.expiresInSeconds}</dd>
              </div>
              <div>
                <dt>Unmask audit</dt>
                <dd>{keycloakUnmaskState.response.auditEventId}</dd>
              </div>
            </>
          ) : null}
          {keycloakUnmaskState.status === "failed" ? (
            <div>
              <dt>Keycloak unmask error</dt>
              <dd>{keycloakUnmaskState.message}</dd>
            </div>
          ) : null}
        </dl>
        <button
          type="button"
          onClick={() => void runKeycloakLoginSmoke("webauthn")}
          disabled={!apiBaseUrl || !keycloakBaseUrl || keycloakWebAuthnState.status === "redirecting" || keycloakWebAuthnState.status === "exchanging"}
        >
          Sign in WebAuthn manager with Keycloak
        </button>
        <dl>
          <div>
            <dt>WebAuthn login</dt>
            <dd>{keycloakWebAuthnLabel(keycloakWebAuthnState)}</dd>
          </div>
          {keycloakWebAuthnState.status === "loaded" ? (
            <>
              <div>
                <dt>WebAuthn manager</dt>
                <dd>{keycloakWebAuthnState.subject}</dd>
              </div>
              <div>
                <dt>WebAuthn token</dt>
                <dd>{keycloakWebAuthnState.tokenType}</dd>
              </div>
              <div>
                <dt>Customer</dt>
                <dd>{keycloakWebAuthnState.customer.customerId}</dd>
              </div>
              <div>
                <dt>Masked phone</dt>
                <dd>{keycloakWebAuthnState.customer.maskedPhone}</dd>
              </div>
              <div>
                <dt>Audit event</dt>
                <dd>{keycloakWebAuthnState.auditEventId}</dd>
              </div>
            </>
          ) : null}
          {keycloakWebAuthnState.status === "failed" ? (
            <div>
              <dt>WebAuthn login error</dt>
              <dd>{keycloakWebAuthnState.message}</dd>
            </div>
          ) : null}
        </dl>
        <button
          type="button"
          onClick={runKeycloakCustomerChangeSmoke}
          disabled={
            !apiBaseUrl ||
            keycloakStaffState.status !== "loaded" ||
            keycloakCheckerState.status !== "loaded" ||
            keycloakCommandState.status === "running"
          }
        >
          Run Keycloak customer change smoke
        </button>
        <dl>
          <div>
            <dt>Keycloak command</dt>
            <dd>{keycloakCommandLabel(keycloakCommandState)}</dd>
          </div>
          {keycloakCommandState.status === "changed" ? (
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
                <dt>Customer</dt>
                <dd>{keycloakCommandState.execution.customer?.customerId}</dd>
              </div>
              <div>
                <dt>Masked phone</dt>
                <dd>{keycloakCommandState.execution.customer?.maskedPhone}</dd>
              </div>
              <div>
                <dt>Executed</dt>
                <dd>{keycloakCommandState.execution.executed ? "Keycloak customer change executed" : "not executed"}</dd>
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
      </div>
    </section>
  );
}

function statusLabel(state: ApiState): string {
  if (state.status === "offline") {
    return apiBaseUrl && !simulatorTokenSmokesEnabled ? "simulator token smoke disabled" : "API URL not configured";
  }
  if (state.status === "loading") {
    return "loading";
  }
  if (state.status === "loaded") {
    return "masked detail loaded";
  }
  return "failed";
}

function commandLabel(state: CommandState): string {
  if (state.status === "disabled") {
    return "simulator token smoke disabled";
  }
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "changed") {
    return "customer change approved";
  }
  return "failed";
}

function unmaskLabel(state: UnmaskState): string {
  if (state.status === "disabled") {
    return "simulator token smoke disabled";
  }
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "unmasked") {
    return "privileged unmask approved";
  }
  return "failed";
}

function paymentInquiryLabel(state: PaymentInquiryState): string {
  if (state.status === "disabled") {
    return "simulator token smoke disabled";
  }
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "loaded") {
    return "payment inquiry audited";
  }
  return "failed";
}

function retryQueueLabel(state: OperationalRetryQueueState): string {
  if (state.status === "offline") {
    return apiBaseUrl && !simulatorTokenSmokesEnabled ? "simulator token smoke disabled" : "API URL not configured";
  }
  if (state.status === "loading") {
    return "loading";
  }
  if (state.status === "loaded") {
    return "retry queue loaded";
  }
  return "failed";
}

function keycloakStaffLabel(state: StaffKeycloakLoginState): string {
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
    return "Keycloak staff detail loaded";
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
    return "Keycloak checker loaded";
  }
  return "failed";
}

function keycloakWebAuthnLabel(state: WebAuthnKeycloakLoginState): string {
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
    return "Keycloak WebAuthn manager loaded";
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
  if (state.status === "changed") {
    return "Keycloak customer change approved";
  }
  return "failed";
}

function keycloakUnmaskLabel(state: KeycloakUnmaskState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "unmasked") {
    return "Keycloak unmask approved";
  }
  return "failed";
}

function extractErrorCode(error: unknown): string {
  if (error instanceof BankingApiError) {
    try {
      const parsed = JSON.parse(error.body) as { error?: { code?: string } };
      return parsed.error?.code ?? error.name;
    } catch {
      return error.name;
    }
  }
  return error instanceof Error ? error.name : "unknown_error";
}

function isOidcIntent(value: string | null): value is OidcIntent {
  return value === "staff" || value === "checker" || value === "webauthn";
}

function keycloakLoginHint(intent: OidcIntent): string {
  if (intent === "checker") {
    return "manager01";
  }
  if (intent === "webauthn") {
    return "manager-webauthn01";
  }
  return "branch01";
}

function clearOidcSession(): void {
  window.sessionStorage.removeItem(oidcStateKey);
  window.sessionStorage.removeItem(oidcVerifierKey);
  window.sessionStorage.removeItem(oidcRedirectKey);
  window.sessionStorage.removeItem(oidcIntentKey);
}

function storeStaffLogin(state: Extract<StaffKeycloakLoginState, { status: "loaded" }>): void {
  window.sessionStorage.setItem(storedStaffLoginKey, JSON.stringify(state));
}

function restoreStaffLogin(): StaffKeycloakLoginState | null {
  const raw = window.sessionStorage.getItem(storedStaffLoginKey);
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as StaffKeycloakLoginState;
    return parsed.status === "loaded" ? parsed : null;
  } catch {
    window.sessionStorage.removeItem(storedStaffLoginKey);
    return null;
  }
}
