import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
const script = "scripts/check-node-retirement-ready-simulation.ts";

test("node retirement ready-state simulation proves future ready gate path", async () => {
  const { stdout, stderr } = await execFileAsync(
    process.execPath,
    ["--experimental-strip-types", script],
    { maxBuffer: 1024 * 1024 * 8 }
  );

  assert.equal(stderr, "");
  assert.match(stdout, /Node retirement ready-state simulation: pass/);
  assert.match(stdout, /Fixture gate and redacted synthetic evidence artifacts can satisfy the ready gate path/);
});

test("node retirement ready-state simulation is wired into scripts and evidence", async () => {
  const packageJson = JSON.parse(await readFile("package.json", "utf8"));
  const gate = JSON.parse(await readFile("docs/migration/node-retirement-gate.json", "utf8"));
  const evidenceRefresh = gate.requiredGates.find((item) => item.id === "evidence-refresh");
  const retirementReview = gate.requiredGates.find((item) => item.id === "retirement-review");
  const nodeGateScript = await readFile("scripts/check-node-retirement-gate.ts", "utf8");
  const boundaryScript = await readFile("scripts/check-retirement-boundary-audit.ts", "utf8");

  assert.equal(packageJson.scripts["retirement:ready-simulate"], `node --experimental-strip-types ${script}`);
  assert.ok(evidenceRefresh?.evidence?.includes(script));
  assert.ok(evidenceRefresh?.evidence?.includes("tests/nodeRetirementReadySimulation.test.mjs"));
  assert.ok(retirementReview?.evidence?.includes(script));
  assert.ok(retirementReview?.evidence?.includes("tests/nodeRetirementReadySimulation.test.mjs"));
  assert.match(nodeGateScript, /BANKING_LAB_NODE_RETIREMENT_GATE_PATH/);
  assert.match(nodeGateScript, /BANKING_LAB_PASSKEY_EVIDENCE_ARTIFACT/);
  assert.match(nodeGateScript, /BANKING_LAB_FINAL_REVIEW_ARTIFACT/);
  assert.match(boundaryScript, /BANKING_LAB_NODE_RETIREMENT_GATE_PATH/);
});
