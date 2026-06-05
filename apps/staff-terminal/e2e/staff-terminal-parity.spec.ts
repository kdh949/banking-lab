import { expect, test } from "@playwright/test";
import type { APIRequestContext, Page } from "@playwright/test";
import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createSimulatorBearerToken } from "@banking-lab/auth-client";

const specDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = process.env.BANKING_LAB_ROOT || path.resolve(specDir, "../../..");
const app = "staff-terminal";
const baseUrl = "http://localhost:3002";
const apiBaseUrl = process.env.BANKING_LAB_E2E_API_BASE_URL ?? "";
const paymentApiBaseUrl = process.env.BANKING_LAB_E2E_PAYMENT_API_BASE_URL ?? "";
const keycloakBaseUrl = process.env.BANKING_LAB_E2E_KEYCLOAK_BASE_URL ?? "";

function manifests() {
  const dir = path.join(repoRoot, "screen-manifests", app);
  return readdirSync(dir)
    .filter((fileName) => fileName.endsWith(".json"))
    .sort()
    .map((fileName) => JSON.parse(readFileSync(path.join(dir, fileName), "utf8")));
}

async function signInWithKeycloak(page: Page, username: string, password: string) {
  const restartLogin = page.getByRole("button", { name: "Restart login" });
  if (await restartLogin.isVisible().catch(() => false)) {
    await restartLogin.click();
  }
  await page.locator("input[name='username']").fill(username);
  await page.locator("input[name='password']").fill(password);
  await page.getByRole("button", { name: "Sign In" }).click();
}

async function createSyntheticCustomerChangeApproval(request: APIRequestContext) {
  const phoneSuffix = String(Date.now()).slice(-4).padStart(4, "0");
  const response = await request.post(`${apiBaseUrl}/api/staff/customers/SYN-CUS-CMD-001/change-requests`, {
    headers: {
      Authorization: createSimulatorBearerToken({
        subject: "branch01",
        roles: ["BRANCH_STAFF"]
      })
    },
    data: {
      requestedBy: "branch01",
      requestedByRole: "BRANCH_STAFF",
      reason: "APR001 manifest approval inbox smoke",
      afterSnapshot: {
        phone: `010-0000-${phoneSuffix}`,
        address: "Seoul Synthetic APR001 Updated"
      }
    }
  });
  expect(response.status()).toBe(201);
  return await response.json() as { item: { approvalId: string } };
}

test("staff terminal renders reason, masking, and maker-checker controls from manifests", async ({ page }) => {
  const screens = manifests();
  const reasonRequired = screens.filter((screen) => screen.audit.reasonRequired).length;
  const makerChecker = screens.filter((screen) => screen.approval?.required).length;

  await page.goto(baseUrl);

  await expect(page.getByText("INZENT Banking")).toBeVisible();
  await expect(page.getByText("[WRK001] Integrated Workstation Dashboard")).toBeVisible();
  await expect(page.getByRole("button", { name: "업무포털" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Integrated Workstation Dashboard" })).toBeVisible();
  await expect(page.getByText("Transaction Code Launcher")).toBeVisible();
  await expect(page.getByLabel("Transaction code search")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Transaction-code workspace" })).toBeAttached();
  await expect(page.locator(".metric", { hasText: String(reasonRequired) })).toBeAttached();
  await expect(page.locator(".metric-label", { hasText: "reason-required" })).toBeAttached();
  await expect(page.locator(".metric", { hasText: String(makerChecker) })).toBeAttached();
  await expect(page.getByText("maker-checker").first()).toBeAttached();
  await expect(page.getByText("Masked by default")).toBeAttached();
  await expect(page.getByText("Business reason required")).toBeAttached();
  await expect(page.getByText("APR-001 declared")).toBeAttached();
  await expect(page.getByLabel("Role-aware menu")).toBeVisible();
  await expect(page.getByText("Catalog Counts")).toBeVisible();
  await expect(page.getByTestId("api-backed-staff-customer")).toBeAttached();
  await expect(page.getByTestId("api-backed-operational-retry-queue")).toBeAttached();
});

test("staff terminal opens transaction codes into tabs and switches active business screens", async ({ page }) => {
  await page.goto(baseUrl);

  await page.getByLabel("Transaction code search").fill("CST001");
  await page.getByRole("button", { name: "Open", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Customer Integrated Search" })).toBeVisible();
  await expect(page.getByText("[CST001] Customer Integrated Search")).toBeVisible();

  await page.getByLabel("Transaction code search").fill("CMP202");
  await page.getByRole("button", { name: "Open", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Complaint Answer Approval" })).toBeVisible();
  await expect(page.getByText("[CMP202] Complaint Answer Approval")).toBeVisible();
  await expect(page.getByText("Case Status")).toBeVisible();
  await expect(page.getByText("Case Handling Reason")).toBeVisible();
  await expect(page.getByText("Business reason required")).toBeVisible();
  await expect(page.getByText("Structured Error Surface")).toBeVisible();
  await expect(page.getByText("WAITING_APPROVAL")).toBeVisible();

  await page.getByRole("button", { name: /\[CST001\] Customer Integrated Search/ }).click();
  await expect(page.getByRole("heading", { name: "Customer Integrated Search" })).toBeVisible();
  await expect(page.getByText("Search Conditions")).toBeVisible();

  await page.getByLabel("Transaction code search").fill("WRK002");
  await page.getByRole("button", { name: "Open", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Operational Retry Queue" })).toBeVisible();
  await expect(page.getByText("[WRK002] Operational Retry Queue")).toBeVisible();
  await expect(page.locator(".manifest-endpoint", { hasText: "GET /api/staff/operations/retry-queue" })).toBeVisible();
  await expect(page.getByLabel("Lookup Reason")).toBeVisible();

  await page.getByLabel("Transaction code search").fill("WRK003");
  await page.getByRole("button", { name: "Open", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Workflow Timeline" })).toBeVisible();
  await expect(page.getByText("[WRK003] Workflow Timeline")).toBeVisible();
  await expect(page.locator(".manifest-endpoint", { hasText: "GET /api/staff/workflows/{businessReferenceId}/timeline" })).toBeVisible();
  await expect(page.getByTestId("manifest-workflow-timeline-api-panel")).toBeAttached();
});

test("staff terminal renders CST-001 through the inquiry manifest renderer", async ({ page }) => {
  await page.goto(`${baseUrl}/?screen=CST-001`);

  const workArea = page.locator(".manifest-work-area");
  const titleMeta = page.locator(".manifest-title-meta");
  const rail = page.locator(".right-rail");

  await expect(page.getByText("INZENT Banking")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Customer Integrated Search" })).toBeVisible();
  await expect(page.getByText("CST-001")).toBeVisible();
  await expect(titleMeta.getByText("CST001", { exact: true })).toBeVisible();
  await expect(workArea.locator(".manifest-search-panel")).toContainText("Search Conditions");
  await expect(page.getByLabel("Customer Name")).toBeVisible();
  await expect(page.getByLabel("Phone")).toBeVisible();
  await expect(page.getByLabel("Customer ID")).toBeVisible();
  await expect(page.getByText("Business reason required")).toBeVisible();
  await expect(page.getByLabel("Lookup Reason")).toBeVisible();
  await expect(workArea.locator(".manifest-result-panel")).toContainText("Result Table");
  await expect(page.getByText("maskedName")).toBeVisible();
  await expect(page.getByText("K** D***").first()).toBeVisible();
  await expect(page.locator(".manifest-audit-panel")).toContainText("Audit Timeline");
  await expect(page.getByText("CUSTOMER_SEARCH")).toBeVisible();
  await expect(rail).toContainText("Masking State");
  await expect(page.getByText("CUSTOMER_PII").first()).toBeVisible();
  await expect(page.getByText("Structured Error Surface")).toBeVisible();
  await expect(page.getByText("POLICY_REASON_REQUIRED")).toBeVisible();
  await expect(page.getByText("API-backed via @banking-lab/api-client").first()).toBeVisible();
});

test("staff terminal renders command and maker-checker approval screens without fake success", async ({ page }) => {
  await page.goto(`${baseUrl}/?screen=CST-103`);

  await expect(page.getByRole("heading", { name: "Customer Information Change Request" })).toBeVisible();
  await expect(page.getByText("Command Form")).toBeVisible();
  await expect(page.getByText("Before After Snapshot")).toBeVisible();
  await expect(page.getByText("Maker Checker Approval").first()).toBeVisible();
  await expect(page.getByText("maker-checker enforced").first()).toBeVisible();
  await expect(page.getByText("API-backed via @banking-lab/api-client").first()).toBeVisible();

  await page.getByLabel("Transaction code search").fill("FEE102");
  await page.getByRole("button", { name: "Open", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Fee Waiver Request" })).toBeVisible();
  await expect(page.getByText("API-backed via @banking-lab/api-client").first()).toBeVisible();
  await expect(page.getByTestId("manifest-fee-waiver-api-panel")).toBeAttached();

  await page.getByLabel("Transaction code search").fill("LED103");
  await page.getByRole("button", { name: "Open", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Transaction Correction Request" })).toBeVisible();
  await expect(page.getByText("API-backed via @banking-lab/api-client").first()).toBeVisible();
  await expect(page.getByTestId("manifest-transaction-correction-api-panel")).toBeAttached();

  await page.getByLabel("Transaction code search").fill("PRD102");
  await page.getByRole("button", { name: "Open", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Deposit Rate Change Request" })).toBeVisible();
  await expect(page.getByText("API-backed via @banking-lab/api-client").first()).toBeVisible();
  await expect(page.getByTestId("manifest-deposit-rate-api-panel")).toBeAttached();

  await page.getByLabel("Transaction code search").fill("FEE103");
  await page.getByRole("button", { name: "Open", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Fee Policy Change Request" })).toBeVisible();
  await expect(page.getByText("API-backed via @banking-lab/api-client").first()).toBeVisible();
  await expect(page.getByTestId("manifest-fee-policy-api-panel")).toBeAttached();
});

test("staff terminal shell has no app-router one-off business screens", async () => {
  const appDir = path.join(repoRoot, "apps", app, "src", "app");
  const componentDir = path.join(repoRoot, "apps", app, "src", "components");
  const pageSource = readFileSync(path.join(appDir, "page.tsx"), "utf8");
  const screenSource = readFileSync(path.join(componentDir, "terminal-screens.tsx"), "utf8");
  const uiSource = readFileSync(path.join(componentDir, "terminal-ui.tsx"), "utf8");
  const files = readdirSync(appDir).sort();

  expect(files).toEqual(["api", "globals.css", "layout.tsx", "page.tsx"]);
  expect(pageSource).toContain("loadChannelManifests");
  expect(pageSource).toContain("StaffIntegratedWorkspace");
  expect(screenSource).toContain("TerminalPortalDashboard");
  expect(screenSource).toContain("TerminalShell");
  expect(uiSource).toContain("TerminalField");
  expect(uiSource).toContain("TerminalButton");
  expect(pageSource).not.toContain("fetch(\"/api/staff");
  expect(pageSource).not.toContain("fetch('/api/staff");
  expect(readdirSync(path.join(appDir, "api", "auth", "keycloak-token")).sort()).toEqual(["route.ts"]);
});

test("staff terminal loads masked customer detail from the Spring API when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed channel smoke.");

  await page.goto(baseUrl);

  const panel = page.getByTestId("api-backed-staff-customer");
  await expect(panel).toContainText("masked detail loaded", { timeout: 15_000 });
  await expect(panel).toContainText("SYN-CUS-001");
  await expect(panel).toContainText("010-****-1001");
  await expect(panel).toContainText("AUD-");
  await expect(panel).toContainText("API-backed channel parity smoke");
});

test("staff terminal loads Spring API-backed operational retry queue when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed operational retry queue smoke.");

  await page.goto(baseUrl);

  const panel = page.getByTestId("api-backed-operational-retry-queue");
  await expect(panel).toContainText("retry queue loaded", { timeout: 15_000 });
  await expect(panel).toContainText("AUD-");
  await expect(panel).toContainText("OBX-SYN-RETRY-001");
  await expect(panel).toContainText("FAILED");
  await expect(panel).toContainText("eligible");
});

test("staff terminal loads Spring API-backed workflow timeline when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed workflow timeline smoke.");

  await page.goto(baseUrl);
  await page.getByLabel("Transaction code search").fill("WRK003");
  await page.getByRole("button", { name: "Open", exact: true }).click();

  const panel = page.getByTestId("manifest-workflow-timeline-api-panel");
  await expect(panel).toContainText("workflow timeline loaded", { timeout: 15_000 });
  await expect(panel).toContainText("AUD-");
  await expect(panel).toContainText("TX-SYN-CORR-001");
  await expect(panel).toContainText("WORKFLOW");
  await expect(panel).toContainText("WAITING_APPROVAL");
});

test("staff terminal executes Spring API-backed privileged unmask when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed unmask smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Run privileged unmask smoke" }).click();
  const panel = page.getByTestId("api-backed-staff-unmask-command");
  await expect(panel).toContainText("privileged unmask approved", { timeout: 15_000 });
  await expect(panel).toContainText("AUTHORIZATION_POLICY_VIOLATION");
  await expect(panel).toContainText("manager01");
  await expect(panel).toContainText("UNMASKED_TIMEBOXED");
  await expect(panel).toContainText("010-0000-1001");
  await expect(panel).toContainText("300");
  await expect(panel).toContainText("AUD-");
});

test("staff terminal executes Spring API-backed customer change approval when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed command smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Run customer change smoke" }).click();
  const panel = page.getByTestId("api-backed-staff-change-command");
  await expect(panel).toContainText("customer change approved", { timeout: 15_000 });
  await expect(panel).toContainText("APR-");
  await expect(panel).toContainText("MAKER_CHECKER_SELF_APPROVAL_REJECTED");
  await expect(panel).toContainText("SYN-CUS-CMD-001");
  await expect(panel).toContainText("010-****-1399");
  await expect(panel).toContainText("customer change executed");
});

test("staff terminal executes Payment Service PAY101 audited instruction inquiry when configured", async ({ page }) => {
  test.skip(!paymentApiBaseUrl, "Set BANKING_LAB_E2E_PAYMENT_API_BASE_URL to run API-backed payment inquiry smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Run payment inquiry smoke" }).click();
  const panel = page.getByTestId("api-backed-staff-payment-inquiry");
  await expect(panel).toContainText("payment inquiry audited", { timeout: 15_000 });
  await expect(panel).toContainText("PAY-");
  await expect(panel).toContainText("POSTING_REQUESTED");
  await expect(panel).toContainText("Synthetic Utility Biller");
  await expect(panel).toContainText("ACC-SYN-001-001");
  await expect(panel).toContainText("PAU-");
  await expect(panel).toContainText("Browser PAY-101 payment instruction inquiry smoke");
});

test("staff terminal ACC103 executes Spring API-backed account hold and release approvals when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed account hold command smoke.");

  await page.goto(`${baseUrl}/?screen=ACC-103`);

  const panel = page.getByTestId("manifest-account-hold-api-panel");
  await expect(panel).toContainText("account hold API ready", { timeout: 15_000 });

  await page.getByRole("button", { name: "Run account hold smoke" }).click();
  await expect(panel).toContainText("account hold release completed", { timeout: 20_000 });
  await expect(panel).toContainText("ACCOUNT_HOLD");
  await expect(panel).toContainText("ACCOUNT_HOLD_RELEASE");
  await expect(panel).toContainText("MAKER_CHECKER_SELF_APPROVAL_REJECTED");
  await expect(panel).toContainText("ACC-SYN-HOLD-001");
  await expect(panel).toContainText("ACTIVE");
});

test("staff terminal LIM102 executes Spring API-backed transfer limit approval when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed transfer limit command smoke.");

  await page.goto(`${baseUrl}/?screen=LIM-102`);

  const panel = page.getByTestId("manifest-transfer-limit-api-panel");
  await expect(panel).toContainText("transfer limit API ready", { timeout: 15_000 });

  await page.getByRole("button", { name: "Run transfer limit smoke" }).click();
  await expect(panel).toContainText("transfer limit applied", { timeout: 20_000 });
  await expect(panel).toContainText("TRANSFER_LIMIT_CHANGE");
  await expect(panel).toContainText("MAKER_CHECKER_SELF_APPROVAL_REJECTED");
  await expect(panel).toContainText("ACC-SYN-LIMIT-001");
  await expect(panel).toContainText("After daily");
  await expect(panel).toContainText("After single");
});

test("staff terminal KYC101 executes Spring API-backed KYC re-confirmation approval when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed KYC review command smoke.");

  await page.goto(`${baseUrl}/?screen=KYC-101`);

  const panel = page.getByTestId("manifest-kyc-review-api-panel");
  await expect(panel).toContainText("KYC review API ready", { timeout: 15_000 });

  await page.getByRole("button", { name: "Run KYC review smoke" }).click();
  await expect(panel).toContainText("KYC review requested", { timeout: 20_000 });
  await expect(panel).toContainText("CUSTOMER_KYC_REVIEW");
  await expect(panel).toContainText("MAKER_CHECKER_SELF_APPROVAL_REJECTED");
  await expect(panel).toContainText("SYN-CUS-KYC-001");
  await expect(panel).toContainText("REVIEW_REQUIRED");
});

test("staff terminal FEE102 executes Spring API-backed fee waiver approval and rejection when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed fee waiver command smoke.");

  await page.goto(`${baseUrl}/?screen=FEE-102`);

  const panel = page.getByTestId("manifest-fee-waiver-api-panel");
  await expect(panel).toContainText("fee waiver API ready", { timeout: 15_000 });

  await page.getByRole("button", { name: "Run fee waiver smoke" }).click();
  await expect(panel).toContainText("fee waiver approved and rejected", { timeout: 20_000 });
  await expect(panel).toContainText("FEE_WAIVER");
  await expect(panel).toContainText("MAKER_CHECKER_SELF_APPROVAL_REJECTED");
  await expect(panel).toContainText("ACC-SYN-FEE-001");
  await expect(panel).toContainText("APPROVED");
  await expect(panel).toContainText("REJECTED");
  await expect(panel).toContainText("feePostingCreated=false");
});

test("staff terminal LED103 executes Spring API-backed transaction correction reversal when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed transaction correction command smoke.");

  await page.goto(`${baseUrl}/?screen=LED-103`);

  const panel = page.getByTestId("manifest-transaction-correction-api-panel");
  await expect(panel).toContainText("transaction correction API ready", { timeout: 15_000 });

  await page.getByRole("button", { name: "Run transaction correction smoke" }).click();
  await expect(panel).toContainText("transaction correction reversed", { timeout: 20_000 });
  await expect(panel).toContainText("TRANSACTION_CORRECTION");
  await expect(panel).toContainText("MAKER_CHECKER_SELF_APPROVAL_REJECTED");
  await expect(panel).toContainText("TX-SYN-CORR-001");
  await expect(panel).toContainText("REVERSAL");
  await expect(panel).toContainText("ledgerSourceRowsMutated=false");
});

test("staff terminal PRD102 executes Spring API-backed deposit rate change approval when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed deposit rate change command smoke.");

  await page.goto(`${baseUrl}/?screen=PRD-102`);

  const panel = page.getByTestId("manifest-deposit-rate-api-panel");
  await expect(panel).toContainText("deposit product API ready", { timeout: 15_000 });

  await page.getByRole("button", { name: "Run deposit rate smoke" }).click();
  await expect(panel).toContainText("deposit rate version applied", { timeout: 20_000 });
  await expect(panel).toContainText("PRODUCT_PARAMETER_CHANGE");
  await expect(panel).toContainText("MAKER_CHECKER_SELF_APPROVAL_REJECTED");
  await expect(panel).toContainText("DP-SYN-SAVINGS");
  await expect(panel).toContainText("APPLIED");
});

test("staff terminal FEE103 executes Spring API-backed fee policy change approval when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed fee policy change command smoke.");

  await page.goto(`${baseUrl}/?screen=FEE-103`);

  const panel = page.getByTestId("manifest-fee-policy-api-panel");
  await expect(panel).toContainText("fee policy API ready", { timeout: 15_000 });

  await page.getByRole("button", { name: "Run fee policy smoke" }).click();
  await expect(panel).toContainText("fee policy version applied", { timeout: 20_000 });
  await expect(panel).toContainText("FEE_POLICY_PARAMETER_CHANGE");
  await expect(panel).toContainText("MAKER_CHECKER_SELF_APPROVAL_REJECTED");
  await expect(panel).toContainText("FEE-SYN-MONTHLY");
  await expect(panel).toContainText("APPLIED");
});

test("staff terminal APR001 tab lists selects approves and shows audit events from Spring API", async ({ page, request }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run APR001 API-backed manifest smoke.");

  const approval = await createSyntheticCustomerChangeApproval(request);

  await page.goto(`${baseUrl}/?screen=APR-001`);

  const panel = page.getByTestId("manifest-approval-api-panel");
  await expect(panel).toContainText("approval inbox loaded", { timeout: 15_000 });
  await expect(panel).toContainText(approval.item.approvalId);

  await page.getByRole("button", { name: `Select approval ${approval.item.approvalId}` }).click();
  await expect(page.getByTestId("manifest-selected-approval")).toContainText("selected approval");
  await expect(page.getByTestId("manifest-selected-approval")).toContainText(approval.item.approvalId);

  await page.getByRole("button", { name: "Approve selected approval" }).click();
  await expect(panel).toContainText("approval executed", { timeout: 15_000 });
  await expect(panel).toContainText("manager01");
  await expect(panel).toContainText("COMMAND_APPROVED");
  await expect(page.getByTestId("manifest-approval-audit-events")).toContainText("AUD-");
});

test("staff terminal AUD001 tab lists selects and displays Spring audit events", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run AUD001 API-backed manifest smoke.");

  await page.goto(`${baseUrl}/?screen=AUD-001`);

  const panel = page.getByTestId("manifest-audit-api-panel");
  await expect(panel).toContainText("audit log loaded", { timeout: 15_000 });
  await expect(panel).toContainText("Hash chain");
  await expect(panel).toContainText("AUD-");

  await page.getByRole("button", { name: /Select audit AUD-/ }).first().click();
  await expect(page.getByTestId("manifest-selected-audit-event")).toContainText("selected audit event");
  await expect(page.getByTestId("manifest-selected-audit-event")).toContainText("valid");
  await expect(panel).toContainText("Payload hash");
});

test("staff terminal propagates interactive Keycloak staff and checker tokens when configured", async ({ page }) => {
  test.skip(!apiBaseUrl || !keycloakBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL and BANKING_LAB_E2E_KEYCLOAK_BASE_URL to run live staff Keycloak browser smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Sign in staff with Keycloak" }).click();
  await expect(page).toHaveURL(/\/realms\/banking-lab\/protocol\/openid-connect\/auth/u, { timeout: 15_000 });
  await signInWithKeycloak(page, "branch01", "branch01-pass");

  const panel = page.getByTestId("api-backed-staff-keycloak-login");
  await expect(panel).toContainText("Keycloak staff detail loaded", { timeout: 20_000 });
  await expect(panel).toContainText("Bearer");
  await expect(panel).toContainText("SYN-CUS-001");
  await expect(panel).toContainText("010-****-1001");
  await expect(panel).toContainText("AUD-");

  await page.getByRole("button", { name: "Sign in checker with Keycloak" }).click();
  await expect(page).toHaveURL(/\/realms\/banking-lab\/protocol\/openid-connect\/auth/u, { timeout: 15_000 });
  await signInWithKeycloak(page, "manager01", "manager01-pass");

  await expect(panel).toContainText("Keycloak checker loaded", { timeout: 20_000 });
  await expect(panel).toContainText("manager01");

  await page.getByRole("button", { name: "Run Keycloak unmask smoke" }).click();
  await expect(panel).toContainText("Keycloak unmask approved", { timeout: 15_000 });
  await expect(panel).toContainText("UNMASKED_TIMEBOXED");
  await expect(panel).toContainText("010-0000-1001");
  await expect(panel).toContainText("300");
  await expect(panel).toContainText("AUD-");

  await page.getByRole("button", { name: "Run Keycloak customer change smoke" }).click();
  await expect(panel).toContainText("Keycloak customer change approved", { timeout: 15_000 });
  await expect(panel).toContainText("APR-");
  await expect(panel).toContainText("branch01");
  await expect(panel).toContainText("manager01");
  await expect(panel).toContainText("SYN-CUS-CMD-001");
  await expect(panel).toContainText("010-****-1499");
  await expect(panel).toContainText("Keycloak customer change executed");
});

test("staff terminal completes interactive Keycloak WebAuthn required action when configured", async ({ page, context, browserName }) => {
  test.skip(browserName !== "chromium", "Chromium CDP virtual authenticator is required for the WebAuthn smoke.");
  test.skip(!apiBaseUrl || !keycloakBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL and BANKING_LAB_E2E_KEYCLOAK_BASE_URL to run live WebAuthn browser smoke.");

  const cdp = await context.newCDPSession(page);
  await cdp.send("WebAuthn.enable");
  await cdp.send("WebAuthn.addVirtualAuthenticator", {
    options: {
      protocol: "ctap2",
      transport: "usb",
      hasResidentKey: true,
      hasUserVerification: true,
      isUserVerified: true,
      automaticPresenceSimulation: true
    }
  });

  await page.goto(baseUrl);
  await page.getByRole("button", { name: "Sign in WebAuthn manager with Keycloak" }).click();
  await expect(page).toHaveURL(/\/realms\/banking-lab\/protocol\/openid-connect\/auth/u, { timeout: 15_000 });
  await signInWithKeycloak(page, "manager-webauthn01", "manager-webauthn01-pass");

  if (page.url().includes("/login-actions/required-action")) {
    await expect(page.locator("body")).toContainText(/WebAuthn|Security Key|Passkey|Register/i, { timeout: 15_000 });
    await page.getByRole("button", { name: /Register|Submit|Save|Continue/i }).click();
  }

  const panel = page.getByTestId("api-backed-staff-keycloak-login");
  await expect(panel).toContainText("Keycloak WebAuthn manager loaded", { timeout: 30_000 });
  await expect(panel).toContainText("manager-webauthn01");
  await expect(panel).toContainText("Bearer");
  await expect(panel).toContainText("SYN-CUS-001");
  await expect(panel).toContainText("010-****-1001");
  await expect(panel).toContainText("AUD-");
});
