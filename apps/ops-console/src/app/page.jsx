import { loadChannelManifests } from "../lib/manifestLoader";

function list(value) {
  return Array.isArray(value) && value.length > 0 ? value.join(", ") : "none";
}

export default async function OpsConsolePage() {
  const manifests = await loadChannelManifests();

  return (
    <main>
      <div className="shell">
        <header className="topbar">
          <div>
            <p className="eyebrow">Operations Console</p>
            <h1>Closing and reconciliation controls</h1>
          </div>
          <span className="status">Balanced adjustments only</span>
        </header>

        <section className="ops-grid" aria-label="Operations manifest shell">
          <aside className="panel">
            <h2>Control Summary</h2>
            <ul>
              <li>Ledger invariant status before closing</li>
              <li>Closed business dates reject direct posting</li>
              <li>Mismatch corrections require approval</li>
              <li>Adjustment transaction remains balanced</li>
            </ul>
          </aside>

          <section className="cards">
            {manifests.map((manifest) => (
              <article className="card" key={manifest.screenId}>
                <div className="card-heading">
                  <span>{manifest.screenId}</span>
                  <h2>{manifest.title}</h2>
                  <p>{manifest.type} · {manifest.domain}</p>
                </div>
                <dl>
                  <div>
                    <dt>Template</dt>
                    <dd>{manifest.layout.template}</dd>
                  </div>
                  <div>
                    <dt>Roles</dt>
                    <dd>{list(manifest.requiredRoles)}</dd>
                  </div>
                  <div>
                    <dt>Declared Surface</dt>
                    <dd>{list(manifest.widgets || manifest.sections)}</dd>
                  </div>
                  <div>
                    <dt>Approval</dt>
                    <dd>{manifest.approval?.required ? list(manifest.approval.businessTypes) : "not required"}</dd>
                  </div>
                  <div>
                    <dt>Audit</dt>
                    <dd>{manifest.audit.reasonRequired ? "reason required" : "standard"}</dd>
                  </div>
                  <div>
                    <dt>Workflow</dt>
                    <dd>
                      {manifest.workflow?.states?.length ? (
                        <>
                          <span>workflow timeline</span>
                          <span>{manifest.workflow.states.join(", ")}</span>
                        </>
                      ) : (
                        "none"
                      )}
                    </dd>
                  </div>
                </dl>
              </article>
            ))}
          </section>
        </section>
      </div>
    </main>
  );
}
