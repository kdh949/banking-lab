import { ApiBackedRiskPanel } from "../components/ApiBackedRiskPanel";
import { loadChannelManifests } from "../lib/manifestLoader";

function list(value: readonly string[] | undefined | null): string {
  return Array.isArray(value) && value.length > 0 ? value.join(", ") : "none";
}

export default async function FdsAmlConsolePage() {
  const manifests = await loadChannelManifests();
  const heldReview = manifests.find((manifest) => manifest.screenId === "FDS-201");
  const amlReview = manifests.find((manifest) => manifest.screenId === "AML-201");

  return (
    <main>
      <div className="shell">
        <header className="topbar">
          <div>
            <p className="eyebrow">FDS / AML Console</p>
            <h1>Investigation and approval workspace</h1>
          </div>
          <span className="status">Held transfers do not post</span>
        </header>

        <section className="summary-grid" aria-label="Risk control summary">
          <article>
            <span>FDS release/block</span>
            <strong>{heldReview?.approval?.required ? "maker-checker" : "pending manifest"}</strong>
          </article>
          <article>
            <span>AML closure</span>
            <strong>{amlReview?.approval?.required ? "approval controlled" : "pending manifest"}</strong>
          </article>
          <article>
            <span>PII policy</span>
            <strong>masked by default</strong>
          </article>
        </section>

        <ApiBackedRiskPanel />

        <section className="case-grid" aria-label="FDS AML manifest screens">
          {manifests.map((manifest) => (
            <article className="case-card" key={manifest.screenId}>
              <div className="case-heading">
                <span>{manifest.screenId}</span>
                <h2>{manifest.title}</h2>
                <p>{manifest.domain} · {manifest.layout.template}</p>
              </div>
              <div className="workflow">
                <span>workflow timeline</span>
                {(manifest.workflow?.states || []).map((state) => (
                  <span key={state}>{state}</span>
                ))}
              </div>
              <dl>
                <div>
                  <dt>Roles</dt>
                  <dd>{list(manifest.requiredRoles)}</dd>
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
                  <dt>Approval Types</dt>
                  <dd>{manifest.approval?.required ? list(manifest.approval.businessTypes) : "not required"}</dd>
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
