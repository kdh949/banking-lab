import assert from "node:assert/strict";
import test from "node:test";
import { access, readdir, readFile } from "node:fs/promises";
import { join } from "node:path";

const ignoredDirs = new Set([
  ".next",
  "build",
  "coverage",
  "dist",
  "node_modules",
  "playwright-report",
  "test-results"
]);

async function exists(filePath) {
  try {
    await access(filePath);
    return true;
  } catch {
    return false;
  }
}

async function listFiles(dir) {
  const entries = await readdir(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const entryPath = join(dir, entry.name);
    if (entry.isDirectory()) {
      if (ignoredDirs.has(entry.name)) {
        continue;
      }
      files.push(...await listFiles(entryPath));
    } else {
      files.push(entryPath);
    }
  }
  return files;
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

test("Node retirement gate keeps reference runtime as archived oracle after ready", async () => {
  const gate = JSON.parse(await readFile("docs/migration/node-retirement-gate.json", "utf8"));

  assert.equal(gate.status, "ready");
  assert.equal(gate.nodeReferenceRuntime.requiredUntil, "KOTLIN_NEXT_PARITY_GREEN");

  for (const referencePath of gate.nodeReferenceRuntime.paths) {
    assert.equal(await exists(referencePath), true, `${referencePath} must remain as archived oracle/reference material`);
  }

  const gateIds = new Set(gate.requiredGates.map((item) => item.id));
  for (const required of [
    "kotlin-spring-health",
    "node-reference-parity",
    "structured-error-contract",
    "next-manifest-renderer",
    "non-synthetic-passkey-operations",
    "evidence-refresh",
    "retirement-review"
  ]) {
    assert.equal(gateIds.has(required), true, `${required} retirement gate is missing`);
  }
});

test("target service directories do not contain Node business modules", async () => {
  const serviceFiles = await listFiles("services");
  const mjsFiles = serviceFiles.filter((filePath) => filePath.endsWith(".mjs"));
  const gate = JSON.parse(await readFile("docs/migration/node-retirement-gate.json", "utf8"));

  assert.deepEqual(mjsFiles, []);
  assert.equal(gate.nodeReferenceRuntime.paths.includes("legacy-node-reference/services"), true);
});

test("target source directories do not contain Node or static shell source files", async () => {
  const files = (await Promise.all(["apps", "services", "packages"].map((root) => listFiles(root)))).flat();
  const disallowedSourceFiles = files.filter((filePath) => /\.(mjs|cjs|js|html)$/.test(filePath));
  const gate = JSON.parse(await readFile("docs/migration/node-retirement-gate.json", "utf8"));

  assert.deepEqual(disallowedSourceFiles, []);
  assert.equal(gate.nodeReferenceRuntime.paths.includes("legacy-node-reference/apps"), true);
  assert.equal(gate.nodeReferenceRuntime.paths.includes("legacy-node-reference/packages/banking-domain/src"), true);
  assert.equal(gate.nodeReferenceRuntime.paths.includes("legacy-node-reference/packages/screen-engine/src"), true);
  assert.equal(gate.nodeReferenceRuntime.paths.includes("legacy-node-reference/packages/form-engine/src"), true);
});

test("target source directories do not import legacy Node reference runtime", async () => {
  const files = (await Promise.all(["apps", "services", "packages"].map((root) => listFiles(root)))).flat();
  const sourceFiles = files.filter((filePath) => /\.(ts|tsx|kt|kts|java|py)$/.test(filePath));
  const offenders = [];
  for (const filePath of sourceFiles) {
    const source = await readFile(filePath, "utf8");
    if (/legacy-node-reference|(?:\.\.\/)+runtime\/(?:labApp|server)\.mjs|runtime\/(?:labApp|server)\.mjs|\.mjs["']/.test(source)) {
      offenders.push(filePath);
    }
  }

  assert.deepEqual(offenders, []);
});

test("migration playbook names parity command, structured error contract, and retirement gate", async () => {
  const playbook = await readFile("docs/migration/kotlin-next-playbook.md", "utf8");

  assert.match(playbook, /npm run parity/);
  assert.match(playbook, /structured API error/i);
  assert.match(playbook, /node-retirement-gate\.json/);
  assert.match(playbook, /Do not remove or rewrite/);
});

test("node retirement gate requires boundary audit before ready status", async () => {
  const script = await readFile("scripts/check-node-retirement-gate.ts", "utf8");

  assert.match(script, /check-retirement-boundary-audit\.ts/);
  assert.match(script, /runReadyBoundaryAudit/);
  assert.match(script, /Retirement boundary audit must pass before the Node reference gate can be ready/);
  assert.match(script, /verify-final-retirement-review\.ts/);
  assert.match(script, /runFinalReviewVerifier/);
  assert.match(script, /Final retirement review artifact must pass strict verification/);
});
