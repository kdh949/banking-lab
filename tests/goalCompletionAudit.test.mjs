import assert from "node:assert/strict";
import { execFile, spawnSync } from "node:child_process";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
const script = "scripts/check-goal-completion-audit.ts";
const doc = "docs/test-evidence/goal-completion-audit.md";

test("goal completion audit reports complete when all retirement gates pass", async () => {
  const { stdout, stderr } = await execFileAsync(
    process.execPath,
    ["--experimental-strip-types", script],
    { maxBuffer: 1024 * 1024 * 4 }
  );

  assert.equal(stderr, "");
  assert.match(stdout, /Goal completion audit: complete/);
  assert.match(stdout, /stack-retirement-by-area: pass/);
  assert.match(stdout, /generated-artifact-boundary: pass/);
  assert.match(stdout, /retirement-ready-state-simulation: pass/);
  assert.match(stdout, /passkey-non-synthetic-preflight: pass/);
  assert.match(stdout, /mapped-parity: pass/);
  assert.match(stdout, /gate:non-synthetic-passkey-operations: pass/);
  assert.match(stdout, /gate:retirement-review: pass/);
  assert.match(stdout, /node-retirement-gate: pass/);
});

test("goal completion audit passes when completion is required", () => {
  const result = spawnSync(
    process.execPath,
    ["--experimental-strip-types", script, "--require-complete"],
    { cwd: process.cwd(), encoding: "utf8", maxBuffer: 1024 * 1024 * 4 }
  );

  assert.equal(result.status, 0);
  assert.match(result.stdout, /Goal completion audit: complete/);
  assert.equal(result.stderr, "");
});

test("goal completion audit is wired into scripts and retirement evidence", async () => {
  const packageJson = JSON.parse(await readFile("package.json", "utf8"));
  const gate = JSON.parse(await readFile("docs/migration/node-retirement-gate.json", "utf8"));
  const evidenceRefresh = gate.requiredGates.find((item) => item.id === "evidence-refresh");
  const retirementReview = gate.requiredGates.find((item) => item.id === "retirement-review");
  const evidenceDoc = await readFile(doc, "utf8");
  const auditScript = await readFile(script, "utf8");

  assert.equal(packageJson.scripts["goal:completion-audit"], `node --experimental-strip-types ${script}`);
  assert.match(auditScript, /check-node-retirement-ready-simulation\.ts/);
  assert.match(auditScript, /check-passkey-non-synthetic-preflight\.ts/);
  assert.match(auditScript, /verify-final-retirement-review\.ts/);
  assert.ok(evidenceRefresh?.evidence?.includes(doc));
  assert.ok(evidenceRefresh?.evidence?.includes(script));
  assert.ok(evidenceRefresh?.evidence?.includes("tests/goalCompletionAudit.test.mjs"));
  assert.ok(retirementReview?.evidence?.includes(doc));
  assert.ok(retirementReview?.evidence?.includes(script));
  assert.ok(retirementReview?.evidence?.includes("tests/goalCompletionAudit.test.mjs"));
  assert.match(evidenceDoc, /Status:\s+pass/i);
  assert.match(evidenceDoc, /Goal completion audit: complete/);
  assert.match(evidenceDoc, /ready-state simulation/i);
  assert.match(evidenceDoc, /passkey preflight/i);
  assert.match(evidenceDoc, /strict final retirement review verifier/i);
  assert.match(evidenceDoc, /docs\/migration\/node-retirement-gate\.json` is `ready`/);
});
