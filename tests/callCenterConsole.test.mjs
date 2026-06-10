import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("call-center workflow is API-backed and synthetic with redacted notes", async () => {
  const migration = await readFile("db/migrations/V040__call_center_workflow.sql", "utf8");
  const approvalMigration = await readFile("db/migrations/V041__call_center_escalation_approval.sql", "utf8");
  const service = await readFile("services/core-banking/src/main/kotlin/lab/banking/core/callcenter/CallCenterService.kt", "utf8");
  const controller = await readFile("services/core-banking/src/main/kotlin/lab/banking/core/callcenter/CallCenterController.kt", "utf8");
  const openApi = await readFile("contracts/openapi/core-banking.yaml", "utf8");
  const manifest = await readFile("screen-manifests/call-center-console/CALL-106.escalation.json", "utf8");
  const client = await readFile("packages/api-client/src/index.ts", "utf8");
  const packageJson = JSON.parse(await readFile("package.json", "utf8"));
  const liveComposeScript = await readFile("scripts/run-call-center-keycloak-e2e-compose-smoke.sh", "utf8");
  const evidence = JSON.parse(await readFile("docs/test-evidence/generated/call-center-console.json", "utf8"));
  const nextPanel = await readFile("apps/call-center-console/src/components/ApiBackedCallCenterPanel.tsx", "utf8");
  const tokenRoute = await readFile("apps/call-center-console/src/app/api/auth/keycloak-token/route.ts", "utf8");
  const e2eSpec = await readFile("apps/call-center-console/e2e/call-center-console-parity.spec.ts", "utf8");
  const keycloakRealm = await readFile("infra/keycloak/realm-banking-lab.json", "utf8");

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
  assert.match(approvalMigration, /approval_id TEXT REFERENCES operator_approvals/);
  assert.match(approvalMigration, /PENDING_APPROVAL/);
  assert.match(approvalMigration, /idx_call_center_escalations_approval_id/);

  assert.match(controller, /\/api\/staff\/call-center/);
  assert.match(service, /CALL_CENTER_NOTE_ADDED/);
  assert.match(service, /CALL_CENTER_ESCALATION_REQUESTED/);
  assert.match(service, /ApprovalBusinessTypes\.CALL_CENTER_ESCALATION/);
  assert.match(service, /applyApprovedEscalation/);
  assert.match(service, /rawComplaintCopiedToAudit" to false/);
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
  assert.match(openApi, /x-maker-checker-required: true/);
  assert.match(openApi, /x-approval-business-type: CALL_CENTER_ESCALATION/);
  assert.match(manifest, /"required": true/);
  assert.match(manifest, /"makerChecker": true/);
  assert.match(manifest, /"CALL_CENTER_ESCALATION"/);
  assert.match(manifest, /"PENDING_APPROVAL"/);
  assert.equal(evidence.status, "partial");
  assert.equal(evidence.syntheticOnly, true);
  assert.equal(evidence.controls.rawNoteCopiedToAudit, false);
  assert.equal(evidence.controls.escalationMakerChecker, true);
  assert.equal(evidence.implemented.nextShell, "@banking-lab/call-center-console");
  assert.equal(evidence.implemented.keycloakClient, "call-center-console");
  assert.deepEqual(evidence.implemented.keycloakUsers, ["call-agent01", "call-manager01"]);
  assert.equal(evidence.implemented.keycloakTokenRoute, "apps/call-center-console/src/app/api/auth/keycloak-token/route.ts");
  assert.ok(evidence.verification.some((entry) => entry.command === "npm run test:call-center-console:keycloak-e2e-compose" && entry.status === "pass_after_sandbox_escalation"));
  assert.ok(evidence.verification.some((entry) => entry.command === "scripts/run-core-banking-tests.sh :services:core-banking:integrationTest --tests lab.banking.core.callcenter.CallCenterWorkflowIntegrationTest" && /maker-checker/.test(entry.evidence ?? "")));
  assert.ok(evidence.remainingLimits.every((limit) => !/Escalation is role-gated but not maker-checker|decide whether call-center escalation should become maker-checker/i.test(limit)));
  assert.ok(evidence.remainingLimits.every((limit) => !/No live full-stack|no live Keycloak\/JWKS/i.test(limit)));
  assert.match(nextPanel, /NEXT_PUBLIC_BANKING_KEYCLOAK_BASE_URL/);
  assert.match(nextPanel, /data-testid="api-backed-call-center-workflow"/);
  assert.match(nextPanel, /data-testid="api-backed-call-center-keycloak-login"/);
  assert.match(nextPanel, /createOidcAuthorizationUrl/);
  assert.match(nextPanel, /createPkcePair/);
  assert.match(nextPanel, /keycloakAgentStorageKey/);
  assert.match(nextPanel, /expiresAtEpochMillis/);
  assert.match(nextPanel, /readStoredKeycloakLoginState\("agent"\)/);
  assert.match(nextPanel, /storeKeycloakLoginState\(intent, loadedState\)/);
  assert.match(nextPanel, /Browser CALL-103 redacted synthetic note smoke/);
  assert.match(nextPanel, /approveStaffApproval/);
  assert.match(nextPanel, /maker-checker approval id/);
  assert.match(tokenRoute, /client_id: "call-center-console"/);
  assert.match(tokenRoute, /AUTHORIZATION_POLICY_VIOLATION/);
  assert.match(keycloakRealm, /"clientId": "call-center-console"/);
  assert.match(keycloakRealm, /"username": "call-agent01"/);
  assert.match(keycloakRealm, /"username": "call-manager01"/);
  assert.match(e2eSpec, /call-center console renders masked interaction controls from manifests/);
  assert.match(e2eSpec, /call-center console propagates live Keycloak agent and manager tokens when configured/);
  assert.match(e2eSpec, /BANKING_LAB_E2E_KEYCLOAK_BASE_URL/);
  assert.equal(packageJson.scripts["test:call-center-console:keycloak-e2e-compose"], "bash scripts/run-call-center-keycloak-e2e-compose-smoke.sh");
  assert.match(liveComposeScript, /BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=false/);
  assert.match(liveComposeScript, /BANKING_LAB_SECURITY_STEP_UP_ENFORCEMENT_ENABLED=false/);
  assert.match(liveComposeScript, /docker compose --profile platform up -d --build postgres keycloak core-banking/);
  assert.match(liveComposeScript, /call-agent01/);
  assert.match(liveComposeScript, /call-manager01/);
  assert.match(liveComposeScript, /call-center console propagates live Keycloak/);
  assert.ok(evidence.remainingLimits.every((limit) => !/No dedicated Next\.js call-center console shell/.test(limit)));
});
