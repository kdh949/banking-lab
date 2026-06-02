import { expect, test } from "@playwright/test";
import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const specDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = process.env.BANKING_LAB_ROOT || path.resolve(specDir, "../../..");
const app = "customer-web";
const baseUrl = "http://localhost:3001";

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

  await expect(page.getByRole("heading", { name: "Customer Web Banking" })).toBeVisible();
  for (const screen of screens) {
    await expect(page.getByText(screen.screenId, { exact: true }).first()).toBeVisible();
  }

  await expect(page.getByText("Default PII masking")).toBeVisible();
  await expect(page.getByText("CUSTOMER_SELF").first()).toBeVisible();
  await expect(page.getByText("Complaint Entry")).toBeVisible();
  await expect(page.getByText("workflow timeline", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("WAITING_APPROVAL").first()).toBeVisible();
});

test("customer web shell has no app-router one-off business screens", async () => {
  const appDir = path.join(repoRoot, "apps", app, "src", "app");
  const pageSource = readFileSync(path.join(appDir, "page.tsx"), "utf8");
  const files = readdirSync(appDir).sort();

  expect(files).toEqual(["globals.css", "layout.tsx", "page.tsx"]);
  expect(pageSource).toContain("loadCustomerWebManifests");
  expect(pageSource).not.toContain("fetch(\"/api/customer/transfers\"");
  expect(pageSource).not.toContain("fetch('/api/customer/transfers'");
});
