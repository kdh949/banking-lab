import assert from "node:assert/strict";
import test from "node:test";
import { mkdtemp, readFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import { join } from "node:path";
import { spawnSync } from "node:child_process";
import { tmpdir } from "node:os";

const prepareScript = "scripts/prepare-passkey-non-synthetic-evidence.ts";
const recordScript = "scripts/record-passkey-non-synthetic-evidence.ts";

function runPrepare(outputDir, overrides = {}) {
  return spawnSync(process.execPath, ["--experimental-strip-types", prepareScript], {
    cwd: process.cwd(),
    env: {
      ...process.env,
      BANKING_LAB_PASSKEY_TEMPLATE_DIR: outputDir,
      BANKING_LAB_PASSKEY_TEST_DATE: "2026-06-03",
      ...overrides
    },
    encoding: "utf8"
  });
}

function runRecorderWithTemplates(outputDir) {
  return spawnSync(process.execPath, ["--experimental-strip-types", recordScript], {
    cwd: process.cwd(),
    env: {
      ...process.env,
      BANKING_LAB_PASSKEY_EVIDENCE_CONFIRMED: "true",
      BANKING_LAB_PASSKEY_AUTHENTICATOR_KIND: "platform",
      BANKING_LAB_PASSKEY_TEST_DATE: "2026-06-03",
      BANKING_LAB_PASSKEY_BROWSER_ORIGIN: "http://localhost:3002",
      BANKING_LAB_PASSKEY_KEYCLOAK_ISSUER: "http://localhost:18127/realms/banking-lab",
      BANKING_LAB_PASSKEY_RP_ID: "localhost",
      BANKING_LAB_PASSKEY_USERNAME: "manager-webauthn01",
      BANKING_LAB_PASSKEY_AUTHORIZATION_FLOW: "authorization-code-pkce",
      BANKING_LAB_PASSKEY_BROWSER_AUTOMATION: "ordinary-browser-no-virtual-authenticator",
      BANKING_LAB_PASSKEY_OPERATOR_CONFIRMATION: "real-platform-or-hardware-authenticator-used",
      BANKING_LAB_PASSKEY_USED_BROWSER_VIRTUAL_AUTHENTICATOR: "false",
      BANKING_LAB_PASSKEY_USED_PLAYWRIGHT_CDP_WEBAUTHN: "false",
      BANKING_LAB_PASSKEY_SIMULATOR_TOKENS_ENABLED: "false",
      BANKING_LAB_PASSKEY_KEYCLOAK_REQUIRED_ACTION_COMPLETED: "true",
      BANKING_LAB_PASSKEY_SPRING_SIGNED_TOKEN_ACCEPTED: "true",
      BANKING_LAB_PASSKEY_SYNTHETIC_ONLY: "true",
      BANKING_LAB_PASSKEY_REDACTION_CONFIRMED: "true",
      BANKING_LAB_PASSKEY_COMMANDS_FILE: join(outputDir, "redacted-commands.template.json"),
      BANKING_LAB_PASSKEY_PANEL_SNAPSHOT_FILE: join(outputDir, "redacted-staff-panel.template.txt"),
      BANKING_LAB_PASSKEY_EVIDENCE_OUTPUT: join(outputDir, "passkey-evidence.json")
    },
    encoding: "utf8"
  });
}

test("passkey evidence prepare writes redacted manual-run templates", async () => {
  const outputDir = await mkdtemp(join(tmpdir(), "banking-lab-passkey-prepare-"));
  const result = runPrepare(outputDir);

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Passkey non-synthetic evidence templates prepared/);

  const commandsPath = join(outputDir, "redacted-commands.template.json");
  const panelPath = join(outputDir, "redacted-staff-panel.template.txt");
  const recordCommandPath = join(outputDir, "record-command.template.sh");
  assert.equal(existsSync(commandsPath), true);
  assert.equal(existsSync(panelPath), true);
  assert.equal(existsSync(recordCommandPath), true);

  const commands = JSON.parse(await readFile(commandsPath, "utf8"));
  assert.equal(commands.length, 5);
  assert.match(commands[0].command, /docker compose --profile platform up/);
  assert.match(commands[0].command, /BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false/);
  assert.match(commands[1].command, /\.well-known\/openid-configuration/);
  assert.match(commands[2].command, /\/health/);
  assert.match(commands[3].command, /real platform authenticator/);
  assert.equal(commands[0].status, "TODO_REPLACE_WITH_pass");
  assert.equal(commands[0].exitCode, "TODO_REPLACE_WITH_0");

  const panel = await readFile(panelPath, "utf8");
  assert.match(panel, /Keycloak WebAuthn manager loaded/);
  assert.match(panel, /manager-webauthn01/);
  assert.match(panel, /010-\*\*\*\*-1001/);
  assert.doesNotMatch(panel, /010-0000-1001/);

  const recordCommand = await readFile(recordCommandPath, "utf8");
  assert.match(recordCommand, /BANKING_LAB_PASSKEY_BROWSER_ORIGIN=http:\/\/localhost:3002/);
  assert.match(recordCommand, /BANKING_LAB_PASSKEY_BROWSER_AUTOMATION=ordinary-browser-no-virtual-authenticator/);
  assert.match(recordCommand, /BANKING_LAB_PASSKEY_COMMANDS_FILE=.*redacted-commands\.template\.json/);
});

test("passkey evidence prepare supports hardware security key templates", async () => {
  const outputDir = await mkdtemp(join(tmpdir(), "banking-lab-passkey-prepare-"));
  const result = runPrepare(outputDir, {
    BANKING_LAB_PASSKEY_AUTHENTICATOR_KIND: "hardware-security-key"
  });

  assert.equal(result.status, 0, result.stderr);
  const commands = JSON.parse(await readFile(join(outputDir, "redacted-commands.template.json"), "utf8"));
  assert.match(commands[3].command, /real hardware security key/);
  assert.match(commands[3].summary, /hardware security key/);
});

test("passkey evidence prepare templates cannot be recorded without manual replacement", async () => {
  const outputDir = await mkdtemp(join(tmpdir(), "banking-lab-passkey-prepare-"));
  const prepare = runPrepare(outputDir);
  assert.equal(prepare.status, 0, prepare.stderr);

  const record = runRecorderWithTemplates(outputDir);
  assert.notEqual(record.status, 0);
  assert.match(record.stderr, /status must be pass|exitCode must be 0|missing required evidence/);
  assert.equal(existsSync(join(outputDir, "passkey-evidence.json")), false);
});

test("passkey evidence prepare rejects unknown authenticator kinds", async () => {
  const outputDir = await mkdtemp(join(tmpdir(), "banking-lab-passkey-prepare-"));
  const result = runPrepare(outputDir, {
    BANKING_LAB_PASSKEY_AUTHENTICATOR_KIND: "virtual"
  });

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /AUTHENTICATOR_KIND must be platform or hardware-security-key/);
});
