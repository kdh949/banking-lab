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
import { ApiBackedComplaintPanel } from "../components/ApiBackedComplaintPanel";
import { loadChannelManifests } from "../lib/manifestLoader";

function list(value: readonly string[] | undefined | null): string {
  return Array.isArray(value) && value.length > 0 ? value.join(", ") : "none";
}

export default async function ComplaintPortalPage() {
  const manifests = await loadChannelManifests();
  const reasonRequired = manifests.filter((manifest) => manifest.audit.reasonRequired).length;

  return (
    <ChannelShell appId="complaint-portal" eyebrow="Electronic Complaint Portal" title="Customer case workspace" status="Synthetic complaints only">
      <ChannelMetricGrid>
        <ChannelMetric label="Default PII masking" value="CUSTOMER_SELF" detail="Control summary" />
        <ChannelMetric label="Reason-required manifests" value={reasonRequired} />
        <ChannelMetric label="Case template" value="workflow timeline" />
      </ChannelMetricGrid>

      <ApiBackedComplaintPanel />

      <ChannelCardGrid>
        {manifests.map((manifest) => (
          <ChannelCard key={manifest.screenId} screenId={manifest.screenId} title={manifest.title}>
            <ChannelWorkflow states={manifest.workflow?.states || []} />
            <ChannelDefinitionList
              items={[
                { term: "Template", detail: manifest.layout.template },
                { term: "Sections", detail: list(manifest.sections) },
                { term: "SLA", detail: manifest.sla?.enabled ? `${manifest.sla.targetHours} hours` : "none" },
                { term: "Audit", detail: manifest.audit.selfService ? "customer self-service" : "standard" }
              ]}
            />
            <ChannelActionRow items={(manifest.actions || []).map((action) => action.label)} />
          </ChannelCard>
        ))}
      </ChannelCardGrid>
    </ChannelShell>
  );
}
