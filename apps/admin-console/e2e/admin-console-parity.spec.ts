import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const specDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = process.env.BANKING_LAB_ROOT || path.resolve(specDir, "../../..");
const app = "admin-console";
const baseUrl = "http://localhost:3007";
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

test("admin console renders platform control and parameter manifests", async ({ page }) => {
  const screens = manifests();
  await page.goto(baseUrl);

  await expect(page.locator(`[data-channel-shell="${app}"]`)).toBeVisible();
  await expect(page.getByRole("heading", { name: "Platform controls and privileged parameters" })).toBeVisible();
  for (const screen of screens) {
    await expect(page.getByText(screen.screenId, { exact: true })).toBeVisible();
  }

  await expect(page.getByText("Synthetic operations only")).toBeVisible();
  await expect(page.getByText("PASSKEY_RECOVERY_ADMIN").first()).toBeVisible();
  await expect(page.getByText("SECURITY_POLICY_PARAMETER_CHANGE")).toBeVisible();
  await expect(page.getByText("reason required").first()).toBeVisible();
});

test("admin console shell has no app-router one-off business screens", async () => {
  const appDir = path.join(repoRoot, "apps", app, "src", "app");
  const pageSource = readFileSync(path.join(appDir, "page.tsx"), "utf8");
  const files = readdirSync(appDir).sort();

  expect(files).toEqual(["api", "globals.css", "layout.tsx", "page.tsx"]);
  expect(pageSource).toContain("loadChannelManifests");
  expect(pageSource).not.toContain("fetch(\"/api/admin");
  expect(pageSource).not.toContain("fetch('/api/admin");
  expect(readdirSync(path.join(appDir, "api", "auth", "keycloak-token")).sort()).toEqual(["route.ts"]);
});

test("admin console loads a Spring API-backed platform summary when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed admin smoke.");

  await page.goto(baseUrl);

  const panel = page.getByTestId("api-backed-admin-summary");
  await expect(panel).toContainText("admin summary loaded", { timeout: 15_000 });
  await expect(panel).toContainText("synthetic only");
  await expect(panel).toContainText("kotlin-spring-boot");
  await expect(panel).toContainText("NODE_REFERENCE_BOUNDARY:BLOCKED");

  const evidencePanel = page.getByTestId("api-backed-admin-evidence-coverage");
  await expect(evidencePanel).toContainText("evidence coverage loaded", { timeout: 15_000 });
  await expect(evidencePanel).toContainText("ADM-501:API_BACKED");
  await expect(evidencePanel).toContainText("PARAMETER_ADMIN_APIS:TRACKED");

  const systemPanel = page.getByTestId("api-backed-admin-system-status");
  await expect(systemPanel).toContainText("system status loaded", { timeout: 15_000 });
  await expect(systemPanel).toContainText("CORE_BANKING:AVAILABLE");
  await expect(systemPanel).toContainText("EOD_CLOSING");
  await expect(systemPanel).toContainText("PROMETHEUS:LOCAL_PROFILE");
});

test("admin console propagates interactive Keycloak security admin token when configured", async ({ page }) => {
  test.skip(!apiBaseUrl || !keycloakBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL and BANKING_LAB_E2E_KEYCLOAK_BASE_URL to run live admin Keycloak browser smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Sign in security admin with Keycloak" }).click();
  await expect(page).toHaveURL(/\/realms\/banking-lab\/protocol\/openid-connect\/auth/u, { timeout: 15_000 });
  await signInWithKeycloak(page, "security-admin01", "security-admin01-pass");

  const panel = page.getByTestId("api-backed-admin-keycloak-login");
  await expect(panel).toContainText("Keycloak admin summary loaded", { timeout: 20_000 });
  await expect(panel).toContainText("Bearer");
  await expect(panel).toContainText("SYNTHETIC_ONLY:PASS");
  await expect(panel).toContainText("NODE_REFERENCE_BOUNDARY:BLOCKED");
});
