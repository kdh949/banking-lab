import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const specDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = process.env.BANKING_LAB_ROOT || path.resolve(specDir, "../../..");
const app = "fds-aml-console";
const baseUrl = "http://localhost:3006";
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

test("FDS/AML console renders masked context, approval metadata, and workflow states from manifests", async ({ page }) => {
  const screens = manifests();
  await page.goto(baseUrl);

  await expect(page.locator(`[data-channel-shell="${app}"]`)).toBeVisible();
  await expect(page.getByRole("heading", { name: "Investigation and approval workspace" })).toBeVisible();
  for (const screen of screens) {
    await expect(page.getByText(screen.screenId, { exact: true })).toBeVisible();
  }

  await expect(page.getByText("Held transfers do not post")).toBeVisible();
  await expect(page.getByText("masked by default")).toBeVisible();
  await expect(page.getByText("FDS_RELEASE, FDS_BLOCK").first()).toBeVisible();
  await expect(page.getByText("AML_CASE_CLOSE")).toBeVisible();
  await expect(page.getByText("workflow timeline", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("RELEASE_REQUESTED").first()).toBeVisible();
  await expect(page.getByText("CLOSURE_REQUESTED").first()).toBeVisible();
});

test("FDS/AML console shell has no app-router one-off business screens", async () => {
  const appDir = path.join(repoRoot, "apps", app, "src", "app");
  const pageSource = readFileSync(path.join(appDir, "page.tsx"), "utf8");
  const files = readdirSync(appDir).sort();

  expect(files).toEqual(["api", "globals.css", "layout.tsx", "page.tsx"]);
  expect(pageSource).toContain("loadChannelManifests");
  expect(pageSource).not.toContain("fetch(\"/api/staff/fds");
  expect(pageSource).not.toContain("fetch('/api/staff/fds");
  expect(pageSource).not.toContain("fetch(\"/api/staff/aml");
  expect(pageSource).not.toContain("fetch('/api/staff/aml");
  expect(readdirSync(path.join(appDir, "api", "auth", "keycloak-token")).sort()).toEqual(["route.ts"]);
});

test("FDS/AML console loads Spring API-backed risk cases when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed channel smoke.");

  await page.goto(baseUrl);

  const panel = page.getByTestId("api-backed-risk-cases");
  await expect(panel).toContainText("risk cases loaded", { timeout: 15_000 });
  await expect(panel).toContainText("FDS-SYN-001");
  await expect(panel).toContainText("AML-SYN-001");
  await expect(panel).toContainText("INVESTIGATING");
});

test("FDS/AML console executes Spring API-backed FDS release approval when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed command smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Run FDS release smoke" }).click();
  const panel = page.getByTestId("api-backed-fds-command");
  await expect(panel).toContainText("FDS release approved", { timeout: 15_000 });
  await expect(panel).toContainText("APR-");
  await expect(panel).toContainText("FDS-SYN-CMD-001");
  await expect(panel).toContainText("RELEASED");
  await expect(panel).toContainText("TX-");
});

test("FDS/AML console requests Spring API-backed FDS parameter change when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed parameter command smoke.");

  await page.goto(baseUrl);

  const panel = page.getByTestId("api-backed-fds-parameters");
  await expect(panel).toContainText("FDS parameters loaded", { timeout: 15_000 });
  await expect(panel).toContainText("highAmountMinor");
  await page.getByRole("button", { name: "Run FDS parameter change smoke" }).click();
  await expect(panel).toContainText("FDS parameter change requested", { timeout: 15_000 });
  await expect(panel).toContainText("PENDING_APPROVAL");
  await expect(panel).toContainText("APR-");
  await expect(panel).toContainText("FPC-");
});

test("FDS/AML console executes Spring API-backed FDS block approval when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed command smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Run FDS block smoke" }).click();
  const panel = page.getByTestId("api-backed-fds-block-command");
  await expect(panel).toContainText("FDS block approved", { timeout: 15_000 });
  await expect(panel).toContainText("APR-");
  await expect(panel).toContainText("FDS-SYN-BLOCK-CMD-001");
  await expect(panel).toContainText("BLOCKED");
  await expect(panel).toContainText("not posted");
});

test("FDS/AML console executes Spring API-backed AML closure approval when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed command smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Run AML closure smoke" }).click();
  const panel = page.getByTestId("api-backed-aml-command");
  await expect(panel).toContainText("AML closure approved", { timeout: 15_000 });
  await expect(panel).toContainText("APR-");
  await expect(panel).toContainText("AML-SYN-CMD-001");
  await expect(panel).toContainText("CLOSED");
  await expect(panel).toContainText("STR_SIMULATED");
});

test("FDS/AML console shows Spring API-backed FDS workflow state failure when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed workflow failure smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Run FDS failure smoke" }).click();
  const panel = page.getByTestId("api-backed-fds-workflow-failure");
  await expect(panel).toContainText("workflow state rejected", { timeout: 15_000 });
  await expect(panel).toContainText("FDS-SYN-FAIL-001");
  await expect(panel).toContainText("WORKFLOW_STATE_VIOLATION");
  await expect(panel).toContainText("workflow");
  await expect(panel).toContainText("409");
  await expect(panel).toContainText("/api/staff/fds-cases/FDS-SYN-FAIL-001/release-requests");
});

test("FDS/AML console shows Spring API-backed AML workflow state failure when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed workflow failure smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Run AML failure smoke" }).click();
  const panel = page.getByTestId("api-backed-aml-workflow-failure");
  await expect(panel).toContainText("workflow state rejected", { timeout: 15_000 });
  await expect(panel).toContainText("AML-SYN-FAIL-001");
  await expect(panel).toContainText("WORKFLOW_STATE_VIOLATION");
  await expect(panel).toContainText("workflow");
  await expect(panel).toContainText("409");
  await expect(panel).toContainText("/api/staff/aml-cases/AML-SYN-FAIL-001/closure-requests");
});

test("FDS/AML console propagates interactive Keycloak reviewer and checker tokens when configured", async ({ page }) => {
  test.skip(!apiBaseUrl || !keycloakBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL and BANKING_LAB_E2E_KEYCLOAK_BASE_URL to run live risk Keycloak browser smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Sign in risk reviewer with Keycloak" }).click();
  await expect(page).toHaveURL(/\/realms\/banking-lab\/protocol\/openid-connect\/auth/u, { timeout: 15_000 });
  await signInWithKeycloak(page, "risk01", "risk01-pass");

  const panel = page.getByTestId("api-backed-risk-keycloak-login");
  await expect(panel).toContainText("Keycloak risk cases loaded", { timeout: 20_000 });
  await expect(panel).toContainText("Bearer");
  await expect(panel).toContainText("FDS-SYN-001");
  await expect(panel).toContainText("AML-SYN-001");
  await expect(panel).toContainText("INVESTIGATING");

  await page.getByRole("button", { name: "Sign in risk checker with Keycloak" }).click();
  await expect(page).toHaveURL(/\/realms\/banking-lab\/protocol\/openid-connect\/auth/u, { timeout: 15_000 });
  await signInWithKeycloak(page, "compliance01", "compliance01-pass");

  await expect(panel).toContainText("Keycloak risk checker loaded", { timeout: 20_000 });
  await expect(panel).toContainText("compliance01");

  await page.getByRole("button", { name: "Run Keycloak risk approval smoke" }).click();
  await expect(panel).toContainText("Keycloak risk approvals completed", { timeout: 20_000 });
  await expect(panel).toContainText("risk01");
  await expect(panel).toContainText("compliance01");
  await expect(panel).toContainText("FDS-SYN-OIDC-REL-001");
  await expect(panel).toContainText("RELEASED");
  await expect(panel).toContainText("TX-");
  await expect(panel).toContainText("FDS-SYN-OIDC-BLOCK-001");
  await expect(panel).toContainText("BLOCKED");
  await expect(panel).toContainText("not posted");
  await expect(panel).toContainText("AML-SYN-OIDC-CLOSE-001");
  await expect(panel).toContainText("CLOSED");
  await expect(panel).toContainText("STR_SIMULATED");

  await page.getByRole("button", { name: "Run Keycloak risk failure smoke" }).click();
  await expect(panel).toContainText("workflow state rejected", { timeout: 15_000 });
  await expect(panel).toContainText("FDS-SYN-FAIL-001");
  await expect(panel).toContainText("AML-SYN-FAIL-001");
  await expect(panel).toContainText("WORKFLOW_STATE_VIOLATION");
  await expect(panel).toContainText("workflow");
  await expect(panel).toContainText("409");
});
