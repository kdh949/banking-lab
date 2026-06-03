import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
const preflightScript = "scripts/check-passkey-non-synthetic-preflight.ts";

test("passkey evidence preflight validates manual-run prerequisites and recorded artifact", async () => {
  const { stdout, stderr } = await execFileAsync(
    process.execPath,
    ["--experimental-strip-types", preflightScript],
    { maxBuffer: 1024 * 1024 }
  );

  assert.equal(stderr, "");
  assert.match(stdout, /Passkey non-synthetic evidence preflight: pass/);
  assert.match(stdout, /Manual-live-passkey evidence artifact is recorded and strict verification passed/);
  assert.match(stdout, /manual-live-passkey evidence/i);
});

test("passkey evidence preflight is wired into scripts and retirement evidence", async () => {
  const packageJson = JSON.parse(await readFile("package.json", "utf8"));
  const gate = JSON.parse(await readFile("docs/migration/node-retirement-gate.json", "utf8"));
  const passkeyGate = gate.requiredGates.find((item) => item.id === "non-synthetic-passkey-operations");
  const evidenceRefresh = gate.requiredGates.find((item) => item.id === "evidence-refresh");
  const preflight = await readFile(preflightScript, "utf8");
  const prepareScript = "scripts/prepare-passkey-non-synthetic-evidence.ts";
  const prepareTest = "tests/passkeyEvidencePrepare.test.mjs";
  const readinessScript = "scripts/check-passkey-manual-readiness.ts";
  const readinessTest = "tests/passkeyManualReadiness.test.mjs";
  const liveReadinessScript = "scripts/check-passkey-live-platform-readiness.ts";
  const liveReadinessTest = "tests/passkeyLivePlatformReadiness.test.mjs";
  const staffPanel = "apps/staff-terminal/src/components/ApiBackedStaffPanel.tsx";
  const staffWebAuthnSpec = "apps/staff-terminal/e2e/staff-terminal-parity.spec.ts";
  const verifierScript = "scripts/verify-passkey-non-synthetic-evidence.ts";
  const verifierTest = "tests/passkeyEvidenceVerifier.test.mjs";
  const artifact = "docs/test-evidence/generated/passkey-non-synthetic-evidence.json";

  assert.equal(packageJson.scripts["passkey:evidence:preflight"], `node --experimental-strip-types ${preflightScript}`);
  assert.equal(packageJson.scripts["passkey:evidence:prepare"], `node --experimental-strip-types ${prepareScript}`);
  assert.equal(packageJson.scripts["passkey:evidence:readiness"], `node --experimental-strip-types ${readinessScript}`);
  assert.equal(packageJson.scripts["passkey:evidence:live-readiness"], `node --experimental-strip-types ${liveReadinessScript}`);
  assert.ok(passkeyGate?.evidence?.includes(prepareScript));
  assert.ok(passkeyGate?.evidence?.includes(prepareTest));
  assert.ok(passkeyGate?.evidence?.includes(readinessScript));
  assert.ok(passkeyGate?.evidence?.includes(readinessTest));
  assert.ok(passkeyGate?.evidence?.includes(liveReadinessScript));
  assert.ok(passkeyGate?.evidence?.includes(liveReadinessTest));
  assert.ok(passkeyGate?.evidence?.includes(staffPanel));
  assert.ok(passkeyGate?.evidence?.includes(staffWebAuthnSpec));
  assert.equal(packageJson.scripts["passkey:evidence:verify"], `node --experimental-strip-types ${verifierScript}`);
  assert.ok(passkeyGate?.evidence?.includes(verifierScript));
  assert.ok(passkeyGate?.evidence?.includes(verifierTest));
  assert.ok(passkeyGate?.evidence?.includes(artifact));
  assert.ok(passkeyGate?.evidence?.includes(preflightScript));
  assert.ok(passkeyGate?.evidence?.includes("tests/passkeyEvidencePreflight.test.mjs"));
  assert.ok(evidenceRefresh?.evidence?.includes(verifierScript));
  assert.ok(evidenceRefresh?.evidence?.includes(verifierTest));
  assert.ok(evidenceRefresh?.evidence?.includes(artifact));
  assert.ok(evidenceRefresh?.evidence?.includes(prepareScript));
  assert.ok(evidenceRefresh?.evidence?.includes(prepareTest));
  assert.ok(evidenceRefresh?.evidence?.includes(readinessScript));
  assert.ok(evidenceRefresh?.evidence?.includes(readinessTest));
  assert.ok(evidenceRefresh?.evidence?.includes(liveReadinessScript));
  assert.ok(evidenceRefresh?.evidence?.includes(liveReadinessTest));
  assert.ok(evidenceRefresh?.evidence?.includes(staffPanel));
  assert.ok(evidenceRefresh?.evidence?.includes(staffWebAuthnSpec));
  assert.ok(evidenceRefresh?.evidence?.includes(preflightScript));
  assert.ok(evidenceRefresh?.evidence?.includes("tests/passkeyEvidencePreflight.test.mjs"));
  assert.match(preflight, /manager-webauthn01/);
  assert.match(preflight, /security-admin01/);
  assert.match(preflight, /PASSKEY_RECOVERY_ADMIN/);
  assert.match(preflight, /NEXT_PUBLIC_BANKING_SIMULATOR_TOKENS_ENABLED/);
  assert.match(preflight, /simulator token smoke disabled/);
  assert.match(preflight, /Sign in WebAuthn manager with Keycloak/);
});
