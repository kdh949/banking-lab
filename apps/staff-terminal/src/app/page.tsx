import type { ScreenManifest } from "../../../../packages/screen-engine/src/types";
import { ApiBackedStaffPanel } from "../components/ApiBackedStaffPanel";
import { loadChannelManifests } from "../lib/manifestLoader";

function list(value: readonly string[] | undefined | null): string {
  return Array.isArray(value) && value.length > 0 ? value.join(", ") : "none";
}

function endpointOf(manifest: ScreenManifest): string {
  return manifest.query?.endpoint || manifest.api?.command || manifest.actions?.[0]?.target || "declared in workflow";
}

function fieldCount(manifest: ScreenManifest): number {
  return manifest.query?.fields?.length || manifest.fields?.length || manifest.sections?.length || manifest.widgets?.length || 0;
}

export default async function StaffTerminalPage() {
  const manifests = await loadChannelManifests();
  const reasonRequired = manifests.filter((manifest) => manifest.audit.reasonRequired).length;
  const makerChecker = manifests.filter((manifest) => manifest.approval?.required).length;

  return (
    <main>
      <div className="shell">
        <header className="topbar">
          <div>
            <p className="eyebrow">Staff Integrated Terminal</p>
            <h1>Transaction-code workspace</h1>
          </div>
          <span className="status">Synthetic only · Manifest rendered</span>
        </header>

        <section className="terminal-strip" aria-label="Staff terminal controls">
          <label>
            <span>Transaction code</span>
            <input value={manifests[0]?.transactionCode || ""} readOnly aria-label="Transaction code" />
          </label>
          <div>
            <span className="metric">{manifests.length}</span>
            <span className="metric-label">screens</span>
          </div>
          <div>
            <span className="metric">{reasonRequired}</span>
            <span className="metric-label">reason-required</span>
          </div>
          <div>
            <span className="metric">{makerChecker}</span>
            <span className="metric-label">maker-checker</span>
          </div>
        </section>

        <section className="layout-grid" aria-label="Manifest-rendered staff screens">
          <aside className="side-stack">
            <section className="panel context-panel">
              <h2>Customer Context</h2>
              <dl>
                <div>
                  <dt>PII exposure</dt>
                  <dd>Masked by default</dd>
                </div>
                <div>
                  <dt>Lookup control</dt>
                  <dd>Business reason required</dd>
                </div>
                <div>
                  <dt>Approval inbox</dt>
                  <dd>APR-001 declared</dd>
                </div>
              </dl>
            </section>
            <ApiBackedStaffPanel />
          </aside>

          <section className="screen-stack">
            {manifests.map((manifest) => (
              <article className="screen-card" key={manifest.screenId}>
                <div className="screen-card-header">
                  <div>
                    <span className="screen-code">{manifest.transactionCode || manifest.screenId}</span>
                    <h2>{manifest.title}</h2>
                  </div>
                  <span className="badge">{manifest.layout.template}</span>
                </div>

                <div className="screen-meta-grid">
                  <div>
                    <span>Roles</span>
                    <strong>{list(manifest.requiredRoles)}</strong>
                  </div>
                  <div>
                    <span>Audit</span>
                    <strong>{manifest.audit.reasonRequired ? "reason required" : "standard"}</strong>
                  </div>
                  <div>
                    <span>Masking</span>
                    <strong>{manifest.audit.maskingPolicy}</strong>
                  </div>
                  <div>
                    <span>Approval</span>
                    <strong>{manifest.approval?.required ? "maker-checker" : "not required"}</strong>
                  </div>
                </div>

                <div className="detail-row">
                  <span>{manifest.type}</span>
                  <span>{manifest.domain}</span>
                  <span>{fieldCount(manifest)} manifest elements</span>
                </div>
                <p className="endpoint">{endpointOf(manifest)}</p>
              </article>
            ))}
          </section>
        </section>
      </div>
    </main>
  );
}
