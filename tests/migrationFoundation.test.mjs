import assert from "node:assert/strict";
import test from "node:test";
import { access, readFile } from "node:fs/promises";

async function exists(filePath) {
  try {
    await access(filePath);
    return true;
  } catch {
    return false;
  }
}

test("migration parity map covers every current Node reference test scenario", async () => {
  const parityMap = JSON.parse(await readFile("docs/migration/parity-scenarios.json", "utf8"));
  let currentReferenceCount = 0;
  for (const suite of parityMap.suites) {
    const source = await readFile(suite.nodeSuite, "utf8");
    currentReferenceCount += [...source.matchAll(/^test\(/gm)].length;
  }
  const mappedCount = parityMap.suites.reduce((sum, suite) => sum + suite.scenarioCount, 0);

  assert.equal(parityMap.nodeReferenceTestCount, 42);
  assert.equal(currentReferenceCount, parityMap.nodeReferenceTestCount);
  assert.equal(mappedCount, parityMap.nodeReferenceTestCount);

  const coveredControls = new Set();
  for (const suite of parityMap.suites) {
    for (const control of suite.controls) {
      coveredControls.add(control);
    }
  }
  for (const check of parityMap.commandChecks) {
    for (const control of check.controls) {
      coveredControls.add(control);
    }
  }
  for (const control of parityMap.requiredControls) {
    assert.equal(coveredControls.has(control), true, `${control} is not covered by the parity map`);
  }
});

test("Node retirement gate keeps reference runtime until parity evidence is ready", async () => {
  const gate = JSON.parse(await readFile("docs/migration/node-retirement-gate.json", "utf8"));

  assert.equal(gate.status, "blocked");
  assert.equal(gate.nodeReferenceRuntime.requiredUntil, "KOTLIN_NEXT_PARITY_GREEN");

  for (const referencePath of gate.nodeReferenceRuntime.paths) {
    assert.equal(await exists(referencePath), true, `${referencePath} must remain while gate is blocked`);
  }

  const gateIds = new Set(gate.requiredGates.map((item) => item.id));
  for (const required of [
    "kotlin-spring-health",
    "node-reference-parity",
    "structured-error-contract",
    "next-manifest-renderer",
    "evidence-refresh",
    "retirement-review"
  ]) {
    assert.equal(gateIds.has(required), true, `${required} retirement gate is missing`);
  }
});

test("migration playbook names parity command, structured error contract, and retirement gate", async () => {
  const playbook = await readFile("docs/migration/kotlin-next-playbook.md", "utf8");

  assert.match(playbook, /npm run parity/);
  assert.match(playbook, /structured API error/i);
  assert.match(playbook, /node-retirement-gate\.json/);
  assert.match(playbook, /Do not remove or rewrite/);
});
