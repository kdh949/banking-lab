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
  assert.match(row("| formal | Executable ledger and idempotency model checking gate"), /actual TLC/);
  assert.match(row("| formal | Executable ledger and idempotency model checking gate"), /\| complete \|$/);
  assert.match(row("| platform | Kubernetes/Helm/Argo CD structural validation"), /\| complete \|$/);
  assert.match(row("| platform | Live cluster deployment validation"), /kind create cluster/);
  assert.match(row("| platform | Live cluster deployment validation"), /\| complete \|$/);
});

test("security evidence has a Docker-forced rerun path and live DAST evidence", async () => {
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
  assert.match(doc, /BANKING_LAB_DAST_URL/);
  assert.equal(summary.forcedDockerScanners, true);
  assert.equal(summary.totals.skipped, 0);
  assert.equal(dast?.status, "pass");
  assert.equal(dast?.outputPath, "docs/test-evidence/generated/zap-baseline.log");
});

test("PostgreSQL backup restore drill records live evidence and reusable Docker prerequisites", async () => {
  const packageJson = JSON.parse(await readFile("package.json", "utf8"));
  const script = await readFile("scripts/run-postgres-backup-drill.ts", "utf8");
  const dockerLiveScript = await readFile("scripts/run-postgres-docker-live-drill.ts", "utf8");
  const dockerCompose = await readFile("infra/docker-compose/postgres-backup-drill.yml", "utf8");
  const doc = await readFile("docs/test-evidence/postgres-backup-restore-drill.md", "utf8");
  const evidence = JSON.parse(await readFile("docs/test-evidence/generated/postgres-backup-restore-drill.json", "utf8"));

  assert.equal(packageJson.scripts["postgres:backup-drill"], "node --experimental-strip-types scripts/run-postgres-backup-drill.ts");
  assert.equal(
    packageJson.scripts["postgres:backup-drill:docker-live"],
    "node --experimental-strip-types scripts/run-postgres-docker-live-drill.ts"
  );
  assert.match(script, /pg_dump/);
  assert.match(script, /pg_restore/);
  assert.match(script, /BANKING_LAB_POSTGRES_URL/);
  assert.match(script, /BANKING_LAB_RESTORE_POSTGRES_URL/);
  assert.match(script, /accountBalanceProjectionMismatchQuery/);
  assert.match(dockerLiveScript, /--mode=live/);
  assert.match(dockerLiveScript, /seedSyntheticBackupFixture/);
  assert.match(dockerCompose, /postgres-source/);
  assert.match(dockerCompose, /postgres-restore/);
  assert.match(doc, /not a live PostgreSQL durability proof/);
  assert.equal(evidence.status, "pass");
  assert.equal(evidence.mode, "live");
  assert.equal(evidence.postgresLive, true);
  assert.equal(evidence.syntheticOnly, true);
  assert.ok(evidence.checks.some((check) => check.id === "ledgerTransactions-count-parity" && check.status === "pass"));
  assert.ok(evidence.checks.some((check) => check.id === "accountBalanceProjections-count-parity" && check.status === "pass"));
  assert.ok(evidence.checks.some((check) => check.id === "ledger-posting-balance-valid" && check.status === "pass"));
  assert.ok(evidence.checks.some((check) => check.id === "account-balance-projection-valid" && check.status === "pass"));
  assert.ok(evidence.checks.some((check) => check.id === "available-balance-projection-valid" && check.status === "pass"));
  assert.ok(evidence.checks.some((check) => check.id === "audit-hash-chain-valid" && check.status === "pass"));
  assert.ok(evidence.checks.some((check) => check.id === "synthetic-boundary-valid" && check.status === "pass"));
});

test("final hardening scorecard and demo script disclose limits without overclaiming", async () => {
  const scorecard = await readFile("docs/test-evidence/final-hardening-scorecard.md", "utf8");
  const demo = await readFile("docs/demo-scenarios/demo-video-script.md", "utf8");

  for (const requiredArea of [
    "Ledger integrity",
    "Idempotency/reversal/adjustment",
    "Staff integrated terminal",
    "Customer channel",
    "Electronic complaint portal",
    "Call-center 상담 전산",
    "FDS/AML 실질 적용",
    "Payment/notification/reporting bounded contexts",
    "Security/JWKS/Keycloak",
    "K8s/Helm/Argo",
    "Documentation consistency"
  ]) {
    assert.match(scorecard, new RegExp(requiredArea.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  }

  assert.match(scorecard, /Call-center 상담 전산 \| 2\/10 \| missing/);
  assert.match(scorecard, /Hosted CI \| 3\/10 \| blocked/);
  assert.match(scorecard, /not as green/);
  assert.match(scorecard, /no real customer money, real PII, real KYC\/AML provider/);
  assert.doesNotMatch(scorecard, /production-ready|real banking ready|actual payment network ready/i);

  assert.match(demo, /Scene 12: Call-Center Workflow Boundary/);
  assert.match(demo, /not demonstrated as complete/);
  assert.match(demo, /GitHub Actions is currently blocked before runner startup/);
  assert.doesNotMatch(demo, /PostgreSQL backup\/restore drill gap remains/);
});
