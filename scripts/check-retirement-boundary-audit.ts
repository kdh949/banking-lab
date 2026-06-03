import { access, readdir, readFile } from "node:fs/promises";
import { join } from "node:path";

type RequiredGate = {
  id: string;
  status: string;
  evidence?: string[];
};

type NodeRetirementGate = {
  status: string;
  nodeReferenceRuntime: {
    paths: string[];
  };
  requiredGates: RequiredGate[];
};

type ParitySuite = {
  nodeSuite: string;
  scenarioCount: number;
  targetSuite: string;
  targetStatus?: string;
};

type ParityScenarioMap = {
  nodeReferenceTestCount: number;
  suites: ParitySuite[];
};

const gatePath = "docs/migration/node-retirement-gate.json";
const parityPath = "docs/migration/parity-scenarios.json";
const ignoredDirs = new Set([
  ".git",
  ".gradle",
  ".next",
  ".turbo",
  "build",
  "coverage",
  "dist",
  "node_modules",
  "playwright-report",
  "test-results"
]);

const scanRoots = [
  "runtime",
  "scripts",
  "tests",
  "packages",
  "legacy-node-reference",
  "services",
  "apps",
  "analytics",
  "db",
  "infra",
  "contracts"
];

const allowedMjsPrefixes = [
  "runtime/",
  "scripts/",
  "tests/",
  "legacy-node-reference/",
  "packages/banking-domain/src/",
  "packages/screen-engine/src/",
  "packages/form-engine/src/"
];

const requiredReferencePaths = [
  "runtime/server.mjs",
  "runtime/labApp.mjs",
  "legacy-node-reference/services",
  "tests"
];

const targetAnchors = [
  "services/core-banking/build.gradle.kts",
  "services/core-banking/src/main/kotlin/lab/banking/core/CoreBankingApplication.kt",
  "services/core-banking/src/main/kotlin/lab/banking/core/ledger/application/LedgerCommandService.kt",
  "services/core-banking/src/main/kotlin/lab/banking/core/eventing/DurableOutboxService.kt",
  "services/core-banking/src/main/kotlin/lab/banking/core/temporal/BankingCaseTemporalWorkflow.kt",
  "services/core-banking/src/main/kotlin/lab/banking/core/security/SignedJwtJwksTokenDecoder.kt",
  "db/migrations/V001__foundation.sql",
  "db/migrations/V004__outbox_inbox.sql",
  "contracts/asyncapi/banking-lab-events.yaml",
  "contracts/temporal/workflows.yaml",
  "infra/keycloak/realm-banking-lab.json",
  "infra/helm/banking-lab/Chart.yaml",
  "infra/terraform/main.tf",
  "infra/argocd/banking-lab-app.yaml",
  "infra/observability/prometheus/prometheus.yml",
  "infra/observability/loki/loki.yml",
  "infra/observability/tempo/tempo.yml",
  "analytics/aml-fds-python/pyproject.toml",
  "apps/customer-web/src/app/page.tsx",
  "apps/customer-web/src/components/ApiBackedCustomerPanel.tsx",
  "apps/staff-terminal/src/app/page.tsx",
  "apps/staff-terminal/src/components/ApiBackedStaffPanel.tsx",
  "apps/complaint-portal/src/app/page.tsx",
  "apps/complaint-portal/src/components/ApiBackedComplaintPanel.tsx",
  "apps/ops-console/src/app/page.tsx",
  "apps/ops-console/src/components/ApiBackedOpsPanel.tsx",
  "apps/audit-console/src/app/page.tsx",
  "apps/audit-console/src/components/ApiBackedAuditPanel.tsx",
  "apps/fds-aml-console/src/app/page.tsx",
  "apps/fds-aml-console/src/components/ApiBackedRiskPanel.tsx"
];

function normalizePath(path: string): string {
  return path.replaceAll("\\", "/");
}

async function exists(path: string): Promise<boolean> {
  try {
    await access(path);
    return true;
  } catch {
    return false;
  }
}

async function listFiles(root: string): Promise<string[]> {
  if (!await exists(root)) {
    return [];
  }

  const entries = await readdir(root, { withFileTypes: true });
  const files: string[] = [];
  for (const entry of entries) {
    const entryPath = normalizePath(join(root, entry.name));
    if (entry.isDirectory()) {
      if (ignoredDirs.has(entry.name)) {
        continue;
      }
      files.push(...await listFiles(entryPath));
    } else {
      files.push(entryPath);
    }
  }
  return files;
}

function isAllowedMjsPath(path: string): boolean {
  return allowedMjsPrefixes.some((prefix) => path.startsWith(prefix));
}

const gate = JSON.parse(await readFile(gatePath, "utf8")) as NodeRetirementGate;
const parity = JSON.parse(await readFile(parityPath, "utf8")) as ParityScenarioMap;
const files = (await Promise.all(scanRoots.map((root) => listFiles(root)))).flat();
const mjsFiles = files.filter((file) => file.endsWith(".mjs"));
const sourceFiles = files.filter((file) => /\.(mjs|ts)$/.test(file));
const errors: string[] = [];

const unboundedMjsFiles = mjsFiles.filter((file) => !isAllowedMjsPath(file));
if (unboundedMjsFiles.length > 0) {
  errors.push("Node .mjs files exist outside approved reference/support paths:");
  errors.push(...unboundedMjsFiles.map((file) => `  - ${file}`));
}

const serviceMjsFiles = mjsFiles.filter((file) => file.startsWith("services/"));
if (serviceMjsFiles.length > 0) {
  errors.push("Target service directories contain Node business modules:");
  errors.push(...serviceMjsFiles.map((file) => `  - ${file}`));
}

const staleServiceImports: string[] = [];
for (const file of sourceFiles) {
  const source = await readFile(file, "utf8");
  if (/(?:\.\.\/)+services\/[^"']+\.mjs/.test(source)) {
    staleServiceImports.push(file);
  }
}
if (staleServiceImports.length > 0) {
  errors.push("Source files still import legacy Node modules through target services/:");
  errors.push(...staleServiceImports.map((file) => `  - ${file}`));
}

const missingReferenceDeclarations = requiredReferencePaths.filter((path) => !gate.nodeReferenceRuntime.paths.includes(path));
if (missingReferenceDeclarations.length > 0) {
  errors.push("Node retirement gate is missing required reference-boundary declarations:");
  errors.push(...missingReferenceDeclarations.map((path) => `  - ${path}`));
}

const missingReferencePaths = [];
for (const referencePath of gate.nodeReferenceRuntime.paths) {
  if (!await exists(referencePath)) {
    missingReferencePaths.push(referencePath);
  }
}
if (missingReferencePaths.length > 0) {
  errors.push("Node retirement gate declares reference paths that do not exist:");
  errors.push(...missingReferencePaths.map((path) => `  - ${path}`));
}

const missingTargetAnchors = [];
for (const targetAnchor of targetAnchors) {
  if (!await exists(targetAnchor)) {
    missingTargetAnchors.push(targetAnchor);
  }
}
if (missingTargetAnchors.length > 0) {
  errors.push("Target-stack retirement anchors are missing:");
  errors.push(...missingTargetAnchors.map((path) => `  - ${path}`));
}

const listedEvidence = [];
const passingGatesWithoutEvidence = [];
for (const requiredGate of gate.requiredGates) {
  const evidence = requiredGate.evidence ?? [];
  if (requiredGate.status === "pass" && evidence.length === 0) {
    passingGatesWithoutEvidence.push(requiredGate.id);
  }
  for (const evidencePath of evidence) {
    listedEvidence.push({ gateId: requiredGate.id, path: evidencePath });
  }
}

const missingEvidence = [];
for (const evidence of listedEvidence) {
  if (!await exists(evidence.path)) {
    missingEvidence.push(`${evidence.gateId}: ${evidence.path}`);
  }
}
if (passingGatesWithoutEvidence.length > 0) {
  errors.push("Passing retirement gates must include evidence paths:");
  errors.push(...passingGatesWithoutEvidence.map((id) => `  - ${id}`));
}
if (missingEvidence.length > 0) {
  errors.push("Retirement gate evidence paths are missing:");
  errors.push(...missingEvidence.map((path) => `  - ${path}`));
}

const mappedScenarioCount = parity.suites.reduce((sum, suite) => sum + suite.scenarioCount, 0);
const nonPassingParitySuites = parity.suites.filter((suite) => suite.targetStatus !== "pass");
if (parity.nodeReferenceTestCount !== 42) {
  errors.push(`Expected 42 Node reference scenarios, found ${parity.nodeReferenceTestCount}.`);
}
if (mappedScenarioCount !== parity.nodeReferenceTestCount) {
  errors.push(`Mapped parity scenario count ${mappedScenarioCount} does not match ${parity.nodeReferenceTestCount}.`);
}
if (nonPassingParitySuites.length > 0) {
  errors.push("Parity suites are not target-backed pass:");
  errors.push(...nonPassingParitySuites.map((suite) => `  - ${suite.nodeSuite} -> ${suite.targetSuite}: ${suite.targetStatus ?? "missing"}`));
}

const incompleteGates = gate.requiredGates.filter((item) => item.status !== "pass");
if (gate.status === "ready" && incompleteGates.length > 0) {
  errors.push("Gate status is ready but required gates are incomplete:");
  errors.push(...incompleteGates.map((item) => `  - ${item.id}: ${item.status}`));
}

if (errors.length > 0) {
  console.error("Retirement boundary audit: failed");
  for (const error of errors) {
    console.error(error);
  }
  process.exit(1);
}

console.log(`Retirement boundary audit: ${gate.status === "ready" ? "ready" : "blocked"}`);
console.log(`Reference boundary: pass (${mjsFiles.length} .mjs files inside approved reference/support paths)`);
console.log(`Target stack anchors: pass (${targetAnchors.length} checked)`);
console.log(`Evidence paths: pass (${listedEvidence.length} checked)`);
console.log(`Parity map: pass (${mappedScenarioCount}/${parity.nodeReferenceTestCount} mapped scenarios)`);
if (incompleteGates.length > 0) {
  console.log("Incomplete gates:");
  for (const item of incompleteGates) {
    console.log(`- ${item.id}: ${item.status}`);
  }
}
