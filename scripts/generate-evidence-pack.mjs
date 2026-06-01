import { mkdir, readFile, writeFile } from "node:fs/promises";

const generatedDir = "docs/test-evidence/generated";
const phases = [
  {
    phase: "Phase 1",
    file: "phase-1-foundation.json",
    report: "docs/test-evidence/phase-1-foundation.md"
  },
  {
    phase: "Phase 2",
    file: "phase-2-ledger-core.json",
    report: "docs/test-evidence/phase-2-ledger-core.md"
  },
  {
    phase: "Phase 3",
    file: "phase-3-staff-terminal.json",
    report: "docs/test-evidence/phase-3-staff-terminal.md"
  },
  {
    phase: "Phase 4",
    file: "phase-4-customer-web.json",
    report: "docs/test-evidence/phase-4-customer-web.md"
  },
  {
    phase: "Phase 5",
    file: "phase-5-complaint-workflow.json",
    report: "docs/test-evidence/phase-5-complaint-workflow.md"
  },
  {
    phase: "Phase 6",
    file: "phase-6-fds-aml-reconciliation.json",
    report: "docs/test-evidence/fds-aml-reconciliation.md"
  }
];

async function readEvidence(phase) {
  const payload = JSON.parse(await readFile(`${generatedDir}/${phase.file}`, "utf8"));
  const checks = payload.checks || [];
  return {
    ...phase,
    scope: payload.scope,
    generatedAt: payload.generatedAt,
    syntheticOnly: payload.syntheticOnly === true,
    totalChecks: checks.length,
    passedChecks: checks.filter((check) => check.status === "pass").length,
    failedChecks: checks.filter((check) => check.status !== "pass").map((check) => check.id),
    checks
  };
}

const phaseEvidence = [];
for (const phase of phases) {
  phaseEvidence.push(await readEvidence(phase));
}

const summary = {
  generatedAt: new Date().toISOString(),
  scope: "Bank-grade Core Banking Lab Evidence Pack",
  syntheticOnly: phaseEvidence.every((phase) => phase.syntheticOnly),
  totalChecks: phaseEvidence.reduce((sum, phase) => sum + phase.totalChecks, 0),
  passedChecks: phaseEvidence.reduce((sum, phase) => sum + phase.passedChecks, 0),
  failedChecks: phaseEvidence.flatMap((phase) => phase.failedChecks.map((checkId) => `${phase.phase}:${checkId}`)),
  phases: phaseEvidence.map((phase) => ({
    phase: phase.phase,
    scope: phase.scope,
    generatedAt: phase.generatedAt,
    totalChecks: phase.totalChecks,
    passedChecks: phase.passedChecks,
    failedChecks: phase.failedChecks,
    report: phase.report
  })),
  evidenceArtifacts: [
    "docs/adr",
    "docs/architecture",
    "docs/regulatory-mapping",
    "docs/threat-model",
    "docs/test-evidence",
    "docs/failure-drills",
    "docs/reconciliation-reports",
    "docs/demo-scenarios"
  ]
};

const markdown = `# Evidence Pack Summary

Generated at: ${summary.generatedAt}

Synthetic-only boundary: ${summary.syntheticOnly ? "pass" : "fail"}

Total checks: ${summary.passedChecks}/${summary.totalChecks}

| Phase | Scope | Checks | Report |
| --- | --- | --- | --- |
${summary.phases.map((phase) => `| ${phase.phase} | ${phase.scope} | ${phase.passedChecks}/${phase.totalChecks} | \`${phase.report}\` |`).join("\n")}

## Failed Checks

${summary.failedChecks.length === 0 ? "None." : summary.failedChecks.map((check) => `- ${check}`).join("\n")}

## Artifact Families

${summary.evidenceArtifacts.map((artifact) => `- \`${artifact}\``).join("\n")}
`;

await mkdir(generatedDir, { recursive: true });
await writeFile(`${generatedDir}/evidence-pack-summary.json`, `${JSON.stringify(summary, null, 2)}\n`);
await writeFile("docs/test-evidence/evidence-pack-summary.md", markdown);

console.log("Wrote docs/test-evidence/generated/evidence-pack-summary.json");
console.log("Wrote docs/test-evidence/evidence-pack-summary.md");
