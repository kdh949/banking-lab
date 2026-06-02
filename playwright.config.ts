import { defineConfig, devices } from "@playwright/test";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = path.dirname(fileURLToPath(import.meta.url));
process.env.BANKING_LAB_ROOT = repoRoot;

const apps = [
  ["customer-web", "@banking-lab/customer-web", 3001],
  ["staff-terminal", "@banking-lab/staff-terminal", 3002],
  ["complaint-portal", "@banking-lab/complaint-portal", 3003],
  ["ops-console", "@banking-lab/ops-console", 3004],
  ["audit-console", "@banking-lab/audit-console", 3005],
  ["fds-aml-console", "@banking-lab/fds-aml-console", 3006]
] as const;

export default defineConfig({
  testDir: ".",
  testMatch: /apps\/[^/]+\/e2e\/.*\.spec\.ts/,
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: [["list"]],
  use: {
    trace: "on-first-retry"
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] }
    }
  ],
  webServer: apps.map(([, workspace, port]) => ({
    command: `npm --workspace ${workspace} run dev`,
    url: `http://localhost:${port}`,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000
  }))
});
