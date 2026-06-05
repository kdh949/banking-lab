import { defineConfig, devices } from "@playwright/test";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = path.dirname(fileURLToPath(import.meta.url));
process.env.BANKING_LAB_ROOT = repoRoot;
const apiBaseUrl = process.env.BANKING_LAB_E2E_API_BASE_URL ?? "";
const paymentApiBaseUrl = process.env.BANKING_LAB_E2E_PAYMENT_API_BASE_URL ?? "";
const keycloakBaseUrl = process.env.BANKING_LAB_E2E_KEYCLOAK_BASE_URL ?? "";

const apps = [
  ["customer-web", "@banking-lab/customer-web", 3001],
  ["staff-terminal", "@banking-lab/staff-terminal", 3002],
  ["complaint-portal", "@banking-lab/complaint-portal", 3003],
  ["ops-console", "@banking-lab/ops-console", 3004],
  ["audit-console", "@banking-lab/audit-console", 3005],
  ["fds-aml-console", "@banking-lab/fds-aml-console", 3006],
  ["admin-console", "@banking-lab/admin-console", 3007]
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
    command: `${apiBaseUrl ? `NEXT_PUBLIC_BANKING_API_BASE_URL=${shellQuote(apiBaseUrl)} ` : ""}${paymentApiBaseUrl ? `NEXT_PUBLIC_BANKING_PAYMENT_API_BASE_URL=${shellQuote(paymentApiBaseUrl)} ` : ""}${keycloakBaseUrl ? `NEXT_PUBLIC_BANKING_KEYCLOAK_BASE_URL=${shellQuote(keycloakBaseUrl)} BANKING_LAB_KEYCLOAK_BASE_URL=${shellQuote(keycloakBaseUrl)} ` : ""}npm --workspace ${workspace} run dev`,
    url: `http://localhost:${port}`,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000
  }))
});

function shellQuote(value: string): string {
  return `'${value.replaceAll("'", "'\\''")}'`;
}
