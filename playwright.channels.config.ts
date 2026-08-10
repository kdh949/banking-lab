import { defineConfig, devices } from "@playwright/test";

const apiBaseUrl = process.env.BANKING_LAB_E2E_API_BASE_URL ?? "";
const notificationApiBaseUrl = process.env.BANKING_LAB_E2E_NOTIFICATION_API_BASE_URL ?? "";

const apps = [
  ["@banking-lab/customer-web", 3001, true],
  ["@banking-lab/staff-terminal", 3002, false],
  ["@banking-lab/call-center-console", 3008, false]
] as const;

export default defineConfig({
  testDir: "apps/customer-web/e2e",
  testMatch: "cross-channel-held-transfer.spec.ts",
  fullyParallel: false,
  forbidOnly: true,
  retries: 0,
  timeout: 180_000,
  reporter: [["list"]],
  use: {
    ...devices["Desktop Chrome"],
    trace: "retain-on-failure"
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: apps.map(([workspace, port, customer]) => ({
    command: `${apiBaseUrl ? `BANKING_LAB_API_BASE_URL=${shellQuote(apiBaseUrl)} ` : ""}${customer && notificationApiBaseUrl ? `BANKING_LAB_NOTIFICATION_API_BASE_URL=${shellQuote(notificationApiBaseUrl)} ` : ""}npm --workspace ${workspace} run dev`,
    url: `http://localhost:${port}`,
    reuseExistingServer: false,
    timeout: 120_000
  }))
});

function shellQuote(value: string): string {
  return `'${value.replaceAll("'", "'\\''")}'`;
}
