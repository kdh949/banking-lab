import assert from "node:assert/strict";
import test from "node:test";
import { mkdtemp, readFile, writeFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import { join } from "node:path";
import { spawnSync } from "node:child_process";
import { tmpdir } from "node:os";

const prepareScript = "scripts/prepare-final-retirement-review-evidence.ts";
const recorderScript = "scripts/record-final-retirement-review.ts";

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

function runPrepare(outputDir, overrides = {}) {
  return spawnSync(process.execPath, ["--experimental-strip-types", prepareScript], {
    cwd: process.cwd(),
    env: {
      ...process.env,
      BANKING_LAB_FINAL_REVIEW_TEMPLATE_DIR: outputDir,
      BANKING_LAB_FINAL_REVIEW_DATE: "2026-06-03",
      BANKING_LAB_FINAL_REVIEW_REVIEWER: "final-retirement-reviewer",
      ...overrides
    },
    encoding: "utf8"
  });
}

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

async function writePasskeyFixture(outputDir) {
  const passkeyPath = join(outputDir, "passkey-evidence.json");
  await writeFile(passkeyPath, `${JSON.stringify(passkeyArtifact(), null, 2)}\n`);
  return passkeyPath;
}

function runRecorderWithTemplate(outputDir, passkeyPath) {
  return spawnSync(process.execPath, ["--experimental-strip-types", recorderScript], {
    cwd: process.cwd(),
    env: {
      ...process.env,
      BANKING_LAB_FINAL_REVIEW_CONFIRMED: "true",
      BANKING_LAB_FINAL_REVIEW_PASSKEY_ARTIFACT_VERIFIED: "true",
      BANKING_LAB_FINAL_REVIEW_REVIEWER: "final-retirement-reviewer",
      BANKING_LAB_FINAL_REVIEW_DATE: "2026-06-03",
      BANKING_LAB_FINAL_REVIEW_COMMANDS_FILE: join(outputDir, "redacted-final-review-commands.template.json"),
      BANKING_LAB_FINAL_REVIEW_OUTPUT: join(outputDir, "final-review.json"),
      BANKING_LAB_PASSKEY_EVIDENCE_ARTIFACT: passkeyPath,
      ...controlEnv
    },
    encoding: "utf8"
  });
}

test("final retirement review prepare writes command and recorder templates", async () => {
  const outputDir = await mkdtemp(join(tmpdir(), "banking-lab-final-review-prepare-"));
  const result = runPrepare(outputDir);

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Final retirement review evidence templates prepared/);

  const commandsPath = join(outputDir, "redacted-final-review-commands.template.json");
  const recordCommandPath = join(outputDir, "record-final-review-command.template.sh");
  assert.equal(existsSync(commandsPath), true);
  assert.equal(existsSync(recordCommandPath), true);

  const commands = JSON.parse(await readFile(commandsPath, "utf8"));
  assert.equal(commands.length, requiredCommands.length);
  assert.deepEqual(commands.map((item) => item.command), requiredCommands);
  assert.equal(commands[0].status, "TODO_REPLACE_WITH_pass");
  assert.equal(commands[0].exitCode, "TODO_REPLACE_WITH_0");
  assert.equal(commands[0].runAfterPasskeyEvidence, "TODO_REPLACE_WITH_true");
  assert.match(commands[0].summary, /after verified non-synthetic passkey evidence exists/);

  const recordCommand = await readFile(recordCommandPath, "utf8");
  assert.match(recordCommand, /BANKING_LAB_FINAL_REVIEW_CONFIRMED=TODO_REPLACE_WITH_true/);
  assert.match(recordCommand, /BANKING_LAB_FINAL_REVIEW_PASSKEY_ARTIFACT_VERIFIED=TODO_REPLACE_WITH_true/);
  assert.match(recordCommand, /BANKING_LAB_FINAL_REVIEW_REVIEWER=final-retirement-reviewer/);
  assert.match(recordCommand, /BANKING_LAB_FINAL_REVIEW_COMMANDS_FILE=.*redacted-final-review-commands\.template\.json/);
  assert.match(recordCommand, /BANKING_LAB_FINAL_REVIEW_OUTBOX_DURABILITY=TODO_REPLACE_WITH_true/);
  assert.match(recordCommand, /npm run retirement:final-review:record/);
});

test("final retirement review prepare templates cannot be recorded without manual replacement", async () => {
  const outputDir = await mkdtemp(join(tmpdir(), "banking-lab-final-review-prepare-"));
  const prepare = runPrepare(outputDir);
  assert.equal(prepare.status, 0, prepare.stderr);
  const passkeyPath = await writePasskeyFixture(outputDir);

  const record = runRecorderWithTemplate(outputDir, passkeyPath);
  assert.notEqual(record.status, 0);
  assert.match(record.stderr, /status must be pass|exitCode must be 0|runAfterPasskeyEvidence=true/);
  assert.equal(existsSync(join(outputDir, "final-review.json")), false);
});

test("final retirement review prepare rejects invalid review dates", async () => {
  const outputDir = await mkdtemp(join(tmpdir(), "banking-lab-final-review-prepare-"));
  const result = runPrepare(outputDir, {
    BANKING_LAB_FINAL_REVIEW_DATE: "2026/06/03"
  });

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /FINAL_REVIEW_DATE must be YYYY-MM-DD/);
});
