import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("Phase 8 large-ledger scripts are wired", async () => {
  const packageJson = JSON.parse(await readFile("package.json", "utf8"));
  const generator = await readFile("scripts/generate-large-ledger-dataset.ts", "utf8");
  const fixture = await readFile("scripts/large-ledger-fixture.ts", "utf8");
  const readiness = await readFile("scripts/check-ledger-partition-readiness.ts", "utf8");
  const plan = await readFile("docs/architecture/ledger-partition-archive-plan.md", "utf8");

  assert.equal(
    packageJson.scripts["ledger:large-dataset-smoke"],
    "node --experimental-strip-types scripts/generate-large-ledger-dataset.ts"
  );
  assert.equal(
    packageJson.scripts["ledger:query-benchmark"],
    "node --experimental-strip-types scripts/check-ledger-partition-readiness.ts"
  );
  assert.match(generator, /large-ledger-dataset-summary\.json/);
  assert.match(fixture, /deterministic-seed-replay/);
  assert.match(fixture, /projection-matches-postings/);
  assert.match(readiness, /idx_ledger_transactions_business_date/);
  assert.match(readiness, /ledger-query-benchmark\.json/);
  assert.match(plan, /source ledger tables FK-compatible/);
  assert.match(plan, /does not introduce real customer money/);
});

test("Phase 8 generated large-ledger evidence is synthetic and balanced", async () => {
  const summary = JSON.parse(await readFile("docs/test-evidence/generated/large-ledger-dataset-summary.json", "utf8"));
  const benchmark = JSON.parse(await readFile("docs/test-evidence/generated/ledger-query-benchmark.json", "utf8"));
  const smoke = await readFile("docs/test-evidence/ledger-large-dataset-smoke.md", "utf8");

  assert.equal(summary.status, "pass");
  assert.equal(summary.syntheticOnly, true);
  assert.equal(summary.deterministic, true);
  assert.equal(summary.counts.ledgerTransactions, summary.config.transactionCount);
  assert.equal(summary.counts.ledgerPostings, summary.config.transactionCount * 2);
  assert.equal(summary.counts.idempotencyKeys, summary.config.transactionCount);
  assert.equal(summary.balance.transactionImbalanceCount, 0);
  assert.equal(summary.balance.projectionMismatchCount, 0);
  assert.equal(summary.balance.globalNetByCurrency.KRW, 0);
  assert.ok(summary.counts.archiveCandidateTransactions > 0);
  assert.ok(summary.checks.every((check) => check.status === "pass"));

  assert.equal(benchmark.status, "pass");
  assert.equal(benchmark.syntheticOnly, true);
  assert.equal(benchmark.datasetHash, summary.datasetHash);
  assert.ok(benchmark.checks.every((check) => check.status === "pass"));
  assert.ok(benchmark.queryEvidence.every((query) => query.status === "pass" && query.resultRows > 0));
  assert.ok(benchmark.residualRisks.some((risk) => /not production throughput evidence/i.test(risk)));

  assert.match(smoke, /not a production capacity benchmark/i);
  assert.match(smoke, /does not use real customer money/i);
});
