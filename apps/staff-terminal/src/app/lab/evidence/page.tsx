import { StaffApiEvidencePanel } from "../../../components/terminal/StaffApiEvidencePanel";

export default function StaffEvidencePage() {
  return (
    <main className="iworks-root staff-lab-root">
      <section className="staff-lab-page" aria-labelledby="staff-evidence-title">
        <p>LAB_ONLY · synthetic smoke controls</p>
        <h1 id="staff-evidence-title">Staff API Evidence</h1>
        <StaffApiEvidencePanel />
      </section>
    </main>
  );
}
