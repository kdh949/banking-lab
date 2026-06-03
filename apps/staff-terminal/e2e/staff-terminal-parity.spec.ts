import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const specDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = process.env.BANKING_LAB_ROOT || path.resolve(specDir, "../../..");
const app = "staff-terminal";
const baseUrl = "http://localhost:3002";
const apiBaseUrl = process.env.BANKING_LAB_E2E_API_BASE_URL ?? "";
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

test("staff terminal renders reason, masking, and maker-checker controls from manifests", async ({ page }) => {
  const screens = manifests();
  const reasonRequired = screens.filter((screen) => screen.audit.reasonRequired).length;
  const makerChecker = screens.filter((screen) => screen.approval?.required).length;

  await page.goto(baseUrl);

  await expect(page.getByRole("heading", { name: "Transaction-code workspace" })).toBeVisible();
  await expect(page.getByLabel("Transaction code")).toHaveValue(screens[0].transactionCode);
  await expect(page.locator(".metric", { hasText: String(reasonRequired) })).toBeVisible();
  await expect(page.locator(".metric-label", { hasText: "reason-required" })).toBeVisible();
  await expect(page.locator(".metric", { hasText: String(makerChecker) })).toBeVisible();
  await expect(page.getByText("maker-checker").first()).toBeVisible();
  await expect(page.getByText("Masked by default")).toBeVisible();
  await expect(page.getByText("Business reason required")).toBeVisible();
  await expect(page.getByText("APR-001 declared")).toBeVisible();
  await expect(page.getByText("Banking Lab")).toBeVisible();
  await expect(page.getByLabel("Role-aware menu")).toBeVisible();
  await expect(page.getByLabel("Inside view and API smoke")).toBeVisible();
  await expect(page.getByText("Audit log panel")).toBeVisible();
  await expect(page.getByText("Workflow timeline")).toBeVisible();
  await expect(page.getByText("Exception/retry panel")).toBeVisible();
});

test("staff terminal shell has no app-router one-off business screens", async () => {
  const appDir = path.join(repoRoot, "apps", app, "src", "app");
  const pageSource = readFileSync(path.join(appDir, "page.tsx"), "utf8");
  const files = readdirSync(appDir).sort();

  expect(files).toEqual(["api", "globals.css", "layout.tsx", "page.tsx"]);
  expect(pageSource).toContain("loadChannelManifests");
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
