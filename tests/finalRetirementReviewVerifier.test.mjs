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

function passkeyCommandEvidence() {
  return [
    "env COMPOSE_PROJECT_NAME=banking-lab-passkey-manual BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false docker compose --profile platform up -d --build postgres keycloak core-banking",
    "curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://localhost:18127/realms/banking-lab/.well-known/openid-configuration",
    "curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18126/health",
    "manual browser sign-in completed with a real platform authenticator",
    "npm run passkey:evidence:record"
  ].map((command) => ({
    command,
    status: "pass",
    exitCode: 0,
    summary: `${command} passed during the manual non-synthetic passkey evidence run.`
  }));
}

function passkeyArtifact() {
  return {
    schemaVersion: 1,
    status: "pass",
    testDate: "2026-06-03",
    evidenceKind: "manual-live-passkey",
    authenticatorKind: "platform",
    usedBrowserVirtualAuthenticator: false,
    usedPlaywrightCdpWebAuthn: false,
    simulatorTokensEnabled: false,
    keycloakRequiredActionCompleted: true,
    springSignedTokenAccepted: true,
    syntheticOnly: true,
    redactionConfirmed: true,
    manualCeremony: {
      browserOrigin: "http://localhost:3002",
      keycloakIssuer: "http://localhost:18127/realms/banking-lab",
      rpId: "localhost",
      username: "manager-webauthn01",
      authorizationFlow: "authorization-code-pkce",
      browserAutomation: "ordinary-browser-no-virtual-authenticator",
      operatorConfirmation: "real-platform-or-hardware-authenticator-used"
    },
    commands: passkeyCommandEvidence(),
    staffPanelAssertions: {
      webAuthnLoaded: true,
      managerSubjectObserved: true,
      bearerTokenTypeObserved: true,
      syntheticCustomerObserved: true,
      maskedPiiObserved: true,
      auditEventObserved: true
    }
  };
}

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

function validArtifact(passkeyEvidenceArtifact, overrides = {}) {
  return {
    schemaVersion: 1,
    status: "pass",
    reviewDate: "2026-06-03",
    evidenceKind: "final-node-retirement-review",
    passkeyEvidenceArtifact,
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

async function artifactFixture(overrides = {}) {
  const dir = await mkdtemp(join(tmpdir(), "banking-lab-final-review-"));
  const passkeyPath = join(dir, "passkey-evidence.json");
  const artifactPath = join(dir, "final-review.json");
  await writeFile(passkeyPath, `${JSON.stringify(passkeyArtifact(), null, 2)}\n`);
  await writeFile(artifactPath, `${JSON.stringify(validArtifact(passkeyPath, overrides), null, 2)}\n`);
  return { artifactPath, passkeyPath };
}

function runVerifier(artifactPath, passkeyPath) {
  const env = artifactPath
    ? {
      ...process.env,
      BANKING_LAB_FINAL_REVIEW_ARTIFACT: artifactPath,
      ...(passkeyPath ? { BANKING_LAB_PASSKEY_EVIDENCE_ARTIFACT: passkeyPath } : {})
    }
    : process.env;
  return spawnSync(process.execPath, ["--experimental-strip-types", script], {
    cwd: process.cwd(),
    env,
    encoding: "utf8"
  });
}

test("final retirement review verifier accepts complete post-passkey evidence", async () => {
  const fixture = await artifactFixture();
  const result = runVerifier(fixture.artifactPath, fixture.passkeyPath);

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Final retirement review verification: pass/);
  assert.match(result.stdout, /final retirement review artifact/);
});

test("final retirement review verifier rejects missing commands and controls", async () => {
  const fixture = await artifactFixture({
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
  const result = runVerifier(fixture.artifactPath, fixture.passkeyPath);

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /commands must include npm run parity/);
  assert.match(result.stderr, /controlAttestations\.outboxDurability must be true/);
  assert.match(result.stderr, /remainingBlockers must be empty/);
});

test("final retirement review verifier rejects unredacted reusable material", async () => {
  const fixture = await artifactFixture({
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
  const result = runVerifier(fixture.artifactPath, fixture.passkeyPath);

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /unredacted token|reusable passkey artifact|unmasked phone/);
});

test("final retirement review verifier rejects extra failed or duplicate command evidence", async () => {
  const fixture = await artifactFixture({
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
  const result = runVerifier(fixture.artifactPath, fixture.passkeyPath);

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /duplicate command npm run parity|unexpected-check status must be pass/);
});

test("final retirement review verifier rejects non-object command evidence", async () => {
  const fixture = await artifactFixture({
    commands: [
      ...requiredCommands.map((command) => ({
        command,
        status: "pass",
        exitCode: 0,
        runAfterPasskeyEvidence: true,
        summary: `${command} passed.`
      })),
      "npm run hidden-string-command"
    ]
  });
  const result = runVerifier(fixture.artifactPath, fixture.passkeyPath);

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /commands must be an array of command evidence objects/);
});

test("final retirement review verifier rejects missing passkey evidence artifact", async () => {
  const dir = await mkdtemp(join(tmpdir(), "banking-lab-final-review-"));
  const missingPasskeyPath = join(dir, "missing-passkey.json");
  const artifactPath = await artifactFile(validArtifact(missingPasskeyPath));
  const result = runVerifier(artifactPath, missingPasskeyPath);

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /Passkey evidence artifact must pass strict verification/);
});

test("final retirement review verifier fails strictly when an explicit artifact is missing", () => {
  const result = runVerifier(join(tmpdir(), "banking-lab-missing-final-review.json"));

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /Could not read final retirement review artifact/);
});
