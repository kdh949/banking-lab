import assert from "node:assert/strict";
import test from "node:test";
import { mkdtemp, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { spawnSync } from "node:child_process";
import { tmpdir } from "node:os";

const script = "scripts/verify-passkey-non-synthetic-evidence.ts";

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
    commands: [
      "env COMPOSE_PROJECT_NAME=banking-lab-passkey-manual BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false docker compose --profile platform up -d --build postgres keycloak core-banking",
      "curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://localhost:18127/realms/banking-lab/.well-known/openid-configuration",
      "curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://127.0.0.1:18126/health",
      "manual browser sign-in completed with a real platform authenticator",
      "npm run passkey:evidence:record"
    ],
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
      "curl -H 'Authorization: Bearer eyJabc.def.ghi' http://localhost",
      "staff panel showed 010-0000-1001"
    ]
  }));
  const result = runVerifier(artifactPath);

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /unredacted token|unmasked phone/);
});

test("passkey evidence verifier rejects incomplete live command evidence", async () => {
  const artifactPath = await artifactFile(validArtifact({
    commands: [
      "manual browser sign-in completed with a real platform authenticator",
      "npm run passkey:evidence:record"
    ]
  }));
  const result = runVerifier(artifactPath);

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /live Docker Compose platform startup|live Keycloak discovery readiness check/);
});

test("passkey evidence verifier fails strictly when the default artifact is missing", () => {
  const result = runVerifier();

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /Could not read passkey evidence artifact/);
});
