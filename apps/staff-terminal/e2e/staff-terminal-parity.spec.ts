import { expect, test } from "@playwright/test";
import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const specDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = process.env.BANKING_LAB_ROOT || path.resolve(specDir, "../../..");
const app = "staff-terminal";
const baseUrl = "http://localhost:3002";

function manifests() {
  const dir = path.join(repoRoot, "screen-manifests", app);
  return readdirSync(dir)
    .filter((fileName) => fileName.endsWith(".json"))
    .sort()
    .map((fileName) => JSON.parse(readFileSync(path.join(dir, fileName), "utf8")));
}

test("staff terminal renders reason, masking, and maker-checker controls from manifests", async ({ page }) => {
  const screens = manifests();
  const reasonRequired = screens.filter((screen) => screen.audit.reasonRequired).length;
  const makerChecker = screens.filter((screen) => screen.approval?.required).length;

  await page.goto(baseUrl);

  await expect(page.getByRole("heading", { name: "Transaction-code workspace" })).toBeVisible();
  await expect(page.getByLabel("Transaction code")).toHaveValue(screens[0].transactionCode);
  await expect(page.locator(".metric", { hasText: String(reasonRequired) })).toBeVisible();
  await expect(page.getByText("reason-required")).toBeVisible();
  await expect(page.locator(".metric", { hasText: String(makerChecker) })).toBeVisible();
  await expect(page.getByText("maker-checker").first()).toBeVisible();
  await expect(page.getByText("Masked by default")).toBeVisible();
  await expect(page.getByText("Business reason required")).toBeVisible();
  await expect(page.getByText("APR-001 declared")).toBeVisible();
});

test("staff terminal shell has no app-router one-off business screens", async () => {
  const appDir = path.join(repoRoot, "apps", app, "src", "app");
  const pageSource = readFileSync(path.join(appDir, "page.jsx"), "utf8");
  const files = readdirSync(appDir).sort();

  expect(files).toEqual(["globals.css", "layout.jsx", "page.jsx"]);
  expect(pageSource).toContain("loadChannelManifests");
  expect(pageSource).not.toContain("fetch(\"/api/staff");
  expect(pageSource).not.toContain("fetch('/api/staff");
});
