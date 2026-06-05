"use client";

import { useEffect, useState } from "react";
import {
  createBankingApiClient,
  type AdminEvidenceCoverageResponse,
  type AdminPlatformSummaryResponse,
  type AdminSystemStatusResponse,
  type NotificationPreferenceDto,
  type NotificationTemplateDto
} from "@banking-lab/api-client";
import { createOidcAuthorizationUrl, createPkcePair, createSimulatorBearerToken } from "@banking-lab/auth-client";

type ApiState =
  | { readonly status: "offline" }
  | { readonly status: "loading" }
  | {
      readonly status: "loaded";
      readonly summary: AdminPlatformSummaryResponse;
      readonly evidence: AdminEvidenceCoverageResponse;
      readonly systemStatus: AdminSystemStatusResponse;
    }
  | { readonly status: "failed"; readonly message: string };

type KeycloakAdminState =
  | { readonly status: "offline" }
  | { readonly status: "idle" }
  | { readonly status: "redirecting" }
  | { readonly status: "exchanging" }
  | { readonly status: "loaded"; readonly summary: AdminPlatformSummaryResponse; readonly tokenType: string }
  | { readonly status: "failed"; readonly message: string };

type NotificationAdminState =
  | { readonly status: "offline" }
  | { readonly status: "loading" }
  | {
      readonly status: "loaded";
      readonly templates: readonly NotificationTemplateDto[];
      readonly preferences: readonly NotificationPreferenceDto[];
    }
  | { readonly status: "failed"; readonly message: string };

const apiBaseUrl = process.env.NEXT_PUBLIC_BANKING_API_BASE_URL ?? "";
const notificationApiBaseUrl = process.env.NEXT_PUBLIC_BANKING_NOTIFICATION_API_BASE_URL || apiBaseUrl;
const keycloakBaseUrl = process.env.NEXT_PUBLIC_BANKING_KEYCLOAK_BASE_URL ?? "";
const oidcStateKey = "bankingLabAdminOidcState";
const oidcVerifierKey = "bankingLabAdminOidcVerifier";
const oidcRedirectKey = "bankingLabAdminOidcRedirectUri";

export function ApiBackedAdminPanel() {
  const [state, setState] = useState<ApiState>(() => (apiBaseUrl ? { status: "loading" } : { status: "offline" }));
  const [notificationState, setNotificationState] = useState<NotificationAdminState>(() =>
    notificationApiBaseUrl ? { status: "loading" } : { status: "offline" }
  );
  const [keycloakAdminState, setKeycloakAdminState] = useState<KeycloakAdminState>(() =>
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
        subject: "security-admin01",
        roles: ["COMPLIANCE_MANAGER", "AUDITOR", "PASSKEY_RECOVERY_ADMIN"]
      })
    });

    Promise.all([
      client.adminPlatformSummary(),
      client.adminEvidenceCoverage("Synthetic admin evidence coverage review"),
      client.adminSystemStatus("Synthetic admin system status review")
    ])
      .then(([summary, evidence, systemStatus]) => {
        if (!cancelled) {
          setState({ status: "loaded", summary, evidence, systemStatus });
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
        subject: "notification-admin01",
        roles: ["OPS_MANAGER", "COMPLIANCE_MANAGER"]
      })
    });

    Promise.all([
      client.listNotificationTemplates(),
      client.listNotificationPreferences({
        requestedBy: "notification-admin01",
        reason: "API-backed notification preference review"
      })
    ])
      .then(([templates, preferences]) => {
        if (!cancelled) {
          setNotificationState({ status: "loaded", templates, preferences });
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setNotificationState({
            status: "failed",
            message: error instanceof Error ? error.message : "Unknown notification API failure"
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
      setKeycloakAdminState({ status: "failed", message: "Keycloak state verification failed" });
      clearOidcSession();
      return;
    }

    let cancelled = false;
    setKeycloakAdminState({ status: "exchanging" });
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
        const summary = await client.adminPlatformSummary();
        if (!cancelled) {
          setKeycloakAdminState({
            status: "loaded",
            summary,
            tokenType
          });
          clearOidcSession();
          window.history.replaceState(null, "", window.location.pathname);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setKeycloakAdminState({ status: "failed", message: error instanceof Error ? error.message : "Unknown Keycloak admin failure" });
          clearOidcSession();
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  async function runKeycloakAdminSmoke() {
    if (!apiBaseUrl || !keycloakBaseUrl || keycloakAdminState.status === "redirecting" || keycloakAdminState.status === "exchanging") {
      return;
    }
    setKeycloakAdminState({ status: "redirecting" });
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
          clientId: "admin-console",
          redirectUri,
          state: stateValue,
          codeChallenge: pkce.codeChallenge,
          prompt: "login",
          loginHint: "security-admin01"
        })
      );
    } catch (error: unknown) {
      clearOidcSession();
      setKeycloakAdminState({ status: "failed", message: error instanceof Error ? error.message : "Unknown Keycloak redirect failure" });
    }
  }

  return (
    <section className="api-panel" aria-label="API-backed admin platform summary">
      <h2>API-backed Admin</h2>
      <dl data-testid="api-backed-admin-summary">
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
              <dt>Synthetic</dt>
              <dd>{state.summary.syntheticOnly ? "synthetic only" : "unsafe"}</dd>
            </div>
            <div>
              <dt>Migration Target</dt>
              <dd>{state.summary.migrationTarget}</dd>
            </div>
            <div>
              <dt>Node Boundary</dt>
              <dd>{state.summary.nodeReferenceRuntimeRetained ? "reference retained" : "ready for review"}</dd>
            </div>
            <div>
              <dt>Controls</dt>
              <dd>{state.summary.controls.map((control) => `${control.controlId}:${control.status}`).join(", ")}</dd>
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
      <dl data-testid="api-backed-admin-evidence-coverage">
        <div>
          <dt>Evidence Coverage</dt>
          <dd>{evidenceCoverageStatusLabel(state)}</dd>
        </div>
        {state.status === "loaded" ? (
          <>
            <div>
              <dt>Audit</dt>
              <dd>{state.evidence.auditEventId}</dd>
            </div>
            <div>
              <dt>Evidence Links</dt>
              <dd>{state.evidence.evidenceLinks.length}</dd>
            </div>
            <div>
              <dt>Feature Screens</dt>
              <dd>{state.evidence.featureCoverage.map((item) => `${item.screenId}:${item.status}`).join(", ")}</dd>
            </div>
            <div>
              <dt>Evidence Sample</dt>
              <dd>
                {state.evidence.evidenceLinks[2]
                  ? `${state.evidence.evidenceLinks[2].evidenceId}:${state.evidence.evidenceLinks[2].status}`
                  : "none"}
              </dd>
            </div>
          </>
        ) : null}
      </dl>
      <dl data-testid="api-backed-admin-system-status">
        <div>
          <dt>System Status</dt>
          <dd>{systemStatusLabel(state)}</dd>
        </div>
        {state.status === "loaded" ? (
          <>
            <div>
              <dt>System Audit</dt>
              <dd>{state.systemStatus.auditEventId}</dd>
            </div>
            <div>
              <dt>Services</dt>
              <dd>{state.systemStatus.services.map((service) => `${service.serviceId}:${service.status}`).join(", ")}</dd>
            </div>
            <div>
              <dt>Batches</dt>
              <dd>{state.systemStatus.batches.map((batch) => `${batch.batchType}:${batch.status}`).join(", ")}</dd>
            </div>
            <div>
              <dt>Monitoring</dt>
              <dd>{state.systemStatus.monitoringLinks.map((link) => `${link.system}:${link.status}`).join(", ")}</dd>
            </div>
          </>
        ) : null}
      </dl>
      <dl data-testid="api-backed-notification-admin">
        <div>
          <dt>Notification API</dt>
          <dd>{notificationApiBaseUrl || "not configured"}</dd>
        </div>
        <div>
          <dt>Notification Status</dt>
          <dd>{notificationAdminStatusLabel(notificationState)}</dd>
        </div>
        {notificationState.status === "loaded" ? (
          <>
            <div>
              <dt>Templates</dt>
              <dd>{notificationState.templates.length}</dd>
            </div>
            <div>
              <dt>Preferences</dt>
              <dd>{notificationState.preferences.length}</dd>
            </div>
            <div>
              <dt>Template Sample</dt>
              <dd>
                {notificationState.templates[0]
                  ? `${notificationState.templates[0].eventType}:${notificationState.templates[0].channel}:v${notificationState.templates[0].version}`
                  : "none"}
              </dd>
            </div>
          </>
        ) : null}
        {notificationState.status === "failed" ? (
          <div>
            <dt>Notification Error</dt>
            <dd>{notificationState.message}</dd>
          </div>
        ) : null}
      </dl>
      <div className="api-actions" data-testid="api-backed-admin-keycloak-login">
        <button
          type="button"
          onClick={runKeycloakAdminSmoke}
          disabled={
            !apiBaseUrl ||
            !keycloakBaseUrl ||
            keycloakAdminState.status === "redirecting" ||
            keycloakAdminState.status === "exchanging"
          }
        >
          Sign in security admin with Keycloak
        </button>
        <dl>
          <div>
            <dt>Admin Login</dt>
            <dd>{keycloakStatusLabel(keycloakAdminState)}</dd>
          </div>
          {keycloakAdminState.status === "loaded" ? (
            <>
              <div>
                <dt>Token</dt>
                <dd>{keycloakAdminState.tokenType}</dd>
              </div>
              <div>
                <dt>Control</dt>
                <dd>{keycloakAdminState.summary.controls.map((control) => `${control.controlId}:${control.status}`).join(", ")}</dd>
              </div>
            </>
          ) : null}
          {keycloakAdminState.status === "failed" ? (
            <div>
              <dt>Error</dt>
              <dd>{keycloakAdminState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
    </section>
  );
}

function statusLabel(state: ApiState): string {
  switch (state.status) {
    case "offline":
      return "API base URL not configured";
    case "loading":
      return "loading admin summary";
    case "loaded":
      return "admin summary loaded";
    case "failed":
      return "API request failed";
  }
}

function evidenceCoverageStatusLabel(state: ApiState): string {
  switch (state.status) {
    case "offline":
      return "API base URL not configured";
    case "loading":
      return "loading evidence coverage";
    case "loaded":
      return "evidence coverage loaded";
    case "failed":
      return "evidence coverage request failed";
  }
}

function systemStatusLabel(state: ApiState): string {
  switch (state.status) {
    case "offline":
      return "API base URL not configured";
    case "loading":
      return "loading system status";
    case "loaded":
      return "system status loaded";
    case "failed":
      return "system status request failed";
  }
}

function keycloakStatusLabel(state: KeycloakAdminState): string {
  switch (state.status) {
    case "offline":
      return "Keycloak not configured";
    case "idle":
      return "ready";
    case "redirecting":
      return "redirecting";
    case "exchanging":
      return "exchanging code";
    case "loaded":
      return "Keycloak admin summary loaded";
    case "failed":
      return "Keycloak admin failed";
  }
}

function notificationAdminStatusLabel(state: NotificationAdminState): string {
  switch (state.status) {
    case "offline":
      return "Notification API URL not configured";
    case "loading":
      return "loading notification controls";
    case "loaded":
      return "notification controls loaded";
    case "failed":
      return "notification API request failed";
  }
}

function clearOidcSession() {
  window.sessionStorage.removeItem(oidcStateKey);
  window.sessionStorage.removeItem(oidcVerifierKey);
  window.sessionStorage.removeItem(oidcRedirectKey);
}
