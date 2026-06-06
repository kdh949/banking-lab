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
});
