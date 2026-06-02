import { expect, test } from "@playwright/test";
import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const specDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = process.env.BANKING_LAB_ROOT || path.resolve(specDir, "../../..");
const app = "fds-aml-console";
const baseUrl = "http://localhost:3006";

function manifests() {
  const dir = path.join(repoRoot, "screen-manifests", app);
  return readdirSync(dir)
    .filter((fileName) => fileName.endsWith(".json"))
    .sort()
    .map((fileName) => JSON.parse(readFileSync(path.join(dir, fileName), "utf8")));
}

test("FDS/AML console renders masked context, approval metadata, and workflow states from manifests", async ({ page }) => {
  const screens = manifests();
  await page.goto(baseUrl);

  await expect(page.getByRole("heading", { name: "Investigation and approval workspace" })).toBeVisible();
  for (const screen of screens) {
    await expect(page.getByText(screen.screenId, { exact: true })).toBeVisible();
  }

  await expect(page.getByText("Held transfers do not post")).toBeVisible();
  await expect(page.getByText("masked by default")).toBeVisible();
  await expect(page.getByText("FDS_RELEASE, FDS_BLOCK")).toBeVisible();
  await expect(page.getByText("AML_CASE_CLOSE")).toBeVisible();
  await expect(page.getByText("workflow timeline", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("RELEASE_REQUESTED").first()).toBeVisible();
  await expect(page.getByText("CLOSURE_REQUESTED")).toBeVisible();
});

test("FDS/AML console shell has no app-router one-off business screens", async () => {
  const appDir = path.join(repoRoot, "apps", app, "src", "app");
  const pageSource = readFileSync(path.join(appDir, "page.jsx"), "utf8");
  const files = readdirSync(appDir).sort();

  expect(files).toEqual(["globals.css", "layout.jsx", "page.jsx"]);
  expect(pageSource).toContain("loadChannelManifests");
  expect(pageSource).not.toContain("fetch(\"/api/staff/fds");
  expect(pageSource).not.toContain("fetch('/api/staff/fds");
  expect(pageSource).not.toContain("fetch(\"/api/staff/aml");
  expect(pageSource).not.toContain("fetch('/api/staff/aml");
});
