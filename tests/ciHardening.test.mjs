import { spawnSync } from "node:child_process";
import { readFile } from "node:fs/promises";
import assert from "node:assert/strict";
import test from "node:test";

test("remaining hardening CI jobs are structurally wired", () => {
  const result = spawnSync(
    process.execPath,
    ["--experimental-strip-types", "scripts/check-ci-workflow.ts"],
    { encoding: "utf8" }
  );

  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.match(result.stdout, /CI workflow hardening check passed/);
});

test("CI hardening evidence documents PR and manual boundaries", async () => {
  const evidence = await readFile("docs/test-evidence/ci-coverage-hardening.md", "utf8");
  const hostedStatus = await readFile("docs/test-evidence/ci-hosted-run-status.md", "utf8");

  for (const required of [
    "backend-payment-service",
    "backend-notification-service",
    "backend-reporting-service",
    "backend-all-gradle",
    "platform-validation",
    "contracts-validation",
    "compose-platform-config"
  ]) {
    assert.match(evidence, new RegExp(required));
  }

  assert.match(evidence, /PR CI Versus Manual\/Nightly CI/);
  assert.match(evidence, /Do not mark historical evidence as current Phase 1 pass evidence/);
  assert.match(evidence, /not implemented/);

  assert.match(hostedStatus, /Run status: blocked/);
  assert.match(hostedStatus, /push to `main` after PR #82/);
  assert.match(hostedStatus, /f150663875d1ec094b11f5db8d8c03d7b242ed38/);
  assert.match(hostedStatus, /https:\/\/github\.com\/kdh949\/banking-lab\/actions\/runs\/27285121678/);
  assert.match(hostedStatus, /pull_request #82/);
  assert.match(hostedStatus, /c2841c7b8cdaadfe3b0e1b7d8a3e19c2a1b46d11/);
  assert.match(hostedStatus, /https:\/\/github\.com\/kdh949\/banking-lab\/actions\/runs\/27285001175/);
  assert.match(hostedStatus, /runner_id: 0/);
  assert.match(hostedStatus, /steps: 0/);
  assert.match(hostedStatus, /issues\/83/);
  assert.match(hostedStatus, /pull_request #79/);
  assert.match(hostedStatus, /https:\/\/github\.com\/kdh949\/banking-lab\/actions\/runs\/27281812788/);
  assert.match(hostedStatus, /pull_request #77/);
  assert.match(hostedStatus, /https:\/\/github\.com\/kdh949\/banking-lab\/actions\/runs\/27280452683/);
  assert.match(hostedStatus, /pull_request #76/);
  assert.match(hostedStatus, /https:\/\/github\.com\/kdh949\/banking-lab\/actions\/runs\/27279285215/);
  assert.match(hostedStatus, /pull_request #75/);
  assert.match(hostedStatus, /https:\/\/github\.com\/kdh949\/banking-lab\/actions\/runs\/27279138120/);
  assert.match(hostedStatus, /pull_request #74/);
  assert.match(hostedStatus, /https:\/\/github\.com\/kdh949\/banking-lab\/actions\/runs\/27278950044/);
  assert.match(hostedStatus, /80567736503/);
  assert.match(hostedStatus, /log not found/);
  assert.match(hostedStatus, /https:\/\/github\.com\/kdh949\/banking-lab\/actions\/runs\/27269813915/);
  assert.match(hostedStatus, /https:\/\/github\.com\/kdh949\/banking-lab\/actions\/runs\/27269207643/);
  assert.match(hostedStatus, /spending limit needs to be increased/);
  assert.match(hostedStatus, /Local commands run for PR #77/);
  assert.match(hostedStatus, /callCenterConsole\.test\.mjs tests\/nextScaffold\.test\.mjs/);
  assert.match(hostedStatus, /Local commands run for PR #79/);
  assert.match(hostedStatus, /test:call-center-console:keycloak-e2e-compose/);
  assert.match(hostedStatus, /Local commands run for PR #82/);
  assert.match(hostedStatus, /test:staff-terminal:api-e2e-compose/);
  assert.match(hostedStatus, /staff-terminal Spring API evidence panel/);
  assert.match(hostedStatus, /Local commands run for PR #74/);
  assert.match(hostedStatus, /npm test`: pass, 191 tests/);
  assert.match(hostedStatus, /Local fallback commands are not hosted CI green/);
  assert.doesNotMatch(hostedStatus, /Run status: green/);
});
