import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const specDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = process.env.BANKING_LAB_ROOT || path.resolve(specDir, "../../..");
const app = "complaint-portal";
const baseUrl = "http://localhost:3003";
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

test("complaint portal renders self-service masking, SLA, and timeline states from manifests", async ({ page }) => {
  const screens = manifests();
  await page.goto(baseUrl);

  await expect(page.getByRole("heading", { name: "Customer case workspace" })).toBeVisible();
  for (const screen of screens) {
    await expect(page.getByText(screen.screenId, { exact: true })).toBeVisible();
  }

  await expect(page.getByText("Default PII masking")).toBeVisible();
  await expect(page.getByText("CUSTOMER_SELF").first()).toBeVisible();
  await expect(page.getByText("workflow timeline", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("RECEIVED").first()).toBeVisible();
  await expect(page.getByText("WAITING_APPROVAL").first()).toBeVisible();
  await expect(page.getByText("72 hours").first()).toBeVisible();
});

test("complaint portal shell has no app-router one-off business screens", async () => {
  const appDir = path.join(repoRoot, "apps", app, "src", "app");
  const pageSource = readFileSync(path.join(appDir, "page.tsx"), "utf8");
  const files = readdirSync(appDir).sort();

  expect(files).toEqual(["api", "globals.css", "layout.tsx", "page.tsx"]);
  expect(pageSource).toContain("loadChannelManifests");
  expect(pageSource).not.toContain("fetch(\"/api/complaints");
  expect(pageSource).not.toContain("fetch('/api/complaints");
  expect(readdirSync(path.join(appDir, "api", "auth", "keycloak-token")).sort()).toEqual(["route.ts"]);
});

test("complaint portal loads a Spring API-backed case when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed channel smoke.");

  await page.goto(baseUrl);

  const panel = page.getByTestId("api-backed-complaint-case");
  await expect(panel).toContainText("case loaded", { timeout: 15_000 });
  await expect(panel).toContainText("CMP-SYN-001");
  await expect(panel).toContainText("SYN-CUS-001");
  await expect(panel).toContainText("IN_REVIEW");
});

test("complaint portal executes Spring API-backed answer approval when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed command smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Run approval smoke" }).click();
  const panel = page.getByTestId("api-backed-complaint-command");
  await expect(panel).toContainText("answer approved", { timeout: 15_000 });
  await expect(panel).toContainText("APR-");
  await expect(panel).toContainText("CMP-SYN-CMD-001");
  await expect(panel).toContainText("ANSWERED");
});

test("complaint portal shows Spring API-backed workflow state failure when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed workflow failure smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Run workflow failure smoke" }).click();
  const panel = page.getByTestId("api-backed-complaint-workflow-failure");
  await expect(panel).toContainText("workflow state rejected", { timeout: 15_000 });
  await expect(panel).toContainText("CMP-SYN-FAIL-001");
  await expect(panel).toContainText("WORKFLOW_STATE_VIOLATION");
  await expect(panel).toContainText("workflow");
  await expect(panel).toContainText("409");
  await expect(panel).toContainText("/api/staff/complaints/CMP-SYN-FAIL-001/answer-drafts");
});

test("complaint portal propagates interactive Keycloak handler and checker tokens when configured", async ({ page }) => {
  test.skip(!apiBaseUrl || !keycloakBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL and BANKING_LAB_E2E_KEYCLOAK_BASE_URL to run live complaint Keycloak browser smoke.");

  await page.goto(baseUrl);

  await page.getByRole("button", { name: "Sign in complaint handler with Keycloak" }).click();
  await expect(page).toHaveURL(/\/realms\/banking-lab\/protocol\/openid-connect\/auth/u, { timeout: 15_000 });
  await signInWithKeycloak(page, "complaint01", "complaint01-pass");

  const panel = page.getByTestId("api-backed-complaint-keycloak-login");
  await expect(panel).toContainText("Keycloak complaint case loaded", { timeout: 20_000 });
  await expect(panel).toContainText("Bearer");
  await expect(panel).toContainText("CMP-SYN-001");
  await expect(panel).toContainText("SYN-CUS-001");
  await expect(panel).toContainText("IN_REVIEW");

  await page.getByRole("button", { name: "Sign in complaint checker with Keycloak" }).click();
  await expect(page).toHaveURL(/\/realms\/banking-lab\/protocol\/openid-connect\/auth/u, { timeout: 15_000 });
  await signInWithKeycloak(page, "manager01", "manager01-pass");

  await expect(panel).toContainText("Keycloak complaint checker loaded", { timeout: 20_000 });
  await expect(panel).toContainText("manager01");

  await page.getByRole("button", { name: "Run Keycloak answer approval smoke" }).click();
  await expect(panel).toContainText("Keycloak answer approved", { timeout: 15_000 });
  await expect(panel).toContainText("APR-");
  await expect(panel).toContainText("complaint01");
  await expect(panel).toContainText("manager01");
  await expect(panel).toContainText("CMP-SYN-CMD-001");
  await expect(panel).toContainText("ANSWERED");

  await page.getByRole("button", { name: "Run Keycloak workflow failure smoke" }).click();
  await expect(panel).toContainText("Keycloak workflow state rejected", { timeout: 15_000 });
  await expect(panel).toContainText("CMP-SYN-FAIL-001");
  await expect(panel).toContainText("WORKFLOW_STATE_VIOLATION");
  await expect(panel).toContainText("workflow");
  await expect(panel).toContainText("409");
  await expect(panel).toContainText("/api/staff/complaints/CMP-SYN-FAIL-001/answer-drafts");
});
