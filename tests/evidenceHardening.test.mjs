import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("coverage matrix uses split API-backed and deployment evidence statuses", async () => {
  const matrix = await readFile("docs/implementation-coverage-matrix.md", "utf8");
  const row = (prefix) => matrix.split("\n").find((line) => line.startsWith(prefix)) ?? "";

  assert.match(matrix, /api-backed-read/);
  assert.match(matrix, /api-backed-command/);
  assert.match(matrix, /browser-e2e-backed/);
  assert.match(matrix, /live-keycloak-backed/);
  assert.doesNotMatch(matrix, /\|\s*api-backed\s*\|/);
  assert.match(row("| analytics | Python AML/FDS scoring"), /GET \/api\/fds\/analytics/);
  assert.match(row("| analytics | Python AML/FDS scoring"), /\| complete \|$/);
  assert.match(row("| formal | Executable ledger and idempotency model checking gate"), /bounded state-search/);
  assert.match(row("| formal | Executable ledger and idempotency model checking gate"), /\| partial \|$/);
  assert.match(row("| platform | Kubernetes/Helm/Argo CD structural validation"), /\| complete \|$/);
  assert.match(row("| platform | Live cluster deployment validation"), /\| missing \|$/);
});

test("security evidence has a Docker-forced rerun path and exact skipped DAST boundary", async () => {
  const packageJson = JSON.parse(await readFile("package.json", "utf8"));
  const runner = await readFile("scripts/run-security-evidence.ts", "utf8");
  const dockerRunner = await readFile("scripts/run-security-evidence-docker.ts", "utf8");
  const doc = await readFile("docs/test-evidence/security-docker-rerun.md", "utf8");
  const summary = JSON.parse(await readFile("docs/test-evidence/generated/security-evidence-summary.json", "utf8"));
  const dast = summary.checks.find((check) => check.id === "dast-zap-baseline");

  assert.equal(packageJson.scripts["security:evidence:docker"], "node --experimental-strip-types scripts/run-security-evidence-docker.ts");
  assert.match(runner, /BANKING_LAB_SECURITY_FORCE_DOCKER/);
  assert.match(dockerRunner, /docker/);
  assert.match(dockerRunner, /BANKING_LAB_SECURITY_FORCE_DOCKER/);
  assert.match(doc, /npm run security:evidence:docker/);
  assert.equal(dast?.status, "skipped");
  assert.equal(dast?.reason, "BANKING_LAB_DAST_URL is not set; no live target was supplied for DAST.");
});

test("PostgreSQL backup restore drill records fixture evidence and live prerequisites", async () => {
  const packageJson = JSON.parse(await readFile("package.json", "utf8"));
  const script = await readFile("scripts/run-postgres-backup-drill.ts", "utf8");
  const doc = await readFile("docs/test-evidence/postgres-backup-restore-drill.md", "utf8");
  const evidence = JSON.parse(await readFile("docs/test-evidence/generated/postgres-backup-restore-drill.json", "utf8"));

  assert.equal(packageJson.scripts["postgres:backup-drill"], "node --experimental-strip-types scripts/run-postgres-backup-drill.ts");
  assert.match(script, /pg_dump/);
  assert.match(script, /pg_restore/);
  assert.match(script, /BANKING_LAB_POSTGRES_URL/);
  assert.match(script, /BANKING_LAB_RESTORE_POSTGRES_URL/);
  assert.match(doc, /not a live PostgreSQL durability proof/);
  assert.equal(evidence.status, "pass");
  assert.equal(evidence.mode, "fixture");
  assert.equal(evidence.postgresLive, false);
  assert.equal(evidence.syntheticOnly, true);
  assert.ok(evidence.checks.some((check) => check.id === "ledger-posting-balance-valid" && check.status === "pass"));
  assert.ok(evidence.checks.some((check) => check.id === "audit-hash-chain-valid" && check.status === "pass"));
});
