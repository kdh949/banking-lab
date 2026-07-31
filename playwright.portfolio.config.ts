import { defineConfig, devices } from "@playwright/test";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = path.dirname(fileURLToPath(import.meta.url));
process.env.BANKING_LAB_ROOT = repoRoot;
process.env.BANKING_LAB_E2E_PAYMENT_API_BASE_URL = "http://127.0.0.1:3004";

export default defineConfig({
  testDir: path.join(repoRoot, "apps", "ops-console", "e2e"),
  testMatch: "settlement-operations.spec.ts",
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [["list"]],
  use: {
    baseURL: "http://127.0.0.1:3004",
    trace: "on-first-retry",
    screenshot: "only-on-failure"
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] }
    }
  ],
  webServer: {
    command:
      "NEXT_PUBLIC_BANKING_PAYMENT_API_BASE_URL=http://127.0.0.1:3004 " +
      "npm --workspace @banking-lab/ops-console run dev",
    url: "http://127.0.0.1:3004",
    reuseExistingServer: !process.env.CI,
    timeout: 120_000
  }
});
