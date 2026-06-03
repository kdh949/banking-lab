import assert from "node:assert/strict";
import test from "node:test";
import { mkdtemp, readFile, writeFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import { join } from "node:path";
import { spawnSync } from "node:child_process";
import { tmpdir } from "node:os";

const script = "scripts/record-passkey-non-synthetic-evidence.ts";

async function fixtureDir() {
  const dir = await mkdtemp(join(tmpdir(), "banking-lab-passkey-evidence-"));
  const commandsFile = join(dir, "commands.txt");
  const panelFile = join(dir, "panel.txt");
  const outputFile = join(dir, "passkey-evidence.json");

  await writeFile(commandsFile, [
    "env COMPOSE_PROJECT_NAME=banking-lab-passkey-manual BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false docker compose --profile platform up -d --build postgres keycloak core-banking",
    "manual browser sign-in completed with a real platform authenticator",
    "npm run passkey:evidence:record"
  ].join("\n"));
  await writeFile(panelFile, [
    "Keycloak WebAuthn manager loaded",
    "manager-webauthn01",
    "Bearer",
    "SYN-CUS-001",
    "010-****-1001",
    "AUD-SYN-PASSKEY-001"
  ].join("\n"));

  return { commandsFile, panelFile, outputFile };
}

function baseEnv(fixture) {
  return {
    ...process.env,
    BANKING_LAB_PASSKEY_EVIDENCE_CONFIRMED: "true",
    BANKING_LAB_PASSKEY_AUTHENTICATOR_KIND: "platform",
    BANKING_LAB_PASSKEY_TEST_DATE: "2026-06-03",
    BANKING_LAB_PASSKEY_USED_BROWSER_VIRTUAL_AUTHENTICATOR: "false",
    BANKING_LAB_PASSKEY_USED_PLAYWRIGHT_CDP_WEBAUTHN: "false",
    BANKING_LAB_PASSKEY_SIMULATOR_TOKENS_ENABLED: "false",
    BANKING_LAB_PASSKEY_KEYCLOAK_REQUIRED_ACTION_COMPLETED: "true",
    BANKING_LAB_PASSKEY_SPRING_SIGNED_TOKEN_ACCEPTED: "true",
    BANKING_LAB_PASSKEY_SYNTHETIC_ONLY: "true",
    BANKING_LAB_PASSKEY_REDACTION_CONFIRMED: "true",
    BANKING_LAB_PASSKEY_COMMANDS_FILE: fixture.commandsFile,
    BANKING_LAB_PASSKEY_PANEL_SNAPSHOT_FILE: fixture.panelFile,
    BANKING_LAB_PASSKEY_EVIDENCE_OUTPUT: fixture.outputFile
  };
}

function runRecorder(env) {
  return spawnSync(process.execPath, ["--experimental-strip-types", script], {
    cwd: process.cwd(),
    env,
    encoding: "utf8"
  });
}

test("passkey evidence recorder writes a validated non-synthetic artifact", async () => {
  const fixture = await fixtureDir();
  const result = runRecorder(baseEnv(fixture));

  assert.equal(result.status, 0, result.stderr);
  const artifact = JSON.parse(await readFile(fixture.outputFile, "utf8"));
  assert.equal(artifact.schemaVersion, 1);
  assert.equal(artifact.status, "pass");
  assert.equal(artifact.evidenceKind, "manual-live-passkey");
  assert.equal(artifact.authenticatorKind, "platform");
  assert.equal(artifact.usedBrowserVirtualAuthenticator, false);
  assert.equal(artifact.usedPlaywrightCdpWebAuthn, false);
  assert.equal(artifact.simulatorTokensEnabled, false);
  assert.equal(artifact.keycloakRequiredActionCompleted, true);
  assert.equal(artifact.springSignedTokenAccepted, true);
  assert.equal(artifact.syntheticOnly, true);
  assert.equal(artifact.redactionConfirmed, true);
  assert.equal(artifact.staffPanelAssertions.maskedPiiObserved, true);
  assert.equal(artifact.staffPanelAssertions.auditEventObserved, true);
  assert.equal(artifact.commands.length, 3);
});

test("passkey evidence recorder rejects virtual or CDP-backed evidence", async () => {
  const fixture = await fixtureDir();
  const env = {
    ...baseEnv(fixture),
    BANKING_LAB_PASSKEY_AUTHENTICATOR_KIND: "virtual",
    BANKING_LAB_PASSKEY_USED_PLAYWRIGHT_CDP_WEBAUTHN: "true"
  };
  const result = runRecorder(env);

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /USED_PLAYWRIGHT_CDP_WEBAUTHN must be false|AUTHENTICATOR_KIND must be platform/);
  assert.equal(existsSync(fixture.outputFile), false);
});

test("passkey evidence recorder rejects unredacted secrets and unmasked PII", async () => {
  const fixture = await fixtureDir();
  await writeFile(fixture.commandsFile, "curl --data 'password=manager-webauthn01-pass'\n");
  await writeFile(fixture.panelFile, [
    "Keycloak WebAuthn manager loaded",
    "manager-webauthn01",
    "Bearer",
    "SYN-CUS-001",
    "010-0000-1001",
    "AUD-SYN-PASSKEY-001"
  ].join("\n"));
  const result = runRecorder(baseEnv(fixture));

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /unredacted token|unmasked phone output/);
  assert.equal(existsSync(fixture.outputFile), false);
});
