import { readFile } from "node:fs/promises";

const workflowPath = ".github/workflows/ci.yml";
const source = await readFile(workflowPath, "utf8");
const errors: string[] = [];

const requiredJobs = [
  "node-and-manifests",
  "next-builds",
  "backend-core-banking",
  "backend-payment-service",
  "backend-notification-service",
  "backend-reporting-service",
  "backend-all-gradle",
  "platform-validation",
  "contracts-validation",
  "compose-platform-config",
  "playwright-manifest-e2e",
  "security-evidence",
  "formal-model"
];

for (const jobId of requiredJobs) {
  if (!jobSection(jobId)) {
    errors.push(`Missing CI job: ${jobId}`);
  }
}

requireJobCommand("node-and-manifests", "npm run ci:check-workflow");
requireJobCommand("backend-core-banking", "scripts/run-core-banking-tests.sh :services:core-banking:test");
requireJobCommand("backend-core-banking", "scripts/run-core-banking-tests.sh :services:core-banking:integrationTest");

for (const service of ["payment-service", "notification-service", "reporting-service"]) {
  requireJobCommand(`backend-${service}`, `scripts/run-core-banking-tests.sh :services:${service}:test`);
  requireJobCommand(`backend-${service}`, `scripts/run-core-banking-tests.sh :services:${service}:integrationTest`);
  requireJobCommand("backend-all-gradle", `:services:${service}:test`);
}

requireJobCommand("backend-all-gradle", ":services:core-banking:test");
requireJobCommand("platform-validation", "npm run platform:validate");
requireJobCommand("compose-platform-config", "docker compose config");
requireJobCommand("compose-platform-config", "docker compose --profile platform config");
requireJobCommand("contracts-validation", "tests/springScaffold.test.mjs");
requireJobCommand("contracts-validation", "tests/nextScaffold.test.mjs");
requireJobCommand("contracts-validation", "tests/stackRetirementAreaAudit.test.mjs");
requireJobCommand("contracts-validation", "npm run contracts:lint");
requireJobCommand("contracts-validation", "npm run contracts:check-client");
requireJobCommand("contracts-validation", "npm run contracts:check-events");

if (!source.includes("workflow_dispatch:")) {
  errors.push("CI workflow must keep workflow_dispatch for manual runs.");
}

if (errors.length > 0) {
  throw new Error(`CI workflow hardening check failed:\n${errors.join("\n")}`);
}

console.log(`CI workflow hardening check passed: ${requiredJobs.length} required jobs are wired.`);

function requireJobCommand(jobId: string, command: string): void {
  const section = jobSection(jobId);
  if (!section) return;
  if (!section.includes(command)) {
    errors.push(`Job ${jobId} must run: ${command}`);
  }
}

function jobSection(jobId: string): string | null {
  const escaped = jobId.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const match = new RegExp(`\\n  ${escaped}:\\n([\\s\\S]*?)(?=\\n  [A-Za-z0-9_-]+:\\n|\\n[^\\s]|$)`, "u").exec(`\n${source}`);
  return match?.[1] ?? null;
}
