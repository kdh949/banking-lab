import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("H8 governance evidence generator and package wiring are present", async () => {
  const packageJson = JSON.parse(await readFile("package.json", "utf8"));
  const script = await readFile("scripts/run-governance-evidence.ts", "utf8");

  assert.equal(packageJson.scripts["governance:evidence"], "node --experimental-strip-types scripts/run-governance-evidence.ts");
  assert.match(script, /access-rights-review\.json/);
  assert.match(script, /deployment-approval-evidence\.json/);
  assert.match(script, /incident-response-drill-log\.json/);
  assert.match(script, /vulnerability-remediation-tracker\.json/);
  assert.match(script, /security-evidence-summary\.json/);
  assert.match(script, /realm-banking-lab\.json/);
});

test("H8 generated governance artifacts are synthetic and complete", async () => {
  const summary = JSON.parse(await readFile("docs/test-evidence/generated/governance/governance-evidence-summary.json", "utf8"));
  const access = JSON.parse(await readFile("docs/test-evidence/generated/governance/access-rights-review.json", "utf8"));
  const deployment = JSON.parse(await readFile("docs/test-evidence/generated/governance/deployment-approval-evidence.json", "utf8"));
  const incident = JSON.parse(await readFile("docs/test-evidence/generated/governance/incident-response-drill-log.json", "utf8"));
  const vulnerability = JSON.parse(await readFile("docs/test-evidence/generated/governance/vulnerability-remediation-tracker.json", "utf8"));
  const runbook = await readFile("docs/incident-response/synthetic-incident-response-runbook.md", "utf8");

  assert.equal(summary.status, "pass");
  assert.equal(summary.syntheticOnly, true);
  assert.equal(summary.controls.realMoneyUsed, false);
  assert.equal(summary.controls.realPiiUsed, false);
  assert.equal(summary.controls.realPaymentNetworkUsed, false);
  assert.deepEqual(
    summary.artifacts.map((artifact) => artifact.path).sort(),
    [
      "docs/incident-response/synthetic-incident-response-runbook.md",
      "docs/test-evidence/generated/governance/access-rights-review.json",
      "docs/test-evidence/generated/governance/deployment-approval-evidence.json",
      "docs/test-evidence/generated/governance/governance-evidence-summary.json",
      "docs/test-evidence/generated/governance/incident-response-drill-log.json",
      "docs/test-evidence/generated/governance/vulnerability-remediation-tracker.json"
    ].sort()
  );

  assert.equal(access.syntheticOnly, true);
  assert.equal(access.summary.principalCount, 12);
  assert.equal(access.summary.roleAssignmentCount, 16);
  assert.ok(access.summary.overPrivilegeFlagCount > 0);
  assert.ok(access.principals.some((principal) => principal.principalId === "security-admin01" && principal.overPrivilegeFlags.length > 0));

  assert.equal(deployment.syntheticOnly, true);
  assert.equal(deployment.releaseId, "REL-HARDENING-H1-H8-2026-06-05");
  assert.equal(deployment.approval.status, "approved");
  assert.equal(deployment.approval.makerCheckerSeparated, true);
  assert.notEqual(deployment.approval.maker, deployment.approval.checker);
  assert.ok(deployment.requiredPreReleaseCommands.includes("npm run governance:evidence"));

  assert.equal(incident.syntheticOnly, true);
  assert.equal(incident.status, "pass");
  assert.equal(incident.linkedDrills.length, 2);
  assert.ok(incident.linkedDrills.every((drill) => drill.status === "pass" && drill.syntheticOnly === true));
  assert.match(runbook, /Synthetic Incident Response Runbook/);
  assert.match(runbook, /No real funds, PII, KYC, payment-network data, external bank API/);

  assert.equal(vulnerability.syntheticOnly, true);
  assert.equal(vulnerability.summary.totalFindings, 5);
  assert.equal(vulnerability.summary.failedChecks, 0);
  assert.equal(vulnerability.summary.openRemediationCount, 0);
  assert.ok(vulnerability.findings.every((finding) => finding.remediationStatus === "closed-verified"));
});

test("H8 regulatory mapping references implemented governance controls only", async () => {
  const coverage = await readFile("docs/implementation-coverage-matrix.md", "utf8");
  const gapReport = await readFile("docs/test-evidence/evidence-gap-report.md", "utf8");
  const controlMatrix = await readFile("docs/regulatory-mapping/evidence-pack-control-matrix.md", "utf8");
  const asvs = await readFile("docs/regulatory-mapping/owasp-asvs-mapping.md", "utf8");
  const governanceMapping = await readFile("docs/regulatory-mapping/governance-artifact-mapping.md", "utf8");

  assert.match(coverage, /hardening-h8/);
  assert.match(gapReport, /Governance artifact automation/);
  assert.match(controlMatrix, /Access-rights review automation/);
  assert.match(controlMatrix, /npm run governance:evidence/);
  assert.match(asvs, /H8 adds access-rights review/);
  assert.match(governanceMapping, /docs\/test-evidence\/generated\/governance\/governance-evidence-summary\.json/);
  assert.match(governanceMapping, /They do not use real customer data/);
  assert.doesNotMatch(governanceMapping, /integrates real|uses real/i);
});
