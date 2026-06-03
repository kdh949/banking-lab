import assert from "node:assert/strict";
import test from "node:test";
import { mkdtemp, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { spawnSync } from "node:child_process";
import { tmpdir } from "node:os";

const script = "scripts/verify-passkey-non-synthetic-evidence.ts";

function commandEvidence() {
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

function validArtifact(overrides = {}) {
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
    commands: commandEvidence(),
    staffPanelAssertions: {
      webAuthnLoaded: true,
      managerSubjectObserved: true,
      bearerTokenTypeObserved: true,
      syntheticCustomerObserved: true,
      maskedPiiObserved: true,
      auditEventObserved: true
    },
    ...overrides
  };
}

async function artifactFile(content) {
  const dir = await mkdtemp(join(tmpdir(), "banking-lab-passkey-verifier-"));
  const artifactPath = join(dir, "passkey-evidence.json");
  await writeFile(artifactPath, `${JSON.stringify(content, null, 2)}\n`);
  return artifactPath;
}

function runVerifier(artifactPath) {
  const env = artifactPath
    ? { ...process.env, BANKING_LAB_PASSKEY_EVIDENCE_ARTIFACT: artifactPath }
    : process.env;
  return spawnSync(process.execPath, ["--experimental-strip-types", script], {
    cwd: process.cwd(),
    env,
    encoding: "utf8"
  });
}

test("passkey evidence verifier accepts a redacted manual-live-passkey artifact", async () => {
  const result = runVerifier(await artifactFile(validArtifact()));

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Passkey non-synthetic evidence verification: pass/);
  assert.match(result.stdout, /manual-live-passkey artifact/);
});

test("passkey evidence verifier rejects virtual or CDP-backed evidence", async () => {
  const artifactPath = await artifactFile(validArtifact({
    authenticatorKind: "virtual",
    usedBrowserVirtualAuthenticator: true,
    usedPlaywrightCdpWebAuthn: true
  }));
  const result = runVerifier(artifactPath);

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /authenticatorKind must be platform|usedBrowserVirtualAuthenticator must be false/);
});

test("passkey evidence verifier rejects unredacted reusable material and unmasked PII", async () => {
  const artifactPath = await artifactFile(validArtifact({
    commands: [
      {
        command: "curl -H 'Authorization: Bearer eyJabc.def.ghi' http://localhost",
        status: "pass",
        exitCode: 0,
        summary: "Secret-bearing command should be rejected."
      },
      {
        command: "staff panel showed 010-0000-1001",
        status: "pass",
        exitCode: 0,
        summary: "Unmasked PII should be rejected."
      }
    ]
  }));
  const result = runVerifier(artifactPath);

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /unredacted token|unmasked phone/);
});

test("passkey evidence verifier rejects incomplete live command evidence", async () => {
  const artifactPath = await artifactFile(validArtifact({
    commands: commandEvidence().slice(3)
  }));
  const result = runVerifier(artifactPath);

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /live Docker Compose platform startup|live Keycloak discovery readiness check/);
});

test("passkey evidence verifier rejects missing or mismatched manual ceremony boundary", async () => {
  const artifactPath = await artifactFile(validArtifact({
    manualCeremony: {
      browserOrigin: "http://127.0.0.1:3002",
      keycloakIssuer: "http://localhost:18127/realms/banking-lab",
      rpId: "127.0.0.1",
      username: "manager-webauthn01",
      authorizationFlow: "authorization-code-pkce",
      browserAutomation: "ordinary-browser-no-virtual-authenticator",
      operatorConfirmation: "virtual-authenticator-used"
    }
  }));
  const result = runVerifier(artifactPath);

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /manualCeremony\.browserOrigin|manualCeremony\.rpId|manualCeremony\.operatorConfirmation/);
});

test("passkey evidence verifier rejects failed or duplicate command evidence", async () => {
  const commands = commandEvidence();
  const artifactPath = await artifactFile(validArtifact({
    commands: [
      {
        ...commands[0],
        status: "failed",
        exitCode: 1,
        summary: "This command failed."
      },
      commands[0],
      ...commands.slice(1)
    ]
  }));
  const result = runVerifier(artifactPath);

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /status must be pass|duplicate command/);
});

test("passkey evidence verifier fails strictly when an explicit artifact is missing", () => {
  const result = runVerifier(join(tmpdir(), "banking-lab-missing-passkey-evidence.json"));

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /Could not read passkey evidence artifact/);
});
