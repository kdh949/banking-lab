import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

test("operational security lab controls declare WORM KMS PAM and SIEM surfaces", async () => {
  const migration = await readFile("db/migrations/V028__operational_security_lab_controls.sql", "utf8");
  const rules = await readFile("infra/observability/loki/rules/fake/operational-security-alerts.yml", "utf8");
  const script = await readFile("scripts/run-siem-alert-drill.ts", "utf8");
  const service = await readFile("services/core-banking/src/main/kotlin/lab/banking/core/opsec/OperationalSecurityService.kt", "utf8");
  const packageJson = JSON.parse(await readFile("package.json", "utf8"));

  for (const table of [
    "synthetic_kms_keys",
    "audit_worm_export_segments",
    "break_glass_grants",
    "break_glass_review_cases"
  ]) {
    assert.match(migration, new RegExp(`CREATE TABLE ${table}`));
  }
  assert.match(migration, /not-real-key-material/);
  assert.match(service, /postHocReviewRequired/);

  for (const alert of [
    "FailedAuthBurst",
    "PrivilegeChangeObserved",
    "MassPiiAccess",
    "BreakGlassUsage"
  ]) {
    assert.match(rules, new RegExp(`alert: ${alert}`));
  }
  assert.match(rules, /synthetic_only: "true"/);
  assert.match(script, /siem-alert-drill\.json/);
  assert.equal(packageJson.scripts["siem:alert-drill"], "node --experimental-strip-types scripts/run-siem-alert-drill.ts");
});
