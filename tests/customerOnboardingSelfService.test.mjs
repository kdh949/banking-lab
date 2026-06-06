import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

async function read(path) {
  return readFile(path, "utf8");
}

test("Phase 1 synthetic customer onboarding contract, client, and manifests are wired", async () => {
  const [contract, client, requestManifest, approvalManifest] = await Promise.all([
    read("contracts/openapi/core-banking.yaml"),
    read("packages/api-client/src/index.ts"),
    read("screen-manifests/staff-terminal/CST-201.customer-onboarding-request.json"),
    read("screen-manifests/staff-terminal/CST-202.customer-onboarding-approval.json")
  ]);

  for (const operationId of [
    "requestStaffCustomerOnboarding",
    "staffCustomerOnboardingRequest",
    "approveStaffCustomerOnboardingRequest",
    "rejectStaffCustomerOnboardingRequest",
    "executeStaffCustomerOnboardingRequest"
  ]) {
    assert.match(contract, new RegExp(`operationId: ${operationId}`));
    assert.match(client, new RegExp(`${operationId}\\(`));
  }

  assert.match(contract, /\/api\/staff\/customers\/onboarding-requests/);
  assert.match(contract, /x-synthetic-only: true/);
  assert.match(contract, /x-maker-checker-required: true/);
  assert.match(client, /interface CustomerOnboardingRequestCommand/);
  assert.match(client, /interface CustomerOnboardingExecuteResponse/);

  const request = JSON.parse(requestManifest);
  const approval = JSON.parse(approvalManifest);
  assert.equal(request.screenId, "CST-201");
  assert.equal(request.approval.businessTypes.includes("CUSTOMER_ONBOARDING"), true);
  assert.equal(request.approval.makerChecker, true);
  assert.equal(request.audit.reasonRequired, true);
  assert.equal(request.fields.some((field) => field.name === "temporaryPassword" && field.type === "password"), true);
  assert.equal(approval.screenId, "CST-202");
  assert.equal(approval.workflow.states.includes("EXECUTED"), true);
  assert.equal(approval.postActions.includes("createSyntheticAuthIdentity"), true);
});

test("Phase 1 synthetic customer onboarding persistence and controller enforce controls", async () => {
  const [migration, service, controller] = await Promise.all([
    read("db/migrations/V037__synthetic_customer_onboarding.sql"),
    read("services/core-banking/src/main/kotlin/lab/banking/core/onboarding/CustomerOnboardingService.kt"),
    read("services/core-banking/src/main/kotlin/lab/banking/core/onboarding/CustomerOnboardingController.kt")
  ]);

  assert.match(migration, /CREATE TABLE customer_onboarding_requests/);
  assert.match(migration, /CREATE TABLE customer_auth_identities/);
  assert.match(migration, /requested_password_hash TEXT NOT NULL/);
  assert.match(migration, /synthetic_only BOOLEAN NOT NULL DEFAULT true/);
  assert.match(migration, /ux_customer_onboarding_active_username_lower/);
  assert.doesNotMatch(migration, /plaintext/i);

  assert.match(service, /passwordEncoder\.encode/);
  assert.match(service, /ApprovalBusinessTypes\.CUSTOMER_ONBOARDING/);
  assert.match(service, /requireUsernameAvailable/);
  assert.match(service, /CUSTOMER_ONBOARDING_EXECUTED/);
  assert.match(service, /keycloakAdminApiCalled" to false/);
  assert.match(service, /realKycProviderCalled" to false/);
  assert.match(service, /val passwordHash = request\.requestedPasswordHash/);
  assert.match(service, /"passwordHash" to passwordHash/);

  assert.match(controller, /\/api\/staff\/customers\/onboarding-requests/);
  assert.match(controller, /hasAnyRole\('BRANCH_STAFF','BRANCH_MANAGER','COMPLIANCE_MANAGER'\)/);
  assert.match(controller, /hasAnyRole\('BRANCH_MANAGER','COMPLIANCE_MANAGER'\)/);
});
