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
import { ApiBackedCallCenterPanel } from "../components/ApiBackedCallCenterPanel";
import { loadChannelManifests } from "../lib/manifestLoader";

function list(value: readonly string[] | undefined | null): string {
  return Array.isArray(value) && value.length > 0 ? value.join(", ") : "none";
}

export default async function CallCenterConsolePage() {
  const manifests = await loadChannelManifests();
  const search = manifests.find((manifest) => manifest.screenId === "CALL-101");
  const noteEntry = manifests.find((manifest) => manifest.screenId === "CALL-103");
  const escalation = manifests.find((manifest) => manifest.screenId === "CALL-106");

  return (
    <ChannelShell
      appId="call-center-console"
      eyebrow="Call-Center Console"
      title="Customer interaction and escalation workspace"
      status="Synthetic masked workflow only"
    >
      <ChannelMetricGrid>
        <ChannelMetric label="Customer lookup" value={search?.audit.reasonRequired ? "reason required" : "pending manifest"} />
        <ChannelMetric label="Note policy" value={noteEntry?.validation?.redactionRequired ? "redacted before persistence" : "pending manifest"} />
        <ChannelMetric label="Escalation" value={escalation?.approval?.makerChecker ? "maker-checker" : "role gated"} />
      </ChannelMetricGrid>

      <ApiBackedCallCenterPanel />

      <ChannelCardGrid density="wide">
        {manifests.map((manifest) => (
          <ChannelCard
            key={manifest.screenId}
            screenId={manifest.screenId}
            title={manifest.title}
            meta={`${manifest.domain} · ${manifest.layout.template}`}
          >
            {manifest.workflow?.states?.length ? <ChannelWorkflow states={manifest.workflow.states} /> : null}
            <ChannelDefinitionList
              items={[
                { term: "Roles", detail: list(manifest.requiredRoles) },
                { term: "Sections", detail: list(manifest.sections) },
                { term: "Audit", detail: manifest.audit.reasonRequired ? "reason required" : manifest.audit.enabled ? "enabled" : "disabled" },
                { term: "Masking", detail: manifest.audit.maskingPolicy || "none" },
                { term: "Approval", detail: manifest.approval?.required ? list(manifest.approval.businessTypes) : "not required" }
              ]}
            />
            <ChannelActionRow items={(manifest.actions || []).map((action) => action.label)} />
          </ChannelCard>
        ))}
      </ChannelCardGrid>
    </ChannelShell>
  );
}
