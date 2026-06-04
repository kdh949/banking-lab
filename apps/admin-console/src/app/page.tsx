import {
  ChannelCard,
  ChannelCardGrid,
  ChannelShell,
  ChannelTable
} from "../../../../packages/channel-ui/src";
import { ApiBackedAdminPanel } from "../components/ApiBackedAdminPanel";
import { loadChannelManifests } from "../lib/manifestLoader";

function list(value: readonly string[] | undefined | null): string {
  return Array.isArray(value) && value.length > 0 ? value.join(", ") : "none";
}

export default async function AdminConsolePage() {
  const manifests = await loadChannelManifests();

  return (
    <ChannelShell appId="admin-console" eyebrow="Admin Console" title="Platform controls and privileged parameters" status="Synthetic operations only">
      <ApiBackedAdminPanel />

      <ChannelCardGrid>
        {manifests.map((manifest) => (
          <ChannelCard key={manifest.screenId} screenId={manifest.screenId} title={manifest.title} meta={`${manifest.type} · ${manifest.domain}`}>
            <ChannelTable>
              <tbody>
                <tr>
                  <th>Template</th>
                  <td>{manifest.layout.template}</td>
                </tr>
                <tr>
                  <th>Roles</th>
                  <td>{list(manifest.requiredRoles)}</td>
                </tr>
                <tr>
                  <th>Audit</th>
                  <td>{manifest.audit.reasonRequired ? "reason required" : "standard"} · {manifest.audit.maskingPolicy}</td>
                </tr>
                <tr>
                  <th>Approval</th>
                  <td>{manifest.approval?.required ? list(manifest.approval.businessTypes) : "not required"}</td>
                </tr>
                <tr>
                  <th>Surface</th>
                  <td>{list(manifest.widgets || manifest.sections)}</td>
                </tr>
                <tr>
                  <th>Parameter</th>
                  <td>{manifest.parameter?.namespace ?? "not parameterized"}</td>
                </tr>
              </tbody>
            </ChannelTable>
          </ChannelCard>
        ))}
      </ChannelCardGrid>
    </ChannelShell>
  );
}
