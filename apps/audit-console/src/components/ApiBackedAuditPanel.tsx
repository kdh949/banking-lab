"use client";

import { useEffect, useState } from "react";
import {
  createBankingApiClient,
  type AuditEventDto,
  type NotificationDeliveryDto,
  type ParameterChangeRequestResponse,
  type ParameterListResponse,
  type ReportArtifactDto
} from "@banking-lab/api-client";
import { createOidcAuthorizationUrl, createPkcePair, createSimulatorBearerToken } from "@banking-lab/auth-client";

type ApiState =
  | { readonly status: "offline" }
  | { readonly status: "loading" }
  | { readonly status: "loaded"; readonly hashChainValid: boolean; readonly event: AuditEventDto }
  | { readonly status: "failed"; readonly message: string };

type KeycloakAuditState =
  | { readonly status: "offline" }
  | { readonly status: "idle" }
  | { readonly status: "redirecting" }
  | { readonly status: "exchanging" }
  | { readonly status: "loaded"; readonly hashChainValid: boolean; readonly event: AuditEventDto; readonly tokenType: string }
  | { readonly status: "failed"; readonly message: string };

type NotificationDeliveryState =
  | { readonly status: "offline" }
  | { readonly status: "loading" }
  | { readonly status: "loaded"; readonly deliveries: readonly NotificationDeliveryDto[] }
  | { readonly status: "failed"; readonly message: string };

type ReportingArtifactState =
  | { readonly status: "offline" }
  | { readonly status: "loading" }
  | { readonly status: "loaded"; readonly auditEventId: string; readonly artifacts: readonly ReportArtifactDto[] }
  | { readonly status: "failed"; readonly message: string };

type AuditParameterState =
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

type AuditParameterCommandState =
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

const apiBaseUrl = process.env.NEXT_PUBLIC_BANKING_API_BASE_URL ?? "";
const notificationApiBaseUrl = process.env.NEXT_PUBLIC_BANKING_NOTIFICATION_API_BASE_URL || apiBaseUrl;
const reportingApiBaseUrl = process.env.NEXT_PUBLIC_BANKING_REPORTING_API_BASE_URL ?? "";
const keycloakBaseUrl = process.env.NEXT_PUBLIC_BANKING_KEYCLOAK_BASE_URL ?? "";
const oidcStateKey = "bankingLabAuditOidcState";
const oidcVerifierKey = "bankingLabAuditOidcVerifier";
const oidcRedirectKey = "bankingLabAuditOidcRedirectUri";

export function ApiBackedAuditPanel() {
  const [state, setState] = useState<ApiState>(() => (apiBaseUrl ? { status: "loading" } : { status: "offline" }));
  const [notificationDeliveryState, setNotificationDeliveryState] = useState<NotificationDeliveryState>(() =>
    notificationApiBaseUrl ? { status: "loading" } : { status: "offline" }
  );
  const [reportingArtifactState, setReportingArtifactState] = useState<ReportingArtifactState>(() =>
    reportingApiBaseUrl ? { status: "loading" } : { status: "offline" }
  );
  const [auditParameterState, setAuditParameterState] = useState<AuditParameterState>(() =>
    apiBaseUrl ? { status: "loading" } : { status: "offline" }
  );
  const [auditParameterCommandState, setAuditParameterCommandState] = useState<AuditParameterCommandState>({ status: "idle" });
  const [keycloakAuditState, setKeycloakAuditState] = useState<KeycloakAuditState>(() =>
    apiBaseUrl && keycloakBaseUrl ? { status: "idle" } : { status: "offline" }
  );

  useEffect(() => {
    if (!apiBaseUrl) {
      return;
    }
    let cancelled = false;
    const client = createBankingApiClient({
      baseUrl: apiBaseUrl,
      bearerToken: createSimulatorBearerToken({
        subject: "auditor01",
        roles: ["AUDITOR"]
      })
    });

    client
      .auditEvents()
      .then((response) => {
        if (!cancelled) {
          const event = response.items.find((item) => item.auditEventId === "AUD-SYN-SEED-001") ?? response.items[0];
          setState(event ? { status: "loaded", hashChainValid: response.hashChainValid, event } : { status: "failed", message: "No audit events returned" });
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
    if (!notificationApiBaseUrl) {
      return;
    }
    let cancelled = false;
    const client = createBankingApiClient({
      baseUrl: notificationApiBaseUrl,
      bearerToken: createSimulatorBearerToken({
        subject: "auditor01",
        roles: ["AUDITOR"]
      })
    });

    client
      .listNotificationDeliveries({
        requestedBy: "auditor01",
        reason: "API-backed notification delivery history review",
        limit: 5
      })
      .then((deliveries) => {
        if (!cancelled) {
          setNotificationDeliveryState({ status: "loaded", deliveries });
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setNotificationDeliveryState({
            status: "failed",
            message: error instanceof Error ? error.message : "Unknown notification delivery API failure"
          });
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!reportingApiBaseUrl) {
      return;
    }
    let cancelled = false;
    const client = createBankingApiClient({
      baseUrl: reportingApiBaseUrl,
      bearerToken: createSimulatorBearerToken({
        subject: "auditor01",
        roles: ["AUDITOR"]
      })
    });

    client
      .reportArtifacts({
        reason: "API-backed reporting artifact audit review"
      })
      .then((response) => {
        if (!cancelled) {
          setReportingArtifactState({
            status: "loaded",
            auditEventId: response.auditEventId,
            artifacts: response.items
          });
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setReportingArtifactState({
            status: "failed",
            message: error instanceof Error ? error.message : "Unknown reporting artifact API failure"
          });
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
    auditParameterClient()
      .auditParameters("Browser AUD-201 parameter read smoke")
      .then((response) => {
        if (!cancelled) {
          setAuditParameterState(toAuditParameterState(response));
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setAuditParameterState({ status: "failed", message: error instanceof Error ? error.message : "Unknown audit parameter API failure" });
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
    if (!expectedState || !codeVerifier || returnedState !== expectedState) {
      setKeycloakAuditState({ status: "failed", message: "Keycloak state verification failed" });
      clearOidcSession();
      return;
    }

    let cancelled = false;
    setKeycloakAuditState({ status: "exchanging" });
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
        const client = createBankingApiClient({
          baseUrl: apiBaseUrl,
          bearerToken: `${tokenType} ${body.accessToken}`
        });
        const audit = await client.auditEvents();
        const event = audit.items.find((item) => item.auditEventId === "AUD-SYN-SEED-001") ?? audit.items[0];
        if (!event) {
          throw new Error("No audit events returned for Keycloak auditor");
        }
        if (!cancelled) {
          setKeycloakAuditState({
            status: "loaded",
            hashChainValid: audit.hashChainValid,
            event,
            tokenType
          });
          clearOidcSession();
          window.history.replaceState(null, "", window.location.pathname);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setKeycloakAuditState({ status: "failed", message: error instanceof Error ? error.message : "Unknown Keycloak audit failure" });
          clearOidcSession();
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  async function runKeycloakAuditSmoke() {
    if (!apiBaseUrl || !keycloakBaseUrl || keycloakAuditState.status === "redirecting" || keycloakAuditState.status === "exchanging") {
      return;
    }
    setKeycloakAuditState({ status: "redirecting" });
    try {
      const redirectUri = `${window.location.origin}${window.location.pathname}`;
      const stateValue = globalThis.crypto.randomUUID();
      const pkce = await createPkcePair();
      window.sessionStorage.setItem(oidcStateKey, stateValue);
      window.sessionStorage.setItem(oidcVerifierKey, pkce.codeVerifier);
      window.sessionStorage.setItem(oidcRedirectKey, redirectUri);
      window.location.assign(
        createOidcAuthorizationUrl({
          issuerBaseUrl: keycloakBaseUrl,
          realm: "banking-lab",
          clientId: "audit-console",
          redirectUri,
          state: stateValue,
          codeChallenge: pkce.codeChallenge,
          prompt: "login",
          loginHint: "auditor01"
        })
      );
    } catch (error: unknown) {
      clearOidcSession();
      setKeycloakAuditState({ status: "failed", message: error instanceof Error ? error.message : "Unknown Keycloak redirect failure" });
    }
  }

  const runAuditParameterChangeSmoke = async () => {
    if (!apiBaseUrl || auditParameterCommandState.status === "running") {
      return;
    }
    setAuditParameterCommandState({ status: "running" });
    try {
      const current = auditParameterState.status === "loaded"
        ? auditParameterState
        : toAuditParameterState(await auditParameterClient().auditParameters("Browser AUD-201 parameter command read"));
      if (current.status !== "loaded") {
        setAuditParameterCommandState({ status: "failed", message: "AUD retentionYears parameter was not loaded" });
        return;
      }
      const requestedValue = nextNumericParameterValue(current.currentValue, 1);
      const effectiveFrom = tomorrowIsoDate();
      const response = await auditParameterClient(true).requestAuditParameterChange({
        parameterKey: "retentionYears",
        scheduledValue: requestedValue,
        effectiveFrom,
        rollbackPlan: "Create a future synthetic rollback version from the prior audit retention period",
        requestedBy: "auditor01",
        requestedByRole: "AUDITOR",
        reason: "Browser AUD-201 parameter change smoke",
        idempotencyKey: `AUD-PARAM-${globalThis.crypto.randomUUID()}`
      });
      setAuditParameterCommandState(toAuditParameterCommandState(response));
    } catch (error: unknown) {
      setAuditParameterCommandState({ status: "failed", message: error instanceof Error ? error.message : "Unknown audit parameter failure" });
    }
  };

  return (
    <section className="api-panel" aria-label="API-backed audit hash chain">
      <h2>API-backed Audit</h2>
      <dl data-testid="api-backed-audit-events">
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
              <dt>Hash chain</dt>
              <dd>{state.hashChainValid ? "valid" : "invalid"}</dd>
            </div>
            <div>
              <dt>Event</dt>
              <dd>{state.event.auditEventId}</dd>
            </div>
            <div>
              <dt>Type</dt>
              <dd>{state.event.eventType}</dd>
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
      <dl data-testid="api-backed-notification-delivery-history">
        <div>
          <dt>Notification API</dt>
          <dd>{notificationApiBaseUrl || "not configured"}</dd>
        </div>
        <div>
          <dt>Delivery Status</dt>
          <dd>{notificationDeliveryStatusLabel(notificationDeliveryState)}</dd>
        </div>
        {notificationDeliveryState.status === "loaded" ? (
          <>
            <div>
              <dt>Deliveries</dt>
              <dd>{notificationDeliveryState.deliveries.length}</dd>
            </div>
            <div>
              <dt>Delivery Sample</dt>
              <dd>
                {notificationDeliveryState.deliveries[0]
                  ? `${notificationDeliveryState.deliveries[0].eventType}:${notificationDeliveryState.deliveries[0].channel}:${notificationDeliveryState.deliveries[0].status}`
                  : "none"}
              </dd>
            </div>
          </>
        ) : null}
        {notificationDeliveryState.status === "failed" ? (
          <div>
            <dt>Notification Error</dt>
            <dd>{notificationDeliveryState.message}</dd>
          </div>
        ) : null}
      </dl>
      <dl data-testid="api-backed-reporting-artifact-history">
        <div>
          <dt>Reporting API</dt>
          <dd>{reportingApiBaseUrl || "not configured"}</dd>
        </div>
        <div>
          <dt>Reporting Status</dt>
          <dd>{reportingArtifactStatusLabel(reportingArtifactState)}</dd>
        </div>
        {reportingArtifactState.status === "loaded" ? (
          <>
            <div>
              <dt>Reporting Audit</dt>
              <dd>{reportingArtifactState.auditEventId}</dd>
            </div>
            <div>
              <dt>Artifacts</dt>
              <dd>{reportingArtifactState.artifacts.length}</dd>
            </div>
            <div>
              <dt>Artifact Sample</dt>
              <dd>
                {reportingArtifactState.artifacts[0]
                  ? `${reportingArtifactState.artifacts[0].reportType}:${reportingArtifactState.artifacts[0].status}:${reportingArtifactState.artifacts[0].maskedByDefault ? "masked" : "unsafe"}:${reportingArtifactState.artifacts[0].exportFormat}`
                  : "none"}
              </dd>
            </div>
            <div>
              <dt>Artifact Retention</dt>
              <dd>
                {reportingArtifactState.artifacts[0]
                  ? `${reportingArtifactState.artifacts[0].retentionPolicy}:${reportingArtifactState.artifacts[0].retentionUntil ?? "unscheduled"}`
                  : "none"}
              </dd>
            </div>
            <div>
              <dt>Artifact Checksum</dt>
              <dd>{reportingArtifactState.artifacts[0]?.contentSha256.slice(0, 12) ?? "none"}</dd>
            </div>
            <div>
              <dt>Artifact Workflow</dt>
              <dd>
                {reportingArtifactState.artifacts[0]
                  ? `${reportingArtifactState.artifacts[0].workflowInstanceId}:${reportingArtifactState.artifacts[0].workflowStatus}`
                  : "none"}
              </dd>
            </div>
            <div>
              <dt>Workflow Timeline</dt>
              <dd>
                {reportingArtifactState.artifacts[0]
                  ? reportingArtifactState.artifacts[0].workflowTimeline.map((entry) => entry.eventType).join(" -> ")
                  : "none"}
              </dd>
            </div>
          </>
        ) : null}
        {reportingArtifactState.status === "failed" ? (
          <div>
            <dt>Reporting Error</dt>
            <dd>{reportingArtifactState.message}</dd>
          </div>
        ) : null}
      </dl>
      <div className="api-actions" data-testid="api-backed-audit-parameters">
        <button
          type="button"
          onClick={runAuditParameterChangeSmoke}
          disabled={!apiBaseUrl || auditParameterCommandState.status === "running"}
        >
          Run audit parameter change smoke
        </button>
        <dl>
          <div>
            <dt>Parameters</dt>
            <dd>{auditParameterLabel(auditParameterState)}</dd>
          </div>
          {auditParameterState.status === "loaded" ? (
            <>
              <div>
                <dt>Parameter</dt>
                <dd>{auditParameterState.parameterKey}</dd>
              </div>
              <div>
                <dt>Current</dt>
                <dd>{auditParameterState.currentValue}</dd>
              </div>
              <div>
                <dt>Current version</dt>
                <dd>{auditParameterState.currentVersionId}</dd>
              </div>
              <div>
                <dt>Scheduled</dt>
                <dd>{auditParameterState.scheduledCount}</dd>
              </div>
              <div>
                <dt>Audit</dt>
                <dd>{auditParameterState.auditEventId}</dd>
              </div>
            </>
          ) : null}
          {auditParameterState.status === "failed" ? (
            <div>
              <dt>Parameter error</dt>
              <dd>{auditParameterState.message}</dd>
            </div>
          ) : null}
          <div>
            <dt>Command</dt>
            <dd>{auditParameterCommandLabel(auditParameterCommandState)}</dd>
          </div>
          {auditParameterCommandState.status === "requested" ? (
            <>
              <div>
                <dt>Request</dt>
                <dd>{auditParameterCommandState.requestId}</dd>
              </div>
              <div>
                <dt>Approval</dt>
                <dd>{auditParameterCommandState.approvalId}</dd>
              </div>
              <div>
                <dt>Requested value</dt>
                <dd>{auditParameterCommandState.requestedValue}</dd>
              </div>
              <div>
                <dt>Effective from</dt>
                <dd>{auditParameterCommandState.effectiveFrom}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>{auditParameterCommandState.requestStatus}</dd>
              </div>
            </>
          ) : null}
          {auditParameterCommandState.status === "failed" ? (
            <div>
              <dt>Command error</dt>
              <dd>{auditParameterCommandState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
      <div className="api-actions" data-testid="api-backed-audit-keycloak-login">
        <button
          type="button"
          onClick={runKeycloakAuditSmoke}
          disabled={
            !apiBaseUrl ||
            !keycloakBaseUrl ||
            keycloakAuditState.status === "redirecting" ||
            keycloakAuditState.status === "exchanging"
          }
        >
          Sign in auditor with Keycloak
        </button>
        <dl>
          <div>
            <dt>Auditor Login</dt>
            <dd>{keycloakAuditLabel(keycloakAuditState)}</dd>
          </div>
          {keycloakAuditState.status === "loaded" ? (
            <>
              <div>
                <dt>Token</dt>
                <dd>{keycloakAuditState.tokenType}</dd>
              </div>
              <div>
                <dt>Hash chain</dt>
                <dd>{keycloakAuditState.hashChainValid ? "valid" : "invalid"}</dd>
              </div>
              <div>
                <dt>Event</dt>
                <dd>{keycloakAuditState.event.auditEventId}</dd>
              </div>
              <div>
                <dt>Type</dt>
                <dd>{keycloakAuditState.event.eventType}</dd>
              </div>
            </>
          ) : null}
          {keycloakAuditState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{keycloakAuditState.message}</dd>
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
    return "audit loaded";
  }
  return "failed";
}

function keycloakAuditLabel(state: KeycloakAuditState): string {
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
    return "Keycloak audit loaded";
  }
  return "failed";
}

function notificationDeliveryStatusLabel(state: NotificationDeliveryState): string {
  if (state.status === "offline") {
    return "Notification API URL not configured";
  }
  if (state.status === "loading") {
    return "loading delivery history";
  }
  if (state.status === "loaded") {
    return "delivery history loaded";
  }
  return "notification API request failed";
}

function reportingArtifactStatusLabel(state: ReportingArtifactState): string {
  if (state.status === "offline") {
    return "Reporting API URL not configured";
  }
  if (state.status === "loading") {
    return "loading reporting artifacts";
  }
  if (state.status === "loaded") {
    return "reporting artifacts loaded";
  }
  return "reporting API request failed";
}

function auditParameterLabel(state: AuditParameterState): string {
  if (state.status === "offline") {
    return "API URL not configured";
  }
  if (state.status === "loading") {
    return "loading";
  }
  if (state.status === "loaded") {
    return "audit parameters loaded";
  }
  return "failed";
}

function auditParameterCommandLabel(state: AuditParameterCommandState): string {
  if (state.status === "idle") {
    return "ready";
  }
  if (state.status === "running") {
    return "running";
  }
  if (state.status === "requested") {
    return "audit parameter change requested";
  }
  return "failed";
}

function toAuditParameterState(response: ParameterListResponse): AuditParameterState {
  const parameter = response.items.find((item) => item.parameterKey === "retentionYears");
  if (!parameter) {
    return { status: "failed", message: "retentionYears parameter missing" };
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

function toAuditParameterCommandState(response: ParameterChangeRequestResponse): AuditParameterCommandState {
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
    throw new Error("AUD retentionYears parameter is not numeric");
  }
  return parsed + increment;
}

function tomorrowIsoDate(): string {
  const value = new Date();
  value.setUTCDate(value.getUTCDate() + 1);
  return value.toISOString().slice(0, 10);
}

function auditParameterClient(stepUp = false) {
  const nowEpochSeconds = Math.floor(Date.now() / 1000);
  return createBankingApiClient({
    baseUrl: apiBaseUrl,
    bearerToken: createSimulatorBearerToken({
      subject: "auditor01",
      roles: ["AUDITOR"],
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

function clearOidcSession(): void {
  window.sessionStorage.removeItem(oidcStateKey);
  window.sessionStorage.removeItem(oidcVerifierKey);
  window.sessionStorage.removeItem(oidcRedirectKey);
}
