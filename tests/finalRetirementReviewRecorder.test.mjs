import assert from "node:assert/strict";
import test from "node:test";
import { mkdtemp, readFile, writeFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import { join } from "node:path";
import { spawnSync } from "node:child_process";
import { tmpdir } from "node:os";

const recorderScript = "scripts/record-final-retirement-review.ts";
const verifierScript = "scripts/verify-final-retirement-review.ts";

const requiredCommands = [
  "npm run parity",
  "npm test",
  "npm run validate:manifests",
  "npm run evidence:pack",
  "npm run retirement:audit",
  "npm run retirement:stack-audit",
  "npm run retirement:generated-boundary",
  "npm run retirement:ready-simulate",
  "npm run passkey:evidence:preflight",
  "npm run retirement:review-preflight",
  "npm run passkey:evidence:verify"
];

const controlEnv = {
  BANKING_LAB_FINAL_REVIEW_LEDGER_BALANCED_POSTINGS: "true",
  BANKING_LAB_FINAL_REVIEW_BALANCES_PROJECTED_FROM_POSTINGS: "true",
  BANKING_LAB_FINAL_REVIEW_IDEMPOTENT_EXTERNAL_COMMANDS: "true",
  BANKING_LAB_FINAL_REVIEW_APPEND_ONLY_FINALIZED_TRANSACTIONS: "true",
  BANKING_LAB_FINAL_REVIEW_REASON_REQUIRED_AUDIT: "true",
  BANKING_LAB_FINAL_REVIEW_PII_MASKED_BY_DEFAULT: "true",
  BANKING_LAB_FINAL_REVIEW_MAKER_CHECKER_SEPARATION: "true",
  BANKING_LAB_FINAL_REVIEW_WORKFLOW_DURABILITY: "true",
  BANKING_LAB_FINAL_REVIEW_OUTBOX_DURABILITY: "true",
  BANKING_LAB_FINAL_REVIEW_RECONCILIATION_ADJUSTMENTS_BALANCED: "true",
  BANKING_LAB_FINAL_REVIEW_TARGET_AREAS_NO_DISALLOWED_STACK: "true",
  BANKING_LAB_FINAL_REVIEW_GENERATED_ARTIFACTS_IGNORED: "true",
  BANKING_LAB_FINAL_REVIEW_SYNTHETIC_ONLY: "true",
  BANKING_LAB_FINAL_REVIEW_NODE_ONLY_CRITICAL_DEPENDENCY_REMOVED: "true"
};

function commandEvidence(overrides = {}) {
  return requiredCommands.map((command) => ({
    command,
    status: "pass",
    exitCode: 0,
    runAfterPasskeyEvidence: true,
    summary: `${command} passed after passkey evidence verification.`,
    ...overrides
  }));
}

async function fixtureDir(commands = commandEvidence()) {
  const dir = await mkdtemp(join(tmpdir(), "banking-lab-final-review-record-"));
  const commandsFile = join(dir, "commands.json");
  const outputFile = join(dir, "final-review.json");
  await writeFile(commandsFile, `${JSON.stringify(commands, null, 2)}\n`);
  return { commandsFile, outputFile };
}

function baseEnv(fixture, overrides = {}) {
  return {
    ...process.env,
    BANKING_LAB_FINAL_REVIEW_CONFIRMED: "true",
    BANKING_LAB_FINAL_REVIEW_PASSKEY_ARTIFACT_VERIFIED: "true",
    BANKING_LAB_FINAL_REVIEW_REVIEWER: "final-retirement-reviewer",
    BANKING_LAB_FINAL_REVIEW_DATE: "2026-06-03",
    BANKING_LAB_FINAL_REVIEW_COMMANDS_FILE: fixture.commandsFile,
    BANKING_LAB_FINAL_REVIEW_OUTPUT: fixture.outputFile,
    ...controlEnv,
    ...overrides
  };
}

function runRecorder(env) {
  return spawnSync(process.execPath, ["--experimental-strip-types", recorderScript], {
    cwd: process.cwd(),
    env,
    encoding: "utf8"
  });
}

function runVerifier(artifactPath) {
  return spawnSync(process.execPath, ["--experimental-strip-types", verifierScript], {
    cwd: process.cwd(),
    env: { ...process.env, BANKING_LAB_FINAL_REVIEW_ARTIFACT: artifactPath },
    encoding: "utf8"
  });
}

test("final retirement review recorder writes an artifact accepted by the verifier", async () => {
  const fixture = await fixtureDir();
  const result = runRecorder(baseEnv(fixture));

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Wrote/);
  const artifact = JSON.parse(await readFile(fixture.outputFile, "utf8"));
  assert.equal(artifact.schemaVersion, 1);
  assert.equal(artifact.status, "pass");
  assert.equal(artifact.evidenceKind, "final-node-retirement-review");
  assert.equal(artifact.passkeyEvidenceArtifact, "docs/test-evidence/generated/passkey-non-synthetic-evidence.json");
  assert.equal(artifact.commands.length, requiredCommands.length);
  assert.deepEqual(artifact.remainingBlockers, []);
  assert.equal(artifact.controlAttestations.outboxDurability, true);

  const verifier = runVerifier(fixture.outputFile);
  assert.equal(verifier.status, 0, verifier.stderr);
  assert.match(verifier.stdout, /Final retirement review verification: pass/);
});

test("final retirement review recorder rejects incomplete command evidence", async () => {
  const fixture = await fixtureDir(commandEvidence().filter((item) => item.command !== "npm run parity"));
  const result = runRecorder(baseEnv(fixture));

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /must include npm run parity/);
  assert.equal(existsSync(fixture.outputFile), false);
});

test("final retirement review recorder rejects missing control attestation", async () => {
  const fixture = await fixtureDir();
  const result = runRecorder(baseEnv(fixture, {
    BANKING_LAB_FINAL_REVIEW_OUTBOX_DURABILITY: "false"
  }));

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /FINAL_REVIEW_OUTBOX_DURABILITY must be true/);
  assert.equal(existsSync(fixture.outputFile), false);
});

test("final retirement review recorder rejects unredacted reusable material", async () => {
  const fixture = await fixtureDir(commandEvidence({
    summary: "curl -H 'Authorization: Bearer eyJabc.def.ghi' http://localhost"
  }));
  const result = runRecorder(baseEnv(fixture));

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /unredacted token|reusable passkey artifact|unmasked phone/);
  assert.equal(existsSync(fixture.outputFile), false);
});

test("final retirement review recorder rejects extra failed or duplicate command evidence", async () => {
  const commands = [
    ...commandEvidence(),
    {
      command: "npm run parity",
      status: "pass",
      exitCode: 0,
      runAfterPasskeyEvidence: true,
      summary: "duplicate parity evidence"
    },
    {
      command: "npm run unexpected-check",
      status: "failed",
      exitCode: 1,
      runAfterPasskeyEvidence: true,
      summary: "unexpected check failed"
    }
  ];
  const fixture = await fixtureDir(commands);
  const result = runRecorder(baseEnv(fixture));

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /duplicate command npm run parity|unexpected-check status must be pass/);
  assert.equal(existsSync(fixture.outputFile), false);
});

test("final retirement review recorder rejects non-object command evidence", async () => {
  const fixture = await fixtureDir([
    ...commandEvidence(),
    "npm run hidden-string-command"
  ]);
  const result = runRecorder(baseEnv(fixture));

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /Every final review command evidence item must be an object/);
  assert.equal(existsSync(fixture.outputFile), false);
});
