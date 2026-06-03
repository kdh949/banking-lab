import assert from "node:assert/strict";
import test from "node:test";
import { mkdtemp, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { spawnSync } from "node:child_process";
import { tmpdir } from "node:os";

const prepareScript = "scripts/prepare-passkey-non-synthetic-evidence.ts";
const readinessScript = "scripts/check-passkey-manual-readiness.ts";

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

function runReadiness(outputDir) {
  return spawnSync(process.execPath, ["--experimental-strip-types", readinessScript], {
    cwd: process.cwd(),
    env: {
      ...process.env,
      BANKING_LAB_PASSKEY_TEMPLATE_DIR: outputDir
    },
    encoding: "utf8"
  });
}

test("passkey manual readiness accepts freshly prepared platform templates", async () => {
  const outputDir = await mkdtemp(join(tmpdir(), "banking-lab-passkey-readiness-"));
  const prepare = runPrepare(outputDir);
  assert.equal(prepare.status, 0, prepare.stderr);

  const readiness = runReadiness(outputDir);
  assert.equal(readiness.status, 0, readiness.stderr);
  assert.match(readiness.stdout, /Passkey manual readiness check: pass/);
  assert.match(readiness.stdout, /does not prove non-synthetic passkey operations/);
});

test("passkey manual readiness accepts freshly prepared hardware key templates", async () => {
  const outputDir = await mkdtemp(join(tmpdir(), "banking-lab-passkey-readiness-"));
  const prepare = runPrepare(outputDir, {
    BANKING_LAB_PASSKEY_AUTHENTICATOR_KIND: "hardware-security-key"
  });
  assert.equal(prepare.status, 0, prepare.stderr);

  const readiness = runReadiness(outputDir);
  assert.equal(readiness.status, 0, readiness.stderr);
  const commands = await readFile(join(outputDir, "redacted-commands.template.json"), "utf8");
  assert.match(commands, /real hardware security key/);
});

test("passkey manual readiness fails when templates have not been prepared", async () => {
  const outputDir = await mkdtemp(join(tmpdir(), "banking-lab-passkey-readiness-"));
  const readiness = runReadiness(outputDir);

  assert.notEqual(readiness.status, 0);
  assert.match(readiness.stderr, /Run npm run passkey:evidence:prepare before readiness|Missing or unreadable passkey manual readiness path/);
});

test("passkey manual readiness rejects simulator-token drift in prepared commands", async () => {
  const outputDir = await mkdtemp(join(tmpdir(), "banking-lab-passkey-readiness-"));
  const prepare = runPrepare(outputDir);
  assert.equal(prepare.status, 0, prepare.stderr);

  const commandsPath = join(outputDir, "redacted-commands.template.json");
  const commands = await readFile(commandsPath, "utf8");
  await writeFile(commandsPath, commands.replace("BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false", "BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true"));

  const readiness = runReadiness(outputDir);
  assert.notEqual(readiness.status, 0);
  assert.match(readiness.stderr, /simulator tokens disabled|SECURITY_SIMULATOR_TOKENS_ENABLED=false/);
});

test("passkey manual readiness rejects virtual authenticator command drift", async () => {
  const outputDir = await mkdtemp(join(tmpdir(), "banking-lab-passkey-readiness-"));
  const prepare = runPrepare(outputDir);
  assert.equal(prepare.status, 0, prepare.stderr);

  const commandsPath = join(outputDir, "redacted-commands.template.json");
  const commands = JSON.parse(await readFile(commandsPath, "utf8"));
  commands[3].command = "manual browser sign-in completed with a virtual authenticator";
  await writeFile(commandsPath, `${JSON.stringify(commands, null, 2)}\n`);

  const readiness = runReadiness(outputDir);
  assert.notEqual(readiness.status, 0);
  assert.match(readiness.stderr, /real authenticator operator step|virtual-authenticator operations/);
});
