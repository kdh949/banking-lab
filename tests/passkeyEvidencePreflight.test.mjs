import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
const preflightScript = "scripts/check-passkey-non-synthetic-preflight.ts";

test("passkey evidence preflight validates static manual-run prerequisites without proving the gate", async () => {
  const { stdout, stderr } = await execFileAsync(
    process.execPath,
    ["--experimental-strip-types", preflightScript],
    { maxBuffer: 1024 * 1024 }
  );

  assert.equal(stderr, "");
  assert.match(stdout, /Passkey non-synthetic evidence preflight: pass/);
  assert.match(stdout, /does not prove non-synthetic passkey operations/);
  assert.match(stdout, /manual-live-passkey evidence/);
});

test("passkey evidence preflight is wired into scripts and retirement evidence", async () => {
  const packageJson = JSON.parse(await readFile("package.json", "utf8"));
  const gate = JSON.parse(await readFile("docs/migration/node-retirement-gate.json", "utf8"));
  const passkeyGate = gate.requiredGates.find((item) => item.id === "non-synthetic-passkey-operations");
  const evidenceRefresh = gate.requiredGates.find((item) => item.id === "evidence-refresh");
  const preflight = await readFile(preflightScript, "utf8");
  const prepareScript = "scripts/prepare-passkey-non-synthetic-evidence.ts";
  const prepareTest = "tests/passkeyEvidencePrepare.test.mjs";
  const verifierScript = "scripts/verify-passkey-non-synthetic-evidence.ts";
  const verifierTest = "tests/passkeyEvidenceVerifier.test.mjs";

  assert.equal(packageJson.scripts["passkey:evidence:preflight"], `node --experimental-strip-types ${preflightScript}`);
  assert.equal(packageJson.scripts["passkey:evidence:prepare"], `node --experimental-strip-types ${prepareScript}`);
  assert.ok(passkeyGate?.evidence?.includes(prepareScript));
  assert.ok(passkeyGate?.evidence?.includes(prepareTest));
  assert.equal(packageJson.scripts["passkey:evidence:verify"], `node --experimental-strip-types ${verifierScript}`);
  assert.ok(passkeyGate?.evidence?.includes(verifierScript));
  assert.ok(passkeyGate?.evidence?.includes(verifierTest));
  assert.ok(passkeyGate?.evidence?.includes(preflightScript));
  assert.ok(passkeyGate?.evidence?.includes("tests/passkeyEvidencePreflight.test.mjs"));
  assert.ok(evidenceRefresh?.evidence?.includes(verifierScript));
  assert.ok(evidenceRefresh?.evidence?.includes(verifierTest));
  assert.ok(evidenceRefresh?.evidence?.includes(prepareScript));
  assert.ok(evidenceRefresh?.evidence?.includes(prepareTest));
  assert.ok(evidenceRefresh?.evidence?.includes(preflightScript));
  assert.ok(evidenceRefresh?.evidence?.includes("tests/passkeyEvidencePreflight.test.mjs"));
  assert.match(preflight, /manager-webauthn01/);
  assert.match(preflight, /security-admin01/);
  assert.match(preflight, /PASSKEY_RECOVERY_ADMIN/);
  assert.match(preflight, /Sign in WebAuthn manager with Keycloak/);
});
