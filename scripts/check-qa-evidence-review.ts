import assert from "node:assert/strict";
import { access, readFile } from "node:fs/promises";

type ParitySuite = {
  scenarioCount: number;
};

type ParityScenarioMap = {
  nodeReferenceTestCount: number;
  suites: ParitySuite[];
};

type RetirementGate = {
  status: string;
};

const requiredDocs = [
  "docs/test-evidence/parity-coverage-matrix.md",
  "docs/test-evidence/evidence-gap-report.md",
  "docs/test-evidence/structured-error-contract-gap-report.md",
  "docs/failure-drills/target-stack-gap-drill-additions.md",
  "docs/architecture/qa-evidence-node-retirement-recommendation.md",
  "docs/codex/coordination-notes/f-qa-evidence-review.md"
];

async function exists(path: string): Promise<boolean> {
  try {
    await access(path);
    return true;
  } catch {
    return false;
  }
}

for (const path of requiredDocs) {
  assert.equal(await exists(path), true, `${path} is missing`);
}

const parity = JSON.parse(await readFile("docs/migration/parity-scenarios.json", "utf8")) as ParityScenarioMap;
const gate = JSON.parse(await readFile("docs/migration/node-retirement-gate.json", "utf8")) as RetirementGate;
const recommendation = await readFile("docs/architecture/qa-evidence-node-retirement-recommendation.md", "utf8");
const matrix = await readFile("docs/test-evidence/parity-coverage-matrix.md", "utf8");
const structuredErrors = await readFile("docs/test-evidence/structured-error-contract-gap-report.md", "utf8");

const mappedScenarioCount = parity.suites.reduce((sum, suite) => sum + suite.scenarioCount, 0);
assert.equal(mappedScenarioCount, parity.nodeReferenceTestCount);
assert.equal(parity.nodeReferenceTestCount, 43);
assert.equal(gate.status, "blocked");
assert.match(recommendation, /Keep the Node reference runtime/);
assert.match(recommendation, /Do not mark `docs\/migration\/node-retirement-gate\.json` ready/);
assert.match(matrix, /Total mapped reference scenarios: 43/);

for (const code of [
  "POLICY_REASON_REQUIRED",
  "AUTHORIZATION_POLICY_VIOLATION",
  "MAKER_CHECKER_SELF_APPROVAL_REJECTED",
  "LEDGER_INSUFFICIENT_AVAILABLE_BALANCE",
  "LEDGER_CLOSED_DAY_IMMUTABLE",
  "LEDGER_REVERSAL_POLICY_VIOLATION",
  "REQUEST_VALIDATION_FAILED",
  "RESOURCE_NOT_FOUND",
  "WORKFLOW_STATE_VIOLATION",
  "INTERNAL_RUNTIME_ERROR"
]) {
  assert.match(structuredErrors, new RegExp(code));
}

console.log("QA evidence review check passed.");
