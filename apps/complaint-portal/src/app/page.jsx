import { loadChannelManifests } from "../lib/manifestLoader";

function list(value) {
  return Array.isArray(value) && value.length > 0 ? value.join(", ") : "none";
}

export default async function ComplaintPortalPage() {
  const manifests = await loadChannelManifests();

  return (
    <main>
      <div className="shell">
        <header className="topbar">
          <div>
            <p className="eyebrow">Electronic Complaint Portal</p>
            <h1>Customer case workspace</h1>
          </div>
          <span className="status">Synthetic complaints only</span>
        </header>

        <section className="case-grid" aria-label="Complaint portal manifests">
          {manifests.map((manifest) => (
            <article className="case-card" key={manifest.screenId}>
              <div className="case-heading">
                <span>{manifest.screenId}</span>
                <h2>{manifest.title}</h2>
              </div>
              <div className="workflow">
                {(manifest.workflow?.states || []).map((state) => (
                  <span key={state}>{state}</span>
                ))}
              </div>
              <dl>
                <div>
                  <dt>Template</dt>
                  <dd>{manifest.layout.template}</dd>
                </div>
                <div>
                  <dt>Sections</dt>
                  <dd>{list(manifest.sections)}</dd>
                </div>
                <div>
                  <dt>SLA</dt>
                  <dd>{manifest.sla?.enabled ? `${manifest.sla.targetHours} hours` : "none"}</dd>
                </div>
                <div>
                  <dt>Audit</dt>
                  <dd>{manifest.audit.selfService ? "customer self-service" : "standard"}</dd>
                </div>
              </dl>
              <div className="actions">
                {(manifest.actions || []).map((action) => (
                  <span key={action.id}>{action.label}</span>
                ))}
              </div>
            </article>
          ))}
        </section>
      </div>
    </main>
  );
}
