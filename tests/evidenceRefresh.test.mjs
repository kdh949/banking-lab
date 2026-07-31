import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
const script = "scripts/check-evidence-refresh.ts";

test("evidence refresh checker passes without marking Node retirement ready", async () => {
  const { stdout, stderr } = await execFileAsync(
    process.execPath,
    ["--experimental-strip-types", script],
    { maxBuffer: 1024 * 1024 }
  );
  assert.match(stdout, /Evidence refresh check: pass/);
  assert.match(stdout, /Node retirement gate is ready after verified passkey and final review evidence/);
});

test("evidence refresh gate is wired to the checker and review artifact", async () => {
  const packageJson = JSON.parse(await readFile("package.json", "utf8"));
  const gate = JSON.parse(await readFile("docs/migration/node-retirement-gate.json", "utf8"));
  const evidenceRefresh = gate.requiredGates.find((item) => item.id === "evidence-refresh");
  const matrix = await readFile("docs/test-evidence/parity-coverage-matrix.md", "utf8");

  assert.equal(packageJson.scripts["evidence:refresh-check"], `node --experimental-strip-types ${script}`);
  assert.equal(packageJson.scripts["retirement:stack-audit"], "node --experimental-strip-types scripts/check-stack-retirement-by-area.ts");
  assert.equal(packageJson.scripts["retirement:generated-boundary"], "node --experimental-strip-types scripts/check-generated-artifact-boundary.ts");
  assert.equal(packageJson.scripts["retirement:ready-simulate"], "node --experimental-strip-types scripts/check-node-retirement-ready-simulation.ts");
  assert.equal(packageJson.scripts["passkey:evidence:prepare"], "node --experimental-strip-types scripts/prepare-passkey-non-synthetic-evidence.ts");
  assert.equal(packageJson.scripts["passkey:evidence:readiness"], "node --experimental-strip-types scripts/check-passkey-manual-readiness.ts");
  assert.equal(packageJson.scripts["passkey:evidence:live-readiness"], "node --experimental-strip-types scripts/check-passkey-live-platform-readiness.ts");
  assert.equal(packageJson.scripts["retirement:final-review:prepare"], "node --experimental-strip-types scripts/prepare-final-retirement-review-evidence.ts");
  assert.equal(packageJson.scripts["retirement:final-review:record"], "node --experimental-strip-types scripts/record-final-retirement-review.ts");
  assert.equal(packageJson.scripts["retirement:final-review:verify"], "node --experimental-strip-types scripts/verify-final-retirement-review.ts");
  assert.equal(packageJson.scripts["governance:evidence"], "node --experimental-strip-types scripts/run-governance-evidence.ts");
  assert.equal(evidenceRefresh?.status, "pass");
  assert.ok(evidenceRefresh?.evidence?.includes("docs/test-evidence/evidence-refresh-review.md"));
  assert.ok(evidenceRefresh?.evidence?.includes("docs/test-evidence/stack-retirement-area-audit.md"));
  assert.ok(evidenceRefresh?.evidence?.includes("scripts/check-stack-retirement-by-area.ts"));
  assert.ok(evidenceRefresh?.evidence?.includes("tests/stackRetirementAreaAudit.test.mjs"));
  assert.ok(evidenceRefresh?.evidence?.includes("docs/test-evidence/generated-artifact-boundary.md"));
  assert.ok(evidenceRefresh?.evidence?.includes("scripts/check-generated-artifact-boundary.ts"));
  assert.ok(evidenceRefresh?.evidence?.includes("tests/generatedArtifactBoundary.test.mjs"));
  assert.ok(evidenceRefresh?.evidence?.includes("scripts/prepare-passkey-non-synthetic-evidence.ts"));
  assert.ok(evidenceRefresh?.evidence?.includes("tests/passkeyEvidencePrepare.test.mjs"));
  assert.ok(evidenceRefresh?.evidence?.includes("scripts/check-passkey-manual-readiness.ts"));
  assert.ok(evidenceRefresh?.evidence?.includes("tests/passkeyManualReadiness.test.mjs"));
  assert.ok(evidenceRefresh?.evidence?.includes("scripts/check-passkey-live-platform-readiness.ts"));
  assert.ok(evidenceRefresh?.evidence?.includes("tests/passkeyLivePlatformReadiness.test.mjs"));
  assert.ok(evidenceRefresh?.evidence?.includes("docs/test-evidence/generated/passkey-non-synthetic-evidence.json"));
  assert.ok(evidenceRefresh?.evidence?.includes("docs/test-evidence/final-retirement-review-verifier.md"));
  assert.ok(evidenceRefresh?.evidence?.includes("docs/test-evidence/generated/final-node-retirement-review.json"));
  assert.ok(evidenceRefresh?.evidence?.includes("scripts/prepare-final-retirement-review-evidence.ts"));
  assert.ok(evidenceRefresh?.evidence?.includes("scripts/record-final-retirement-review.ts"));
  assert.ok(evidenceRefresh?.evidence?.includes("scripts/verify-final-retirement-review.ts"));
  assert.ok(evidenceRefresh?.evidence?.includes("scripts/check-node-retirement-ready-simulation.ts"));
  assert.ok(evidenceRefresh?.evidence?.includes("tests/finalRetirementReviewPrepare.test.mjs"));
  assert.ok(evidenceRefresh?.evidence?.includes("tests/finalRetirementReviewRecorder.test.mjs"));
  assert.ok(evidenceRefresh?.evidence?.includes("tests/finalRetirementReviewVerifier.test.mjs"));
  assert.ok(evidenceRefresh?.evidence?.includes("tests/nodeRetirementReadySimulation.test.mjs"));
  assert.ok(evidenceRefresh?.evidence?.includes("docs/test-evidence/goal-completion-audit.md"));
  assert.ok(evidenceRefresh?.evidence?.includes("scripts/check-goal-completion-audit.ts"));
  assert.ok(evidenceRefresh?.evidence?.includes("tests/goalCompletionAudit.test.mjs"));
  assert.ok(evidenceRefresh?.evidence?.includes("scripts/run-governance-evidence.ts"));
  assert.ok(evidenceRefresh?.evidence?.includes("tests/governanceEvidence.test.mjs"));
  assert.ok(evidenceRefresh?.evidence?.includes("docs/test-evidence/hardening-h8-governance-artifacts.md"));
  assert.ok(evidenceRefresh?.evidence?.includes("docs/regulatory-mapping/governance-artifact-mapping.md"));
  assert.ok(evidenceRefresh?.evidence?.includes("docs/incident-response/synthetic-incident-response-runbook.md"));
  assert.ok(evidenceRefresh?.evidence?.includes("docs/test-evidence/generated/governance/governance-evidence-summary.json"));
  assert.ok(evidenceRefresh?.evidence?.includes("docs/test-evidence/generated/governance/access-rights-review.json"));
  assert.ok(evidenceRefresh?.evidence?.includes("docs/test-evidence/generated/governance/deployment-approval-evidence.json"));
  assert.ok(evidenceRefresh?.evidence?.includes("docs/test-evidence/generated/governance/incident-response-drill-log.json"));
  assert.ok(evidenceRefresh?.evidence?.includes("docs/test-evidence/generated/governance/vulnerability-remediation-tracker.json"));
  assert.ok(evidenceRefresh?.evidence?.includes(script));
  assert.ok(evidenceRefresh?.evidence?.includes("tests/evidenceRefresh.test.mjs"));
  assert.doesNotMatch(gate.statusReason, /evidence-refresh completion/i);
  assert.doesNotMatch(matrix, /\|\s*Partial\.\s*\|/);
  assert.match(matrix, /Pass for current mapped parity/);
});
