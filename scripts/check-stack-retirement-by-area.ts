import { access, readdir, readFile } from "node:fs/promises";
import { join } from "node:path";

type RequiredGate = {
  id?: unknown;
  status?: unknown;
};

type NodeRetirementGate = {
  status?: unknown;
  requiredGates?: unknown;
};

type Area = {
  id: string;
  label: string;
  roots: string[];
  allowedStack: string;
  anchors: string[];
  disallowedExtensions: string[];
  disallowedPaths?: string[];
  dependencyPatterns: RegExp[];
};

const gatePath = "docs/migration/node-retirement-gate.json";
const ignoredDirs = new Set([
  ".git",
  ".gradle",
  ".next",
  ".pytest_cache",
  ".turbo",
  ".venv",
  "__pycache__",
  "build",
  "coverage",
  "dist",
  "node_modules",
  "playwright-report",
  "test-results"
]);

const legacyDependencyPatterns = [
  /legacy-node-reference/u,
  /runtime\/(?:labApp|server)\.mjs/u,
  /(?:\.\.\/)+runtime\/(?:labApp|server)\.mjs/u,
  /\.mjs["']/u
];

const areas: Area[] = [
  {
    id: "core-banking-backend",
    label: "Core Banking Backend",
    roots: ["services/core-banking"],
    allowedStack: "Kotlin/Java + Spring Boot, PostgreSQL/Flyway, Outbox, Temporal, Keycloak/OIDC",
    anchors: [
      "services/core-banking/build.gradle.kts",
      "services/core-banking/src/main/kotlin/lab/banking/core/CoreBankingApplication.kt",
      "services/core-banking/src/main/kotlin/lab/banking/core/ledger/application/LedgerCommandService.kt",
      "services/core-banking/src/main/kotlin/lab/banking/core/eventing/DurableOutboxService.kt",
      "services/core-banking/src/main/kotlin/lab/banking/core/temporal/BankingCaseTemporalWorkflow.kt",
      "services/core-banking/src/main/kotlin/lab/banking/core/security/SignedJwtJwksTokenDecoder.kt"
    ],
    disallowedExtensions: [".mjs", ".cjs", ".js", ".jsx", ".ts", ".tsx", ".html"],
    dependencyPatterns: legacyDependencyPatterns
  },
  {
    id: "frontend-channels",
    label: "Frontend Channels",
    roots: ["apps"],
    allowedStack: "TypeScript + Next.js/React manifest-rendered channel apps",
    anchors: [
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
      "apps/fds-aml-console/src/components/ApiBackedRiskPanel.tsx",
      "apps/admin-console/src/app/page.tsx",
      "apps/admin-console/src/components/ApiBackedAdminPanel.tsx"
    ],
    disallowedExtensions: [".mjs", ".cjs", ".js", ".jsx", ".html"],
    disallowedPaths: [
      "apps/admin-console/public/index.html",
      "apps/audit-console/public/index.html",
      "apps/complaint-portal/public/index.html",
      "apps/customer-web/public/index.html",
      "apps/fds-aml-console/public/index.html",
      "apps/ops-console/public/index.html",
      "apps/staff-terminal/public/index.html"
    ],
    dependencyPatterns: legacyDependencyPatterns
  },
  {
    id: "shared-packages",
    label: "Shared Packages",
    roots: ["packages"],
    allowedStack: "TypeScript shared API/auth/screen/form packages",
    anchors: [
      "packages/api-client/src/index.ts",
      "packages/auth-client/src/index.ts",
      "packages/screen-engine/src/index.ts",
      "packages/screen-engine/src/manifest.ts",
      "packages/screen-engine/src/types.ts",
      "packages/form-engine/src/index.ts",
      "packages/form-engine/src/validation.ts"
    ],
    disallowedExtensions: [".mjs", ".cjs", ".js", ".jsx", ".html"],
    disallowedPaths: [
      "packages/banking-domain/package.json",
      "packages/banking-domain/src/index.mjs",
      "packages/screen-engine/src/index.mjs",
      "packages/screen-engine/src/manifest.mjs",
      "packages/form-engine/src/index.mjs",
      "packages/form-engine/src/validation.mjs",
      "packages/ui/public/app.js",
      "packages/ui/public/lab.css"
    ],
    dependencyPatterns: legacyDependencyPatterns
  },
  {
    id: "analytics",
    label: "AML/FDS Analytics",
    roots: ["analytics"],
    allowedStack: "Python + DuckDB/scikit-learn synthetic AML/FDS analytics",
    anchors: [
      "analytics/aml-fds-python/pyproject.toml",
      "analytics/aml-fds-python/src/banking_lab_analytics/scoring.py",
      "analytics/aml-fds-python/tests/test_scoring.py"
    ],
    disallowedExtensions: [".mjs", ".cjs", ".js", ".jsx", ".ts", ".tsx", ".html"],
    dependencyPatterns: legacyDependencyPatterns
  },
  {
    id: "platform-infra",
    label: "Platform Infrastructure",
    roots: ["infra", "docker-compose.yml"],
    allowedStack: "Docker Compose, Kubernetes, Terraform, Helm, Argo CD, Keycloak, OpenTelemetry stack, security tooling",
    anchors: [
      "docker-compose.yml",
      "infra/k8s/core-banking-deployment.yaml",
      "infra/terraform/main.tf",
      "infra/helm/banking-lab/Chart.yaml",
      "infra/argocd/banking-lab-app.yaml",
      "infra/keycloak/realm-banking-lab.json",
      "infra/observability/prometheus/prometheus.yml",
      "infra/observability/loki/loki.yml",
      "infra/observability/tempo/tempo.yml",
      "infra/security/semgrep.yml",
      "infra/security/trivy.yaml",
      "infra/security/sbom.md",
      "infra/security/dast.md"
    ],
    disallowedExtensions: [".mjs", ".cjs", ".js", ".jsx", ".ts", ".tsx", ".html"],
    dependencyPatterns: legacyDependencyPatterns
  },
  {
    id: "contracts-and-data",
    label: "Contracts And Data",
    roots: ["contracts", "db"],
    allowedStack: "OpenAPI/AsyncAPI/Temporal contracts and PostgreSQL/Flyway migrations",
    anchors: [
      "contracts/asyncapi/banking-lab-events.yaml",
      "contracts/events/ledger-transaction-posted.schema.json",
      "contracts/temporal/workflows.yaml",
      "db/migrations/V001__foundation.sql",
      "db/migrations/V004__outbox_inbox.sql",
      "db/migrations/V010__temporal_workflow_references.sql"
    ],
    disallowedExtensions: [".mjs", ".cjs", ".js", ".jsx", ".ts", ".tsx", ".html"],
    dependencyPatterns: legacyDependencyPatterns
  }
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

async function listFiles(path: string): Promise<string[]> {
  if (!await exists(path)) {
    return [];
  }
  const entries = await readdir(path, { withFileTypes: true }).catch(() => []);
  if (entries.length === 0) {
    return [path];
  }

  const files: string[] = [];
  for (const entry of entries) {
    const entryPath = normalizePath(join(path, entry.name));
    if (entry.isDirectory()) {
      if (!ignoredDirs.has(entry.name)) {
        files.push(...await listFiles(entryPath));
      }
    } else {
      files.push(entryPath);
    }
  }
  return files;
}

function objectArray<T>(value: unknown): T[] {
  return Array.isArray(value) ? value.filter((item): item is T => typeof item === "object" && item !== null) : [];
}

function stringValue(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function isTextFile(path: string): boolean {
  return /\.(gradle\.kts|kts|kt|java|ts|tsx|py|toml|json|ya?ml|sql|tf|md|Dockerfile)$/u.test(path)
    || path.endsWith("Dockerfile")
    || path === "docker-compose.yml";
}

async function validateArea(area: Area): Promise<string[]> {
  const errors: string[] = [];
  const files = (await Promise.all(area.roots.map((root) => listFiles(root)))).flat();
  const missingAnchors = [];
  for (const anchor of area.anchors) {
    if (!await exists(anchor)) {
      missingAnchors.push(anchor);
    }
  }
  if (missingAnchors.length > 0) {
    errors.push(`missing target-stack anchors: ${missingAnchors.join(", ")}`);
  }

  const disallowedFiles = files.filter((file) => area.disallowedExtensions.some((extension) => file.endsWith(extension)));
  if (disallowedFiles.length > 0) {
    errors.push(`disallowed source extensions found: ${disallowedFiles.join(", ")}`);
  }

  const disallowedExistingPaths = [];
  for (const disallowedPath of area.disallowedPaths ?? []) {
    if (await exists(disallowedPath)) {
      disallowedExistingPaths.push(disallowedPath);
    }
  }
  if (disallowedExistingPaths.length > 0) {
    errors.push(`legacy target paths still exist: ${disallowedExistingPaths.join(", ")}`);
  }

  const legacyDependencies = [];
  for (const file of files.filter(isTextFile)) {
    const source = await readFile(file, "utf8").catch(() => "");
    if (area.dependencyPatterns.some((pattern) => pattern.test(source))) {
      legacyDependencies.push(file);
    }
  }
  if (legacyDependencies.length > 0) {
    errors.push(`legacy Node reference/runtime dependencies found: ${legacyDependencies.join(", ")}`);
  }

  return errors;
}

const errors: string[] = [];
const gate = JSON.parse(await readFile(gatePath, "utf8")) as NodeRetirementGate;
const requiredGates = objectArray<RequiredGate>(gate.requiredGates);
const incompleteGates = requiredGates.filter((item) => stringValue(item.status) !== "pass");

for (const area of areas) {
  const areaErrors = await validateArea(area);
  if (areaErrors.length > 0) {
    errors.push(`${area.label} (${area.id}) failed; allowed stack: ${area.allowedStack}`);
    errors.push(...areaErrors.map((error) => `  - ${error}`));
  }
}

if (errors.length > 0) {
  console.error("Stack retirement area audit: failed");
  for (const error of errors) {
    console.error(error);
  }
  process.exit(1);
}

console.log("Stack retirement area audit: pass");
for (const area of areas) {
  console.log(`- ${area.id}: ${area.allowedStack}`);
}
console.log(`Node retirement gate: ${stringValue(gate.status) || "unknown"}`);
if (incompleteGates.length > 0) {
  console.log("Incomplete gates:");
  for (const item of incompleteGates) {
    console.log(`- ${stringValue(item.id)}: ${stringValue(item.status)}`);
  }
}
