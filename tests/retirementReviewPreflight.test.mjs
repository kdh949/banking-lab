import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
const script = "scripts/check-retirement-review-preflight.ts";
const reviewDoc = "docs/test-evidence/node-retirement-review.md";

test("retirement review preflight passes with final retirement ready", async () => {
  const { stdout, stderr } = await execFileAsync(
    process.execPath,
    ["--experimental-strip-types", script],
    { maxBuffer: 1024 * 1024 * 8 }
  );
  assert.match(stdout, /Node retirement review preflight: pass/);
  assert.match(stdout, /Boundary, stack area, generated artifact, ready-state simulation, evidence refresh, passkey preflight, strict final review verifier, and retirement gate checks are consistent/);
  assert.match(stdout, /Node retirement gate is ready; passkey and final retirement review artifacts verify/);
});

test("retirement review gate keeps verified final review evidence", async () => {
  const packageJson = JSON.parse(await readFile("package.json", "utf8"));
  const gate = JSON.parse(await readFile("docs/migration/node-retirement-gate.json", "utf8"));
  const retirementReview = gate.requiredGates.find((item) => item.id === "retirement-review");
  const passkey = gate.requiredGates.find((item) => item.id === "non-synthetic-passkey-operations");
  const evidence = await readFile(reviewDoc, "utf8");
  const preflightScript = await readFile(script, "utf8");

  assert.equal(packageJson.scripts["retirement:review-preflight"], `node --experimental-strip-types ${script}`);
  assert.equal(packageJson.scripts["passkey:evidence:verify"], "node --experimental-strip-types scripts/verify-passkey-non-synthetic-evidence.ts");
  assert.equal(packageJson.scripts["retirement:stack-audit"], "node --experimental-strip-types scripts/check-stack-retirement-by-area.ts");
  assert.equal(packageJson.scripts["retirement:generated-boundary"], "node --experimental-strip-types scripts/check-generated-artifact-boundary.ts");
  assert.equal(packageJson.scripts["retirement:ready-simulate"], "node --experimental-strip-types scripts/check-node-retirement-ready-simulation.ts");
  assert.equal(packageJson.scripts["retirement:final-review:prepare"], "node --experimental-strip-types scripts/prepare-final-retirement-review-evidence.ts");
  assert.equal(packageJson.scripts["retirement:final-review:record"], "node --experimental-strip-types scripts/record-final-retirement-review.ts");
  assert.equal(packageJson.scripts["retirement:final-review:verify"], "node --experimental-strip-types scripts/verify-final-retirement-review.ts");
  assert.equal(packageJson.scripts["goal:completion-audit"], "node --experimental-strip-types scripts/check-goal-completion-audit.ts");
  assert.equal(gate.status, "ready");
  assert.equal(passkey?.status, "pass");
  assert.equal(retirementReview?.status, "pass");
  assert.ok(passkey?.evidence?.includes("docs/test-evidence/generated/passkey-non-synthetic-evidence.json"));
  assert.ok(retirementReview?.evidence?.includes(reviewDoc));
  assert.ok(retirementReview?.evidence?.includes("docs/test-evidence/generated/final-node-retirement-review.json"));
  assert.ok(retirementReview?.evidence?.includes("docs/test-evidence/stack-retirement-area-audit.md"));
  assert.ok(retirementReview?.evidence?.includes("scripts/check-stack-retirement-by-area.ts"));
  assert.ok(retirementReview?.evidence?.includes("tests/stackRetirementAreaAudit.test.mjs"));
  assert.ok(retirementReview?.evidence?.includes("docs/test-evidence/generated-artifact-boundary.md"));
  assert.ok(retirementReview?.evidence?.includes("scripts/check-generated-artifact-boundary.ts"));
  assert.ok(retirementReview?.evidence?.includes("tests/generatedArtifactBoundary.test.mjs"));
  assert.ok(retirementReview?.evidence?.includes("docs/test-evidence/final-retirement-review-verifier.md"));
  assert.ok(retirementReview?.evidence?.includes("scripts/prepare-final-retirement-review-evidence.ts"));
  assert.ok(retirementReview?.evidence?.includes("scripts/record-final-retirement-review.ts"));
  assert.ok(retirementReview?.evidence?.includes("scripts/verify-final-retirement-review.ts"));
  assert.ok(retirementReview?.evidence?.includes("scripts/check-node-retirement-ready-simulation.ts"));
  assert.ok(retirementReview?.evidence?.includes("tests/finalRetirementReviewPrepare.test.mjs"));
  assert.ok(retirementReview?.evidence?.includes("tests/finalRetirementReviewRecorder.test.mjs"));
  assert.ok(retirementReview?.evidence?.includes("tests/finalRetirementReviewVerifier.test.mjs"));
  assert.ok(retirementReview?.evidence?.includes("tests/nodeRetirementReadySimulation.test.mjs"));
  assert.ok(retirementReview?.evidence?.includes("docs/test-evidence/goal-completion-audit.md"));
  assert.ok(retirementReview?.evidence?.includes("scripts/check-goal-completion-audit.ts"));
  assert.ok(retirementReview?.evidence?.includes("tests/goalCompletionAudit.test.mjs"));
  assert.ok(retirementReview?.evidence?.includes(script));
  assert.ok(retirementReview?.evidence?.includes("tests/retirementReviewPreflight.test.mjs"));
  assert.match(preflightScript, /assertFinalReviewVerifierPasses/);
  assert.match(evidence, /Status:\s+pass/i);
  assert.match(evidence, /npm run retirement:review-preflight/);
  assert.match(evidence, /npm run retirement:final-review:prepare/);
  assert.match(evidence, /npm run retirement:stack-audit/);
  assert.match(evidence, /npm run retirement:generated-boundary/);
  assert.match(evidence, /npm run retirement:ready-simulate/);
  assert.match(evidence, /npm run retirement:final-review:record/);
  assert.match(evidence, /npm run retirement:final-review:verify/);
  assert.match(evidence, /npm run goal:completion-audit/);
  assert.match(evidence, /npm run passkey:evidence:verify/);
  assert.match(evidence, /docs\/test-evidence\/generated\/passkey-non-synthetic-evidence\.json/);
  assert.match(evidence, /docs\/test-evidence\/generated\/final-node-retirement-review\.json/);
  assert.match(evidence, /strict final retirement review verifier/i);
  assert.match(evidence, /Node retirement gate is ready/);
  assert.match(evidence, /final retirement review/i);
});
