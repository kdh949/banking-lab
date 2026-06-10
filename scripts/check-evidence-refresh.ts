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

type GovernanceEvidenceSummary = {
  status?: unknown;
  syntheticOnly?: unknown;
  artifacts?: unknown;
  vulnerabilityRemediation?: {
    failedChecks?: unknown;
    openRemediationCount?: unknown;
  };
};

type GovernanceArtifact = {
  id?: unknown;
  path?: unknown;
  status?: unknown;
};

const gatePath = "docs/migration/node-retirement-gate.json";
const evidencePackSummaryPath = "docs/test-evidence/generated/evidence-pack-summary.json";
const evidenceRefreshReviewPath = "docs/test-evidence/evidence-refresh-review.md";
const parityMatrixPath = "docs/test-evidence/parity-coverage-matrix.md";
const evidenceGapReportPath = "docs/test-evidence/evidence-gap-report.md";
const qaRecommendationPath = "docs/architecture/qa-evidence-node-retirement-recommendation.md";
const stackAreaAuditDocPath = "docs/test-evidence/stack-retirement-area-audit.md";
const stackAreaAuditScriptPath = "scripts/check-stack-retirement-by-area.ts";
const stackAreaAuditTestPath = "tests/stackRetirementAreaAudit.test.mjs";
const generatedBoundaryDocPath = "docs/test-evidence/generated-artifact-boundary.md";
const generatedBoundaryScriptPath = "scripts/check-generated-artifact-boundary.ts";
const generatedBoundaryTestPath = "tests/generatedArtifactBoundary.test.mjs";
const passkeyPrepareScriptPath = "scripts/prepare-passkey-non-synthetic-evidence.ts";
const passkeyReadinessScriptPath = "scripts/check-passkey-manual-readiness.ts";
const passkeyLiveReadinessScriptPath = "scripts/check-passkey-live-platform-readiness.ts";
const passkeyArtifactPath = "docs/test-evidence/generated/passkey-non-synthetic-evidence.json";
const passkeyPrepareTestPath = "tests/passkeyEvidencePrepare.test.mjs";
const passkeyReadinessTestPath = "tests/passkeyManualReadiness.test.mjs";
const passkeyLiveReadinessTestPath = "tests/passkeyLivePlatformReadiness.test.mjs";
const finalReviewVerifierDocPath = "docs/test-evidence/final-retirement-review-verifier.md";
const finalReviewPrepareScriptPath = "scripts/prepare-final-retirement-review-evidence.ts";
const finalReviewRecorderScriptPath = "scripts/record-final-retirement-review.ts";
const finalReviewVerifierScriptPath = "scripts/verify-final-retirement-review.ts";
const readySimulationScriptPath = "scripts/check-node-retirement-ready-simulation.ts";
const finalReviewPrepareTestPath = "tests/finalRetirementReviewPrepare.test.mjs";
const finalReviewRecorderTestPath = "tests/finalRetirementReviewRecorder.test.mjs";
const finalReviewVerifierTestPath = "tests/finalRetirementReviewVerifier.test.mjs";
const readySimulationTestPath = "tests/nodeRetirementReadySimulation.test.mjs";
const goalCompletionAuditDocPath = "docs/test-evidence/goal-completion-audit.md";
const goalCompletionAuditScriptPath = "scripts/check-goal-completion-audit.ts";
const goalCompletionAuditTestPath = "tests/goalCompletionAudit.test.mjs";
const finalReviewArtifactPath = "docs/test-evidence/generated/final-node-retirement-review.json";
const governanceEvidenceScriptPath = "scripts/run-governance-evidence.ts";
const governanceEvidenceTestPath = "tests/governanceEvidence.test.mjs";
const governanceEvidenceDocPath = "docs/test-evidence/hardening-h8-governance-artifacts.md";
const governanceMappingPath = "docs/regulatory-mapping/governance-artifact-mapping.md";
const incidentResponseRunbookPath = "docs/incident-response/synthetic-incident-response-runbook.md";
const governanceSummaryPath = "docs/test-evidence/generated/governance/governance-evidence-summary.json";
const governanceArtifactPaths = [
  governanceSummaryPath,
  "docs/test-evidence/generated/governance/access-rights-review.json",
  "docs/test-evidence/generated/governance/deployment-approval-evidence.json",
  "docs/test-evidence/generated/governance/incident-response-drill-log.json",
  "docs/test-evidence/generated/governance/vulnerability-remediation-tracker.json"
];
const checkScriptPath = "scripts/check-evidence-refresh.ts";
const checkTestPath = "tests/evidenceRefresh.test.mjs";
const evidenceRefreshGateId = "evidence-refresh";

const textDirectoriesToScan = [
  "docs/test-evidence",
  "docs/architecture"
];

const textFilesToScan = [
  "runtime/synthetic-reference/README.md"
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
  "npm run passkey:evidence:prepare",
  "npm run passkey:evidence:readiness",
  "npm run retirement:final-review:prepare",
  "npm run passkey:evidence:preflight",
  "npm run retirement:stack-audit",
  "npm run retirement:generated-boundary",
  "npm run retirement:ready-simulate",
  "npm run goal:completion-audit",
  "npm run evidence:pack",
  "npm run governance:evidence",
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

function stringValue(value: unknown): string {
  return typeof value === "string" ? value : "";
}

for (const path of [
  gatePath,
  evidencePackSummaryPath,
  evidenceRefreshReviewPath,
  parityMatrixPath,
  evidenceGapReportPath,
  qaRecommendationPath,
  stackAreaAuditDocPath,
  stackAreaAuditScriptPath,
  stackAreaAuditTestPath,
  generatedBoundaryDocPath,
  generatedBoundaryScriptPath,
  generatedBoundaryTestPath,
  passkeyPrepareScriptPath,
  passkeyReadinessScriptPath,
  passkeyLiveReadinessScriptPath,
  passkeyPrepareTestPath,
  passkeyReadinessTestPath,
  passkeyLiveReadinessTestPath,
  finalReviewVerifierDocPath,
  finalReviewPrepareScriptPath,
  finalReviewRecorderScriptPath,
  finalReviewVerifierScriptPath,
  readySimulationScriptPath,
  finalReviewPrepareTestPath,
  finalReviewRecorderTestPath,
  finalReviewVerifierTestPath,
  readySimulationTestPath,
  goalCompletionAuditDocPath,
  goalCompletionAuditScriptPath,
  goalCompletionAuditTestPath,
  governanceEvidenceScriptPath,
  governanceEvidenceTestPath,
  governanceEvidenceDocPath,
  governanceMappingPath,
  incidentResponseRunbookPath,
  ...governanceArtifactPaths,
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
const passkeyGate = requiredGates.find((item) => item.id === "non-synthetic-passkey-operations");
const reviewGate = requiredGates.find((item) => item.id === "retirement-review");
const passkeyPassed = passkeyGate?.status === "pass";
const reviewPassed = reviewGate?.status === "pass";
const gateReady = gate?.status === "ready";

if (gate?.status !== "blocked" && gate?.status !== "ready") {
  errors.push("Node retirement gate must be blocked while review work remains or ready after all retirement gates pass.");
}
if (!passkeyPassed && gate?.status !== "blocked") {
  errors.push("Node retirement gate must remain blocked until passkey evidence is complete.");
}
if (reviewPassed && !gateReady) {
  errors.push("Node retirement gate must be ready once final review passes.");
}
if (gateReady && (!passkeyPassed || !reviewPassed)) {
  errors.push("Node retirement gate must not be ready until passkey and final review gates pass.");
}
if (!passkeyPassed && (typeof gate?.statusReason !== "string" || !/non-synthetic passkey operations and final retirement review remain incomplete/i.test(gate.statusReason))) {
  errors.push("Node retirement gate statusReason must list non-synthetic passkey operations and final retirement review while both are incomplete.");
}
if (passkeyPassed && !reviewPassed && (typeof gate?.statusReason !== "string" || !/final retirement review remains incomplete/i.test(gate.statusReason))) {
  errors.push("Node retirement gate statusReason must list only final retirement review after passkey evidence passes.");
}
if (gateReady && (typeof gate?.statusReason !== "string" || !/all required retirement gates/i.test(gate.statusReason))) {
  errors.push("Node retirement gate statusReason must explain that all required retirement gates are passing when ready.");
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
  for (const path of [
    evidenceRefreshReviewPath,
    stackAreaAuditDocPath,
    stackAreaAuditScriptPath,
    stackAreaAuditTestPath,
    generatedBoundaryDocPath,
    generatedBoundaryScriptPath,
    generatedBoundaryTestPath,
    passkeyPrepareScriptPath,
    passkeyReadinessScriptPath,
    passkeyLiveReadinessScriptPath,
    ...(passkeyPassed ? [passkeyArtifactPath] : []),
    passkeyPrepareTestPath,
    passkeyReadinessTestPath,
    passkeyLiveReadinessTestPath,
    finalReviewVerifierDocPath,
    finalReviewPrepareScriptPath,
    finalReviewRecorderScriptPath,
    finalReviewVerifierScriptPath,
    ...(reviewPassed ? [finalReviewArtifactPath] : []),
    readySimulationScriptPath,
    finalReviewPrepareTestPath,
    finalReviewRecorderTestPath,
    finalReviewVerifierTestPath,
    readySimulationTestPath,
    goalCompletionAuditDocPath,
    goalCompletionAuditScriptPath,
    goalCompletionAuditTestPath,
    governanceEvidenceScriptPath,
    governanceEvidenceTestPath,
    governanceEvidenceDocPath,
    governanceMappingPath,
    incidentResponseRunbookPath,
    ...governanceArtifactPaths,
    checkScriptPath,
    checkTestPath
  ]) {
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
requireIncludes(parityMatrix, "Total mapped reference scenarios: 43", "Parity matrix must keep the 43 mapped scenario count.");
requireIncludes(parityMatrix, "Pass for current mapped parity", "Parity matrix must report pass for current mapped parity, not stale partial status.");
requireIncludes(parityMatrix, "non-synthetic passkey evidence", "Parity matrix must name non-synthetic passkey evidence.");
requireIncludes(parityMatrix, "final review", "Parity matrix must name final review state.");

const evidenceGapReport = await readFile(evidenceGapReportPath, "utf8").catch(() => "");
if (passkeyPassed && reviewPassed) {
  requireIncludes(evidenceGapReport, "It proves non-synthetic passkey operations and final Node independence for the current retirement gate.", "Evidence gap report must record ready-state passkey and final review evidence.");
} else if (passkeyPassed) {
  requireIncludes(evidenceGapReport, "It proves non-synthetic passkey operations, but it does not yet prove Node independence for retirement.", "Evidence gap report must record passkey completion and final review as the remaining gap.");
} else {
  requireIncludes(evidenceGapReport, "It does not yet prove non-synthetic passkey operations or Node independence for retirement.", "Evidence gap report must remove stale evidence-refresh blocker text.");
}

const qaRecommendation = await readFile(qaRecommendationPath, "utf8").catch(() => "");
requireIncludes(qaRecommendation, "| `evidence-refresh` | Pass |", "QA recommendation must mark evidence-refresh pass.");
if (passkeyPassed && reviewPassed) {
  requireIncludes(qaRecommendation, "all required retirement gates are pass", "QA recommendation must record the final ready state.");
} else if (passkeyPassed) {
  requireIncludes(qaRecommendation, "remaining blocker is final retirement review", "QA recommendation must name final review as the only remaining blocker.");
} else {
  requireIncludes(qaRecommendation, "remaining blockers are non-synthetic passkey operations and final retirement review", "QA recommendation must name the current remaining blockers.");
}

const evidenceRefreshReview = await readFile(evidenceRefreshReviewPath, "utf8").catch(() => "");
requireIncludes(evidenceRefreshReview, "Status: pass", "Evidence refresh review must be marked pass.");
if (gateReady) {
  requireIncludes(evidenceRefreshReview, "ready Node retirement gate", "Evidence refresh review must record the ready retirement gate state.");
} else {
  requireIncludes(evidenceRefreshReview, "does not mark Node retirement ready", "Evidence refresh review must avoid overstating retirement readiness.");
}
for (const command of requiredCommands) {
  requireIncludes(evidenceRefreshReview, command, `Evidence refresh review must include command: ${command}.`);
}

const governanceSummary = await readJson<GovernanceEvidenceSummary>(governanceSummaryPath);
if (governanceSummary) {
  if (governanceSummary.status !== "pass") {
    errors.push("Governance evidence summary must be pass.");
  }
  if (governanceSummary.syntheticOnly !== true) {
    errors.push("Governance evidence summary syntheticOnly must be true.");
  }
  if (governanceSummary.vulnerabilityRemediation?.failedChecks !== 0 || governanceSummary.vulnerabilityRemediation?.openRemediationCount !== 0) {
    errors.push("Governance vulnerability remediation summary must have no failed checks or open remediation items.");
  }
  const artifactPaths = objectArray<GovernanceArtifact>(governanceSummary.artifacts).map((artifact) => stringValue(artifact.path));
  for (const path of [...governanceArtifactPaths, incidentResponseRunbookPath]) {
    if (!artifactPaths.includes(path)) {
      errors.push(`Governance evidence summary must include artifact path: ${path}.`);
    }
  }
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
if (gateReady) {
  console.log("Node retirement gate is ready after verified passkey and final review evidence.");
} else if (passkeyPassed) {
  console.log("Node retirement remains blocked by final retirement review.");
} else {
  console.log("Node retirement remains blocked by non-synthetic passkey operations and final retirement review.");
}
