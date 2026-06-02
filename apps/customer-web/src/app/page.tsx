import { loadCustomerWebManifests } from "../lib/manifestLoader";

export default async function CustomerWebPage() {
  const manifests = await loadCustomerWebManifests();

  return (
    <main>
      <div className="workbench">
        <header className="toolbar">
          <h1>Customer Web Banking</h1>
          <span className="status">Synthetic only · Node reference retained</span>
        </header>

        <section className="grid" aria-label="Customer web manifest workbench">
          <aside className="panel">
            <h2>Manifest Screens</h2>
            <div className="screen-list">
              {manifests.map((manifest) => (
                <article className="screen-row" key={manifest.screenId}>
                  <span className="screen-id">{manifest.screenId}</span>
                  <span className="screen-meta">
                    {manifest.type} · {manifest.domain}
                  </span>
                </article>
              ))}
            </div>
          </aside>

          <section className="panel">
            <h2>Control Surface</h2>
            <table className="policy-table">
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
                      <span className={manifest.audit.reasonRequired ? "badge critical" : "badge"}>
                        {manifest.audit.reasonRequired ? "reason required" : "standard"}
                      </span>
                    </td>
                    <td>{manifest.audit.maskingPolicy}</td>
                    <td>
                      {manifest.approval?.required ? (
                        <span className="badge critical">maker-checker</span>
                      ) : (
                        <span className="badge">not required</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        </section>
      </div>
    </main>
  );
}
