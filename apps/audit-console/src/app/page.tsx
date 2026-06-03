import { ApiBackedAuditPanel } from "../components/ApiBackedAuditPanel";
import { loadChannelManifests } from "../lib/manifestLoader";

function list(value: readonly string[] | undefined | null): string {
  return Array.isArray(value) && value.length > 0 ? value.join(", ") : "none";
}

export default async function AuditConsolePage() {
  const manifests = await loadChannelManifests();

  return (
    <main>
      <div className="shell">
        <header className="topbar">
          <div>
            <p className="eyebrow">Audit Console</p>
            <h1>Hash-chain review workspace</h1>
          </div>
          <span className="status">Append-only evidence view</span>
        </header>

        <ApiBackedAuditPanel />

        <section className="audit-grid" aria-label="Audit console manifest shell">
          {manifests.map((manifest) => (
            <article className="audit-card" key={manifest.screenId}>
              <div className="heading">
                <span>{manifest.screenId}</span>
                <h2>{manifest.title}</h2>
              </div>
              <table>
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
              </table>
            </article>
          ))}
        </section>
      </div>
    </main>
  );
}
