"use client";

import { useEffect, useState } from "react";
import {
  createBankingApiClient,
  type AdminEvidenceCoverageResponse,
  type AdminPlatformSummaryResponse,
  type AdminSystemStatusResponse,
  type ParameterChangeRequestResponse,
  type ParameterListResponse,
  type ReportArtifactDto,
  type ReportArtifactExportResponse,
  type ReportDefinitionDto,
  type NotificationPreferenceDto,
  type NotificationTemplateChangeRequestDto,
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
      readonly templateChanges: readonly NotificationTemplateChangeRequestDto[];
      readonly preferences: readonly NotificationPreferenceDto[];
    }
  | { readonly status: "failed"; readonly message: string };

type ReportingAdminState =
  | { readonly status: "offline" }
  | { readonly status: "loading" }
  | {
      readonly status: "loaded";
      readonly definitions: readonly ReportDefinitionDto[];
      readonly artifact: ReportArtifactDto;
      readonly exported: ReportArtifactExportResponse;
      readonly artifacts: readonly ReportArtifactDto[];
    }
  | { readonly status: "failed"; readonly message: string };

type SecurityParameterState =
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

type SecurityParameterCommandState =
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
const oidcStateKey = "bankingLabAdminOidcState";
const oidcVerifierKey = "bankingLabAdminOidcVerifier";
const oidcRedirectKey = "bankingLabAdminOidcRedirectUri";

export function ApiBackedAdminPanel() {
  const [state, setState] = useState<ApiState>(() => (apiBaseUrl ? { status: "loading" } : { status: "offline" }));
  const [notificationState, setNotificationState] = useState<NotificationAdminState>(() =>
    notificationApiBaseUrl ? { status: "loading" } : { status: "offline" }
  );
  const [reportingState, setReportingState] = useState<ReportingAdminState>(() =>
    reportingApiBaseUrl ? { status: "loading" } : { status: "offline" }
  );
  const [securityParameterState, setSecurityParameterState] = useState<SecurityParameterState>(() =>
    apiBaseUrl ? { status: "loading" } : { status: "offline" }
  );
  const [securityParameterCommandState, setSecurityParameterCommandState] = useState<SecurityParameterCommandState>({ status: "idle" });
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
      client.listNotificationTemplateChangeRequests(),
      client.listNotificationPreferences({
        requestedBy: "notification-admin01",
        reason: "API-backed notification preference review"
      })
    ])
      .then(([templates, templateChanges, preferences]) => {
        if (!cancelled) {
          setNotificationState({ status: "loaded", templates, templateChanges, preferences });
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
    if (!reportingApiBaseUrl) {
      return;
    }
    let cancelled = false;
    const client = createBankingApiClient({
      baseUrl: reportingApiBaseUrl,
      bearerToken: createSimulatorBearerToken({
        subject: "reporting-admin01",
        roles: ["REPORTING_ANALYST"]
      })
    });

    void (async () => {
      const catalog = await client.reportCatalog("API-backed reporting catalog review");
      const generated = await client.generateReportArtifact({
        reportType: "EVIDENCE_COVERAGE",
        requestedBy: "reporting-admin01",
        requestedByRole: "REPORTING_ANALYST",
        reason: "API-backed reporting artifact generation",
        idempotencyKey: "RPT-ADMIN-UI-SMOKE-001"
      });
      const exported = await client.exportReportArtifact(
        generated.item.artifactId,
        "API-backed reporting artifact export package review"
      );
      const artifacts = await client.reportArtifacts({
        reason: "API-backed reporting artifact list review",
        reportType: "EVIDENCE_COVERAGE"
      });

      return { catalog, generated, exported, artifacts };
    })()
      .then(({ catalog, generated, exported, artifacts }) => {
        if (!cancelled) {
          setReportingState({
            status: "loaded",
            definitions: catalog.items,
            artifact: generated.item,
            exported,
            artifacts: artifacts.items
          });
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setReportingState({
            status: "failed",
            message: error instanceof Error ? error.message : "Unknown reporting API failure"
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
    securityParameterClient()
      .securityParameters("Browser ADM-201 parameter read smoke")
      .then((response) => {
        if (!cancelled) {
          setSecurityParameterState(toSecurityParameterState(response));
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setSecurityParameterState({ status: "failed", message: error instanceof Error ? error.message : "Unknown security parameter API failure" });
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

  const runSecurityParameterChangeSmoke = async () => {
    if (!apiBaseUrl || securityParameterCommandState.status === "running") {
      return;
    }
    setSecurityParameterCommandState({ status: "running" });
    try {
      const current = securityParameterState.status === "loaded"
        ? securityParameterState
        : toSecurityParameterState(await securityParameterClient().securityParameters("Browser ADM-201 parameter command read"));
      if (current.status !== "loaded") {
        setSecurityParameterCommandState({ status: "failed", message: "ADM staffSessionTtlSeconds parameter was not loaded" });
        return;
      }
      const requestedValue = nextNumericParameterValue(current.currentValue, 300);
      const effectiveFrom = tomorrowIsoDate();
      const response = await securityParameterClient(true).requestSecurityParameterChange({
        parameterKey: "staffSessionTtlSeconds",
        scheduledValue: requestedValue,
        effectiveFrom,
        rollbackPlan: "Create a future synthetic rollback version from the prior staff session TTL",
        requestedBy: "security-admin01",
        requestedByRole: "COMPLIANCE_MANAGER",
        reason: "Browser ADM-201 parameter change smoke",
        idempotencyKey: `ADM-SEC-PARAM-${globalThis.crypto.randomUUID()}`
      });
      setSecurityParameterCommandState(toSecurityParameterCommandState(response));
    } catch (error: unknown) {
      setSecurityParameterCommandState({ status: "failed", message: error instanceof Error ? error.message : "Unknown security parameter failure" });
    }
  };

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
              <dt>Template Workflows</dt>
              <dd>
                {notificationState.templateChanges.map((change) => `${change.changeRequestId}:${change.workflowStatus}`).join(", ") ||
                  "none"}
              </dd>
            </div>
            <div>
              <dt>Workflow Timeline</dt>
              <dd>
                {notificationState.templateChanges[0]
                  ? notificationState.templateChanges[0].workflowTimeline.map((entry) => entry.eventType).join(" -> ")
                  : "none"}
              </dd>
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
      <dl data-testid="api-backed-reporting-admin">
        <div>
          <dt>Reporting API</dt>
          <dd>{reportingApiBaseUrl || "not configured"}</dd>
        </div>
        <div>
          <dt>Reporting Status</dt>
          <dd>{reportingAdminStatusLabel(reportingState)}</dd>
        </div>
        {reportingState.status === "loaded" ? (
          <>
            <div>
              <dt>Catalog</dt>
              <dd>{reportingState.definitions.map((definition) => definition.reportType).join(", ")}</dd>
            </div>
            <div>
              <dt>Generated Artifact</dt>
              <dd>{`${reportingState.artifact.artifactId}:${reportingState.artifact.reportType}`}</dd>
            </div>
            <div>
              <dt>Masking</dt>
              <dd>{reportingState.artifact.maskedByDefault ? "masked by default" : "unsafe"}</dd>
            </div>
            <div>
              <dt>Artifact Checksum</dt>
              <dd>{`${reportingState.artifact.exportFormat}:${reportingState.artifact.contentSha256.slice(0, 12)}`}</dd>
            </div>
            <div>
              <dt>Export Package</dt>
              <dd>{`${reportingState.exported.packageName}:${reportingState.exported.contentSha256.slice(0, 12)}`}</dd>
            </div>
            <div>
              <dt>Retention</dt>
              <dd>{`${reportingState.artifact.retentionPolicy}:${reportingState.artifact.retentionUntil ?? "unscheduled"}`}</dd>
            </div>
            <div>
              <dt>Artifact Workflow</dt>
              <dd>{`${reportingState.artifact.workflowInstanceId}:${reportingState.artifact.workflowStatus}`}</dd>
            </div>
            <div>
              <dt>Workflow Timeline</dt>
              <dd>{reportingState.artifact.workflowTimeline.map((entry) => entry.eventType).join(" -> ") || "none"}</dd>
            </div>
            <div>
              <dt>Listed Artifacts</dt>
              <dd>{reportingState.artifacts.map((artifact) => `${artifact.artifactId}:${artifact.status}`).join(", ") || "none"}</dd>
            </div>
          </>
        ) : null}
        {reportingState.status === "failed" ? (
          <div>
            <dt>Reporting Error</dt>
            <dd>{reportingState.message}</dd>
          </div>
        ) : null}
      </dl>
      <div className="api-actions" data-testid="api-backed-security-parameters">
        <button
          type="button"
          onClick={runSecurityParameterChangeSmoke}
          disabled={!apiBaseUrl || securityParameterCommandState.status === "running"}
        >
          Run security parameter change smoke
        </button>
        <dl>
          <div>
            <dt>Security parameters</dt>
            <dd>{securityParameterLabel(securityParameterState)}</dd>
          </div>
          {securityParameterState.status === "loaded" ? (
            <>
              <div>
                <dt>Parameter</dt>
                <dd>{securityParameterState.parameterKey}</dd>
              </div>
              <div>
                <dt>Current</dt>
                <dd>{securityParameterState.currentValue}</dd>
              </div>
              <div>
                <dt>Current version</dt>
                <dd>{securityParameterState.currentVersionId}</dd>
              </div>
              <div>
                <dt>Scheduled</dt>
                <dd>{securityParameterState.scheduledCount}</dd>
              </div>
              <div>
                <dt>Audit</dt>
                <dd>{securityParameterState.auditEventId}</dd>
              </div>
            </>
          ) : null}
          {securityParameterState.status === "failed" ? (
            <div>
              <dt>Parameter error</dt>
              <dd>{securityParameterState.message}</dd>
            </div>
          ) : null}
          <div>
            <dt>Command</dt>
            <dd>{securityParameterCommandLabel(securityParameterCommandState)}</dd>
          </div>
          {securityParameterCommandState.status === "requested" ? (
            <>
              <div>
                <dt>Request</dt>
                <dd>{securityParameterCommandState.requestId}</dd>
              </div>
              <div>
                <dt>Approval</dt>
                <dd>{securityParameterCommandState.approvalId}</dd>
              </div>
              <div>
                <dt>Requested value</dt>
                <dd>{securityParameterCommandState.requestedValue}</dd>
              </div>
              <div>
                <dt>Effective from</dt>
                <dd>{securityParameterCommandState.effectiveFrom}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>{securityParameterCommandState.requestStatus}</dd>
              </div>
            </>
          ) : null}
          {securityParameterCommandState.status === "failed" ? (
            <div>
              <dt>Command error</dt>
              <dd>{securityParameterCommandState.message}</dd>
            </div>
          ) : null}
        </dl>
      </div>
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

function reportingAdminStatusLabel(state: ReportingAdminState): string {
  switch (state.status) {
    case "offline":
      return "Reporting API URL not configured";
    case "loading":
      return "loading reporting controls";
    case "loaded":
      return "reporting controls loaded";
    case "failed":
      return "reporting API request failed";
  }
}

function securityParameterLabel(state: SecurityParameterState): string {
  switch (state.status) {
    case "offline":
      return "API base URL not configured";
    case "loading":
      return "loading";
    case "loaded":
      return "security parameters loaded";
    case "failed":
      return "failed";
  }
}

function securityParameterCommandLabel(state: SecurityParameterCommandState): string {
  switch (state.status) {
    case "idle":
      return "ready";
    case "running":
      return "running";
    case "requested":
      return "security parameter change requested";
    case "failed":
      return "failed";
  }
}

function toSecurityParameterState(response: ParameterListResponse): SecurityParameterState {
  const parameter = response.items.find((item) => item.parameterKey === "staffSessionTtlSeconds");
  if (!parameter) {
    return { status: "failed", message: "staffSessionTtlSeconds parameter missing" };
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

function toSecurityParameterCommandState(response: ParameterChangeRequestResponse): SecurityParameterCommandState {
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
    throw new Error("ADM staffSessionTtlSeconds parameter is not numeric");
  }
  return parsed + increment;
}

function tomorrowIsoDate(): string {
  const value = new Date();
  value.setUTCDate(value.getUTCDate() + 1);
  return value.toISOString().slice(0, 10);
}

function securityParameterClient(stepUp = false) {
  const nowEpochSeconds = Math.floor(Date.now() / 1000);
  return createBankingApiClient({
    baseUrl: apiBaseUrl,
    bearerToken: createSimulatorBearerToken({
      subject: "security-admin01",
      roles: ["COMPLIANCE_MANAGER"],
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

function clearOidcSession() {
  window.sessionStorage.removeItem(oidcStateKey);
  window.sessionStorage.removeItem(oidcVerifierKey);
  window.sessionStorage.removeItem(oidcRedirectKey);
}
