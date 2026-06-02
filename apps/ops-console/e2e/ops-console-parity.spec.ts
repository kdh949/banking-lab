import { expect, test } from "@playwright/test";
import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const specDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = process.env.BANKING_LAB_ROOT || path.resolve(specDir, "../../..");
const app = "ops-console";
const baseUrl = "http://localhost:3004";

function manifests() {
  const dir = path.join(repoRoot, "screen-manifests", app);
  return readdirSync(dir)
    .filter((fileName) => fileName.endsWith(".json"))
    .sort()
    .map((fileName) => JSON.parse(readFileSync(path.join(dir, fileName), "utf8")));
}

test("ops console renders closing, reconciliation, approval, and workflow controls from manifests", async ({ page }) => {
  const screens = manifests();
  await page.goto(baseUrl);

  await expect(page.getByRole("heading", { name: "Closing and reconciliation controls" })).toBeVisible();
  for (const screen of screens) {
    await expect(page.getByText(screen.screenId, { exact: true })).toBeVisible();
  }

  await expect(page.getByText("Balanced adjustments only")).toBeVisible();
  await expect(page.getByText("Mismatch corrections require approval")).toBeVisible();
  await expect(page.getByText("RECONCILIATION_ADJUSTMENT")).toBeVisible();
  await expect(page.getByText("workflow timeline", { exact: true })).toBeVisible();
  await expect(page.getByText("ADJUSTMENT_REQUESTED")).toBeVisible();
  await expect(page.getByText("reason required").first()).toBeVisible();
});

test("ops console shell has no app-router one-off business screens", async () => {
  const appDir = path.join(repoRoot, "apps", app, "src", "app");
  const pageSource = readFileSync(path.join(appDir, "page.jsx"), "utf8");
  const files = readdirSync(appDir).sort();

  expect(files).toEqual(["globals.css", "layout.jsx", "page.jsx"]);
  expect(pageSource).toContain("loadChannelManifests");
  expect(pageSource).not.toContain("fetch(\"/api/ops");
  expect(pageSource).not.toContain("fetch('/api/ops");
});
