import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("H7 data platform scripts and package entrypoints are wired", async () => {
  const packageJson = JSON.parse(await readFile("package.json", "utf8"));
  const pyproject = await readFile("analytics/aml-fds-python/pyproject.toml", "utf8");
  const module = await readFile("analytics/aml-fds-python/src/banking_lab_analytics/data_platform.py", "utf8");
  const pythonTest = await readFile("analytics/aml-fds-python/tests/test_data_platform.py", "utf8");

  assert.equal(
    packageJson.scripts["data:dq-check"],
    "uv --cache-dir .uv-cache run --directory analytics/aml-fds-python --project . banking-lab-data-platform --source-dir sample-data/data-platform --output-dir ../../docs/test-evidence/generated/data-platform"
  );
  assert.match(pyproject, /banking-lab-data-platform = "banking_lab_analytics\.data_platform:main"/);
  assert.match(module, /COPY finance_balance_mart TO .*FORMAT parquet/);
  assert.match(module, /ledger_projection_reconciliation/);
  assert.match(module, /resolve_lineage/);
  assert.match(pythonTest, /inject_dirty_data=True/);
  assert.match(pythonTest, /test_risk_report_is_reproducible_for_fixed_inputs/);
});

test("H7 generated data-platform evidence is synthetic complete and reproducible", async () => {
  const evidence = JSON.parse(await readFile("docs/test-evidence/generated/data-platform/data-platform-evidence.json", "utf8"));
  const lineage = JSON.parse(await readFile("docs/test-evidence/generated/data-platform/data-lineage.json", "utf8"));
  const report = JSON.parse(await readFile("docs/test-evidence/generated/data-platform/synthetic-risk-report.json", "utf8"));

  assert.equal(evidence.syntheticOnly, true);
  assert.equal(evidence.dqStatus, "pass");
  assert.equal(evidence.controls.realMoneyUsed, false);
  assert.equal(evidence.controls.realPiiUsed, false);
  assert.equal(evidence.controls.realBankNetworkUsed, false);
  assert.equal(evidence.controls.realKycUsed, false);
  assert.equal(evidence.controls.realExternalProviderUsed, false);
  assert.equal(evidence.controls.parquetMartsWritten, true);
  assert.equal(evidence.martRowCounts.financeBalanceMart, 3);
  assert.equal(evidence.martRowCounts.riskExposureMart, 3);
  assert.ok(evidence.dqChecks.every((check) => check.status === "pass"));
  assert.equal(report.reportId, "RISK-SYN-H7-LIQUIDITY-EXPOSURE");
  assert.equal(report.reportHash, evidence.riskReport.reportHash);
  assert.equal(report.totalLedgerBalanceMinor, 6630000);
  assert.equal(report.totalAvailableBalanceMinor, 6080000);
  assert.deepEqual(
    lineage.fieldLineage["riskReport.totalAvailableBalanceMinor"],
    [
      "source.synthetic_oltp.account_balance_projections.available_balance_minor",
      "mart.risk_exposure_mart.available_balance_minor",
      "report.synthetic_liquidity_exposure.totalAvailableBalanceMinor"
    ]
  );
});
