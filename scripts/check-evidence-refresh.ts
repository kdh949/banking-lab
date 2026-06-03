import { access, readdir, readFile } from "node:fs/promises";
import { join } from "node:path";

type RequiredGate = {
  id?: unknown;
  status?: unknown;
  evidence?: unknown;
};

type NodeRetirementGate = {
  status?: unknown;
  statusReason?: unknown;
  requiredGates?: unknown;
};

type EvidencePackSummary = {
  syntheticOnly?: unknown;
  totalChecks?: unknown;
  passedChecks?: unknown;
  failedChecks?: unknown;
};

const gatePath = "docs/migration/node-retirement-gate.json";
const evidencePackSummaryPath = "docs/test-evidence/generated/evidence-pack-summary.json";
const evidenceRefreshReviewPath = "docs/test-evidence/evidence-refresh-review.md";
const parityMatrixPath = "docs/test-evidence/parity-coverage-matrix.md";
const evidenceGapReportPath = "docs/test-evidence/evidence-gap-report.md";
const qaRecommendationPath = "docs/architecture/qa-evidence-node-retirement-recommendation.md";
const checkScriptPath = "scripts/check-evidence-refresh.ts";
const checkTestPath = "tests/evidenceRefresh.test.mjs";
const evidenceRefreshGateId = "evidence-refresh";

const textDirectoriesToScan = [
  "docs/test-evidence",
  "docs/architecture"
];

const textFilesToScan = [
  "legacy-node-reference/README.md"
];

const ignoredDirs = new Set(["generated"]);
const stalePatterns = [
  /evidence-refresh completion/i,
  /\|\s*Partial\.\s*\|/,
  /Blocks retirement until remaining Node suites/i,
  /remaining non-runtime Node suites/i,
  /until all mapped parity scenarios and final review gates pass/i,
  /until every parity suite is covered/i,
  /broader non-ledger suites remain incomplete/i,
  /Needs command logs for full target workflow/i
];

const requiredCommands = [
  "npm run passkey:evidence:preflight",
  "npm run evidence:pack",
  "npm run retirement:audit",
  "npm run node:retirement-gate",
  "npm test",
  "npm run validate:manifests",
  "npm run parity"
];

const errors: string[] = [];

async function exists(path: string): Promise<boolean> {
  try {
    await access(path);
    return true;
  } catch {
    return false;
  }
}

async function readJson<T>(path: string): Promise<T | undefined> {
  try {
    return JSON.parse(await readFile(path, "utf8")) as T;
  } catch (error) {
    errors.push(`Could not parse ${path}: ${(error as Error).message}`);
    return undefined;
  }
}

function objectArray<T>(value: unknown): T[] {
  return Array.isArray(value) ? value.filter((item): item is T => typeof item === "object" && item !== null) : [];
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}

async function listMarkdownFiles(root: string): Promise<string[]> {
  if (!await exists(root)) {
    return [];
  }
  const entries = await readdir(root, { withFileTypes: true });
  const files: string[] = [];
  for (const entry of entries) {
    const entryPath = join(root, entry.name).replaceAll("\\", "/");
    if (entry.isDirectory()) {
      if (!ignoredDirs.has(entry.name)) {
        files.push(...await listMarkdownFiles(entryPath));
      }
    } else if (entry.name.endsWith(".md")) {
      files.push(entryPath);
    }
  }
  return files;
}

function requireIncludes(source: string, needle: string, message: string): void {
  if (!source.includes(needle)) {
    errors.push(message);
  }
}

for (const path of [
  gatePath,
  evidencePackSummaryPath,
  evidenceRefreshReviewPath,
  parityMatrixPath,
  evidenceGapReportPath,
  qaRecommendationPath,
  checkScriptPath,
  checkTestPath
]) {
  if (!await exists(path)) {
    errors.push(`Missing evidence-refresh path: ${path}`);
  }
}

const gate = await readJson<NodeRetirementGate>(gatePath);
const summary = await readJson<EvidencePackSummary>(evidencePackSummaryPath);
const requiredGates = objectArray<RequiredGate>(gate?.requiredGates);
const evidenceRefreshGate = requiredGates.find((item) => item.id === evidenceRefreshGateId);

if (gate?.status !== "blocked") {
  errors.push("Node retirement gate must remain blocked until passkey evidence and final review are complete.");
}
if (typeof gate?.statusReason !== "string" || !/non-synthetic passkey operations and final retirement review remain incomplete/i.test(gate.statusReason)) {
  errors.push("Node retirement gate statusReason must now list only non-synthetic passkey operations and final retirement review as incomplete.");
}
if (typeof gate?.statusReason === "string" && /evidence-refresh completion/i.test(gate.statusReason)) {
  errors.push("Node retirement gate statusReason must not keep stale evidence-refresh completion blocker text.");
}

if (!evidenceRefreshGate) {
  errors.push(`Missing ${evidenceRefreshGateId} gate.`);
} else {
  if (evidenceRefreshGate.status !== "pass") {
    errors.push("evidence-refresh gate must be pass after the current evidence review is refreshed.");
  }
  for (const path of [evidenceRefreshReviewPath, checkScriptPath, checkTestPath]) {
    if (!stringArray(evidenceRefreshGate.evidence).includes(path)) {
      errors.push(`evidence-refresh evidence must include ${path}.`);
    }
  }
  for (const evidencePath of stringArray(evidenceRefreshGate.evidence)) {
    if (!await exists(evidencePath)) {
      errors.push(`evidence-refresh evidence path is missing: ${evidencePath}.`);
    }
  }
}

if (summary) {
  if (summary.syntheticOnly !== true) {
    errors.push("Evidence pack summary syntheticOnly must be true.");
  }
  if (typeof summary.totalChecks !== "number" || typeof summary.passedChecks !== "number" || summary.totalChecks !== summary.passedChecks) {
    errors.push("Evidence pack summary must have all checks passing.");
  }
  if (Array.isArray(summary.failedChecks) && summary.failedChecks.length > 0) {
    errors.push("Evidence pack summary must not contain failed checks.");
  }
}

const parityMatrix = await readFile(parityMatrixPath, "utf8").catch(() => "");
requireIncludes(parityMatrix, "Total mapped reference scenarios: 42", "Parity matrix must keep the 42 mapped scenario count.");
requireIncludes(parityMatrix, "Pass for current mapped parity", "Parity matrix must report pass for current mapped parity, not stale partial status.");
requireIncludes(parityMatrix, "non-synthetic passkey evidence", "Parity matrix must name the current passkey retirement blocker.");
requireIncludes(parityMatrix, "final review", "Parity matrix must name the current final review retirement blocker.");

const evidenceGapReport = await readFile(evidenceGapReportPath, "utf8").catch(() => "");
requireIncludes(evidenceGapReport, "It does not yet prove non-synthetic passkey operations or Node independence for retirement.", "Evidence gap report must remove stale evidence-refresh blocker text.");

const qaRecommendation = await readFile(qaRecommendationPath, "utf8").catch(() => "");
requireIncludes(qaRecommendation, "| `evidence-refresh` | Pass |", "QA recommendation must mark evidence-refresh pass.");
requireIncludes(qaRecommendation, "remaining blockers are non-synthetic passkey operations and final retirement review", "QA recommendation must name the current remaining blockers.");

const evidenceRefreshReview = await readFile(evidenceRefreshReviewPath, "utf8").catch(() => "");
requireIncludes(evidenceRefreshReview, "Status: pass", "Evidence refresh review must be marked pass.");
requireIncludes(evidenceRefreshReview, "does not mark Node retirement ready", "Evidence refresh review must avoid overstating retirement readiness.");
for (const command of requiredCommands) {
  requireIncludes(evidenceRefreshReview, command, `Evidence refresh review must include command: ${command}.`);
}

const markdownFiles = [
  ...(await Promise.all(textDirectoriesToScan.map((root) => listMarkdownFiles(root)))).flat(),
  ...textFilesToScan
];
for (const file of markdownFiles) {
  const source = await readFile(file, "utf8");
  for (const pattern of stalePatterns) {
    if (pattern.test(source)) {
      errors.push(`${file} contains stale evidence-refresh wording or partial status: ${pattern}`);
    }
  }
}

if (errors.length > 0) {
  console.error("Evidence refresh check: failed");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log("Evidence refresh check: pass");
console.log("Current evidence docs, generated evidence pack, and retirement gate refresh state are consistent.");
console.log("Node retirement remains blocked by non-synthetic passkey operations and final retirement review.");
