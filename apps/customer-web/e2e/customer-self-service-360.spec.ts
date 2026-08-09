import { expect, test, type Page } from "@playwright/test";
import { createBankingApiClient, type AccountOpeningExecuteResponse } from "@banking-lab/api-client";

const baseUrl = "http://localhost:3001";
const apiBaseUrl = process.env.BANKING_LAB_E2E_API_BASE_URL ?? "";
const staffMakerBearerToken = process.env.BANKING_LAB_E2E_STAFF_MAKER_BEARER_TOKEN ?? "";
const staffCheckerBearerToken = process.env.BANKING_LAB_E2E_STAFF_CHECKER_BEARER_TOKEN ?? "";

test("customer profile onboarding 360 statements and artifacts smoke when synthetic API is configured", async ({ page }) => {
  test.skip(
    !apiBaseUrl || !staffMakerBearerToken || !staffCheckerBearerToken,
    "Set BANKING_LAB_E2E_API_BASE_URL, BANKING_LAB_E2E_STAFF_MAKER_BEARER_TOKEN, and BANKING_LAB_E2E_STAFF_CHECKER_BEARER_TOKEN to run customer profile->onboarding->360->statements smoke."
  );

  const suffix = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  const username = `c360-e2e-${suffix}`;
  const password = `Synthetic-${suffix}-Pass1!`;
  const anonymousClient = createBankingApiClient({ baseUrl: apiBaseUrl });

  const signup = await anonymousClient.signupCustomer({
    idempotencyKey: `C360-E2E-SIGNUP-${suffix}`,
    username,
    password,
    syntheticCustomerName: `Synthetic C360 ${suffix}`,
    syntheticPhone: "010-2222-3333",
    syntheticAddress: "Synthetic C360 self-service address"
  });
  const login = await anonymousClient.loginCustomer({ username, password });
  const customerClient = createBankingApiClient({
    baseUrl: apiBaseUrl,
    bearerToken: `${login.tokenType} ${login.bearerToken}`
  });

  const profile = await customerClient.customerProfile();
  expect(profile.customerId).toBe(signup.customer.customerId);
  expect(profile.syntheticOnly).toBe(true);
  expect(profile.maskingPolicy).toBe("CUSTOMER_SELF");
  expect(profile.onboardingChecks.length).toBeGreaterThanOrEqual(4);
  expect(profile.duplicateCheckStatus.length).toBeGreaterThan(0);

  const selfServiceOpening = await customerClient.requestCustomerAccountOpening({
    idempotencyKey: `C360-E2E-CSAO-${suffix}`,
    productCode: "SYNTHETIC_DEPOSIT",
    accountAlias: "self-service intake",
    currency: "KRW",
    syntheticInitialDepositAmountMinor: 0,
    termsAccepted: true
  });
  expect(selfServiceOpening.item.status).toBe("CUSTOMER_SUBMITTED");
  expect(selfServiceOpening.item.generatedAccountId).toBeFalsy();
  const openingReplay = await customerClient.requestCustomerAccountOpening({
    idempotencyKey: `C360-E2E-CSAO-${suffix}`,
    productCode: "SYNTHETIC_DEPOSIT",
    accountAlias: "self-service intake",
    currency: "KRW",
    syntheticInitialDepositAmountMinor: 0,
    termsAccepted: true
  });
  expect(openingReplay.replayed).toBe(true);
  expect(openingReplay.item.requestId).toBe(selfServiceOpening.item.requestId);
  const openingRequests = await customerClient.customerAccountOpeningRequests();
  expect(openingRequests.items.some((item) => item.requestId === selfServiceOpening.item.requestId)).toBe(true);

  const source = await openSyntheticAccount({
    suffix,
    customerId: login.customer.customerId,
    alias: "Synthetic C360 Source",
    initialDepositAmountMinor: 75_000
  });
  const sourceAccount = source.account;
  if (!sourceAccount) {
    throw new Error("Account opening execute did not return created synthetic account.");
  }

  const customer360 = await customerClient.customer360();
  expect(customer360.profile.customerId).toBe(login.customer.customerId);
  expect(customer360.accountSummary.totalAccounts).toBeGreaterThanOrEqual(1);
  expect(customer360.accounts.some((account) => account.accountId === sourceAccount.accountId)).toBe(true);
  expect(customer360.availableActions.some((action) => action.actionType === "VIEW_CONSOLIDATED_STATEMENT")).toBe(true);

  const accountStatement = await customerClient.customerAccountStatement(sourceAccount.accountId, "2026-06-01", "2026-06-30");
  expect(accountStatement.statementScope).toBe("ACCOUNT");
  expect(accountStatement.accountId).toBe(sourceAccount.accountId);
  expect(accountStatement.sourceLedgerHash?.length).toBeGreaterThan(0);
  expect(accountStatement.payloadHash?.length).toBeGreaterThan(0);

  const consolidatedStatement = await customerClient.customerConsolidatedStatement("2026-06-01", "2026-06-30");
  expect(consolidatedStatement.statementScope).toBe("CONSOLIDATED");
  expect(consolidatedStatement.customerId).toBe(login.customer.customerId);

  const artifacts = await customerClient.customerStatementArtifacts();
  expect(artifacts.items.some((item) => item.statementId === accountStatement.statementId)).toBe(true);
  expect(artifacts.items.some((item) => item.statementId === consolidatedStatement.statementId)).toBe(true);

  await establishBrowserBffSession(page, username, password);
  await page.goto(`${baseUrl}/profile`);
  await expect(page.getByRole("heading", { name: "Customer Profile" }).first()).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText(profile.maskedPhone ?? "CUSTOMER_SELF").first()).toBeVisible({ timeout: 15_000 });

  await page.goto(`${baseUrl}/onboarding`);
  await expect(page.getByRole("heading", { name: "Onboarding" }).first()).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText("DUPLICATE_IDENTITY").first()).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText(selfServiceOpening.item.requestId).first()).toBeVisible({ timeout: 15_000 });

  await page.goto(`${baseUrl}/360`);
  await expect(page.getByRole("heading", { name: "Customer 360" }).first()).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText(sourceAccount.maskedAccountNo).first()).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText("VIEW_CONSOLIDATED_STATEMENT").first()).toBeVisible({ timeout: 15_000 });

  await page.goto(`${baseUrl}/accounts/${encodeURIComponent(sourceAccount.accountId)}/statement`);
  await expect(page.getByRole("heading", { name: `Account Statement ${sourceAccount.accountId}` }).first()).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText(accountStatement.statementId ?? "STMT-").first()).toBeVisible({ timeout: 15_000 });

  await page.goto(`${baseUrl}/statements`);
  await expect(page.getByRole("heading", { name: "Consolidated Statements" }).first()).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText(consolidatedStatement.statementId ?? "STMT-").first()).toBeVisible({ timeout: 15_000 });
});

async function openSyntheticAccount(input: {
  readonly suffix: string;
  readonly customerId: string;
  readonly alias: string;
  readonly initialDepositAmountMinor: number;
}): Promise<AccountOpeningExecuteResponse> {
  const makerClient = createBankingApiClient({
    baseUrl: apiBaseUrl,
    bearerToken: staffMakerBearerToken
  });
  const checkerClient = createBankingApiClient({
    baseUrl: apiBaseUrl,
    bearerToken: staffCheckerBearerToken
  });
  const request = await makerClient.requestStaffAccountOpening({
    requestedBy: "e2e-maker",
    requestedByRole: "BRANCH_STAFF",
    reason: "Synthetic E2E customer 360 account opening",
    idempotencyKey: `C360-E2E-AOR-${input.suffix}`,
    customerId: input.customerId,
    productCode: "SYNTHETIC_DEPOSIT",
    accountAlias: input.alias,
    currency: "KRW",
    dailyTransferLimitMinor: 1_000_000,
    singleTransferLimitMinor: 500_000,
    initialDepositAmountMinor: input.initialDepositAmountMinor,
    initialDepositIdempotencyKey: input.initialDepositAmountMinor > 0 ? `C360-E2E-DEP-${input.suffix}` : null,
    businessDate: "2026-06-07"
  });
  await checkerClient.approveStaffAccountOpeningRequest(request.item.requestId, {
    approvedBy: "e2e-checker",
    approvedByRole: "BRANCH_MANAGER",
    screenId: "ACC-202"
  });
  return makerClient.executeStaffAccountOpeningRequest(request.item.requestId, {
    executedBy: "e2e-maker",
    executedByRole: "BRANCH_STAFF",
    reason: "Execute approved synthetic E2E customer 360 account opening",
    idempotencyKey: `C360-E2E-AOR-EXEC-${input.suffix}`
  });
}

async function establishBrowserBffSession(page: Page, username: string, password: string) {
  await page.goto(`${baseUrl}/login`);
  await page.getByLabel("Username").fill(username);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page.getByText("CUSTOMER_SESSION_ACTIVE")).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText("HttpOnly cookie · not readable by browser JavaScript")).toBeVisible();
}
