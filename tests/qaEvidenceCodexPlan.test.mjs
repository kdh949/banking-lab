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

test("QA Temporal restart evidence reflects all-current-case server and PostgreSQL drills", async () => {
  const gate = JSON.parse(await readFile("docs/migration/node-retirement-gate.json", "utf8"));
  const server = await readFile("docs/test-evidence/temporal-server-restart-drill.md", "utf8");
  const postgres = await readFile("docs/test-evidence/temporal-postgres-restart-drill.md", "utf8");
  const matrix = await readFile("docs/test-evidence/parity-coverage-matrix.md", "utf8");

  assert.match(gate.statusReason, /Temporal server restart drill evidence for all current Temporal workflow case types/i);
  assert.match(gate.statusReason, /PostgreSQL restart drill evidence for all current Temporal workflow case types/i);
  assert.match(server, /all current workflow case types/i);
  assert.match(postgres, /all current workflow case types/i);
  assert.match(matrix, /live Compose Temporal server restart drills for all current Temporal case types/i);
  assert.match(matrix, /live Compose PostgreSQL restart drills for all current Temporal case types/i);
  assert.doesNotMatch(gate.statusReason, /representative live Compose Temporal server restart/i);
  assert.doesNotMatch(gate.statusReason, /broader database\/process-failure variants/i);
});

test("QA passkey evidence keeps Node retirement blocked until non-synthetic proof exists", async () => {
  const gate = JSON.parse(await readFile("docs/migration/node-retirement-gate.json", "utf8"));
  const evidence = await readFile("docs/test-evidence/passkey-non-synthetic-operations.md", "utf8");
  const passkeyGate = gate.requiredGates.find((item) => item.id === "non-synthetic-passkey-operations");

  assert.equal(gate.status, "blocked");
  assert.equal(passkeyGate?.status, "pending");
  assert.match(gate.statusReason, /non-synthetic passkey operations/i);
  assert.match(evidence, /Status:\s+blocked/i);
  assert.match(evidence, /Chromium CDP `WebAuthn\.enable`/);
  assert.match(evidence, /not non-synthetic passkey evidence/i);
  assert.match(evidence, /BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false/);
});
