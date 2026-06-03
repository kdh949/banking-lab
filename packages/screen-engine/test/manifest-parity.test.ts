import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { loadExpandedManifests, loadManifests } from "../src/manifest.ts";

const manifestRoot = "../../screen-manifests";
const repositoryRoot = "../..";

test("target screen manifests validate and cover channel shells", async () => {
  const manifests = await loadManifests(manifestRoot);
  const apps = new Set(manifests.map((manifest) => manifest.app));

  for (const app of ["customer-web", "staff-terminal", "complaint-portal", "ops-console", "audit-console", "fds-aml-console"]) {
    assert.equal(apps.has(app), true, `${app} must have target manifest coverage`);
  }
  assert.equal(manifests.length >= 26, true);
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
    assert.notEqual(manifest.audit.maskingPolicy, "NONE", `${manifest.screenId} must mask PII by default`);
    assert.equal(manifest.controlMetadata.masking.defaultMasked, true);
    assert.notEqual(manifest.controlMetadata.masking.policy, "NONE");
  }
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
