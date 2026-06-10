import { expect, test } from "@playwright/test";
import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const specDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = process.env.BANKING_LAB_ROOT || path.resolve(specDir, "../../..");
const app = "call-center-console";
const baseUrl = "http://localhost:3008";
const apiBaseUrl = process.env.BANKING_LAB_E2E_API_BASE_URL ?? "";

function manifests() {
  const dir = path.join(repoRoot, "screen-manifests", app);
  return readdirSync(dir)
    .filter((fileName) => fileName.endsWith(".json"))
    .sort()
    .map((fileName) => JSON.parse(readFileSync(path.join(dir, fileName), "utf8")));
}

test("call-center console renders masked interaction controls from manifests", async ({ page }) => {
  const screens = manifests();
  await page.goto(baseUrl);

  await expect(page.locator(`[data-channel-shell="${app}"]`)).toBeVisible();
  await expect(page.getByRole("heading", { name: "Customer interaction and escalation workspace" })).toBeVisible();
  for (const screen of screens) {
    await expect(page.getByText(screen.screenId, { exact: true })).toBeVisible();
  }

  await expect(page.getByText("Synthetic masked workflow only")).toBeVisible();
  await expect(page.getByText("reason required").first()).toBeVisible();
  await expect(page.getByText("CALL_CENTER_NOTE_REDACTION")).toBeVisible();
  await expect(page.getByText("CALL_CENTER_ESCALATION_REDACTION")).toBeVisible();
  await expect(page.getByText("workflow timeline", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("AFTERCALL", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("ESCALATED", { exact: true }).first()).toBeVisible();
});

test("call-center console shell has no app-router one-off business screens", async () => {
  const appDir = path.join(repoRoot, "apps", app, "src", "app");
  const pageSource = readFileSync(path.join(appDir, "page.tsx"), "utf8");
  const files = readdirSync(appDir).sort();

  expect(files).toEqual(["globals.css", "layout.tsx", "page.tsx"]);
  expect(pageSource).toContain("loadChannelManifests");
  expect(pageSource).not.toContain("fetch(\"/api/staff/call-center");
  expect(pageSource).not.toContain("fetch('/api/staff/call-center");
});

test("call-center console executes Spring API-backed synthetic workflow when configured", async ({ page }) => {
  test.skip(!apiBaseUrl, "Set BANKING_LAB_E2E_API_BASE_URL to run API-backed call-center smoke.");

  await page.goto(baseUrl);

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
