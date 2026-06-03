import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
const script = "scripts/check-stack-retirement-by-area.ts";
const doc = "docs/test-evidence/stack-retirement-area-audit.md";

test("stack retirement area audit proves target areas do not use legacy Node MVP stack", async () => {
  const { stdout, stderr } = await execFileAsync(
    process.execPath,
    ["--experimental-strip-types", script],
    { maxBuffer: 1024 * 1024 * 2 }
  );

  assert.equal(stderr, "");
  assert.match(stdout, /Stack retirement area audit: pass/);
  assert.match(stdout, /core-banking-backend: Kotlin\/Java \+ Spring Boot/);
  assert.match(stdout, /frontend-channels: TypeScript \+ Next\.js\/React/);
  assert.match(stdout, /shared-packages: TypeScript shared API\/auth\/screen\/form packages/);
  assert.match(stdout, /analytics: Python \+ DuckDB\/scikit-learn/);
  assert.match(stdout, /platform-infra: Docker Compose, Kubernetes, Terraform, Helm, Argo CD/);
  assert.match(stdout, /contracts-and-data: OpenAPI\/AsyncAPI\/Temporal contracts and PostgreSQL\/Flyway migrations/);
  assert.match(stdout, /Node retirement gate: blocked/);
  assert.match(stdout, /non-synthetic-passkey-operations: pending/);
  assert.match(stdout, /retirement-review: pending/);
});

test("stack retirement area audit is wired into scripts and retirement evidence", async () => {
  const packageJson = JSON.parse(await readFile("package.json", "utf8"));
  const gate = JSON.parse(await readFile("docs/migration/node-retirement-gate.json", "utf8"));
  const evidenceRefresh = gate.requiredGates.find((item) => item.id === "evidence-refresh");
  const retirementReview = gate.requiredGates.find((item) => item.id === "retirement-review");
  const source = await readFile(script, "utf8");
  const evidenceDoc = await readFile(doc, "utf8");

  assert.equal(packageJson.scripts["retirement:stack-audit"], `node --experimental-strip-types ${script}`);
  assert.ok(evidenceRefresh?.evidence?.includes(doc));
  assert.ok(evidenceRefresh?.evidence?.includes(script));
  assert.ok(evidenceRefresh?.evidence?.includes("tests/stackRetirementAreaAudit.test.mjs"));
  assert.ok(retirementReview?.evidence?.includes(doc));
  assert.ok(retirementReview?.evidence?.includes(script));
  assert.ok(retirementReview?.evidence?.includes("tests/stackRetirementAreaAudit.test.mjs"));
  assert.match(source, /core-banking-backend/);
  assert.match(source, /frontend-channels/);
  assert.match(source, /shared-packages/);
  assert.match(source, /analytics/);
  assert.match(source, /platform-infra/);
  assert.match(source, /contracts-and-data/);
  assert.match(source, /legacy-node-reference/);
  assert.match(evidenceDoc, /Status:\s+pass/i);
  assert.match(evidenceDoc, /does not mark Node retirement ready/);
});
