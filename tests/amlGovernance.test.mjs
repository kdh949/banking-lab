import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);

test("AML/FDS governance migration defines synthetic screening and reporting controls", async () => {
  const migration = await readFile("db/migrations/V029__aml_fds_governance_reporting.sql", "utf8");

  assert.match(migration, /CREATE TABLE synthetic_watchlist_entries/);
  assert.match(migration, /CREATE TABLE sanctions_screening_hits/);
  assert.match(migration, /CREATE TABLE aml_model_versions/);
  assert.match(migration, /CREATE TABLE aml_case_evidence_packages/);
  assert.match(migration, /CREATE TABLE synthetic_str_reports/);
  assert.match(migration, /CREATE TABLE synthetic_aml_regulatory_reports/);
  assert.match(migration, /SANCTIONS_SIM/);
  assert.match(migration, /PEP_SIM/);
  assert.match(migration, /realSanctionsData":false/);
});

test("AML STR report generator produces synthetic-only model and reporting artifacts", async () => {
  const packageJson = JSON.parse(await readFile("package.json", "utf8"));
  assert.equal(packageJson.scripts["aml:str-report"], "node --experimental-strip-types scripts/run-aml-str-report.ts");

  const result = await execFileAsync("npm", ["run", "aml:str-report"], { maxBuffer: 1024 * 1024 });
  assert.equal(result.stderr, "");
  assert.match(result.stdout, /AML STR\/regulatory report generation: pass/);

  const modelCard = JSON.parse(await readFile("docs/test-evidence/generated/aml-model-card.json", "utf8"));
  const strReport = JSON.parse(await readFile("docs/test-evidence/generated/synthetic-str-report.json", "utf8"));
  const regulatoryReport = JSON.parse(await readFile("docs/test-evidence/generated/aml-regulatory-report.json", "utf8"));
  const summary = JSON.parse(await readFile("docs/test-evidence/generated/aml-str-report-summary.json", "utf8"));

  assert.equal(summary.status, "pass");
  assert.equal(summary.syntheticOnly, true);
  assert.equal(summary.realPiiUsed, false);
  assert.equal(summary.realMoneyUsed, false);
  assert.equal(summary.realBankNetworkUsed, false);
  assert.equal(summary.realSanctionsDataUsed, false);
  assert.equal(summary.realRegulatorSubmission, false);
  assert.equal(modelCard.modelVersionId, "AML-MODEL-SYN-RULES-V1");
  assert.equal(modelCard.syntheticOnly, true);
  assert.equal(modelCard.realSanctionsDataUsed, false);
  assert.ok(modelCard.features.includes("amount_minor"));
  assert.equal(strReport.reportType, "SYNTHETIC_STR");
  assert.equal(strReport.regulatorFormat, "SYNTHETIC_STR_V1");
  assert.equal(strReport.submitted, false);
  assert.equal(strReport.syntheticOnly, true);
  assert.equal(strReport.realSanctionsDataUsed, false);
  assert.equal(regulatoryReport.reportType, "SYNTHETIC_AML_PERIODIC_REPORT");
  assert.equal(regulatoryReport.syntheticOnly, true);
  assert.equal(regulatoryReport.realRegulatorSubmission, false);
  assert.ok(regulatoryReport.totalScoredTransactions > 0);
  assert.ok(regulatoryReport.highRiskCount > 0);
});

test("AML/FDS governance API surface enforces high-risk controls in source", async () => {
  const service = await readFile("services/core-banking/src/main/kotlin/lab/banking/core/aml/AmlFdsGovernanceService.kt", "utf8");
  const authFilter = await readFile("services/core-banking/src/main/kotlin/lab/banking/core/security/BankingLabAuthorizationFilter.kt", "utf8");
  const routePolicy = await readFile("services/core-banking/src/main/kotlin/lab/banking/core/security/BankingLabRouteAuthorizationManager.kt", "utf8");
  const securityPolicy = await readFile("services/core-banking/src/main/kotlin/lab/banking/core/security/BankingLabSecurityPolicyEnforcer.kt", "utf8");

  assert.match(service, /MAKER_CHECKER_SELF_APPROVAL_REJECTED/);
  assert.match(service, /SANCTIONS_FALSE_POSITIVE_DISPOSITION_SEPARATION_OF_DUTIES/);
  assert.match(service, /approvedByRole != "COMPLIANCE_MANAGER"/);
  assert.match(service, /SANCTIONS_SCREENING_HIT/);
  assert.match(service, /SANCTIONS_FALSE_POSITIVE_DISPOSITIONED/);
  assert.match(authFilter, /routeAuthorizationManager\.allowedRoles\(request\)/);
  assert.match(routePolicy, /path\.startsWith\("\/api\/aml\/governance"\)/);
  assert.match(routePolicy, /"AML_REVIEWER", "COMPLIANCE_MANAGER", "AUDITOR"/);
  assert.match(securityPolicy, /path\.startsWith\("\/api\/aml\/governance\/"\)/);
});
