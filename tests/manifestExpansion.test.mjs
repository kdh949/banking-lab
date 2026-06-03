import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";
import {
  TEMPLATE_CONVENTIONS,
  loadExpandedManifests
} from "../legacy-node-reference/packages/screen-engine/src/index.mjs";

test("template conventions define reusable banking screen contracts", () => {
  for (const type of ["INQUIRY", "COMMAND", "CASE", "PARAMETER"]) {
    const convention = TEMPLATE_CONVENTIONS[type];
    assert.ok(convention, `${type} convention is missing`);
    assert.equal(convention.template, type.toLowerCase());
    assert.ok(convention.requiredManifestKeys.length >= 1);
    assert.ok(convention.regions.includes("auditPanel"));
    assert.ok(convention.expectedControls.includes("audit-logged"));
  }

  assert.ok(TEMPLATE_CONVENTIONS.INQUIRY.regions.includes("resultTable"));
  assert.ok(TEMPLATE_CONVENTIONS.COMMAND.regions.includes("beforeSnapshot"));
  assert.ok(TEMPLATE_CONVENTIONS.CASE.regions.includes("workflowTimeline"));
  assert.ok(TEMPLATE_CONVENTIONS.PARAMETER.regions.includes("rollbackPanel"));
});

test("expanded manifests cover reusable templates across bank channels", async () => {
  const manifests = await loadExpandedManifests("screen-manifests");
  const apps = new Set(manifests.map((manifest) => manifest.app));
  const types = new Set(manifests.map((manifest) => manifest.type));

  for (const app of ["staff-terminal", "customer-web", "complaint-portal", "ops-console", "audit-console", "fds-aml-console"]) {
    assert.ok(apps.has(app), `${app} manifest coverage is missing`);
  }
  for (const type of ["INQUIRY", "COMMAND", "CASE", "PARAMETER"]) {
    assert.ok(types.has(type), `${type} template coverage is missing`);
  }

  const parameterScreens = manifests.filter((manifest) => manifest.type === "PARAMETER");
  assert.ok(parameterScreens.length >= 3);
  for (const manifest of parameterScreens) {
    assert.equal(manifest.templateContract.template, "parameter");
    assert.ok(manifest.templateContract.regions.includes("currentValue"));
    assert.ok(manifest.templateContract.regions.includes("scheduledValue"));
    assert.ok(manifest.templateContract.regions.includes("rollbackPanel"));
    assert.equal(manifest.controlMetadata.approval.required, true);
    assert.equal(manifest.controlMetadata.approval.makerChecker, true);
    assert.equal(manifest.controlMetadata.audit.reasonRequired, true);
    assert.ok(manifest.formContract.requiredFieldNames.includes("reason"));
  }
});

test("expanded manifests preserve role audit masking workflow and approval metadata", async () => {
  const manifests = await loadExpandedManifests("screen-manifests");

  const piiScreens = manifests.filter((manifest) => manifest.controlMetadata.audit.piiAccess);
  assert.ok(piiScreens.length >= 8);
  for (const manifest of piiScreens) {
    assert.notEqual(manifest.controlMetadata.masking.policy, "NONE");
    assert.equal(manifest.controlMetadata.masking.defaultMasked, true);
  }

  const workflowScreens = manifests.filter((manifest) => manifest.type === "CASE");
  assert.ok(workflowScreens.length >= 6);
  for (const manifest of workflowScreens) {
    assert.equal(manifest.controlMetadata.workflow.required, true);
    assert.ok(manifest.controlMetadata.workflow.name);
    assert.ok(manifest.controlMetadata.workflow.states.length >= 1);
    assert.equal(manifest.controlMetadata.workflow.timelineRequired, true);
  }

  const highRiskScreens = manifests.filter((manifest) => manifest.highRisk === true);
  assert.ok(highRiskScreens.length >= 4);
  for (const manifest of highRiskScreens) {
    assert.equal(manifest.controlMetadata.approval.required, true);
    assert.equal(manifest.controlMetadata.approval.makerChecker, true);
  }
});

test("manifest expansion covers parity control scenarios for screen factory domains", async () => {
  const parity = JSON.parse(await readFile("docs/migration/parity-scenarios.json", "utf8"));
  const manifests = await loadExpandedManifests("screen-manifests");
  const manifestSuite = parity.suites.find((suite) => suite.nodeSuite === "tests/manifest.test.mjs");
  const staffSuite = parity.suites.find((suite) => suite.nodeSuite === "tests/staffTerminal.test.mjs");
  const complaintSuite = parity.suites.find((suite) => suite.nodeSuite === "tests/complaintWorkflow.test.mjs");
  const fdsAmlSuite = parity.suites.find((suite) => suite.nodeSuite === "tests/fdsAmlReconciliation.test.mjs");

  assert.deepEqual(manifestSuite.controls, ["manifest", "maker-checker", "masking"]);
  assert.ok(staffSuite.controls.includes("audit"));
  assert.ok(complaintSuite.controls.includes("workflow"));
  assert.ok(fdsAmlSuite.controls.includes("reconciliation"));

  assert.ok(manifests.some((manifest) => manifest.app === "staff-terminal" && manifest.controlMetadata.audit.reasonRequired));
  assert.ok(manifests.some((manifest) => manifest.controlMetadata.approval.makerChecker));
  assert.ok(manifests.some((manifest) => manifest.controlMetadata.masking.defaultMasked));
  assert.ok(manifests.some((manifest) => manifest.domain === "complaint" && manifest.controlMetadata.workflow.required));
  assert.ok(manifests.some((manifest) => ["fds", "aml"].includes(manifest.domain) && manifest.controlMetadata.workflow.required));
  assert.ok(manifests.some((manifest) => manifest.domain === "reconciliation"));
});
