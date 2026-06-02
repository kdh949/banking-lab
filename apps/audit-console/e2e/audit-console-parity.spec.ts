import { expect, test } from "@playwright/test";
import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const specDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = process.env.BANKING_LAB_ROOT || path.resolve(specDir, "../../..");
const app = "audit-console";
const baseUrl = "http://localhost:3005";

function manifests() {
  const dir = path.join(repoRoot, "screen-manifests", app);
  return readdirSync(dir)
    .filter((fileName) => fileName.endsWith(".json"))
    .sort()
    .map((fileName) => JSON.parse(readFileSync(path.join(dir, fileName), "utf8")));
}

test("audit console renders hash-chain evidence and retention approval metadata from manifests", async ({ page }) => {
  const screens = manifests();
  await page.goto(baseUrl);

  await expect(page.getByRole("heading", { name: "Hash-chain review workspace" })).toBeVisible();
  for (const screen of screens) {
    await expect(page.getByText(screen.screenId, { exact: true })).toBeVisible();
  }

  await expect(page.getByText("Append-only evidence view")).toBeVisible();
  await expect(page.getByText("previousEventHash")).toBeVisible();
  await expect(page.getByText("payloadHash")).toBeVisible();
  await expect(page.getByText("AUDIT_PARAMETER_CHANGE")).toBeVisible();
  await expect(page.getByText("maker-checker")).toBeVisible();
  await expect(page.getByText("reason required")).toBeVisible();
});

test("audit console shell has no app-router one-off business screens", async () => {
  const appDir = path.join(repoRoot, "apps", app, "src", "app");
  const pageSource = readFileSync(path.join(appDir, "page.jsx"), "utf8");
  const files = readdirSync(appDir).sort();

  expect(files).toEqual(["globals.css", "layout.jsx", "page.jsx"]);
  expect(pageSource).toContain("loadChannelManifests");
  expect(pageSource).not.toContain("fetch(\"/api/staff/audit");
  expect(pageSource).not.toContain("fetch('/api/staff/audit");
});
