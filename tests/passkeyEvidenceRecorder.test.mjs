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
  const commandsFile = join(dir, "commands.json");
  const panelFile = join(dir, "panel.txt");
  const outputFile = join(dir, "passkey-evidence.json");

  await writeFile(commandsFile, `${JSON.stringify(commandEvidence(), null, 2)}\n`);
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
  assert.equal(artifact.commands.length, 5);
  assert.equal(artifact.commands[0].status, "pass");
  assert.equal(artifact.commands[0].exitCode, 0);
  assert.match(artifact.commands[0].summary, /passed/);
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
  await writeFile(fixture.commandsFile, `${JSON.stringify([
    {
      command: "curl --data 'password=manager-webauthn01-pass'",
      status: "pass",
      exitCode: 0,
      summary: "Secret-bearing command should be rejected."
    }
  ], null, 2)}\n`);
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

test("passkey evidence recorder rejects incomplete live command evidence", async () => {
  const fixture = await fixtureDir();
  await writeFile(fixture.commandsFile, `${JSON.stringify(commandEvidence().slice(3), null, 2)}\n`);
  const result = runRecorder(baseEnv(fixture));

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /live Docker Compose platform startup|live Keycloak discovery readiness check/);
  assert.equal(existsSync(fixture.outputFile), false);
});

test("passkey evidence recorder rejects failed or duplicate command evidence", async () => {
  const fixture = await fixtureDir();
  const commands = commandEvidence();
  await writeFile(fixture.commandsFile, `${JSON.stringify([
    {
      ...commands[0],
      status: "failed",
      exitCode: 1,
      summary: "This command failed."
    },
    commands[0],
    ...commands.slice(1)
  ], null, 2)}\n`);
  const result = runRecorder(baseEnv(fixture));

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /status must be pass|duplicate command/);
  assert.equal(existsSync(fixture.outputFile), false);
});
