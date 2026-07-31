import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const specDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = process.env.BANKING_LAB_ROOT || path.resolve(specDir, "../../..");
const app = "ops-console";
const baseUrl = "http://localhost:3004";
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

test("ops console renders closing, reconciliation, approval, and workflow controls from manifests", async ({ page }) => {
  const screens = manifests();
  await page.goto(baseUrl);

  await expect(page.locator(`[data-channel-shell="${app}"]`)).toBeVisible();
  await expect(page.getByRole("heading", { name: "Payment settlement and ledger reconciliation" })).toBeVisible();
  for (const screen of screens) {
    await expect(page.locator(".channel-card-heading > span", { hasText: screen.screenId })).toBeVisible();
  }

  await expect(page.getByText("Independent evidence · balanced ledger · SLA exceptions")).toBeVisible();
  await expect(page.getByText("Mismatch corrections require approval")).toBeVisible();
  await expect(page.getByText("RECONCILIATION_ADJUSTMENT").first()).toBeVisible();
  await expect(page.getByText("workflow timeline", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("ADJUSTMENT_REQUESTED")).toBeVisible();
  await expect(page.getByText("reason required").first()).toBeVisible();
});

test("ops console shell has no app-router one-off business screens", async () => {
  const appDir = path.join(repoRoot, "apps", app, "src", "app");
  const pageSource = readFileSync(path.join(appDir, "page.tsx"), "utf8");
  const files = readdirSync(appDir).sort();

  expect(files).toEqual(["api", "globals.css", "layout.tsx", "page.tsx"]);
  expect(pageSource).toContain("loadChannelManifests");
  expect(pageSource).not.toContain("fetch(\"/api/ops");
  expect(pageSource).not.toContain("fetch('/api/ops");
  expect(readdirSync(path.join(appDir, "api", "auth", "keycloak-token")).sort()).toEqual(["route.ts"]);
});

test("ops console loads a Spring API-backed reconciliation item when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed channel smoke.");

  await page.goto(baseUrl);

  const panel = page.getByTestId("api-backed-ops-reconciliation");
  await expect(panel).toContainText("item loaded", { timeout: 15_000 });
  await expect(panel).toContainText("REC-SYN-001");
  await expect(panel).toContainText("OPEN");
  await expect(panel).toContainText("12000 KRW");
});

test("ops console executes Spring API-backed reconciliation adjustment approval when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed command smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Run reconciliation adjustment smoke" }).click();
  const panel = page.getByTestId("api-backed-reconciliation-command");
  await expect(panel).toContainText("reconciliation adjustment approved", { timeout: 15_000 });
  await expect(panel).toContainText("APR-");
  await expect(panel).toContainText("REC-SYN-CMD-001");
  await expect(panel).toContainText("ADJUSTED");
  await expect(panel).toContainText("TX-");
});

test("ops console shows Spring API-backed reconciliation workflow state failure when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed workflow failure smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Run reconciliation failure smoke" }).click();
  const panel = page.getByTestId("api-backed-reconciliation-workflow-failure");
  await expect(panel).toContainText("workflow state rejected", { timeout: 15_000 });
  await expect(panel).toContainText("REC-SYN-FAIL-001");
  await expect(panel).toContainText("WORKFLOW_STATE_VIOLATION");
  await expect(panel).toContainText("workflow");
  await expect(panel).toContainText("409");
  await expect(panel).toContainText("/api/ops/reconciliation-items/REC-SYN-FAIL-001/adjustment-requests");
});

test("ops console requests Spring API-backed reconciliation parameter change when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed OPS-301 parameter command smoke.");

  await page.goto(baseUrl);

  const panel = page.getByTestId("api-backed-reconciliation-parameters");
  await expect(panel).toContainText("reconciliation parameters loaded", { timeout: 15_000 });
  await expect(panel).toContainText("autoMatchToleranceMinor");
  await page.getByRole("button", { name: "Run reconciliation parameter change smoke" }).click();
  await expect(panel).toContainText("reconciliation parameter change requested", { timeout: 15_000 });
  await expect(panel).toContainText("PENDING_APPROVAL");
  await expect(panel).toContainText("APR-");
  await expect(panel).toContainText("RPC-");
});

test("ops console executes Spring API-backed ledger projection rebuild workflow when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed OPS-LEDGER projection workflow smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Run projection rebuild smoke" }).click();
  const panel = page.getByTestId("api-backed-ledger-projection-workflow");
  await expect(panel).toContainText("projection rebuild completed", { timeout: 15_000 });
  await expect(panel).toContainText("LEDGER_PROJECTION_REBUILD");
  await expect(panel).toContainText("ACC-SYN-CORR-TO");
  await expect(panel).toContainText("LPD-");
  await expect(panel).toContainText("LPR-");
  await expect(panel).toContainText("LPRUN-");
  await expect(panel).toContainText("projection rows rebuilt");
  await expect(panel).toContainText("Source hash");
  await expect(panel).toContainText("Projection hash");
});

test("ops console executes Payment Service OPS404 outbox dispatch when configured", async ({ page }) => {
  test.skip(!paymentApiBaseUrl, "Set BANKING_LAB_E2E_PAYMENT_API_BASE_URL to run API-backed payment outbox dispatch smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Run payment outbox dispatch smoke" }).click();
  const panel = page.getByTestId("api-backed-payment-outbox-dispatch");
  await expect(panel).toContainText("payment outbox dispatch recorded", { timeout: 15_000 });
  await expect(panel).toContainText(/PUBLISHED|FAILED|DEAD_LETTER|NO_PENDING_EVENT/);
  await expect(panel).toContainText("Retry count");
  await expect(panel).toContainText("Browser OPS-404 payment outbox dispatch smoke");
});

test("ops console propagates interactive Keycloak operator and checker tokens when configured", async ({ page }) => {
  test.skip(!apiBaseUrl || !keycloakBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL and BANKING_LAB_E2E_KEYCLOAK_BASE_URL to run live ops Keycloak browser smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Sign in ops operator with Keycloak" }).click();
  await expect(page).toHaveURL(/\/realms\/banking-lab\/protocol\/openid-connect\/auth/u, { timeout: 15_000 });
  await signInWithKeycloak(page, "ops01", "ops01-pass");

  const panel = page.getByTestId("api-backed-ops-keycloak-login");
  await expect(panel).toContainText("Keycloak reconciliation item loaded", { timeout: 20_000 });
  await expect(panel).toContainText("Bearer");
  await expect(panel).toContainText("REC-SYN-001");
  await expect(panel).toContainText("OPEN");
  await expect(panel).toContainText("12000 KRW");

  await page.getByRole("button", { name: "Sign in reconciliation checker with Keycloak" }).click();
  await expect(page).toHaveURL(/\/realms\/banking-lab\/protocol\/openid-connect\/auth/u, { timeout: 15_000 });
  await signInWithKeycloak(page, "manager01", "manager01-pass");

  await expect(panel).toContainText("Keycloak reconciliation checker loaded", { timeout: 20_000 });
  await expect(panel).toContainText("manager01");

  await page.getByRole("button", { name: "Run Keycloak reconciliation adjustment smoke" }).click();
  await expect(panel).toContainText("Keycloak reconciliation adjusted", { timeout: 15_000 });
  await expect(panel).toContainText("APR-");
  await expect(panel).toContainText("ops01");
  await expect(panel).toContainText("manager01");
  await expect(panel).toContainText("REC-SYN-CMD-001");
  await expect(panel).toContainText("ADJUSTED");
  await expect(panel).toContainText("TX-");

  await page.getByRole("button", { name: "Run Keycloak reconciliation failure smoke" }).click();
  await expect(panel).toContainText("workflow state rejected", { timeout: 15_000 });
  await expect(panel).toContainText("REC-SYN-FAIL-001");
  await expect(panel).toContainText("WORKFLOW_STATE_VIOLATION");
  await expect(panel).toContainText("workflow");
  await expect(panel).toContainText("409");
  await expect(panel).toContainText("/api/ops/reconciliation-items/REC-SYN-FAIL-001/adjustment-requests");
});
