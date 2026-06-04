import {
  ChannelCard,
  ChannelCardGrid,
  ChannelShell,
  ChannelTable
} from "../../../../packages/channel-ui/src";
import { ApiBackedAuditPanel } from "../components/ApiBackedAuditPanel";
import { loadChannelManifests } from "../lib/manifestLoader";

function list(value: readonly string[] | undefined | null): string {
  return Array.isArray(value) && value.length > 0 ? value.join(", ") : "none";
}

export default async function AuditConsolePage() {
  const manifests = await loadChannelManifests();

  return (
    <ChannelShell appId="audit-console" eyebrow="Audit Console" title="Hash-chain review workspace" status="Append-only evidence view">
      <ApiBackedAuditPanel />

      <ChannelCardGrid>
        {manifests.map((manifest) => (
          <ChannelCard key={manifest.screenId} screenId={manifest.screenId} title={manifest.title}>
            <ChannelTable>
              <tbody>
                <tr>
                  <th>Roles</th>
                  <td>{list(manifest.requiredRoles)}</td>
                </tr>
                <tr>
                  <th>Endpoint</th>
                  <td>{manifest.query?.endpoint || "none"}</td>
                </tr>
                <tr>
                  <th>Columns</th>
                  <td>{list(manifest.resultTable?.columns)}</td>
                </tr>
                <tr>
                  <th>Approval</th>
                  <td>{manifest.approval?.required ? "maker-checker" : "not required"}</td>
                </tr>
                <tr>
                  <th>Approval Types</th>
                  <td>{manifest.approval?.required ? list(manifest.approval.businessTypes) : "none"}</td>
                </tr>
                <tr>
                  <th>Audit</th>
                  <td>{manifest.audit.reasonRequired ? "reason required" : manifest.audit.enabled ? "enabled" : "disabled"}</td>
                </tr>
              </tbody>
            </ChannelTable>
          </ChannelCard>
        ))}
      </ChannelCardGrid>
    </ChannelShell>
  );
}
