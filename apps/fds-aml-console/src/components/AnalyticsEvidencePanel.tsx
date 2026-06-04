"use client";

import { useEffect, useState } from "react";
import { createBankingApiClient, type FdsAnalyticsEvidenceDto } from "@banking-lab/api-client";
import { createSimulatorBearerToken } from "@banking-lab/auth-client";
import {
  ChannelCard,
  ChannelDefinitionList,
  ChannelMetric,
  ChannelMetricGrid
} from "../../../../packages/channel-ui/src";

type AnalyticsState =
  | { readonly status: "offline" }
  | { readonly status: "loading" }
  | { readonly status: "loaded"; readonly evidence: FdsAnalyticsEvidenceDto }
  | { readonly status: "failed"; readonly message: string };

const apiBaseUrl = process.env.NEXT_PUBLIC_BANKING_API_BASE_URL ?? "";

export function AnalyticsEvidencePanel() {
  const [state, setState] = useState<AnalyticsState>(() => (apiBaseUrl ? { status: "loading" } : { status: "offline" }));

  useEffect(() => {
    if (!apiBaseUrl) {
      return;
    }
    let cancelled = false;
    const client = createBankingApiClient({
      baseUrl: apiBaseUrl,
      bearerToken: createSimulatorBearerToken({
        subject: "fds01",
        roles: ["FDS_REVIEWER", "AML_REVIEWER"]
      })
    });

    client
      .fdsAnalyticsEvidence("Synthetic FDS/AML console analytics evidence view")
      .then((evidence) => {
        if (!cancelled) {
          setState({ status: "loaded", evidence });
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setState({ status: "failed", message: error instanceof Error ? error.message : "Unknown analytics API failure" });
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const evidence = state.status === "loaded" ? state.evidence : null;
  const alertCount = evidence ? Object.values(evidence.alertCounts).reduce((total, count) => total + count, 0) : 0;

  return (
    <ChannelCard screenId="ANL-EVID" title="Analytics evidence" meta={evidence?.engine ?? "Spring API"}>
      <div data-testid="analytics-evidence-panel">
        <ChannelMetricGrid>
          <ChannelMetric label="Scored transactions" value={evidence?.scoredTransactions ?? 0} />
          <ChannelMetric label="High-risk results" value={evidence?.highRiskResults ?? 0} />
          <ChannelMetric label="Rule alerts" value={alertCount} />
        </ChannelMetricGrid>
        <ChannelDefinitionList
          items={[
            { term: "Spring API", detail: apiBaseUrl || "not configured" },
            { term: "Status", detail: statusLabel(state) },
            { term: "Generated", detail: evidence?.generatedAt ?? "pending" },
            { term: "Synthetic controls", detail: evidence ? syntheticControlSummary(evidence.controls) : "pending" },
            {
              term: "Highest risk",
              detail: evidence?.highestRisk
                ? `${evidence.highestRisk.transactionId} · ${evidence.highestRisk.riskBand} · score ${evidence.highestRisk.totalScore}`
                : "none"
            },
            {
              term: "Top alerts",
              detail: evidence?.highestRisk && evidence.highestRisk.alerts.length > 0 ? evidence.highestRisk.alerts.join(", ") : "none"
            },
            {
              term: "Audit event",
              detail: evidence?.auditEventId ?? "pending"
            }
          ]}
        />
      </div>
    </ChannelCard>
  );
}

function syntheticControlSummary(controls: Record<string, boolean>): string {
  return [
    `realMoneyUsed=${String(controls.realMoneyUsed)}`,
    `realPiiUsed=${String(controls.realPiiUsed)}`,
    `realBankNetworkUsed=${String(controls.realBankNetworkUsed)}`
  ].join(", ");
}

function statusLabel(state: AnalyticsState): string {
  switch (state.status) {
    case "offline":
      return "API not configured";
    case "loading":
      return "loading";
    case "loaded":
      return state.evidence.syntheticOnly ? "synthetic API evidence loaded" : "unsafe";
    case "failed":
      return state.message;
  }
}
