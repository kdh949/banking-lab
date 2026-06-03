import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

test("QA evidence review keeps Node retirement blocked until target parity is proven", async () => {
  const gate = JSON.parse(await readFile("docs/migration/node-retirement-gate.json", "utf8"));
  const recommendation = await readFile("docs/architecture/qa-evidence-node-retirement-recommendation.md", "utf8");

  assert.equal(gate.status, "blocked");
  assert.equal(gate.requiredGates.find((item) => item.id === "api-backed-channel-parity")?.status, "pass");
  assert.match(recommendation, /Keep the Node reference runtime/);
  assert.match(recommendation, /All 42 mapped parity scenarios pass/);
});

test("QA parity matrix matches the mapped Node reference scenario count", async () => {
  const parity = JSON.parse(await readFile("docs/migration/parity-scenarios.json", "utf8"));
  const matrix = await readFile("docs/test-evidence/parity-coverage-matrix.md", "utf8");
  const mappedScenarioCount = parity.suites.reduce((sum, suite) => sum + suite.scenarioCount, 0);

  assert.equal(parity.nodeReferenceTestCount, 42);
  assert.equal(mappedScenarioCount, 42);
  assert.match(matrix, /Total mapped reference scenarios: 42/);
});

test("QA structured error gap report covers every required error family", async () => {
  const report = await readFile("docs/test-evidence/structured-error-contract-gap-report.md", "utf8");

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
    assert.match(report, new RegExp(code));
  }
  assert.match(report, /nine required families now have real Spring route coverage/i);
  assert.match(report, /INTERNAL_RUNTIME_ERROR.*profile-only/i);
  assert.doesNotMatch(report, /remaining probe-backed families/i);
});
