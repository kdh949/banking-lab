import { readFile } from "node:fs/promises";

const workflowPath = ".github/workflows/ci.yml";
const source = await readFile(workflowPath, "utf8");
const packageJson = JSON.parse(await readFile("package.json", "utf8")) as { scripts?: Record<string, string> };
const formalCheckerSource = await readFile("scripts/check-tla-model.ts", "utf8");
const securityEvidenceSource = await readFile("scripts/run-security-evidence.ts", "utf8");
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

const requiredScripts = [
  "test",
  "validate:manifests",
  "packages:typecheck",
  "scripts:typecheck",
  "ci:check-workflow",
  "test:core-banking:unit",
  "test:core-banking:integration",
  "test:payment-service:unit",
  "test:payment-service:integration",
  "test:notification-service:unit",
  "test:notification-service:integration",
  "test:reporting-service:unit",
  "test:reporting-service:integration",
  "contracts:lint",
  "contracts:check-client",
  "contracts:check-events",
  "contracts:diff-openapi",
  "platform:validate",
  "security:evidence",
  "formal:ledger"
];

for (const jobId of requiredJobs) {
  if (!jobSection(jobId)) {
    errors.push(`Missing CI job: ${jobId}`);
  }
}

for (const scriptName of requiredScripts) {
  if (!packageJson.scripts?.[scriptName]) {
    errors.push(`Missing package.json script: ${scriptName}`);
  }
}

requireJobCommand("node-and-manifests", "npm test");
requireJobCommand("node-and-manifests", "npm run validate:manifests");
requireJobCommand("node-and-manifests", "npm run packages:typecheck");
requireJobCommand("node-and-manifests", "npm run scripts:typecheck");
requireJobCommand("node-and-manifests", "npm run ci:check-workflow");
requireJobCommand("next-builds", "npm run next:${{ matrix.app }}:typecheck");
requireJobCommand("next-builds", "npm run next:${{ matrix.app }}:build");
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
requireJobCommand("contracts-validation", "npm run contracts:diff-openapi");
requireJobCommand("playwright-manifest-e2e", "npm run test:e2e");
requireJobCommand("security-evidence", "npm audit --audit-level=high");
requireJobCommand("security-evidence", "npm run security:evidence");
requireJobCommand("formal-model", "npm run formal:ledger");

const securityJob = jobSection("security-evidence") ?? "";
if (/continue-on-error:\s*true/u.test(securityJob)) {
  errors.push("security-evidence job must not be marked continue-on-error.");
}

for (const token of [
  "process.env.CI",
  "process.env.GITHUB_ACTIONS",
  "BANKING_LAB_ALLOW_FORMAL_STATIC_ONLY",
  "Static-only formal checks are not allowed"
]) {
  if (!formalCheckerSource.includes(token)) {
    errors.push(`Formal checker must enforce non-static CI evidence token: ${token}`);
  }
}

for (const token of [
  "totals",
  "skipped",
  "BANKING_LAB_DAST_URL",
  "Security evidence:"
]) {
  if (!securityEvidenceSource.includes(token)) {
    errors.push(`Security evidence runner must report skip/pass/fail detail token: ${token}`);
  }
}

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
