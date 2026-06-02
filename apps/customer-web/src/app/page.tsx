import { loadCustomerWebManifests } from "../lib/manifestLoader";

export default async function CustomerWebPage() {
  const manifests = await loadCustomerWebManifests();
  const reasonRequired = manifests.filter((manifest) => manifest.audit.reasonRequired).length;
  const makerChecker = manifests.filter((manifest) => manifest.approval?.required).length;
  const workflowManifests = manifests.filter((manifest) => "workflow" in manifest);

  return (
    <main>
      <div className="workbench">
        <header className="toolbar">
          <h1>Customer Web Banking</h1>
          <span className="status">Synthetic only · Node reference retained</span>
        </header>

        <section className="panel" aria-label="Customer channel control summary">
          <h2>Control Summary</h2>
          <dl>
            <div>
              <dt>Default PII masking</dt>
              <dd>CUSTOMER_SELF</dd>
            </div>
            <div>
              <dt>Reason-required manifests</dt>
              <dd>{reasonRequired}</dd>
            </div>
            <div>
              <dt>Maker-checker manifests</dt>
              <dd>{makerChecker}</dd>
            </div>
          </dl>
        </section>

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
                  <span>{manifest.title}</span>
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

        {workflowManifests.length > 0 ? (
          <section className="panel" aria-label="Customer workflow manifests">
            <h2>Workflow Metadata</h2>
            {workflowManifests.map((manifest) => (
              <article className="screen-row" key={manifest.screenId}>
                <span>{manifest.screenId}</span>
                <span>workflow timeline</span>
                <span>{manifest.workflow?.states?.join(", ")}</span>
              </article>
            ))}
          </section>
        ) : null}
      </div>
    </main>
  );
}
