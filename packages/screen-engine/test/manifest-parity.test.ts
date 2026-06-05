import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { MINIMUM_MANIFEST_COUNTS, loadExpandedManifests, loadManifests, validateManifest } from "../src/manifest.ts";

const manifestRoot = "../../screen-manifests";
const repositoryRoot = "../..";

test("target screen manifests validate and cover channel shells", async () => {
  const manifests = await loadManifests(manifestRoot);
  const apps = new Set(manifests.map((manifest) => manifest.app));

  for (const app of ["customer-web", "staff-terminal", "complaint-portal", "ops-console", "audit-console", "fds-aml-console", "admin-console"]) {
    assert.equal(apps.has(app), true, `${app} must have target manifest coverage`);
  }
  assert.equal(manifests.length >= MINIMUM_MANIFEST_COUNTS.total, true);
});

test("target screen manifest catalog enforces minimum breadth and unique codes", async () => {
  const manifests = await loadManifests(manifestRoot);
  const screenIds = new Set(manifests.map((manifest) => manifest.screenId));
  const transactionCodes = new Set(manifests.map((manifest) => manifest.transactionCode));
  const appCounts = new Map<string, number>();

  for (const manifest of manifests) {
    appCounts.set(manifest.app, (appCounts.get(manifest.app) || 0) + 1);
  }

  assert.equal(manifests.length, screenIds.size, "screenId values must be unique");
  assert.equal(manifests.length, transactionCodes.size, "transactionCode values must be present and unique");
  assert.equal(manifests.length >= 60, true, "screen manifest total must be >= 60");
  for (const [app, minimum] of Object.entries(MINIMUM_MANIFEST_COUNTS.byApp)) {
    assert.equal((appCounts.get(app) || 0) >= minimum, true, `${app} must have at least ${minimum} manifests`);
  }
});

test("target screen manifest catalog validates reusable template shapes", async () => {
  const manifests = await loadExpandedManifests(manifestRoot);

  assert.equal(manifests.some((manifest) => manifest.type === "INQUIRY"), true);
  assert.equal(manifests.some((manifest) => manifest.type === "COMMAND"), true);
  assert.equal(manifests.some((manifest) => manifest.type === "CASE"), true);
  assert.equal(manifests.some((manifest) => manifest.type === "PARAMETER"), true);

  for (const manifest of manifests) {
    if (manifest.type === "INQUIRY") {
      assert.equal(Boolean(manifest.query || manifest.resultTable), true, `${manifest.screenId} inquiry must declare query or result table`);
      assert.equal(manifest.templateContract.regions.includes("auditPanel"), true);
    }
    if (manifest.type === "COMMAND") {
      assert.equal(Boolean(manifest.api?.command || manifest.actions?.length), true, `${manifest.screenId} command must declare api.command or actions`);
      assert.equal(manifest.formContract.fields.length > 0, true, `${manifest.screenId} command must expose form fields`);
    }
    if (manifest.type === "CASE") {
      assert.equal(Boolean(manifest.workflow || manifest.actions?.length), true, `${manifest.screenId} case must declare workflow or actions`);
      assert.equal(manifest.controlMetadata.workflow.timelineRequired, true);
    }
    if (manifest.type === "PARAMETER") {
      assert.equal(Boolean(manifest.parameter?.namespace || manifest.parameter?.currentValueEndpoint || manifest.parameter?.historyEndpoint), true);
      assert.equal(manifest.controlMetadata.approval.makerChecker, true);
    }
  }
});

test("target admin manifests cover privileged platform controls without one-off screens", async () => {
  const manifests = await loadExpandedManifests(manifestRoot);
  const adminManifests = manifests.filter((manifest) => manifest.app === "admin-console");
  const parameter = adminManifests.find((manifest) => manifest.screenId === "ADM-201");
  const evidenceCoverage = adminManifests.find((manifest) => manifest.screenId === "ADM-501");

  assert.equal(adminManifests.length >= 2, true);
  assert.equal(parameter?.type, "PARAMETER");
  assert.equal(parameter?.approval?.required, true);
  assert.equal(parameter?.approval?.makerChecker, true);
  assert.equal(parameter?.audit.reasonRequired, true);
  assert.equal(parameter?.controlMetadata.approval.required, true);
  assert.equal(parameter?.controlMetadata.approval.makerChecker, true);
  assert.equal(parameter?.requiredRoles.includes("PASSKEY_RECOVERY_ADMIN"), true);
  assert.equal(evidenceCoverage?.type, "DASHBOARD");
  assert.equal(evidenceCoverage?.audit.reasonRequired, true);
  assert.equal(evidenceCoverage?.controlMetadata.audit.eventTypes.includes("ADMIN_EVIDENCE_COVERAGE_VIEW"), true);
});

test("target staff high-risk commands require maker-checker approval metadata", async () => {
  const manifests = await loadExpandedManifests(manifestRoot);
  const highRiskCommands = manifests.filter((manifest) => manifest.app === "staff-terminal" && manifest.highRisk === true);

  assert.equal(highRiskCommands.length >= 1, true);
  for (const manifest of highRiskCommands) {
    assert.equal(manifest.approval?.required, true, `${manifest.screenId} must require approval`);
    assert.equal(manifest.approval?.makerChecker, true, `${manifest.screenId} must use maker-checker`);
    assert.equal(manifest.audit.reasonRequired, true, `${manifest.screenId} must require an audit reason`);
    assert.equal(manifest.controlMetadata.approval.required, true);
    assert.equal(manifest.controlMetadata.approval.makerChecker, true);
  }
});

test("target complaint workflow manifests cover staff and customer views", async () => {
  const manifests = await loadExpandedManifests(manifestRoot);
  const byId = new Map(manifests.map((manifest) => [manifest.screenId, manifest]));

  const staffWorkflow = byId.get("CMP-201");
  const customerIntake = byId.get("CMP-101");
  const customerStatus = byId.get("CMP-102");

  assert.equal(staffWorkflow?.app, "staff-terminal");
  assert.equal(staffWorkflow?.type, "CASE");
  assert.equal(staffWorkflow?.approval?.makerChecker, true);
  assert.equal(staffWorkflow?.controlMetadata.approval.makerChecker, true);
  assert.equal(staffWorkflow?.audit.reasonRequired, true);
  assert.equal(staffWorkflow?.workflow?.states.includes("WAITING_APPROVAL"), true);
  assert.equal(staffWorkflow?.workflow?.states.includes("ANSWERED"), true);

  for (const manifest of [customerIntake, customerStatus]) {
    assert.equal(manifest?.app, "complaint-portal");
    assert.equal(manifest?.type, "CASE");
    assert.equal(manifest?.audit.selfService, true);
    assert.equal(manifest?.audit.maskingPolicy, "CUSTOMER_SELF");
    assert.equal(manifest?.controlMetadata.workflow.required, true);
    assert.equal(manifest?.sla?.targetHours, 72);
  }
});

test("target staff PII inquiries require reason and non-empty masking policy", async () => {
  const manifests = await loadExpandedManifests(manifestRoot);
  const staffPiiScreens = manifests.filter((manifest) => manifest.app === "staff-terminal" && manifest.audit.piiAccess === true);

  assert.equal(staffPiiScreens.length >= 2, true);
  for (const manifest of staffPiiScreens) {
    assert.equal(manifest.audit.reasonRequired, true, `${manifest.screenId} must require a reason`);
    if (manifest.type === "INQUIRY") {
      assert.equal((manifest.audit.eventTypes || []).length > 0, true, `${manifest.screenId} must declare audit events`);
      assert.equal(manifest.controlMetadata.audit.eventTypes.length > 0, true);
    }
    assert.notEqual(manifest.audit.maskingPolicy, "NONE", `${manifest.screenId} must mask PII by default`);
    assert.equal(manifest.controlMetadata.masking.defaultMasked, true);
    assert.notEqual(manifest.controlMetadata.masking.policy, "NONE");
  }
});

test("target staff customer search declares reason and customer search audit event", async () => {
  const manifests = await loadExpandedManifests(manifestRoot);
  const customerSearch = manifests.find((manifest) => manifest.screenId === "CST-001");
  const fields = customerSearch?.formContract.fields || [];

  assert.equal(customerSearch?.transactionCode, "CST001");
  assert.equal(customerSearch?.audit.eventTypes?.includes("CUSTOMER_SEARCH"), true);
  assert.equal(customerSearch?.controlMetadata.audit.eventTypes.includes("CUSTOMER_SEARCH"), true);
  assert.equal(fields.some((field) => field.name === "reason" && field.required === true), true);
  assert.equal(customerSearch?.formContract.reasonFieldNames.includes("reason"), true);
});

test("target staff PII inquiry validation fails without audit event declaration", async () => {
  const manifests = await loadManifests(manifestRoot);
  const customerSearch = manifests.find((manifest) => manifest.screenId === "CST-001");
  assert.ok(customerSearch);

  assert.throws(
    () => validateManifest({ ...customerSearch, audit: { ...customerSearch.audit, eventTypes: [] } }),
    /CST-001 staff PII inquiry must declare audit\.eventTypes/
  );
});

test("target schema migrations contain ledger source-of-truth and control tables", async () => {
  const foundation = await readFile(`${repositoryRoot}/db/migrations/V001__foundation.sql`, "utf8");
  const constraints = await readFile(`${repositoryRoot}/db/migrations/V002__ledger_constraints.sql`, "utf8");
  const audit = await readFile(`${repositoryRoot}/db/migrations/V003__audit_approval_workflow.sql`, "utf8");
  const outbox = await readFile(`${repositoryRoot}/db/migrations/V004__outbox_inbox.sql`, "utf8");

  assert.match(foundation, /CREATE TABLE ledger_transactions/);
  assert.match(foundation, /CREATE TABLE ledger_postings/);
  assert.match(foundation, /CREATE TABLE account_balance_projections/);
  assert.match(audit, /CREATE TABLE audit_events/);
  assert.match(audit, /CREATE TABLE operator_approvals/);
  assert.match(constraints, /ledger_transactions_no_update_delete/);
  assert.match(outbox, /CREATE TABLE outbox_events/);
  assert.match(outbox, /CREATE TABLE inbox_events/);
});
