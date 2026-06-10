import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("call-center workflow is API-backed and synthetic with redacted notes", async () => {
  const migration = await readFile("db/migrations/V040__call_center_workflow.sql", "utf8");
  const service = await readFile("services/core-banking/src/main/kotlin/lab/banking/core/callcenter/CallCenterService.kt", "utf8");
  const controller = await readFile("services/core-banking/src/main/kotlin/lab/banking/core/callcenter/CallCenterController.kt", "utf8");
  const openApi = await readFile("contracts/openapi/core-banking.yaml", "utf8");
  const client = await readFile("packages/api-client/src/index.ts", "utf8");
  const evidence = JSON.parse(await readFile("docs/test-evidence/generated/call-center-console.json", "utf8"));
  const nextPanel = await readFile("apps/call-center-console/src/components/ApiBackedCallCenterPanel.tsx", "utf8");
  const e2eSpec = await readFile("apps/call-center-console/e2e/call-center-console-parity.spec.ts", "utf8");

  for (const table of [
    "call_center_interactions",
    "call_center_notes",
    "call_center_aftercall_tasks",
    "call_center_escalations",
    "call_center_access_audit"
  ]) {
    assert.match(migration, new RegExp(`CREATE TABLE ${table}`));
  }
  assert.match(migration, /synthetic_only BOOLEAN NOT NULL DEFAULT TRUE/);
  assert.match(migration, /call_center_notes_synthetic_only/);

  assert.match(controller, /\/api\/staff\/call-center/);
  assert.match(service, /CALL_CENTER_NOTE_ADDED/);
  assert.match(service, /rawNoteCopiedToAudit" to false/);
  assert.match(service, /PII_PATTERNS/);
  assert.match(service, /CALL_CENTER_MANAGER/);
  assert.match(service, /CALL_CENTER_AGENT/);
  assert.doesNotMatch(service, /real payment|real KYC|external financial/i);

  for (const operationId of [
    "searchCallCenterCustomers",
    "startCallCenterInteraction",
    "callCenterInteraction",
    "addCallCenterNote",
    "createCallCenterAftercallTask",
    "escalateCallCenterInteraction",
    "closeCallCenterInteraction",
    "callCenterCustomerHistory"
  ]) {
    assert.match(openApi, new RegExp(`operationId: ${operationId}`));
    assert.match(client, new RegExp(`${operationId}\\(`));
  }
  assert.match(openApi, /x-audit-raw-payload-forbidden: true/);
  assert.equal(evidence.status, "partial");
  assert.equal(evidence.syntheticOnly, true);
  assert.equal(evidence.controls.rawNoteCopiedToAudit, false);
  assert.equal(evidence.implemented.nextShell, "@banking-lab/call-center-console");
  assert.match(nextPanel, /data-testid="api-backed-call-center-workflow"/);
  assert.match(nextPanel, /Browser CALL-103 redacted synthetic note smoke/);
  assert.match(e2eSpec, /call-center console renders masked interaction controls from manifests/);
  assert.ok(evidence.remainingLimits.every((limit) => !/No dedicated Next\.js call-center console shell/.test(limit)));
});
