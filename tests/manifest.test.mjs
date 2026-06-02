import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";
import { loadManifests } from "../packages/screen-engine/src/index.mjs";

test("screen manifests validate and cover Phase 1 app shells", async () => {
  const manifests = await loadManifests("screen-manifests");
  const apps = new Set(manifests.map((manifest) => manifest.app));

  assert.ok(apps.has("customer-web"));
  assert.ok(apps.has("staff-terminal"));
  assert.ok(apps.has("complaint-portal"));
  assert.ok(manifests.length >= 8);
});

test("high-risk staff commands require maker-checker approval", async () => {
  const manifests = await loadManifests("screen-manifests");
  const highRiskCommands = manifests.filter((manifest) => manifest.app === "staff-terminal" && manifest.highRisk === true);

  assert.ok(highRiskCommands.length >= 1);
  for (const manifest of highRiskCommands) {
    assert.equal(manifest.approval.required, true);
    assert.equal(manifest.approval.makerChecker, true);
    assert.equal(manifest.audit.reasonRequired, true);
  }
});

test("staff PII inquiry manifests require reason and masking policy", async () => {
  const manifests = await loadManifests("screen-manifests");
  const staffPiiScreens = manifests.filter((manifest) => manifest.app === "staff-terminal" && manifest.audit.piiAccess === true);

  assert.ok(staffPiiScreens.length >= 2);
  for (const manifest of staffPiiScreens) {
    assert.equal(manifest.audit.reasonRequired, true);
    assert.notEqual(manifest.audit.maskingPolicy, "NONE");
  }
});

test("foundation migration contains source-of-truth and control tables", async () => {
  const sql = await readFile("db/migrations/V001__foundation.sql", "utf8");
  const constraints = await readFile("db/migrations/V002__ledger_constraints.sql", "utf8");
  const audit = await readFile("db/migrations/V003__audit_approval_workflow.sql", "utf8");
  const outbox = await readFile("db/migrations/V004__outbox_inbox.sql", "utf8");

  assert.match(sql, /CREATE TABLE ledger_transactions/);
  assert.match(sql, /CREATE TABLE ledger_postings/);
  assert.match(sql, /CREATE TABLE account_balance_projections/);
  assert.match(audit, /CREATE TABLE audit_events/);
  assert.match(audit, /CREATE TABLE operator_approvals/);
  assert.match(constraints, /ledger_transactions_no_update_delete/);
  assert.match(outbox, /CREATE TABLE outbox_events/);
  assert.match(outbox, /CREATE TABLE inbox_events/);
});
