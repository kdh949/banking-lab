import {
  ChannelActionRow,
  ChannelCard,
  ChannelCardGrid,
  ChannelDefinitionList,
  ChannelMetric,
  ChannelMetricGrid,
  ChannelShell,
  ChannelWorkflow
} from "../../../../packages/channel-ui/src";
import { AnalyticsEvidencePanel } from "../components/AnalyticsEvidencePanel";
import { ApiBackedRiskPanel } from "../components/ApiBackedRiskPanel";
import { loadChannelManifests } from "../lib/manifestLoader";

function list(value: readonly string[] | undefined | null): string {
  return Array.isArray(value) && value.length > 0 ? value.join(", ") : "none";
}

export default async function FdsAmlConsolePage() {
  const manifests = await loadChannelManifests();
  const heldReview = manifests.find((manifest) => manifest.screenId === "FDS-201");
  const amlReview = manifests.find((manifest) => manifest.screenId === "AML-201");

  return (
    <ChannelShell appId="fds-aml-console" eyebrow="FDS / AML Console" title="Investigation and approval workspace" status="Held transfers do not post">
      <ChannelMetricGrid>
        <ChannelMetric label="FDS release/block" value={heldReview?.approval?.required ? "maker-checker" : "pending manifest"} />
        <ChannelMetric label="AML closure" value={amlReview?.approval?.required ? "approval controlled" : "pending manifest"} />
        <ChannelMetric label="PII policy" value="masked by default" />
      </ChannelMetricGrid>

      <ApiBackedRiskPanel />

      <AnalyticsEvidencePanel />

      <ChannelCardGrid density="wide">
        {manifests.map((manifest) => (
          <ChannelCard
            key={manifest.screenId}
            screenId={manifest.screenId}
            title={manifest.title}
            meta={`${manifest.domain} · ${manifest.layout.template}`}
          >
            <ChannelWorkflow states={manifest.workflow?.states || []} />
            <ChannelDefinitionList
              items={[
                { term: "Roles", detail: list(manifest.requiredRoles) },
                { term: "Sections", detail: list(manifest.sections) },
                { term: "SLA", detail: manifest.sla?.enabled ? `${manifest.sla.targetHours} hours` : "none" },
                { term: "Approval Types", detail: manifest.approval?.required ? list(manifest.approval.businessTypes) : "not required" }
              ]}
            />
            <ChannelActionRow items={(manifest.actions || []).map((action) => action.label)} />
          </ChannelCard>
        ))}
      </ChannelCardGrid>
    </ChannelShell>
  );
}
