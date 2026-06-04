import {
  ChannelBadge,
  ChannelCard,
  ChannelCardGrid,
  ChannelDefinitionList,
  ChannelMetric,
  ChannelMetricGrid,
  ChannelPanel,
  ChannelShell,
  ChannelSplit,
  ChannelTable,
  ChannelWorkflow
} from "../../../../packages/channel-ui/src";
import { ApiBackedCustomerPanel } from "../components/ApiBackedCustomerPanel";
import { loadCustomerWebManifests } from "../lib/manifestLoader";

export default async function CustomerWebPage() {
  const manifests = await loadCustomerWebManifests();
  const reasonRequired = manifests.filter((manifest) => manifest.audit.reasonRequired).length;
  const makerChecker = manifests.filter((manifest) => manifest.approval?.required).length;
  const workflowManifests = manifests.filter((manifest) => "workflow" in manifest);

  return (
    <ChannelShell appId="customer-web" eyebrow="Customer channel" title="Customer Web Banking" status="Synthetic only · Node reference retained">
      <ChannelMetricGrid>
        <ChannelMetric label="Default PII masking" value="CUSTOMER_SELF" />
        <ChannelMetric label="Reason-required manifests" value={reasonRequired} />
        <ChannelMetric label="Maker-checker manifests" value={makerChecker} />
      </ChannelMetricGrid>

      <ApiBackedCustomerPanel />

      <ChannelSplit
        aside={
          <ChannelPanel title="Manifest Screens">
            <ChannelCardGrid>
              {manifests.map((manifest) => (
                <ChannelCard
                  key={manifest.screenId}
                  screenId={manifest.screenId}
                  title={manifest.title}
                  meta={`${manifest.type} · ${manifest.domain}`}
                >
                  <ChannelBadge tone={manifest.audit.reasonRequired ? "critical" : "neutral"}>
                    {manifest.audit.reasonRequired ? "reason required" : "standard"}
                  </ChannelBadge>
                </ChannelCard>
              ))}
            </ChannelCardGrid>
          </ChannelPanel>
        }
      >
        <ChannelPanel title="Control Surface">
          <ChannelTable>
            <thead>
              <tr>
                <th>Screen</th>
                <th>Roles</th>
                <th>Audit</th>
                <th>Masking</th>
                <th>Approval</th>
              </tr>
            </thead>
            <tbody>
              {manifests.map((manifest) => (
                <tr key={manifest.screenId}>
                  <td>{manifest.screenId}</td>
                  <td>{manifest.requiredRoles.join(", ")}</td>
                  <td>
                    <ChannelBadge tone={manifest.audit.reasonRequired ? "critical" : "neutral"}>
                      {manifest.audit.reasonRequired ? "reason required" : "standard"}
                    </ChannelBadge>
                  </td>
                  <td>{manifest.audit.maskingPolicy}</td>
                  <td>
                    {manifest.approval?.required ? (
                      <ChannelBadge tone="critical">maker-checker</ChannelBadge>
                    ) : (
                      <ChannelBadge>not required</ChannelBadge>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </ChannelTable>
        </ChannelPanel>
      </ChannelSplit>

      {workflowManifests.length > 0 ? (
        <ChannelPanel title="Workflow Metadata">
          <ChannelCardGrid>
            {workflowManifests.map((manifest) => (
              <ChannelCard key={manifest.screenId} screenId={manifest.screenId} title={`${manifest.screenId} workflow`}>
                <ChannelWorkflow states={manifest.workflow?.states ?? []} />
                <ChannelDefinitionList
                  items={[
                    {
                      term: "Template",
                      detail: manifest.layout.template
                    }
                  ]}
                />
              </ChannelCard>
            ))}
          </ChannelCardGrid>
        </ChannelPanel>
      ) : null}
    </ChannelShell>
  );
}
