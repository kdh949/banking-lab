import { expect, test } from "@playwright/test";
import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const specDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = process.env.BANKING_LAB_ROOT || path.resolve(specDir, "../../..");
const app = "customer-web";
const baseUrl = "http://localhost:3001";
const apiBaseUrl = process.env.BANKING_LAB_E2E_API_BASE_URL ?? "";
const paymentApiBaseUrl = process.env.BANKING_LAB_E2E_PAYMENT_API_BASE_URL ?? "";
const notificationApiBaseUrl = process.env.BANKING_LAB_E2E_NOTIFICATION_API_BASE_URL ?? "";
const keycloakBaseUrl = process.env.BANKING_LAB_E2E_KEYCLOAK_BASE_URL ?? "";

function manifests() {
  const dir = path.join(repoRoot, "screen-manifests", app);
  return readdirSync(dir)
    .filter((fileName) => fileName.endsWith(".json"))
    .sort()
    .map((fileName) => JSON.parse(readFileSync(path.join(dir, fileName), "utf8")));
}

test("customer web renders account, transfer, and complaint controls from manifests", async ({ page }) => {
  const screens = manifests();
  await page.goto(baseUrl);

  await expect(page.locator(`[data-channel-shell="${app}"]`)).toBeVisible();
  await expect(page.getByRole("heading", { name: "Self-Service Workspace" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Self-service starts here" })).toBeVisible();
  await expect(page.getByText("Service Hub")).toBeVisible();
  for (const screen of screens) {
    await expect(page.getByText(screen.screenId, { exact: true }).first()).toBeVisible();
  }

  await expect(page.getByText("Default PII masking")).toBeVisible();
  await expect(page.getByText("CUSTOMER_SELF").first()).toBeVisible();
  await expect(page.getByText("Complaint Entry", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("workflow timeline", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("WAITING_APPROVAL").first()).toBeVisible();
});

test("customer web exposes form-backed self-service routes and shared workflow metadata", async () => {
  const appDir = path.join(repoRoot, "apps", app, "src", "app");
  const pageSource = readFileSync(path.join(appDir, "page.tsx"), "utf8");
  const routeComponent = readFileSync(path.join(repoRoot, "apps", app, "src", "components", "workflow-routes.tsx"), "utf8");
  const selfService = readFileSync(path.join(repoRoot, "apps", app, "src", "components", "CustomerSelfService.tsx"), "utf8");
  const files = readdirSync(appDir).sort();

  expect(files).toEqual([
    "360",
    "accounts",
    "api",
    "cards",
    "complaints",
    "globals.css",
    "layout.tsx",
    "loans",
    "login",
    "notifications",
    "onboarding",
    "page.tsx",
    "payments",
    "profile",
    "security",
    "signup",
    "statements",
    "transfers"
  ]);
  expect(readdirSync(path.join(appDir, "api", "auth", "keycloak-token")).sort()).toEqual(["route.ts"]);
  expect(pageSource).toContain("loadCustomerWebManifests");
  expect(pageSource).toContain("customerWorkflowRouteSummaries");
  expect(routeComponent).toContain("CustomerWorkflowRoutePage");
  expect(selfService).toContain("CustomerSelfServiceHomeSurface");
  expect(selfService).toContain("Service Hub");
  expect(selfService).toContain("Default PII masking");
  expect(routeComponent).toContain("CWB-001");
  expect(routeComponent).toContain("CWB-002");
  expect(routeComponent).toContain("CWB-003");
  expect(routeComponent).toContain("CWB-004");
  expect(routeComponent).toContain("CWB-104");
  expect(routeComponent).toContain("CWB-105");
  expect(routeComponent).toContain("CWB-106");
  expect(routeComponent).toContain("CWB-107");
  expect(routeComponent).toContain("CWB-201");
  expect(routeComponent).toContain("POSTED");
  expect(routeComponent).toContain("HELD");
  expect(routeComponent).toContain("FAILED");
  expect(routeComponent).toContain("BLOCKED");
  expect(routeComponent).toContain("STEP_UP_REQUIRED");
  expect(routeComponent).toContain("synthetic demo fallback");
  expect(selfService).toContain("signupCustomer");
  expect(selfService).toContain("loginCustomer");
  expect(selfService).toContain("customerAccounts");
  expect(selfService).toContain("customerProfile");
  expect(selfService).toContain("requestCustomerAccountOpening");
  expect(selfService).toContain("customer360");
  expect(selfService).toContain("customerAccountStatement");
  expect(selfService).toContain("customerConsolidatedStatement");
  expect(selfService).toContain("customerStatementArtifacts");
  expect(selfService).toContain("internalRecipientLookup");
  expect(selfService).toContain("requestCustomerTransfer");
  expect(pageSource).not.toContain("fetch(\"/api/customer/transfers\"");
  expect(pageSource).not.toContain("fetch('/api/customer/transfers'");
});

test("customer web route pages render signup login transfer forms and complaint workflow states", async ({ page }) => {
  await page.goto(`${baseUrl}/signup`);
  await expect(page.getByRole("heading", { name: "Customer Signup" }).first()).toBeVisible();
  await expect(page.getByLabel("Username")).toBeVisible();
  await expect(page.getByRole("button", { name: "Create synthetic customer" })).toBeVisible();

  await page.goto(`${baseUrl}/login`);
  await expect(page.getByRole("heading", { name: "Customer Login" }).first()).toBeVisible();
  await expect(page.getByRole("button", { name: "Sign in" })).toBeVisible();

  await page.goto(`${baseUrl}/profile`);
  await expect(page.getByRole("heading", { name: "Customer Profile" }).first()).toBeVisible();
  await expect(page.getByText("DEMO_FALLBACK_API_NOT_CONFIGURED").first()).toBeVisible();

  await page.goto(`${baseUrl}/onboarding`);
  await expect(page.getByRole("heading", { name: "Onboarding" }).first()).toBeVisible();
  await expect(page.getByText("DEMO_FALLBACK_API_NOT_CONFIGURED").first()).toBeVisible();

  await page.goto(`${baseUrl}/360`);
  await expect(page.getByRole("heading", { name: "Customer 360" }).first()).toBeVisible();
  await expect(page.getByText("DEMO_FALLBACK_API_NOT_CONFIGURED").first()).toBeVisible();

  await page.goto(`${baseUrl}/statements`);
  await expect(page.getByRole("heading", { name: "Consolidated Statements" }).first()).toBeVisible();
  await expect(page.getByText("DEMO_FALLBACK_API_NOT_CONFIGURED").first()).toBeVisible();

  await page.goto(`${baseUrl}/accounts`);
  await expect(page.getByRole("heading", { name: "Accounts" }).first()).toBeVisible();
  await expect(page.getByText("DEMO_FALLBACK_API_NOT_CONFIGURED").first()).toBeVisible();

  await page.goto(`${baseUrl}/transfers/new`);

  await expect(page.locator(`[data-channel-shell="${app}"]`)).toBeVisible();
  await expect(page.getByRole("heading", { name: "New Transfer" }).first()).toBeVisible();
  await expect(page.getByLabel("Recipient account number or ID")).toBeVisible();
  await expect(page.getByRole("button", { name: "Lookup recipient" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Submit transfer" })).toBeVisible();
  await expect(page.getByText("Recipient Lookup").first()).toBeVisible();

  await page.goto(`${baseUrl}/complaints/CMP-ROUTE-001`);
  await expect(page.getByRole("heading", { name: "Complaint Detail: CMP-ROUTE-001" }).first()).toBeVisible();
  await expect(page.getByText("WAITING_APPROVAL").first()).toBeVisible();
  await expect(page.getByText("CLOSED").first()).toBeVisible();
  await expect(page.getByText("CUSTOMER_OWNERSHIP_DENIED").first()).toBeVisible();
});

test("customer web loads owned masked account detail from the Spring API when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed channel smoke.");

  await page.goto(baseUrl);

  const panel = page.getByTestId("api-backed-customer-account");
  await expect(panel).toContainText("masked account loaded", { timeout: 15_000 });
  await expect(panel).toContainText("SYN-CUS-001");
  await expect(panel).toContainText("LAB-***-0001");
  await expect(panel).toContainText("100000000 KRW");
});

test("customer web propagates interactive Keycloak login token to the Spring API when configured", async ({ page }) => {
  test.skip(!apiBaseUrl || !keycloakBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL and BANKING_LAB_E2E_KEYCLOAK_BASE_URL to run live Keycloak browser login smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Sign in with Keycloak" }).click();
  await expect(page).toHaveURL(/\/realms\/banking-lab\/protocol\/openid-connect\/auth/u, { timeout: 15_000 });
  await page.locator("#username").fill("customer01");
  await page.locator("#password").fill("customer01-pass");
  await page.locator("#kc-login").click();

  const panel = page.getByTestId("api-backed-customer-keycloak-login");
  await expect(panel).toContainText("Keycloak account loaded", { timeout: 20_000 });
  await expect(panel).toContainText("Bearer");
  await expect(panel).toContainText("SYN-CUS-001");
  await expect(panel).toContainText("LAB-***-0001");
  await expect(panel).toContainText("100000000 KRW");

  await page.getByRole("button", { name: "Run Keycloak transfer smoke" }).click();
  await expect(panel).toContainText("Keycloak transfer replayed", { timeout: 15_000 });
  await expect(panel).toContainText("CWB-OIDC-TRF-");
  await expect(panel).toContainText("TX-");
  await expect(panel).toContainText("POSTED");
  await expect(panel).toContainText("same transaction id");

  await page.getByRole("button", { name: "Run Keycloak transfer failure smoke" }).click();
  await expect(panel).toContainText("Keycloak transfer rejected", { timeout: 15_000 });
  await expect(panel).toContainText("LEDGER_INSUFFICIENT_AVAILABLE_BALANCE");
  await expect(panel).toContainText("ledger");
  await expect(panel).toContainText("409");
  await expect(panel).toContainText("/api/customer/transfers");

  await page.getByRole("button", { name: "Run Keycloak history status smoke" }).click();
  await expect(panel).toContainText("Keycloak history and held status loaded", { timeout: 15_000 });
  await expect(panel).toContainText("CWB-OIDC-HIST-");
  await expect(panel).toContainText("INTERNAL_TRANSFER");
  await expect(panel).toContainText("CUSTOMER_WEB");
  await expect(panel).toContainText("DEBIT");
  await expect(panel).toContainText("FDS-SYN-001");
  await expect(panel).toContainText("HELD");
  await expect(panel).toContainText("15000000");

  await page.getByRole("button", { name: "Run Keycloak held failed status smoke" }).click();
  await expect(panel).toContainText("Keycloak held and failed statuses loaded", { timeout: 15_000 });
  await expect(panel).toContainText("CWB-OIDC-HELD-");
  await expect(panel).toContainText("CWB-OIDC-FAILED-");
  await expect(panel).toContainText("FAILED");
  await expect(panel).toContainText("REQUEST_VALIDATION_FAILED");

  await page.getByRole("button", { name: "Run Keycloak complaint entry smoke" }).click();
  await expect(panel).toContainText("Keycloak complaint received", { timeout: 15_000 });
  await expect(panel).toContainText("CMP-");
  await expect(panel).toContainText("ACCOUNT_ACCESS");
  await expect(panel).toContainText("RECEIVED");

  await page.getByRole("button", { name: "Run Keycloak complaint confirm smoke" }).click();
  await expect(panel).toContainText("Keycloak complaint closed", { timeout: 15_000 });
  await expect(panel).toContainText("CMP-SYN-CONFIRM-001");
  await expect(panel).toContainText("CLOSED");
  await expect(panel).toContainText("Confirmed");
});

test("customer web executes Spring API-backed idempotent transfer retry when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed transfer retry smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Run transfer retry smoke" }).click();
  const panel = page.getByTestId("api-backed-customer-transfer-retry");
  await expect(panel).toContainText("transfer retry replayed", { timeout: 15_000 });
  await expect(panel).toContainText("CWB-TRF-RETRY-");
  await expect(panel).toContainText("TX-");
  await expect(panel).toContainText("POSTED");
  await expect(panel).toContainText("same transaction id");
});

test("customer web shows Spring API-backed transfer insufficient-balance failure when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed transfer failure smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Run transfer failure smoke" }).click();
  const panel = page.getByTestId("api-backed-customer-transfer-failure");
  await expect(panel).toContainText("transfer rejected", { timeout: 15_000 });
  await expect(panel).toContainText("LEDGER_INSUFFICIENT_AVAILABLE_BALANCE");
  await expect(panel).toContainText("ledger");
  await expect(panel).toContainText("409");
  await expect(panel).toContainText("/api/customer/transfers");
});

test("customer web shows Spring API-backed transfer history and held FDS status when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed history/status smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Run history status smoke" }).click();
  const panel = page.getByTestId("api-backed-customer-history-status");
  await expect(panel).toContainText("history and held status loaded", { timeout: 15_000 });
  await expect(panel).toContainText("CWB-HIST-");
  await expect(panel).toContainText("TX-");
  await expect(panel).toContainText("INTERNAL_TRANSFER");
  await expect(panel).toContainText("CUSTOMER_WEB");
  await expect(panel).toContainText("DEBIT");
  await expect(panel).toContainText("FDS-SYN-001");
  await expect(panel).toContainText("HELD");
  await expect(panel).toContainText("15000000");
});

test("customer web shows Spring API-backed held and failed transfer statuses when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed held/failed status smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Run held failed status smoke" }).click();
  const panel = page.getByTestId("api-backed-customer-held-failed-status");
  await expect(panel).toContainText("held and failed statuses loaded", { timeout: 15_000 });
  await expect(panel).toContainText("CWB-HELD-");
  await expect(panel).toContainText("CWB-FAILED-");
  await expect(panel).toContainText("HELD");
  await expect(panel).toContainText("FAILED");
  await expect(panel).toContainText("REQUEST_VALIDATION_FAILED");
  await expect(panel).toContainText("FDS-");
});

test("customer web submits Spring API-backed complaint entry when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed complaint entry smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Run complaint entry smoke" }).click();
  const panel = page.getByTestId("api-backed-customer-complaint-entry");
  await expect(panel).toContainText("complaint received", { timeout: 15_000 });
  await expect(panel).toContainText("CMP-");
  await expect(panel).toContainText("SYN-CUS-001");
  await expect(panel).toContainText("ACCOUNT_ACCESS");
  await expect(panel).toContainText("RECEIVED");
});

test("customer web confirms Spring API-backed answered complaint when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed complaint confirmation smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Run complaint confirm smoke" }).click();
  const panel = page.getByTestId("api-backed-customer-complaint-confirm");
  await expect(panel).toContainText("complaint closed", { timeout: 15_000 });
  await expect(panel).toContainText("CMP-SYN-CONFIRM-001");
  await expect(panel).toContainText("SYN-CUS-001");
  await expect(panel).toContainText("CLOSED");
});

test("customer web executes Payment Service bill payment and autopay smoke when configured", async ({ page }) => {
  test.skip(!paymentApiBaseUrl, "Set BANKING_LAB_E2E_PAYMENT_API_BASE_URL to run API-backed payment-service smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Run payment domain smoke" }).click();
  const panel = page.getByTestId("api-backed-customer-payment-domain");
  await expect(panel).toContainText("payment and autopay recorded", { timeout: 15_000 });
  await expect(panel).toContainText("PAY-");
  await expect(panel).toContainText("same instruction");
  await expect(panel).toContainText("Synthetic Utility Biller");
  await expect(panel).toContainText("APAY-");
  await expect(panel).toContainText("PAUSED / ACTIVE / CANCELED");
  await expect(panel).toContainText("2026-04-30");
});

test("customer web manages owned Notification Service preferences when configured", async ({ page }) => {
  test.skip(!notificationApiBaseUrl, "Set BANKING_LAB_E2E_NOTIFICATION_API_BASE_URL to run API-backed notification-service preference smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Run notification preference smoke" }).click();
  const panel = page.getByTestId("api-backed-customer-notification-preferences");
  await expect(panel).toContainText("notification preferences saved", { timeout: 15_000 });
  await expect(panel).toContainText("NPF-");
  await expect(panel).toContainText("SYN-CUS-001");
  await expect(panel).toContainText("PUSH");
  await expect(panel).toContainText("PaymentLedgerPostingRequested");
  await expect(panel).toContainText("disabled");
  await expect(panel).toContainText("customer01");
});

test("customer web reads owned Notification Service delivery history when configured", async ({ page }) => {
  test.skip(!notificationApiBaseUrl, "Set BANKING_LAB_E2E_NOTIFICATION_API_BASE_URL to run API-backed notification-service history smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Run notification history smoke" }).click();
  const panel = page.getByTestId("api-backed-customer-notification-delivery-history");
  await expect(panel).toContainText("notification history loaded", { timeout: 15_000 });
  await expect(panel).toContainText("SYN-CUS-001");
  await expect(panel).toContainText("Rows");
});
