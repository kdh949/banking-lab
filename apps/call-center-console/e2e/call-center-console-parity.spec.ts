import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const specDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = process.env.BANKING_LAB_ROOT || path.resolve(specDir, "../../..");
const app = "call-center-console";
const baseUrl = "http://localhost:3008";
const apiBaseUrl = process.env.BANKING_LAB_E2E_API_BASE_URL ?? "";
const keycloakBaseUrl = process.env.BANKING_LAB_E2E_KEYCLOAK_BASE_URL ?? "";

async function signInWithKeycloak(page: Page, username: string, password: string) {
  const restartLogin = page.getByRole("button", { name: "Restart login" });
  if (await restartLogin.isVisible().catch(() => false)) {
    await restartLogin.click();
  }
  await page.locator("input[name='username']").fill(username);
  await page.locator("input[name='password']").fill(password);
  await page.getByRole("button", { name: "Sign In" }).click();
}

function manifests() {
  const dir = path.join(repoRoot, "screen-manifests", app);
  return readdirSync(dir)
    .filter((fileName) => fileName.endsWith(".json"))
    .sort()
    .map((fileName) => JSON.parse(readFileSync(path.join(dir, fileName), "utf8")));
}

test("call-center console product workspace excludes lab evidence controls", async ({ page }) => {
  await page.goto(baseUrl);

  await expect(page.locator(`[data-channel-shell="${app}"]`)).toBeVisible();
  await expect(page.getByRole("heading", { name: "Agent Workspace" })).toBeVisible();
  await expect(page.getByText("Reason-gated masked customer 360")).toBeVisible();
  await expect(page.getByText("LAB_ONLY")).toHaveCount(0);
  await expect(page.getByTestId("api-backed-call-center-search")).toHaveCount(0);

  await page.goto(`${baseUrl}/workspace`);
  await expect(page.getByRole("button", { name: "Simulate inbound call" })).toBeVisible();
  await expect(page.getByLabel("Business reason")).toBeVisible();
  await expect(page.getByLabel("Journey ID")).toBeVisible();
  await expect(page.getByRole("button", { name: "Search masked customer" })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Request FDS handoff" })).toBeDisabled();
  await expect(page.getByText("Authenticated BFF session required")).toBeVisible();
});

test("call-center softphone simulator exposes every required synthetic state", async ({ page }) => {
  await page.goto(`${baseUrl}/workspace`);
  const advance = page.getByRole("button", { name: /Simulate inbound call|Advance call state/u });
  for (const state of ["RINGING", "CONNECTED", "HOLD", "AFTER_CALL", "IDLE"]) {
    await advance.click();
    await expect(page.getByText(state, { exact: true }).first()).toBeVisible();
  }
});

test("call-center console lab catalog renders masked interaction manifests", async ({ page }) => {
  const screens = manifests();
  await page.goto(`${baseUrl}/lab/manifests`);

  await expect(page.locator(`[data-channel-shell="${app}"]`)).toBeVisible();
  await expect(page.getByRole("heading", { name: "Call-Center Screen Manifests" })).toBeVisible();
  for (const screen of screens) {
    await expect(page.getByText(screen.screenId, { exact: true })).toBeVisible();
  }

  await expect(page.getByText(/LAB_ONLY/)).toBeVisible();
  await expect(page.getByText("reason required").first()).toBeVisible();
  await expect(page.getByText("CALL_CENTER_NOTE_REDACTION")).toBeVisible();
  await expect(page.getByText("CALL_CENTER_ESCALATION_REDACTION")).toBeVisible();
  await expect(page.getByText("workflow timeline", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("AFTERCALL", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("ESCALATED", { exact: true }).first()).toBeVisible();
});

test("call-center console keeps product and lab route boundaries explicit", async () => {
  const appDir = path.join(repoRoot, "apps", app, "src", "app");
  const pageSource = readFileSync(path.join(appDir, "page.tsx"), "utf8");
  const files = readdirSync(appDir).sort();

  expect(files).toEqual(["api", "globals.css", "lab", "layout.tsx", "page.tsx", "workspace"]);
  expect(pageSource).toContain("CallCenterWorkspace");
  expect(pageSource).not.toContain("loadChannelManifests");
  expect(pageSource).not.toContain("ApiBackedCallCenterPanel");
  expect(pageSource).not.toContain("fetch(\"/api/staff/call-center");
  expect(pageSource).not.toContain("fetch('/api/staff/call-center");
  expect(readdirSync(path.join(appDir, "api", "auth", "keycloak-token")).sort()).toEqual(["route.ts"]);
});

test("call-center console executes Spring API-backed synthetic workflow when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed call-center smoke.");

  await page.goto(`${baseUrl}/lab/evidence`);

  const searchPanel = page.getByTestId("api-backed-call-center-search");
  await expect(searchPanel).toContainText("customers loaded", { timeout: 15_000 });
  await expect(searchPanel).toContainText("masked by default");

  await page.getByRole("button", { name: "Run call-center workflow smoke" }).click();
  const workflowPanel = page.getByTestId("api-backed-call-center-workflow");
  await expect(workflowPanel).toContainText("call-center workflow completed", { timeout: 20_000 });
  await expect(workflowPanel).toContainText("CALL-");
  await expect(workflowPanel).toContainText("CLOSED");
  await expect(workflowPanel).toContainText("applied");
  await expect(workflowPanel).toContainText("COMPLAINT CMP-");
});

test("call-center console completes live Keycloak PKCE login through the opaque product BFF", async ({ page }) => {
  test.skip(!apiBaseUrl || !keycloakBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL and BANKING_LAB_E2E_KEYCLOAK_BASE_URL to run live call-center Keycloak browser smoke.");

  await page.goto(`${baseUrl}/workspace`);

  await page.getByRole("link", { name: "Sign in with Keycloak" }).click();
  await expect(page).toHaveURL(/\/realms\/banking-lab\/protocol\/openid-connect\/auth/u, { timeout: 15_000 });
  await signInWithKeycloak(page, "call-agent01", "call-agent01-pass");

  await expect(page).toHaveURL(`${baseUrl}/workspace`, { timeout: 20_000 });
  await expect(page.getByText(/call-agent01 · CALL_CENTER_AGENT · OIDC/u)).toBeVisible();
  await page.getByRole("button", { name: "Simulate inbound call" }).click();
  await page.getByLabel("Business reason").fill("Live Keycloak BFF masked lookup verification");
  await page.getByLabel("Customer query").fill("SYN-CUS");
  await page.getByRole("button", { name: "Search masked customer" }).click();
  await expect(page.getByText("Masked customer context loaded and audited.")).toBeVisible({ timeout: 20_000 });
  await expect(page.getByText(/010-\*\*\*\*/u).first()).toBeVisible();
});
