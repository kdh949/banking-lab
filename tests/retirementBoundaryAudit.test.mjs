import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);

test("retirement boundary audit passes with current gate ready", async () => {
  const { stdout, stderr } = await execFileAsync(
    process.execPath,
    ["--experimental-strip-types", "scripts/check-retirement-boundary-audit.ts"],
    { maxBuffer: 1024 * 1024 }
  );

  assert.equal(stderr, "");
  assert.match(stdout, /Retirement boundary audit: ready/);
  assert.match(stdout, /Reference boundary: pass/);
  assert.match(stdout, /Target stack anchors: pass/);
  assert.match(stdout, /Evidence paths: pass/);
  assert.match(stdout, /Parity map: pass \(42\/42 mapped scenarios\)/);
  assert.doesNotMatch(stdout, /Incomplete gates:/);
});

test("retirement boundary audit is wired into the package scripts and gate evidence", async () => {
  const packageJson = JSON.parse(await readFile("package.json", "utf8"));
  const gate = JSON.parse(await readFile("docs/migration/node-retirement-gate.json", "utf8"));
  const evidenceRefresh = gate.requiredGates.find((item) => item.id === "evidence-refresh");
  const script = await readFile("scripts/check-retirement-boundary-audit.ts", "utf8");

  assert.equal(packageJson.scripts["retirement:audit"], "node --experimental-strip-types scripts/check-retirement-boundary-audit.ts");
  assert.ok(evidenceRefresh?.evidence?.includes("scripts/check-retirement-boundary-audit.ts"));
  assert.ok(evidenceRefresh?.evidence?.includes("tests/retirementBoundaryAudit.test.mjs"));
  assert.match(script, /legacy-node-reference\/services/);
  assert.match(script, /services\/core-banking\/src\/main\/kotlin/);
  assert.ok(script.includes("(?:\\.\\.\\/)+services\\/[^\"']+\\.mjs"));
  assert.match(script, /targetSourceRoots/);
  assert.match(script, /disallowedTargetSourceExtensions/);
  assert.match(script, /Target source directories contain disallowed Node\/static source files/);
  assert.match(script, /targetSourceDependencyPattern/);
  assert.match(script, /Target source files depend on legacy Node reference\/runtime modules/);
});
