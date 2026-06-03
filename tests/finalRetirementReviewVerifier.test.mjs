import assert from "node:assert/strict";
import test from "node:test";
import { mkdtemp, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { spawnSync } from "node:child_process";
import { tmpdir } from "node:os";

const script = "scripts/verify-final-retirement-review.ts";

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

const controlAttestations = {
  ledgerBalancedPostings: true,
  balancesProjectedFromPostings: true,
  idempotentExternalCommands: true,
  appendOnlyFinalizedTransactions: true,
  reasonRequiredAudit: true,
  piiMaskedByDefault: true,
  makerCheckerSeparation: true,
  workflowDurability: true,
  outboxDurability: true,
  reconciliationAdjustmentsBalanced: true,
  targetAreasNoDisallowedStack: true,
  generatedArtifactsIgnored: true,
  syntheticOnly: true,
  nodeOnlyCriticalDependencyRemoved: true
};

function validArtifact(overrides = {}) {
  return {
    schemaVersion: 1,
    status: "pass",
    reviewDate: "2026-06-03",
    evidenceKind: "final-node-retirement-review",
    passkeyEvidenceArtifact: "docs/test-evidence/generated/passkey-non-synthetic-evidence.json",
    reviewer: "final-retirement-reviewer",
    commands: requiredCommands.map((command) => ({
      command,
      status: "pass",
      exitCode: 0,
      runAfterPasskeyEvidence: true,
      summary: `${command} passed after passkey evidence was verified.`
    })),
    controlAttestations,
    remainingBlockers: [],
    ...overrides
  };
}

async function artifactFile(content) {
  const dir = await mkdtemp(join(tmpdir(), "banking-lab-final-review-"));
  const artifactPath = join(dir, "final-review.json");
  await writeFile(artifactPath, `${JSON.stringify(content, null, 2)}\n`);
  return artifactPath;
}

function runVerifier(artifactPath) {
  const env = artifactPath
    ? { ...process.env, BANKING_LAB_FINAL_REVIEW_ARTIFACT: artifactPath }
    : process.env;
  return spawnSync(process.execPath, ["--experimental-strip-types", script], {
    cwd: process.cwd(),
    env,
    encoding: "utf8"
  });
}

test("final retirement review verifier accepts complete post-passkey evidence", async () => {
  const result = runVerifier(await artifactFile(validArtifact()));

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Final retirement review verification: pass/);
  assert.match(result.stdout, /final retirement review artifact/);
});

test("final retirement review verifier rejects missing commands and controls", async () => {
  const artifact = validArtifact({
    commands: requiredCommands
      .filter((command) => command !== "npm run parity")
      .map((command) => ({
        command,
        status: "pass",
        exitCode: 0,
        runAfterPasskeyEvidence: true,
        summary: `${command} passed.`
      })),
    controlAttestations: {
      ...controlAttestations,
      outboxDurability: false
    },
    remainingBlockers: ["manual review incomplete"]
  });
  const result = runVerifier(await artifactFile(artifact));

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /commands must include npm run parity/);
  assert.match(result.stderr, /controlAttestations\.outboxDurability must be true/);
  assert.match(result.stderr, /remainingBlockers must be empty/);
});

test("final retirement review verifier rejects unredacted reusable material", async () => {
  const artifact = validArtifact({
    commands: [
      {
        command: "npm run parity",
        status: "pass",
        exitCode: 0,
        runAfterPasskeyEvidence: true,
        summary: "curl -H 'Authorization: Bearer eyJabc.def.ghi' http://localhost"
      }
    ]
  });
  const result = runVerifier(await artifactFile(artifact));

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /unredacted token|reusable passkey artifact|unmasked phone/);
});

test("final retirement review verifier rejects extra failed or duplicate command evidence", async () => {
  const artifact = validArtifact({
    commands: [
      ...requiredCommands.map((command) => ({
        command,
        status: "pass",
        exitCode: 0,
        runAfterPasskeyEvidence: true,
        summary: `${command} passed.`
      })),
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
    ]
  });
  const result = runVerifier(await artifactFile(artifact));

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /duplicate command npm run parity|unexpected-check status must be pass/);
});

test("final retirement review verifier fails strictly when the default artifact is missing", () => {
  const result = runVerifier();

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /Could not read final retirement review artifact/);
});
