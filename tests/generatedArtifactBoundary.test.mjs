import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
const script = "scripts/check-generated-artifact-boundary.ts";
const doc = "docs/test-evidence/generated-artifact-boundary.md";

test("generated artifact boundary distinguishes ignored build output from target source", async () => {
  const { stdout, stderr } = await execFileAsync(
    process.execPath,
    ["--experimental-strip-types", script],
    { maxBuffer: 1024 * 1024 * 4 }
  );

  assert.equal(stderr, "");
  assert.match(stdout, /Generated artifact boundary audit: pass/);
  assert.match(stdout, /Tracked target legacy\/static extension files: 0/);
  assert.match(stdout, /Untracked source-like legacy\/static extension files: 0/);
  assert.match(stdout, /Ignored generated directories: \d+/);
});

test("generated artifact boundary is wired into scripts and evidence", async () => {
  const packageJson = JSON.parse(await readFile("package.json", "utf8"));
  const gate = JSON.parse(await readFile("docs/migration/node-retirement-gate.json", "utf8"));
  const evidenceRefresh = gate.requiredGates.find((item) => item.id === "evidence-refresh");
  const retirementReview = gate.requiredGates.find((item) => item.id === "retirement-review");
  const evidenceDoc = await readFile(doc, "utf8");

  assert.equal(packageJson.scripts["retirement:generated-boundary"], `node --experimental-strip-types ${script}`);
  assert.ok(evidenceRefresh?.evidence?.includes(doc));
  assert.ok(evidenceRefresh?.evidence?.includes(script));
  assert.ok(evidenceRefresh?.evidence?.includes("tests/generatedArtifactBoundary.test.mjs"));
  assert.ok(retirementReview?.evidence?.includes(doc));
  assert.ok(retirementReview?.evidence?.includes(script));
  assert.ok(retirementReview?.evidence?.includes("tests/generatedArtifactBoundary.test.mjs"));
  assert.match(evidenceDoc, /Status:\s+pass/i);
  assert.match(evidenceDoc, /does not mark Node retirement ready/);
});
