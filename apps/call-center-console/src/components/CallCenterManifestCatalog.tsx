import {
  ChannelActionRow,
  ChannelCard,
  ChannelCardGrid,
  ChannelDefinitionList,
  ChannelShell,
  ChannelWorkflow
} from "../../../../packages/channel-ui/src/operator-workbench";
import { loadChannelManifests } from "../lib/manifestLoader";

function list(value: readonly string[] | undefined | null): string {
  return Array.isArray(value) && value.length > 0 ? value.join(", ") : "none";
}

export async function CallCenterManifestCatalog() {
  const manifests = await loadChannelManifests();
  return (
    <ChannelShell appId="call-center-console" eyebrow="Lab · manifest catalog" title="Call-Center Screen Manifests" status="LAB_ONLY · not a product route">
      <ChannelCardGrid density="wide">
        {manifests.map((manifest) => (
          <ChannelCard key={manifest.screenId} screenId={manifest.screenId} title={manifest.title} meta={`${manifest.domain} · ${manifest.layout.template}`}>
            {manifest.workflow?.states?.length ? <ChannelWorkflow states={manifest.workflow.states} /> : null}
            <ChannelDefinitionList items={[
              { term: "Roles", detail: list(manifest.requiredRoles) },
              { term: "Sections", detail: list(manifest.sections) },
              { term: "Audit", detail: manifest.audit.reasonRequired ? "reason required" : manifest.audit.enabled ? "enabled" : "disabled" },
              { term: "Masking", detail: manifest.audit.maskingPolicy || "none" },
              { term: "Approval", detail: manifest.approval?.required ? list(manifest.approval.businessTypes) : "not required" }
            ]} />
            <ChannelActionRow items={(manifest.actions || []).map((action) => action.label)} />
          </ChannelCard>
        ))}
      </ChannelCardGrid>
    </ChannelShell>
  );
}
