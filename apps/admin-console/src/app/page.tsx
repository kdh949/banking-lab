import { ApiBackedAdminPanel } from "../components/ApiBackedAdminPanel";
import { loadChannelManifests } from "../lib/manifestLoader";

function list(value: readonly string[] | undefined | null): string {
  return Array.isArray(value) && value.length > 0 ? value.join(", ") : "none";
}

export default async function AdminConsolePage() {
  const manifests = await loadChannelManifests();

  return (
    <main>
      <div className="shell">
        <header className="topbar">
          <div>
            <p className="eyebrow">Admin Console</p>
            <h1>Platform controls and privileged parameters</h1>
          </div>
          <span className="status">Synthetic operations only</span>
        </header>

        <ApiBackedAdminPanel />

        <section className="admin-grid" aria-label="Admin manifest shell">
          {manifests.map((manifest) => (
            <article className="admin-card" key={manifest.screenId}>
              <div className="heading">
                <span>{manifest.screenId}</span>
                <h2>{manifest.title}</h2>
                <p>{manifest.type} · {manifest.domain}</p>
              </div>
              <table>
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
              </table>
            </article>
          ))}
        </section>
      </div>
    </main>
  );
}
