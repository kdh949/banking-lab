import {
  ChannelCard,
  ChannelDefinitionList,
  ChannelMetric,
  ChannelMetricGrid
} from "../../../../packages/channel-ui/src";
import analyticsArtifact from "../../../../docs/test-evidence/generated/fds-aml-analytics.json";

interface AnalyticsControls {
  readonly realMoneyUsed: boolean;
  readonly realPiiUsed: boolean;
  readonly realBankNetworkUsed: boolean;
}

interface AnalyticsResult {
  readonly transactionId: string;
  readonly customerId: string;
  readonly riskBand: string;
  readonly totalScore: number;
  readonly alerts: readonly string[];
}

interface AnalyticsArtifact {
  readonly engine: string;
  readonly generatedAt: string;
  readonly controls: AnalyticsControls;
  readonly alertCounts: Record<string, number>;
  readonly results: readonly AnalyticsResult[];
}

export function AnalyticsEvidencePanel() {
  const artifact = analyticsArtifact as AnalyticsArtifact;
  const highRiskCount = artifact.results.filter((result) => result.riskBand === "HIGH").length;
  const alertCount = Object.values(artifact.alertCounts).reduce((total, count) => total + count, 0);
  const highestRisk = [...artifact.results].sort((left, right) => right.totalScore - left.totalScore)[0];

  return (
    <ChannelCard screenId="ANL-EVID" title="Analytics evidence" meta={artifact.engine}>
      <div data-testid="analytics-evidence-panel">
        <ChannelMetricGrid>
          <ChannelMetric label="Scored transactions" value={artifact.results.length} />
          <ChannelMetric label="High-risk results" value={highRiskCount} />
          <ChannelMetric label="Rule alerts" value={alertCount} />
        </ChannelMetricGrid>
        <ChannelDefinitionList
          items={[
            { term: "Generated", detail: artifact.generatedAt },
            { term: "Synthetic controls", detail: syntheticControlSummary(artifact.controls) },
            {
              term: "Highest risk",
              detail: highestRisk
                ? `${highestRisk.transactionId} · ${highestRisk.riskBand} · score ${highestRisk.totalScore}`
                : "none"
            },
            {
              term: "Top alerts",
              detail: highestRisk && highestRisk.alerts.length > 0 ? highestRisk.alerts.join(", ") : "none"
            }
          ]}
        />
      </div>
    </ChannelCard>
  );
}

function syntheticControlSummary(controls: AnalyticsControls): string {
  return [
    `realMoneyUsed=${String(controls.realMoneyUsed)}`,
    `realPiiUsed=${String(controls.realPiiUsed)}`,
    `realBankNetworkUsed=${String(controls.realBankNetworkUsed)}`
  ].join(", ");
}
