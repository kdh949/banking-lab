import {
  ChannelBadge,
  ChannelCard,
  ChannelCardGrid,
  ChannelDefinitionList,
  ChannelPanel,
  ChannelShell,
  ChannelSplit,
  ChannelTable,
  ChannelWorkflow
} from "../../../../packages/channel-ui/src/customer-ui";
import { customerWorkflowRouteSummaries } from "./workflow-routes";
import { loadCustomerWebManifests } from "../lib/manifestLoader";

export async function CustomerManifestCatalog() {
  const manifests = await loadCustomerWebManifests();
  const workflowManifests = manifests.filter((manifest) => "workflow" in manifest);

  return (
    <ChannelShell appId="customer-web" eyebrow="Lab · manifest catalog" title="Customer Screen Manifests" status="LAB_ONLY · not a product route">
      <ChannelPanel title="Route Workflow Surface" eyebrow="Lab inventory">
        <nav className="workflow-route-nav" aria-label="Customer manifest route inventory">
          {customerWorkflowRouteSummaries.map((route) => (
            <a href={route.href.replace("[accountId]", "ACC-SELECTED").replace("[resultId]", "TRF-RESULT").replace("[caseId]", "CMP-CASE").replace("[cardId]", "CARD-SELECTED")} key={route.href}>
              <strong>{route.title}</strong>
              <span className="workflow-route-note">{route.screenIds.join(", ")}</span>
            </a>
          ))}
        </nav>
      </ChannelPanel>

      <ChannelSplit aside={<ManifestCards manifests={manifests} />}>
        <ChannelPanel title="Control Surface">
          <ChannelTable>
            <thead><tr><th>Screen</th><th>Roles</th><th>Audit</th><th>Masking</th><th>Approval</th></tr></thead>
            <tbody>
              {manifests.map((manifest) => (
                <tr key={manifest.screenId}>
                  <td>{manifest.screenId}</td>
                  <td>{manifest.requiredRoles.join(", ")}</td>
                  <td>{manifest.audit.reasonRequired ? "reason required" : "standard"}</td>
                  <td>{manifest.audit.maskingPolicy}</td>
                  <td>{manifest.approval?.required ? "maker-checker" : "not required"}</td>
                </tr>
              ))}
            </tbody>
          </ChannelTable>
        </ChannelPanel>
      </ChannelSplit>

      <ChannelPanel title="Workflow Metadata">
        <ChannelCardGrid>
          {workflowManifests.map((manifest) => (
            <ChannelCard key={manifest.screenId} screenId={manifest.screenId} title={`${manifest.screenId} workflow`}>
              <ChannelWorkflow states={manifest.workflow?.states ?? []} />
              <ChannelDefinitionList items={[{ term: "Template", detail: manifest.layout.template }]} />
            </ChannelCard>
          ))}
        </ChannelCardGrid>
      </ChannelPanel>
    </ChannelShell>
  );
}

function ManifestCards({ manifests }: { readonly manifests: Awaited<ReturnType<typeof loadCustomerWebManifests>> }) {
  return (
    <ChannelPanel title="Manifest Screens">
      <ChannelCardGrid>
        {manifests.map((manifest) => (
          <ChannelCard key={manifest.screenId} screenId={manifest.screenId} title={manifest.title} meta={`${manifest.type} · ${manifest.domain}`}>
            <ChannelBadge tone={manifest.audit.reasonRequired ? "critical" : "neutral"}>
              {manifest.audit.reasonRequired ? "reason required" : "standard"}
            </ChannelBadge>
          </ChannelCard>
        ))}
      </ChannelCardGrid>
    </ChannelPanel>
  );
}
