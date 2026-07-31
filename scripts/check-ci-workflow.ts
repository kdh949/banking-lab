import { readFile } from "node:fs/promises";

const portfolioWorkflowPath = ".github/workflows/portfolio-gate.yml";
const fullWorkflowPath = ".github/workflows/ci.yml";
const portfolioSource = await readFile(portfolioWorkflowPath, "utf8");
const fullSource = await readFile(fullWorkflowPath, "utf8");
const packageJson = JSON.parse(await readFile("package.json", "utf8")) as {
  engines?: Record<string, string>;
  packageManager?: string;
  scripts?: Record<string, string>;
};
const nvmVersion = (await readFile(".nvmrc", "utf8")).trim();
const nodeVersion = (await readFile(".node-version", "utf8")).trim();
const formalCheckerSource = await readFile("scripts/check-tla-model.ts", "utf8");
const securityEvidenceSource = await readFile("scripts/run-security-evidence.ts", "utf8");
const errors: string[] = [];

const requiredPortfolioJobs = [
  "node-contracts",
  "core-payment",
  "ops-console",
  "settlement-playwright"
];

const requiredFullJobs = [
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
  "portfolio:verify:node",
  "portfolio:verify:backend",
  "portfolio:verify:web",
  "portfolio:verify",
  "test:e2e:portfolio",
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
  "contracts:validate-runtime-events",
  "platform:validate",
  "security:evidence",
  "formal:ledger"
];

const requiredNextApps = [
  "customer-web",
  "staff-terminal",
  "complaint-portal",
  "ops-console",
  "audit-console",
  "fds-aml-console",
  "admin-console",
  "call-center-console"
];

for (const jobId of requiredPortfolioJobs) {
  if (!jobSection(portfolioSource, jobId)) {
    errors.push(`Missing portfolio gate job: ${jobId}`);
  }
}

for (const jobId of requiredFullJobs) {
  if (!jobSection(fullSource, jobId)) {
    errors.push(`Missing full validation job: ${jobId}`);
  }
}

for (const scriptName of requiredScripts) {
  if (!packageJson.scripts?.[scriptName]) {
    errors.push(`Missing package.json script: ${scriptName}`);
  }
}

for (const app of requiredNextApps) {
  if (!jobSection(fullSource, "next-builds")?.includes(`- ${app}`)) {
    errors.push(`Next.js full validation matrix is missing app: ${app}`);
  }
  for (const suffix of ["", ":typecheck", ":build"]) {
    const scriptName = `next:${app}${suffix}`;
    if (!packageJson.scripts?.[scriptName]) {
      errors.push(`Missing package.json script: ${scriptName}`);
    }
  }
}

requireJobCommand(portfolioSource, "node-contracts", "npm run portfolio:verify:node");
requireJobCommand(portfolioSource, "core-payment", "npm run portfolio:verify:backend");
requireJobCommand(portfolioSource, "ops-console", "npm run next:ops-console:typecheck");
requireJobCommand(portfolioSource, "ops-console", "npm run next:ops-console:build");
requireJobCommand(portfolioSource, "settlement-playwright", "npm run test:e2e:portfolio");
requireJobCommand(portfolioSource, "settlement-playwright", "playwright install --with-deps chromium");

requireJobCommand(fullSource, "node-and-manifests", "npm test");
requireJobCommand(fullSource, "node-and-manifests", "npm run validate:manifests");
requireJobCommand(fullSource, "node-and-manifests", "npm run packages:typecheck");
requireJobCommand(fullSource, "node-and-manifests", "npm run scripts:typecheck");
requireJobCommand(fullSource, "node-and-manifests", "npm run ci:check-workflow");
requireJobCommand(fullSource, "next-builds", "npm run next:${{ matrix.app }}:typecheck");
requireJobCommand(fullSource, "next-builds", "npm run next:${{ matrix.app }}:build");
requireJobCommand(fullSource, "backend-core-banking", "scripts/run-core-banking-tests.sh :services:core-banking:test");
requireJobCommand(fullSource, "backend-core-banking", "scripts/run-core-banking-tests.sh :services:core-banking:integrationTest");

for (const service of ["payment-service", "notification-service", "reporting-service"]) {
  requireJobCommand(fullSource, `backend-${service}`, `scripts/run-core-banking-tests.sh :services:${service}:test`);
  requireJobCommand(fullSource, `backend-${service}`, `scripts/run-core-banking-tests.sh :services:${service}:integrationTest`);
  requireJobCommand(fullSource, "backend-all-gradle", `:services:${service}:test`);
}

requireJobCommand(fullSource, "backend-all-gradle", ":services:core-banking:test");
requireJobCommand(fullSource, "platform-validation", "npm run platform:validate");
requireJobCommand(fullSource, "compose-platform-config", "docker compose config");
requireJobCommand(fullSource, "compose-platform-config", "docker compose --profile platform config");
requireJobCommand(fullSource, "contracts-validation", "tests/springScaffold.test.mjs");
requireJobCommand(fullSource, "contracts-validation", "tests/nextScaffold.test.mjs");
requireJobCommand(fullSource, "contracts-validation", "tests/stackRetirementAreaAudit.test.mjs");
requireJobCommand(fullSource, "contracts-validation", "npm run contracts:lint");
requireJobCommand(fullSource, "contracts-validation", "npm run contracts:check-client");
requireJobCommand(fullSource, "contracts-validation", "npm run contracts:check-events");
requireJobCommand(fullSource, "contracts-validation", "npm run contracts:diff-openapi");
requireJobCommand(fullSource, "contracts-validation", "npm run contracts:validate-runtime-events");
requireJobCommand(fullSource, "playwright-manifest-e2e", "npm run test:e2e");
requireJobCommand(fullSource, "security-evidence", "npm audit --audit-level=high");
requireJobCommand(fullSource, "security-evidence", "npm run security:evidence");
requireJobCommand(fullSource, "formal-model", "npm run formal:ledger");

for (const source of [portfolioSource, fullSource]) {
  if (!source.includes("node-version: 24")) {
    errors.push("Every Node-based workflow must pin Node 24.");
  }
  if (!source.includes("workflow_dispatch:")) {
    errors.push("Both workflows must keep workflow_dispatch for explicit reruns.");
  }
}

if (!portfolioSource.includes("pull_request:")) {
  errors.push("Portfolio gate must run for pull requests.");
}
if (/\npush:\s*\n\s+branches:\s*\n\s+- main/u.test(portfolioSource)) {
  errors.push("Portfolio gate must not duplicate the full main-branch validation run.");
}
if (fullSource.includes("pull_request:")) {
  errors.push("Full validation must not run on every pull request.");
}
for (const token of ["push:", "- main", "schedule:", "cron:"]) {
  if (!fullSource.includes(token)) {
    errors.push(`Full validation must keep main/manual/nightly boundary token: ${token}`);
  }
}

if (packageJson.engines?.node !== ">=24 <25") {
  errors.push("package.json must pin the supported runtime to Node >=24 <25.");
}
if (packageJson.packageManager !== "npm@10.9.2") {
  errors.push("package.json must pin npm@10.9.2.");
}
if (nvmVersion !== "24" || nodeVersion !== "24") {
  errors.push(".nvmrc and .node-version must both pin Node 24.");
}

const securityJob = jobSection(fullSource, "security-evidence") ?? "";
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

for (const token of ["totals", "skipped", "BANKING_LAB_DAST_URL", "Security evidence:"]) {
  if (!securityEvidenceSource.includes(token)) {
    errors.push(`Security evidence runner must report skip/pass/fail detail token: ${token}`);
  }
}

if (errors.length > 0) {
  throw new Error(`CI workflow hardening check failed:\n${errors.join("\n")}`);
}

console.log(
  `CI workflow hardening check passed: ${requiredPortfolioJobs.length} portfolio jobs and ${requiredFullJobs.length} full validation jobs are wired.`
);

function requireJobCommand(source: string, jobId: string, command: string): void {
  const section = jobSection(source, jobId);
  if (!section) return;
  if (!section.includes(command)) {
    errors.push(`Job ${jobId} must run: ${command}`);
  }
}

function jobSection(source: string, jobId: string): string | null {
  const escaped = jobId.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const match = new RegExp(`\\n  ${escaped}:\\n([\\s\\S]*?)(?=\\n  [A-Za-z0-9_-]+:\\n|\\n[^\\s]|$)`, "u").exec(`\n${source}`);
  return match?.[1] ?? null;
}
