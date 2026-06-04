import {
  ChannelCard,
  ChannelCardGrid,
  ChannelDefinitionList,
  ChannelPanel,
  ChannelShell,
  ChannelSplit,
  ChannelWorkflow
} from "../../../../packages/channel-ui/src";
import { ApiBackedOpsPanel } from "../components/ApiBackedOpsPanel";
import { loadChannelManifests } from "../lib/manifestLoader";

function list(value: readonly string[] | undefined | null): string {
  return Array.isArray(value) && value.length > 0 ? value.join(", ") : "none";
}

export default async function OpsConsolePage() {
  const manifests = await loadChannelManifests();

  return (
    <ChannelShell appId="ops-console" eyebrow="Operations Console" title="Closing and reconciliation controls" status="Balanced adjustments only">
      <ApiBackedOpsPanel />

      <ChannelSplit
        aside={
          <ChannelPanel title="Control Summary">
            <ul>
              <li>Ledger invariant status before closing</li>
              <li>Closed business dates reject direct posting</li>
              <li>Mismatch corrections require approval</li>
              <li>Adjustment transaction remains balanced</li>
            </ul>
          </ChannelPanel>
        }
      >
        <ChannelCardGrid>
          {manifests.map((manifest) => (
            <ChannelCard key={manifest.screenId} screenId={manifest.screenId} title={manifest.title} meta={`${manifest.type} · ${manifest.domain}`}>
              {manifest.workflow?.states?.length ? <ChannelWorkflow states={manifest.workflow.states} /> : null}
              <ChannelDefinitionList
                items={[
                  { term: "Template", detail: manifest.layout.template },
                  { term: "Roles", detail: list(manifest.requiredRoles) },
                  { term: "Declared Surface", detail: list(manifest.widgets || manifest.sections) },
                  { term: "Approval", detail: manifest.approval?.required ? list(manifest.approval.businessTypes) : "not required" },
                  { term: "Audit", detail: manifest.audit.reasonRequired ? "reason required" : "standard" },
                  { term: "Workflow", detail: manifest.workflow?.states?.length ? "declared" : "none" }
                ]}
              />
            </ChannelCard>
          ))}
        </ChannelCardGrid>
      </ChannelSplit>
    </ChannelShell>
  );
}
