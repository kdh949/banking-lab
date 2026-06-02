import { expect, test } from "@playwright/test";
import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const specDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = process.env.BANKING_LAB_ROOT || path.resolve(specDir, "../../..");
const app = "complaint-portal";
const baseUrl = "http://localhost:3003";

function manifests() {
  const dir = path.join(repoRoot, "screen-manifests", app);
  return readdirSync(dir)
    .filter((fileName) => fileName.endsWith(".json"))
    .sort()
    .map((fileName) => JSON.parse(readFileSync(path.join(dir, fileName), "utf8")));
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
  const pageSource = readFileSync(path.join(appDir, "page.jsx"), "utf8");
  const files = readdirSync(appDir).sort();

  expect(files).toEqual(["globals.css", "layout.jsx", "page.jsx"]);
  expect(pageSource).toContain("loadChannelManifests");
  expect(pageSource).not.toContain("fetch(\"/api/complaints");
  expect(pageSource).not.toContain("fetch('/api/complaints");
});
