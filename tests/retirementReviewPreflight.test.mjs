import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
const script = "scripts/check-retirement-review-preflight.ts";
const reviewDoc = "docs/test-evidence/node-retirement-review.md";

test("retirement review preflight passes while keeping final retirement blocked", async () => {
  const { stdout, stderr } = await execFileAsync(
    process.execPath,
    ["--experimental-strip-types", script],
    { maxBuffer: 1024 * 1024 * 8 }
  );

  assert.equal(stderr, "");
  assert.match(stdout, /Node retirement review preflight: pass/);
  assert.match(stdout, /Boundary, stack area, evidence refresh, passkey preflight, and retirement gate checks are consistent/);
  assert.match(stdout, /does not mark Node retirement ready/);
  assert.match(stdout, /non-synthetic passkey operations and final retirement review remain pending/);
});

test("retirement review gate keeps preflight evidence but remains pending", async () => {
  const packageJson = JSON.parse(await readFile("package.json", "utf8"));
  const gate = JSON.parse(await readFile("docs/migration/node-retirement-gate.json", "utf8"));
  const retirementReview = gate.requiredGates.find((item) => item.id === "retirement-review");
  const passkey = gate.requiredGates.find((item) => item.id === "non-synthetic-passkey-operations");
  const evidence = await readFile(reviewDoc, "utf8");

  assert.equal(packageJson.scripts["retirement:review-preflight"], `node --experimental-strip-types ${script}`);
  assert.equal(packageJson.scripts["passkey:evidence:verify"], "node --experimental-strip-types scripts/verify-passkey-non-synthetic-evidence.ts");
  assert.equal(packageJson.scripts["retirement:stack-audit"], "node --experimental-strip-types scripts/check-stack-retirement-by-area.ts");
  assert.equal(packageJson.scripts["goal:completion-audit"], "node --experimental-strip-types scripts/check-goal-completion-audit.ts");
  assert.equal(gate.status, "blocked");
  assert.equal(passkey?.status, "pending");
  assert.equal(retirementReview?.status, "pending");
  assert.ok(retirementReview?.evidence?.includes(reviewDoc));
  assert.ok(retirementReview?.evidence?.includes("docs/test-evidence/stack-retirement-area-audit.md"));
  assert.ok(retirementReview?.evidence?.includes("scripts/check-stack-retirement-by-area.ts"));
  assert.ok(retirementReview?.evidence?.includes("tests/stackRetirementAreaAudit.test.mjs"));
  assert.ok(retirementReview?.evidence?.includes("docs/test-evidence/goal-completion-audit.md"));
  assert.ok(retirementReview?.evidence?.includes("scripts/check-goal-completion-audit.ts"));
  assert.ok(retirementReview?.evidence?.includes("tests/goalCompletionAudit.test.mjs"));
  assert.ok(retirementReview?.evidence?.includes(script));
  assert.ok(retirementReview?.evidence?.includes("tests/retirementReviewPreflight.test.mjs"));
  assert.match(evidence, /Status:\s+blocked/i);
  assert.match(evidence, /npm run retirement:review-preflight/);
  assert.match(evidence, /npm run retirement:stack-audit/);
  assert.match(evidence, /npm run goal:completion-audit/);
  assert.match(evidence, /npm run passkey:evidence:verify/);
  assert.match(evidence, /docs\/test-evidence\/generated\/passkey-non-synthetic-evidence\.json/);
  assert.match(evidence, /does not mark Node retirement ready/);
});
