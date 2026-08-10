import { expect, test, type Browser, type Page } from "@playwright/test";
import {
  BankingApiError,
  createBankingApiClient,
  type AccountOpeningExecuteResponse
} from "@banking-lab/api-client";
import { mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";

const customerWebBaseUrl = "http://localhost:3001";
const staffTerminalBaseUrl = "http://localhost:3002";
const callCenterBaseUrl = "http://localhost:3008";
const apiBaseUrl = process.env.BANKING_LAB_E2E_API_BASE_URL ?? "";
const notificationApiBaseUrl = process.env.BANKING_LAB_E2E_NOTIFICATION_API_BASE_URL ?? "";
const staffMakerBearerToken = process.env.BANKING_LAB_E2E_STAFF_MAKER_BEARER_TOKEN ?? "";
const staffCheckerBearerToken = process.env.BANKING_LAB_E2E_STAFF_CHECKER_BEARER_TOKEN ?? "";
const fdsMakerBearerToken = process.env.BANKING_LAB_E2E_FDS_MAKER_BEARER_TOKEN ?? "";
const repoRoot = process.env.BANKING_LAB_ROOT ?? process.cwd();
const screenshotDir = path.join(repoRoot, "docs/assets/channel-workbench");
const statePath = process.env.BANKING_LAB_CHANNEL_DEMO_STATE_PATH ?? "/tmp/banking-lab-channel-demo-state.json";

test("cross-channel held transfer completes release and block controls", async ({ browser, page }) => {
  test.skip(
    !apiBaseUrl || !notificationApiBaseUrl || !staffMakerBearerToken || !staffCheckerBearerToken || !fdsMakerBearerToken,
    "Run through npm run demo:test:channels so every disposable service and separated actor is configured."
  );
  test.setTimeout(180_000);
  mkdirSync(screenshotDir, { recursive: true });
  const startedAt = Date.now();
  const browserErrors: string[] = [];
  page.on("pageerror", (error) => browserErrors.push(error.message));

  const anonymous = createBankingApiClient({ baseUrl: apiBaseUrl, fetchImpl: correlatedFetch("ANON") });
  const primarySignup = await anonymous.signupCustomer({
    idempotencyKey: "CHANNEL-DEMO-SIGNUP-PRIMARY-001",
    username: "channel-demo-customer",
    password: "Synthetic-Channel-Demo-Pass1!",
    syntheticCustomerName: "Synthetic Channel Customer",
    syntheticPhone: "010-0000-0001",
    syntheticAddress: "Synthetic channel demo address"
  });
  const otherSignup = await anonymous.signupCustomer({
    idempotencyKey: "CHANNEL-DEMO-SIGNUP-OTHER-001",
    username: "channel-demo-other",
    password: "Synthetic-Channel-Other-Pass1!",
    syntheticCustomerName: "Synthetic Other Customer",
    syntheticPhone: "010-0000-0002",
    syntheticAddress: "Synthetic other demo address"
  });
  const primaryLogin = await anonymous.loginCustomer({
    username: "channel-demo-customer",
    password: "Synthetic-Channel-Demo-Pass1!"
  });
  const otherLogin = await anonymous.loginCustomer({
    username: "channel-demo-other",
    password: "Synthetic-Channel-Other-Pass1!"
  });
  const primaryBearer = `${primaryLogin.tokenType} ${primaryLogin.bearerToken}`;
  const otherBearer = `${otherLogin.tokenType} ${otherLogin.bearerToken}`;
  const primaryCustomer = createBankingApiClient({
    baseUrl: apiBaseUrl,
    bearerToken: primaryBearer,
    fetchImpl: correlatedFetch("CUSTOMER")
  });
  const otherCustomer = createBankingApiClient({
    baseUrl: apiBaseUrl,
    bearerToken: otherBearer,
    fetchImpl: correlatedFetch("OTHER-CUSTOMER")
  });
  const fdsMaker = createBankingApiClient({
    baseUrl: apiBaseUrl,
    bearerToken: fdsMakerBearerToken,
    fetchImpl: correlatedFetch("FDS-MAKER")
  });
  const checker = createBankingApiClient({
    baseUrl: apiBaseUrl,
    bearerToken: staffCheckerBearerToken,
    fetchImpl: correlatedFetch("CHECKER")
  });
  const notificationCustomer = createBankingApiClient({
    baseUrl: notificationApiBaseUrl,
    bearerToken: primaryBearer,
    fetchImpl: correlatedFetch("NOTIFICATION-CUSTOMER")
  });

  const source = await openSyntheticAccount({
    customerId: primarySignup.customer.customerId,
    key: "SOURCE",
    alias: "Channel demo source",
    initialDepositAmountMinor: 13_000_000
  });
  const destination = await openSyntheticAccount({
    customerId: primarySignup.customer.customerId,
    key: "DESTINATION",
    alias: "Channel demo beneficiary",
    initialDepositAmountMinor: 0
  });
  if (!source.account || !destination.account) {
    throw new Error("Synthetic account opening did not return both accounts.");
  }

  const releaseTransfer = await primaryCustomer.requestCustomerTransfer({
    customerId: primarySignup.customer.customerId,
    fromAccountId: source.account.accountId,
    toAccountId: destination.account.accountId,
    amountMinor: 5_000_000,
    idempotencyKey: "CHANNEL-DEMO-RELEASE-001",
    requestedBy: "channel-demo-customer",
    reason: "Synthetic new-beneficiary high-amount cross-channel release demo",
    firstTimeBeneficiary: true,
    newDevice: true
  });
  expect(releaseTransfer.item.status).toBe("HELD");
  expect(releaseTransfer.item.transactionId).toBeNull();
  expect(releaseTransfer.item.journeyId).toBeTruthy();
  expect(releaseTransfer.item.caseId).toBeTruthy();
  const releaseJourneyId = releaseTransfer.item.journeyId ?? "";
  const releaseCaseId = releaseTransfer.item.caseId ?? "";
  const initialTransactionCount = (await primaryCustomer.customerTransactions(
    primarySignup.customer.customerId,
    source.account.accountId
  )).items.length;

  await expectApiStatus(() => otherCustomer.customerTransfers(primarySignup.customer.customerId), 403);
  await expectApiStatus(() => otherCustomer.customerJourney(releaseJourneyId), 403);
  await expectApiStatus(
    () => otherCustomer.customerAccountDetail(source.account!.accountId, primarySignup.customer.customerId),
    403
  );

  await establishCustomerSession(page);
  const releaseResultId = releaseTransfer.item.resultId ?? releaseTransfer.item.idempotencyKey ?? "CHANNEL-DEMO-RELEASE-001";
  await page.goto(`${customerWebBaseUrl}/transfers/${encodeURIComponent(releaseResultId)}`);
  await expect(page.getByText("HELD", { exact: true }).first()).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText(releaseJourneyId).first()).toBeVisible();
  await expect(page.getByTestId("customer-journey-timeline")).toBeVisible();
  await page.screenshot({ path: path.join(screenshotDir, "customer-held-transfer.png"), fullPage: true });

  await completeCallCenterHandoff(browser, primarySignup.customer.customerId, releaseJourneyId, browserErrors);
  const releaseApprovalId = await requestReleaseInFds201(browser, releaseCaseId, releaseJourneyId, fdsMaker, browserErrors);
  const ledgerTransactionId = await approveInApr101(browser, releaseApprovalId, releaseJourneyId, browserErrors);

  await expectApiStatus(
    () => fdsMaker.requestFdsRelease(releaseCaseId, {
      actorId: "risk01",
      requestedByRole: "FDS_REVIEWER",
      reason: "Synthetic duplicate release retry must not post twice"
    }),
    409
  );
  await expect.poll(async () => {
    const statuses = await primaryCustomer.customerTransfers(primarySignup.customer.customerId);
    return statuses.items.find((item) => item.caseId === releaseCaseId)?.status;
  }, { timeout: 20_000 }).toBe("POSTED");
  expect((await primaryCustomer.customerJourney(releaseJourneyId)).status).toBe("POSTED");
  expect((await primaryCustomer.customerTransactions(
    primarySignup.customer.customerId,
    source.account.accountId
  )).items.length).toBe(initialTransactionCount + 1);

  await expect.poll(async () => {
    const deliveries = await notificationCustomer.listCustomerNotificationDeliveries(primarySignup.customer.customerId, {
      eventType: "CustomerTransferStatusChanged"
    });
    return deliveries.filter((delivery) => delivery.maskedMessage.includes(releaseJourneyId) && delivery.maskedMessage.includes("POSTED")).length;
  }, { timeout: 30_000 }).toBe(1);

  await page.goto(`${customerWebBaseUrl}/transfers/${encodeURIComponent(releaseResultId)}`);
  await expect(page.getByText("POSTED", { exact: true }).first()).toBeVisible({ timeout: 15_000 });
  await page.screenshot({ path: path.join(screenshotDir, "customer-posted-transfer.png"), fullPage: true });
  await page.goto(`${customerWebBaseUrl}/notifications`);
  await expect(page.getByTestId("customer-notification-history")).toContainText(releaseJourneyId, { timeout: 15_000 });
  await expect(page.getByTestId("customer-notification-history")).toContainText("POSTED");
  await page.screenshot({ path: path.join(screenshotDir, "customer-transfer-notification.png"), fullPage: true });

  const beforeBlockTransactionCount = (await primaryCustomer.customerTransactions(
    primarySignup.customer.customerId,
    source.account.accountId
  )).items.length;
  const blockTransfer = await primaryCustomer.requestCustomerTransfer({
    customerId: primarySignup.customer.customerId,
    fromAccountId: source.account.accountId,
    toAccountId: destination.account.accountId,
    amountMinor: 6_000_000,
    idempotencyKey: "CHANNEL-DEMO-BLOCK-001",
    requestedBy: "channel-demo-customer",
    reason: "Synthetic new-beneficiary high-amount cross-channel block demo",
    firstTimeBeneficiary: true,
    newDevice: true
  });
  expect(blockTransfer.item.status).toBe("HELD");
  const blockCaseId = blockTransfer.item.caseId ?? "";
  const blockJourneyId = blockTransfer.item.journeyId ?? "";
  await fdsMaker.assignFdsCase(blockCaseId, {
    actorId: "risk01",
    actorRole: "FDS_REVIEWER",
    owner: "risk01",
    reason: "Synthetic FDS maker block investigation"
  });
  const blockRequest = await fdsMaker.requestFdsBlock(blockCaseId, {
    actorId: "risk01",
    requestedByRole: "FDS_REVIEWER",
    reason: "Synthetic FDS maker requests block"
  });
  const blockExecution = await checker.approveStaffApproval(blockRequest.approval.approvalId, {
    approvedBy: "manager01",
    approvedByRole: "BRANCH_MANAGER",
    screenId: "APR101"
  });
  expect(blockExecution.fdsCase?.status).toBe("BLOCKED");
  expect(blockExecution.ledgerTransaction ?? null).toBeNull();
  expect((await primaryCustomer.customerJourney(blockJourneyId)).status).toBe("BLOCKED");
  expect((await primaryCustomer.customerTransactions(
    primarySignup.customer.customerId,
    source.account.accountId
  )).items.length).toBe(beforeBlockTransactionCount);
  await expect.poll(async () => {
    const deliveries = await notificationCustomer.listCustomerNotificationDeliveries(primarySignup.customer.customerId, {
      eventType: "CustomerTransferStatusChanged"
    });
    return deliveries.filter((delivery) => delivery.maskedMessage.includes(blockJourneyId) && delivery.maskedMessage.includes("BLOCKED")).length;
  }, { timeout: 30_000 }).toBe(1);

  expect(browserErrors).toEqual([]);
  writeFileSync(statePath, `${JSON.stringify({
    syntheticOnly: true,
    customerId: primarySignup.customer.customerId,
    otherCustomerId: otherSignup.customer.customerId,
    sourceAccountId: source.account.accountId,
    destinationAccountId: destination.account.accountId,
    release: {
      caseId: releaseCaseId,
      journeyId: releaseJourneyId,
      approvalId: releaseApprovalId,
      ledgerTransactionId,
      status: "POSTED"
    },
    block: {
      caseId: blockCaseId,
      journeyId: blockJourneyId,
      approvalId: blockRequest.approval.approvalId,
      status: "BLOCKED"
    },
    browserErrors,
    durationSeconds: Math.round((Date.now() - startedAt) / 1000)
  }, null, 2)}\n`);
});

async function openSyntheticAccount(input: {
  readonly customerId: string;
  readonly key: string;
  readonly alias: string;
  readonly initialDepositAmountMinor: number;
}): Promise<AccountOpeningExecuteResponse> {
  const maker = createBankingApiClient({ baseUrl: apiBaseUrl, bearerToken: staffMakerBearerToken, fetchImpl: correlatedFetch(`ACCOUNT-${input.key}`) });
  const checker = createBankingApiClient({ baseUrl: apiBaseUrl, bearerToken: staffCheckerBearerToken, fetchImpl: correlatedFetch(`ACCOUNT-CHECK-${input.key}`) });
  const request = await maker.requestStaffAccountOpening({
    requestedBy: "channel-account-maker",
    requestedByRole: "BRANCH_STAFF",
    reason: "Synthetic cross-channel demo account opening",
    idempotencyKey: `CHANNEL-DEMO-ACCOUNT-${input.key}-001`,
    customerId: input.customerId,
    productCode: "SYNTHETIC_DEPOSIT",
    accountAlias: input.alias,
    currency: "KRW",
    dailyTransferLimitMinor: 20_000_000,
    singleTransferLimitMinor: 10_000_000,
    initialDepositAmountMinor: input.initialDepositAmountMinor,
    initialDepositIdempotencyKey: input.initialDepositAmountMinor > 0 ? `CHANNEL-DEMO-DEPOSIT-${input.key}-001` : null,
    businessDate: "2026-06-07"
  });
  await checker.approveStaffAccountOpeningRequest(request.item.requestId, {
    approvedBy: "manager01",
    approvedByRole: "BRANCH_MANAGER",
    screenId: "ACC-202"
  });
  return maker.executeStaffAccountOpeningRequest(request.item.requestId, {
    executedBy: "channel-account-maker",
    executedByRole: "BRANCH_STAFF",
    reason: "Execute synthetic cross-channel demo account opening",
    idempotencyKey: `CHANNEL-DEMO-ACCOUNT-EXEC-${input.key}-001`
  });
}

async function establishCustomerSession(page: Page) {
  await page.goto(`${customerWebBaseUrl}/login`);
  await page.waitForLoadState("networkidle");
  await page.getByLabel("Username").fill("channel-demo-customer");
  await page.getByLabel("Password").fill("Synthetic-Channel-Demo-Pass1!");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page.getByText("CUSTOMER_SESSION_ACTIVE")).toBeVisible({ timeout: 15_000 });
}

async function completeCallCenterHandoff(
  browser: Browser,
  customerId: string,
  journeyId: string,
  browserErrors: string[]
) {
  const context = await browser.newContext();
  const page = await context.newPage();
  page.on("pageerror", (error) => browserErrors.push(error.message));
  try {
    await page.goto(`${callCenterBaseUrl}/workspace`);
    await page.getByRole("button", { name: "Dev/test simulated agent" }).click();
    await expect(page.getByText(/CALL_CENTER_AGENT · SIMULATED/u)).toBeVisible();
    await page.getByLabel("Business reason").fill("Customer called about synthetic held transfer status");
    await page.getByLabel("Customer query").fill(customerId);
    await page.getByLabel("Journey ID").fill(journeyId);
    await page.getByRole("button", { name: "Search masked customer" }).click();
    await expect(page.getByTestId("masked-customer-context")).toContainText(customerId, { timeout: 15_000 });
    await expect(page.getByTestId("masked-customer-context")).toContainText("010-****");
    await page.getByRole("button", { name: "Pass simulated identity check" }).click();
    await expect(page.getByText("PASSED_SIMULATED", { exact: true })).toBeVisible();
    await page.getByRole("button", { name: "Start linked interaction" }).click();
    await expect(page.getByText("Interaction opened with masked customer context.")).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole("button", { name: "Save after-call disposition" })).toBeDisabled();
    await page.getByLabel("Agent note").fill("Customer 010-1234-5678 asked why the synthetic transfer is held.");
    await page.getByRole("button", { name: "Save redacted note" }).click();
    await expect(page.getByTestId("redacted-note-proof")).toContainText("applied", { timeout: 15_000 });
    await page.getByRole("button", { name: "Request FDS handoff" }).click();
    await expect(page.getByTestId("fds-handoff-proof")).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole("button", { name: "Save after-call disposition" })).toBeEnabled();
    await page.getByRole("button", { name: "Save after-call disposition" }).click();
    await expect(page.getByTestId("aftercall-disposition-proof")).toContainText("FDS_HANDOFF_COMPLETED", { timeout: 15_000 });
    await page.getByRole("button", { name: "Close interaction" }).click();
    await expect(page.getByTestId("interaction-closed-proof")).toContainText("CLOSED", { timeout: 15_000 });
    await expect(page.getByTestId("call-center-journey")).toContainText(journeyId);
    await page.screenshot({ path: path.join(screenshotDir, "call-center-fds-handoff.png"), fullPage: true });
  } finally {
    await context.close();
  }
}

async function requestReleaseInFds201(
  browser: Browser,
  caseId: string,
  journeyId: string,
  fdsMaker: ReturnType<typeof createBankingApiClient>,
  browserErrors: string[]
): Promise<string> {
  const context = await browser.newContext();
  const page = await context.newPage();
  page.on("pageerror", (error) => browserErrors.push(error.message));
  try {
    await page.goto(staffTerminalBaseUrl);
    await page.getByRole("button", { name: "DEV FDS maker" }).click();
    await expect(page.getByTestId("staff-bff-session")).toContainText("FDS_REVIEWER · SIMULATED");
    await openTransactionCode(page, "FDS201");
    await page.getByLabel("caseId").fill(caseId);
    await page.getByRole("button", { name: "목록/상세" }).click();
    await expect(page.locator(".api-workbench")).toContainText(journeyId, { timeout: 15_000 });
    await page.getByRole("button", { name: "담당 지정" }).click();
    await expect(page.locator(".api-workbench")).toContainText("INVESTIGATING", { timeout: 15_000 });
    await page.getByRole("button", { name: "release 승인요청" }).click();
    await expect.poll(async () => {
      const selected = (await fdsMaker.fdsCases()).find((item) => item.caseId === caseId);
      return selected?.approvalId ?? "";
    }, { timeout: 15_000 }).not.toBe("");
    const selected = (await fdsMaker.fdsCases()).find((item) => item.caseId === caseId);
    expect(selected?.status).toBe("RELEASE_REQUESTED");
    expect(selected?.approvalId).toBeTruthy();
    await expect(page.locator(".api-workbench")).toContainText(selected?.approvalId ?? "");
    await page.screenshot({ path: path.join(screenshotDir, "staff-fds201-release-request.png"), fullPage: true });
    return selected?.approvalId ?? "";
  } finally {
    await context.close();
  }
}

async function approveInApr101(
  browser: Browser,
  approvalId: string,
  journeyId: string,
  browserErrors: string[]
): Promise<string> {
  const context = await browser.newContext();
  const page = await context.newPage();
  page.on("pageerror", (error) => browserErrors.push(error.message));
  try {
    await page.goto(staffTerminalBaseUrl);
    await page.getByRole("button", { name: "DEV checker" }).click();
    await expect(page.getByTestId("staff-bff-session")).toContainText("BRANCH_MANAGER · SIMULATED");
    await openTransactionCode(page, "APR101");
    await page.getByLabel("approvalId").fill(approvalId);
    await page.getByRole("button", { name: "상세" }).click();
    await expect(page.locator(".api-workbench")).toContainText("risk01", { timeout: 15_000 });
    await expect(page.locator(".api-workbench")).toContainText("manager01");
    await expect(page.locator(".api-workbench")).toContainText(journeyId);
    await page.locator(".api-workbench").getByRole("button", { name: "승인", exact: true }).click();
    await expect(page.locator(".api-workbench")).toContainText("balanced double-entry 1건", { timeout: 20_000 });
    await expect(page.locator(".api-workbench")).toContainText("POSTED");
    const transactionId = await page
      .locator(".api-kv-grid > div")
      .filter({ has: page.locator("dt", { hasText: /^ledgerTransactionId$/u }) })
      .locator("dd")
      .textContent();
    expect(transactionId).toMatch(/^TX-/u);
    await page.screenshot({ path: path.join(screenshotDir, "staff-apr101-checker-release.png"), fullPage: true });
    return transactionId ?? "";
  } finally {
    await context.close();
  }
}

async function openTransactionCode(page: Page, code: string) {
  await page.getByLabel("통합검색").fill(code);
  await page.getByLabel("통합검색").press("Enter");
  await expect(page.locator(".screen-title")).toContainText(`[${code}]`);
}

async function expectApiStatus(action: () => Promise<unknown>, status: number) {
  try {
    await action();
    throw new Error(`Expected HTTP ${status} but API request succeeded.`);
  } catch (error) {
    expect(error).toBeInstanceOf(BankingApiError);
    expect((error as BankingApiError).status).toBe(status);
  }
}

function correlatedFetch(actor: string): typeof fetch {
  let sequence = 0;
  const traceId = actorTraceId(actor);
  return async (input, init) => {
    sequence += 1;
    const headers = new Headers(init?.headers);
    headers.set("x-request-id", `REQ-CHANNEL-DEMO-${actor}-${String(sequence).padStart(3, "0")}`);
    headers.set("traceparent", `00-${traceId}-${sequence.toString(16).padStart(16, "0")}-01`);
    return fetch(input, { ...init, headers });
  };
}

function actorTraceId(actor: string): string {
  return Array.from(actor).reduce((value, character) => ((value * 31) + character.charCodeAt(0)) >>> 0, 1)
    .toString(16)
    .padStart(32, "1")
    .slice(-32);
}
