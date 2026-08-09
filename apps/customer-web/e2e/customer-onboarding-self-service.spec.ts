import { expect, test, type Page } from "@playwright/test";
import { createBankingApiClient, type AccountOpeningExecuteResponse } from "@banking-lab/api-client";

const baseUrl = "http://localhost:3001";
const apiBaseUrl = process.env.BANKING_LAB_E2E_API_BASE_URL ?? "";
const staffMakerBearerToken = process.env.BANKING_LAB_E2E_STAFF_MAKER_BEARER_TOKEN ?? "";
const staffCheckerBearerToken = process.env.BANKING_LAB_E2E_STAFF_CHECKER_BEARER_TOKEN ?? "";

test("customer signup login accounts transfer history smoke when synthetic API is configured", async ({ page }) => {
  test.skip(
    !apiBaseUrl || !staffMakerBearerToken || !staffCheckerBearerToken,
    "Set BANKING_LAB_E2E_API_BASE_URL, BANKING_LAB_E2E_STAFF_MAKER_BEARER_TOKEN, and BANKING_LAB_E2E_STAFF_CHECKER_BEARER_TOKEN to run signup->login->accounts->transfer->history smoke."
  );

  const suffix = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  const username = `cwb-e2e-${suffix}`;
  const password = `Synthetic-${suffix}-Pass1!`;
  const anonymousClient = createBankingApiClient({ baseUrl: apiBaseUrl });

  const signup = await anonymousClient.signupCustomer({
    idempotencyKey: `CWB-E2E-SIGNUP-${suffix}`,
    username,
    password,
    syntheticCustomerName: `Synthetic E2E ${suffix}`,
    syntheticPhone: "010-0000-0000",
    syntheticAddress: "Synthetic E2E self-service address"
  });
  expect(signup.syntheticOnly).toBe(true);
  expect(signup.customer.username).toBe(username);

  const login = await anonymousClient.loginCustomer({ username, password });
  expect(login.customer.customerId).toBe(signup.customer.customerId);
  expect(login.bearerToken.length).toBeGreaterThan(0);

  const source = await openSyntheticAccount({
    suffix,
    customerId: login.customer.customerId,
    alias: "Synthetic E2E Source",
    initialDepositAmountMinor: 6_000_000
  });
  const destination = await openSyntheticAccount({
    suffix: `${suffix}-dst`,
    customerId: login.customer.customerId,
    alias: "Synthetic E2E Destination",
    initialDepositAmountMinor: 0
  });
  const sourceAccount = source.account;
  const destinationAccount = destination.account;
  if (!sourceAccount || !destinationAccount) {
    throw new Error("Account opening execute did not return created synthetic accounts.");
  }

  const customerClient = createBankingApiClient({
    baseUrl: apiBaseUrl,
    bearerToken: `${login.tokenType} ${login.bearerToken}`
  });
  const accounts = await customerClient.customerAccounts(login.customer.customerId);
  expect(accounts.syntheticOnly).toBe(true);
  expect(accounts.items.some((account) => account.accountId === sourceAccount.accountId)).toBe(true);
  expect(accounts.items.some((account) => account.accountId === destinationAccount.accountId)).toBe(true);

  const recipient = await customerClient.internalRecipientLookup(destinationAccount.accountId);
  expect(recipient.item.accountId).toBe(destinationAccount.accountId);
  expect(recipient.item.internalOnly).toBe(true);
  expect(recipient.item.syntheticOnly).toBe(true);

  const transfer = await customerClient.requestCustomerTransfer({
    customerId: login.customer.customerId,
    fromAccountId: sourceAccount.accountId,
    toAccountId: recipient.item.accountId,
    amountMinor: 1_200,
    idempotencyKey: `CWB-E2E-TRF-${suffix}`,
    requestedBy: username,
    reason: "Synthetic customer-web signup login account transfer smoke"
  });
  expect(transfer.item.status).toBe("POSTED");
  expect(transfer.replayed).toBe(false);

  const transferReplay = await customerClient.requestCustomerTransfer({
    customerId: login.customer.customerId,
    fromAccountId: sourceAccount.accountId,
    toAccountId: recipient.item.accountId,
    amountMinor: 1_200,
    idempotencyKey: `CWB-E2E-TRF-${suffix}`,
    requestedBy: username,
    reason: "Synthetic customer-web signup login account transfer smoke"
  });
  expect(transferReplay.replayed).toBe(true);
  expect(transferReplay.item.transactionId).toBe(transfer.item.transactionId);

  const history = await customerClient.customerTransactions(login.customer.customerId, sourceAccount.accountId);
  expect(history.items.some((item) => item.transactionId === transfer.item.transactionId)).toBe(true);

  const statuses = await customerClient.customerTransfers(login.customer.customerId);
  expect(statuses.items.some((item) => item.idempotencyKey === transfer.item.idempotencyKey && item.status === "POSTED")).toBe(true);

  const heldTransfer = await customerClient.requestCustomerTransfer({
    customerId: login.customer.customerId,
    fromAccountId: sourceAccount.accountId,
    toAccountId: recipient.item.accountId,
    amountMinor: 5_000_000,
    idempotencyKey: `CWB-E2E-HELD-${suffix}`,
    requestedBy: username,
    reason: "Synthetic customer-web high-risk new-beneficiary journey smoke",
    firstTimeBeneficiary: true
  });
  expect(heldTransfer.item.status).toBe("HELD");
  expect(heldTransfer.item.transactionId).toBeNull();
  expect(heldTransfer.item.journeyId).toBeTruthy();

  const heldJourney = await customerClient.customerJourney(heldTransfer.item.journeyId ?? "");
  expect(heldJourney.status).toBe("HELD");
  expect(heldJourney.statusMessage).toBe("Security review in progress");
  expect(heldJourney.references.some((reference) => reference.referenceType === "FDS_CASE")).toBe(false);

  await establishBrowserBffSession(page, username, password);
  await page.goto(`${baseUrl}/accounts`);
  await expect(page.getByText(sourceAccount.maskedAccountNo).first()).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText(destinationAccount.maskedAccountNo).first()).toBeVisible();

  await page.goto(`${baseUrl}/accounts/${encodeURIComponent(sourceAccount.accountId)}`);
  await expect(page.getByText(sourceAccount.maskedAccountNo).first()).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText(transfer.item.transactionId ?? transfer.item.idempotencyKey).first()).toBeVisible({ timeout: 15_000 });

  const resultId = transfer.item.resultId ?? transfer.item.transactionId ?? transfer.item.idempotencyKey;
  await page.goto(`${baseUrl}/transfers/${encodeURIComponent(resultId)}`);
  await expect(page.getByText("POSTED").first()).toBeVisible({ timeout: 15_000 });

  const heldResultId = heldTransfer.item.resultId ?? heldTransfer.item.idempotencyKey;
  await page.goto(`${baseUrl}/transfers/${encodeURIComponent(heldResultId)}`);
  await expect(page.getByText("HELD").first()).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText(heldTransfer.item.journeyId ?? "").first()).toBeVisible({ timeout: 15_000 });
  await expect(page.getByTestId("customer-journey-timeline")).toBeVisible({ timeout: 15_000 });
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
    reason: "Synthetic E2E customer self-service account opening",
    idempotencyKey: `CWB-E2E-AOR-${input.suffix}`,
    customerId: input.customerId,
    productCode: "SYNTHETIC_DEPOSIT",
    accountAlias: input.alias,
    currency: "KRW",
    dailyTransferLimitMinor: 1_000_000,
    singleTransferLimitMinor: 500_000,
    initialDepositAmountMinor: input.initialDepositAmountMinor,
    initialDepositIdempotencyKey: input.initialDepositAmountMinor > 0 ? `CWB-E2E-DEP-${input.suffix}` : null,
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
    reason: "Execute approved synthetic E2E account opening",
    idempotencyKey: `CWB-E2E-AOR-EXEC-${input.suffix}`
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
