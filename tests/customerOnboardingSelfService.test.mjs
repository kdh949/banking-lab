import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

async function read(path) {
  return readFile(path, "utf8");
}

test("Phase 1 synthetic customer onboarding contract and client are wired without staff-terminal manifests", async () => {
  const [contract, client] = await Promise.all([
    read("contracts/openapi/core-banking.yaml"),
    read("packages/api-client/src/index.ts")
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

test("Phase 2 synthetic account opening contract and client are wired without staff-terminal manifests", async () => {
  const [contract, client] = await Promise.all([
    read("contracts/openapi/core-banking.yaml"),
    read("packages/api-client/src/index.ts")
  ]);

  for (const operationId of [
    "requestStaffAccountOpening",
    "staffAccountOpeningRequest",
    "approveStaffAccountOpeningRequest",
    "rejectStaffAccountOpeningRequest",
    "executeStaffAccountOpeningRequest"
  ]) {
    assert.match(contract, new RegExp(`operationId: ${operationId}`));
    assert.match(client, new RegExp(`${operationId}\\(`));
  }

  assert.match(contract, /\/api\/staff\/accounts\/opening-requests/);
  assert.match(contract, /x-synthetic-only: true/);
  assert.match(contract, /x-maker-checker-required: true/);
  assert.match(client, /interface AccountOpeningRequestCommand/);
  assert.match(client, /interface AccountOpeningExecuteResponse/);
});

test("Phase 2 synthetic account opening persistence and service preserve ledger boundaries", async () => {
  const [migration, service, controller] = await Promise.all([
    read("db/migrations/V038__synthetic_account_opening.sql"),
    read("services/core-banking/src/main/kotlin/lab/banking/core/account/AccountOpeningService.kt"),
    read("services/core-banking/src/main/kotlin/lab/banking/core/account/AccountOpeningController.kt")
  ]);

  assert.match(migration, /CREATE TABLE account_opening_requests/);
  assert.match(migration, /CREATE SEQUENCE IF NOT EXISTS synthetic_account_opening_seq/);
  assert.match(migration, /generated_account_no TEXT UNIQUE/);
  assert.match(migration, /initial_deposit_ledger_transaction_id TEXT UNIQUE REFERENCES ledger_transactions/);
  assert.match(migration, /synthetic_only BOOLEAN NOT NULL DEFAULT true/);
  assert.match(migration, /realPaymentNetworkCalled/);

  assert.match(service, /ApprovalBusinessTypes\.ACCOUNT_OPENING/);
  assert.match(service, /ledgerCommandService\.deposit/);
  assert.match(service, /INSERT INTO account_balance_projections/);
  assert.match(service, /ledger_balance_minor, available_balance_minor, hold_amount_minor/);
  assert.match(service, /ACCOUNT_OPENING_EXECUTED/);
  assert.match(service, /realPaymentNetworkCalled" to false/);
  assert.doesNotMatch(service, /UPDATE account_balance_projections[\s\S]*ledger_balance_minor = 25000/);

  assert.match(controller, /\/api\/staff\/accounts\/opening-requests/);
  assert.match(controller, /hasAnyRole\('BRANCH_STAFF','BRANCH_MANAGER','COMPLIANCE_MANAGER'\)/);
  assert.match(controller, /hasAnyRole\('BRANCH_MANAGER','COMPLIANCE_MANAGER'\)/);
});

test("Phase 3 synthetic customer signup and login contract and client are wired", async () => {
  const [contract, client] = await Promise.all([
    read("contracts/openapi/core-banking.yaml"),
    read("packages/api-client/src/index.ts")
  ]);

  for (const operationId of ["signupCustomer", "loginCustomer"]) {
    assert.match(contract, new RegExp(`operationId: ${operationId}`));
    assert.match(client, new RegExp(`${operationId}\\(`));
  }

  assert.match(contract, /\/api\/auth\/customer\/signup/);
  assert.match(contract, /\/api\/auth\/customer\/login/);
  assert.match(contract, /x-synthetic-auth-dev-test-only: true/);
  assert.match(contract, /x-idempotency-policy: body\.idempotencyKey/);
  assert.match(client, /interface CustomerSignupCommand/);
  assert.match(client, /interface CustomerLoginCommand/);
  assert.match(client, /interface CustomerAuthResponse/);
  assert.match(client, /bearerToken: string/);
});

test("Phase 3 synthetic customer auth implementation is guarded hashed and unauthenticated", async () => {
  const [migration, service, issuer, controller, securityConfig, authFilter, guard] = await Promise.all([
    read("db/migrations/V039__synthetic_customer_signup_auth.sql"),
    read("services/core-banking/src/main/kotlin/lab/banking/core/auth/CustomerAuthService.kt"),
    read("services/core-banking/src/main/kotlin/lab/banking/core/auth/SyntheticCustomerAuthTokenIssuer.kt"),
    read("services/core-banking/src/main/kotlin/lab/banking/core/auth/CustomerAuthController.kt"),
    read("services/core-banking/src/main/kotlin/lab/banking/core/security/SecurityConfig.kt"),
    read("services/core-banking/src/main/kotlin/lab/banking/core/security/BankingLabAuthorizationFilter.kt"),
    read("services/core-banking/src/main/kotlin/lab/banking/core/security/BankingLabSimulatorTokenProfileGuard.kt")
  ]);

  assert.match(migration, /CREATE SEQUENCE synthetic_customer_signup_customer_seq/);
  assert.match(migration, /signup_idempotency_key TEXT UNIQUE/);
  assert.match(migration, /signup_command_hash TEXT/);

  assert.match(service, /passwordEncoder\.encode/);
  assert.match(service, /passwordEncoder\.matches/);
  assert.match(service, /CUSTOMER_SIGNUP_SUCCEEDED/);
  assert.match(service, /CUSTOMER_LOGIN_SUCCEEDED/);
  assert.match(service, /CUSTOMER_LOGIN_FAILED/);
  assert.match(service, /passwordStoredPlaintext" to false/);
  assert.match(service, /keycloakAdminApiCalled" to false/);
  assert.match(service, /realKycProviderCalled" to false/);
  assert.match(service, /requireSameSignupCommandHash/);
  assert.doesNotMatch(service, /password_hash = :password/);

  assert.match(issuer, /customer-auth\.synthetic-token-issuer-enabled:false/);
  assert.match(issuer, /simulatorTokensEnabled && devSimulatorTokenEnabled/);
  assert.match(issuer, /"roles" to listOf\("CUSTOMER"\)/);
  assert.match(issuer, /"customerId" to customerId/);
  assert.match(issuer, /"realKeycloakToken" to false/);

  assert.match(controller, /\/api\/auth\/customer/);
  assert.match(controller, /@PostMapping\("\/signup"\)/);
  assert.match(controller, /@PostMapping\("\/login"\)/);
  assert.match(securityConfig, /requestMatchers\(HttpMethod\.POST, "\/api\/auth\/customer\/signup", "\/api\/auth\/customer\/login"\)\.permitAll/);
  assert.match(authFilter, /path == "\/api\/auth\/customer\/signup"/);
  assert.match(authFilter, /path == "\/api\/auth\/customer\/login"/);
  assert.match(guard, /synthetic customer auth token issuance is dev\/test only/);
});

test("Phase 4 customer account list recipient lookup and form routes are wired", async () => {
  const [contract, client, accountService, transferService, selfService, signupPage, loginPage, accountsPage, accountDetailPage, transferPage, resultPage] = await Promise.all([
    read("contracts/openapi/core-banking.yaml"),
    read("packages/api-client/src/index.ts"),
    read("services/core-banking/src/main/kotlin/lab/banking/core/customer/CustomerAccountService.kt"),
    read("services/core-banking/src/main/kotlin/lab/banking/core/customer/CustomerTransferService.kt"),
    read("apps/customer-web/src/components/CustomerSelfService.tsx"),
    read("apps/customer-web/src/app/signup/page.tsx"),
    read("apps/customer-web/src/app/login/page.tsx"),
    read("apps/customer-web/src/app/accounts/page.tsx"),
    read("apps/customer-web/src/app/accounts/[accountId]/page.tsx"),
    read("apps/customer-web/src/app/transfers/new/page.tsx"),
    read("apps/customer-web/src/app/transfers/[resultId]/page.tsx")
  ]);

  for (const operationId of ["customerAccounts", "internalRecipientLookup"]) {
    assert.match(contract, new RegExp(`operationId: ${operationId}`));
    assert.match(client, new RegExp(`${operationId}\\(`));
  }
  assert.match(contract, /\/api\/customer\/accounts/);
  assert.match(contract, /\/api\/customer\/recipients\/internal-account-lookup/);
  assert.match(contract, /x-internal-recipient-only: true/);
  assert.match(client, /interface CustomerAccountListResponse/);
  assert.match(client, /interface InternalRecipientLookupResponse/);

  assert.match(accountService, /ACCOUNT_LIST_VIEW/);
  assert.match(accountService, /INTERNAL_RECIPIENT_LOOKUP/);
  assert.match(accountService, /synthetic_system_account = FALSE/);
  assert.match(accountService, /maskAccountNo/);
  assert.doesNotMatch(accountService, /"accountNo" to/);
  assert.match(transferService, /ensureActiveInternalRecipient/);
  assert.match(transferService, /recipient must be an active internal synthetic account/);

  assert.match(signupPage, /CustomerSignupForm/);
  assert.match(loginPage, /CustomerLoginForm/);
  assert.match(accountsPage, /CustomerAccountsView/);
  assert.match(accountDetailPage, /CustomerAccountDetailView/);
  assert.match(transferPage, /CustomerTransferForm/);
  assert.match(resultPage, /CustomerTransferResultView/);
  assert.match(selfService, /NEXT_PUBLIC_BANKING_API_BASE_URL/);
  assert.match(selfService, /localStorage/);
  assert.match(selfService, /DEMO_FALLBACK_API_NOT_CONFIGURED/);
  assert.match(selfService, /CUSTOMER_SESSION_REQUIRED/);
  assert.match(selfService, /RECIPIENT_LOOKUP_REQUIRED/);
  assert.doesNotMatch(selfService, /SYN-CUS-001/);
  assert.doesNotMatch(selfService, /ACC-SYN-001-001/);
});

test("Phase 5 customer self-service manifests smoke and evidence are wired", async () => {
  const [signupManifestText, loginManifestText, accountManifestText, transferManifestText, smoke, evidence, matrix] = await Promise.all([
    read("screen-manifests/customer-web/CWB-001.customer-signup.json"),
    read("screen-manifests/customer-web/CWB-002.customer-login.json"),
    read("screen-manifests/customer-web/CWB-101.account-overview.json"),
    read("screen-manifests/customer-web/CWB-201.internal-transfer.json"),
    read("apps/customer-web/e2e/customer-onboarding-self-service.spec.ts"),
    read("docs/test-evidence/customer-onboarding-self-service.md"),
    read("docs/implementation-coverage-matrix.md")
  ]);

  const signupManifest = JSON.parse(signupManifestText);
  const loginManifest = JSON.parse(loginManifestText);
  const accountManifest = JSON.parse(accountManifestText);
  const transferManifest = JSON.parse(transferManifestText);

  assert.equal(signupManifest.screenId, "CWB-001");
  assert.equal(signupManifest.api.clientMethod, "signupCustomer");
  assert.equal(signupManifest.api.idempotencyPolicy, "body.idempotencyKey");
  assert.equal(signupManifest.api.syntheticAuthDevTestOnly, true);
  assert.equal(signupManifest.audit.selfService, true);
  assert.equal(signupManifest.fields.some((field) => field.name === "password" && field.mask === "SECRET"), true);
  assert.equal(loginManifest.screenId, "CWB-002");
  assert.equal(loginManifest.api.clientMethod, "loginCustomer");
  assert.equal(loginManifest.audit.eventTypes.includes("CUSTOMER_LOGIN_FAILED"), true);
  assert.equal(accountManifest.api.clientMethod, "customerAccounts");
  assert.equal(accountManifest.api.ownershipEnforced, true);
  assert.equal(accountManifest.audit.eventTypes.includes("ACCOUNT_LIST_VIEW"), true);
  assert.equal(transferManifest.api.lookupClientMethod, "internalRecipientLookup");
  assert.equal(transferManifest.api.clientMethod, "requestCustomerTransfer");
  assert.equal(transferManifest.api.internalRecipientOnly, true);
  assert.equal(transferManifest.api.idempotencyPolicy, "body.idempotencyKey");

  for (const token of [
    "BANKING_LAB_E2E_API_BASE_URL",
    "BANKING_LAB_E2E_STAFF_MAKER_BEARER_TOKEN",
    "BANKING_LAB_E2E_STAFF_CHECKER_BEARER_TOKEN",
    "signupCustomer",
    "loginCustomer",
    "requestStaffAccountOpening",
    "approveStaffAccountOpeningRequest",
    "executeStaffAccountOpeningRequest",
    "customerAccounts",
    "internalRecipientLookup",
    "requestCustomerTransfer",
    "customerTransactions",
    "customerTransfers",
    "signup->login->accounts->transfer->history"
  ]) {
    assert.match(smoke, new RegExp(token.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  }

  assert.match(evidence, /Customer Onboarding Self-Service Evidence/);
  assert.match(evidence, /synthetic-only/);
  assert.match(evidence, /npm run k8s:validate/);
  assert.match(evidence, /skipped_no_cluster/);
  assert.match(evidence, /BANKING_LAB_E2E_STAFF_MAKER_BEARER_TOKEN/);
  assert.match(evidence, /spending[- ]limit/);

  assert.match(matrix, /Synthetic signup\/login: `CWB-001`, `CWB-002`/);
  assert.match(matrix, /Synthetic customer onboarding: `CST-201`, `CST-202`/);
  assert.match(matrix, /Synthetic account opening: `ACC-201`, `ACC-202`/);
  assert.match(matrix, /docs\/test-evidence\/customer-onboarding-self-service\.md/);
});
