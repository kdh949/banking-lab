import { readdir, readFile, stat } from "node:fs/promises";
import path from "node:path";

type Finding = {
  file: string;
  line: number;
  message: string;
};

const root = process.cwd();
const checkedRoots = [
  ".env.example",
  ".github",
  "apps",
  "contracts",
  "db",
  "docker-compose.yml",
  "docs",
  "infra",
  "package.json",
  "packages",
  "scripts",
  "services",
  "tests"
];

const ignoredDirectories = new Set([
  ".git",
  ".gradle",
  ".next",
  ".venv",
  ".uv-cache",
  "bin",
  "build",
  "dist",
  "node_modules",
  "test-results"
]);

const ignoredFileSuffixes = [
  ".png",
  ".jpg",
  ".jpeg",
  ".gif",
  ".ico",
  ".pdf",
  ".woff",
  ".woff2",
  ".jar",
  ".class",
  ".tsbuildinfo"
];

const allowedSecretExactValues = new Set([
  "",
  "banking_lab",
  "admin",
  "password",
  "true",
  "false"
]);

const allowedSecretValueFragments = [
  "local-synthetic",
  "not-real",
  "placeholder",
  "replace-with",
  "synthetic",
  "${",
  "$(",
  "{{",
  "jdbc:postgresql://",
  "http://",
  "https://",
  "core-banking-api",
  "payment-service-api",
  "notification-service-api",
  "reporting-service-api",
  "banking.lab"
];

const assignmentPattern = /^\s*(?:-?\s*)?([A-Z0-9_]*(?:PASSWORD|SECRET|TOKEN|PRIVATE_KEY|API_KEY)[A-Z0-9_]*)\s*[:=]\s*("?)([^"#\n]*?)\2\s*(?:#.*)?$/;
const privateKeyPattern = /-----BEGIN [A-Z ]*PRIVATE KEY-----/;
const bearerLikePattern = /\b(?:ghp|gho|ghu|github_pat|sk-[A-Za-z0-9]|xox[baprs]-)[A-Za-z0-9_\-]{12,}/;
const jwtPattern = /\beyJ[A-Za-z0-9_\-]{8,}\.[A-Za-z0-9_\-]{8,}\.[A-Za-z0-9_\-]{8,}\b/;

const findings: Finding[] = [];
const files = await listFiles(checkedRoots);

for (const file of files) {
  const content = await readFile(file, "utf8");
  const lines = content.split(/\r?\n/);
  lines.forEach((line, index) => {
    if (privateKeyPattern.test(line)) {
      findings.push({ file, line: index + 1, message: "private key material must not be committed" });
    }
    if (bearerLikePattern.test(line)) {
      findings.push({ file, line: index + 1, message: "real-looking reusable token must not be committed" });
    }
    if (jwtPattern.test(line)) {
      findings.push({ file, line: index + 1, message: "JWT-like token must not be committed" });
    }
    const assignment = assignmentPattern.exec(line);
    if (assignment) {
      const [, key, , rawValue] = assignment;
      const value = rawValue.trim().replace(/\\+$/, "").trim().replace(/^["']|["']$/g, "");
      if (!isAllowedPlaceholder(value)) {
        findings.push({
          file,
          line: index + 1,
          message: `${key} must use an explicit local synthetic placeholder or runtime interpolation`
        });
      }
    }
  });
}

await requireContains(".gitignore", "!.env.example", "tracked .env.example allow-list");
await requireContains(".env.example", "replace-with-local-synthetic-password", ".env.example synthetic password placeholder");
await requireContains("docs/security/secrets-management.md", "Do not commit real customer data", "secrets management synthetic boundary");
await requireContains(
  "services/core-banking/src/main/kotlin/lab/banking/core/security/BankingLabDefaultSecretProfileGuard.kt",
  "default synthetic database password is not allowed for prod-like profiles",
  "prod-like default database password fail-fast guard"
);
await requireContains("infra/k8s/secret.example.yaml", "replace-with-local-synthetic-password", "Kubernetes Secret placeholder");
await requireContains("infra/helm/banking-lab/templates/secret-example.yaml", "replace-with-local-synthetic-password", "Helm Secret placeholder");

if (findings.length > 0) {
  console.error("Secret placeholder check failed:");
  for (const finding of findings) {
    console.error(`- ${finding.file}:${finding.line} ${finding.message}`);
  }
  process.exit(1);
}

console.log(`Secret placeholder check passed for ${files.length} files.`);

async function listFiles(entries: string[]): Promise<string[]> {
  const result: string[] = [];
  for (const entry of entries) {
    const absolute = path.join(root, entry);
    let info;
    try {
      info = await stat(absolute);
    } catch {
      continue;
    }
    if (info.isDirectory()) {
      await walk(absolute, result);
    } else if (isTextCandidate(absolute)) {
      result.push(entry);
    }
  }
  return result.sort();
}

async function walk(directory: string, result: string[]) {
  const entries = await readdir(directory, { withFileTypes: true });
  for (const entry of entries) {
    if (ignoredDirectories.has(entry.name)) {
      continue;
    }
    const absolute = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      await walk(absolute, result);
    } else if (entry.isFile() && isTextCandidate(absolute)) {
      result.push(path.relative(root, absolute));
    }
  }
}

function isTextCandidate(file: string): boolean {
  if (ignoredFileSuffixes.some((suffix) => file.endsWith(suffix))) {
    return false;
  }
  if (file.includes(`${path.sep}docs${path.sep}test-evidence${path.sep}generated${path.sep}`)) {
    return false;
  }
  if (file.endsWith(".log")) {
    return false;
  }
  return true;
}

function isAllowedPlaceholder(value: string): boolean {
  const normalized = value.trim().toLowerCase();
  if (allowedSecretExactValues.has(normalized)) {
    return true;
  }
  return allowedSecretValueFragments.some((fragment) => normalized.includes(fragment.toLowerCase()));
}

async function requireContains(file: string, expected: string, description: string) {
  const content = await readFile(file, "utf8");
  if (!content.includes(expected)) {
    findings.push({ file, line: 1, message: `missing ${description}` });
  }
}
