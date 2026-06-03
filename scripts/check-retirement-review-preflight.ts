import { spawnSync } from "node:child_process";
import { access, readFile } from "node:fs/promises";

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

type PackageJson = {
  scripts?: Record<string, string>;
};

const packageJsonPath = "package.json";
const gatePath = "docs/migration/node-retirement-gate.json";
const reviewDocPath = "docs/test-evidence/node-retirement-review.md";
const preflightPath = "scripts/check-retirement-review-preflight.ts";
const preflightTestPath = "tests/retirementReviewPreflight.test.mjs";
const passkeyVerifierPath = "scripts/verify-passkey-non-synthetic-evidence.ts";
const stackAreaAuditDocPath = "docs/test-evidence/stack-retirement-area-audit.md";
const stackAreaAuditScriptPath = "scripts/check-stack-retirement-by-area.ts";
const stackAreaAuditTestPath = "tests/stackRetirementAreaAudit.test.mjs";
const generatedBoundaryDocPath = "docs/test-evidence/generated-artifact-boundary.md";
const generatedBoundaryScriptPath = "scripts/check-generated-artifact-boundary.ts";
const generatedBoundaryTestPath = "tests/generatedArtifactBoundary.test.mjs";
const finalReviewVerifierDocPath = "docs/test-evidence/final-retirement-review-verifier.md";
const finalReviewVerifierScriptPath = "scripts/verify-final-retirement-review.ts";
const finalReviewVerifierTestPath = "tests/finalRetirementReviewVerifier.test.mjs";
const goalCompletionAuditDocPath = "docs/test-evidence/goal-completion-audit.md";
const goalCompletionAuditScriptPath = "scripts/check-goal-completion-audit.ts";
const goalCompletionAuditTestPath = "tests/goalCompletionAudit.test.mjs";

const requiredPassingGateIds = [
  "kotlin-spring-health",
  "node-reference-parity",
  "structured-error-contract",
  "next-manifest-renderer",
  "api-backed-channel-parity",
  "evidence-refresh"
];

const delegatedChecks = [
  {
    name: "retirement boundary audit",
    args: ["--experimental-strip-types", "scripts/check-retirement-boundary-audit.ts"],
    requiredOutput: [
      "Retirement boundary audit: blocked",
      "Reference boundary: pass",
      "Target stack anchors: pass",
      "Evidence paths: pass",
      "Parity map: pass (42/42 mapped scenarios)",
      "- non-synthetic-passkey-operations: pending",
      "- retirement-review: pending"
    ],
    forbiddenOutput: ["- evidence-refresh: in-progress", "- evidence-refresh: pending"]
  },
  {
    name: "stack retirement area audit",
    args: ["--experimental-strip-types", stackAreaAuditScriptPath],
    requiredOutput: [
      "Stack retirement area audit: pass",
      "core-banking-backend",
      "frontend-channels",
      "shared-packages",
      "analytics",
      "platform-infra",
      "contracts-and-data",
      "Node retirement gate: blocked"
    ],
    forbiddenOutput: ["Stack retirement area audit: failed"]
  },
  {
    name: "generated artifact boundary audit",
    args: ["--experimental-strip-types", generatedBoundaryScriptPath],
    requiredOutput: [
      "Generated artifact boundary audit: pass",
      "Tracked target legacy/static extension files: 0",
      "Untracked source-like legacy/static extension files: 0"
    ],
    forbiddenOutput: ["Generated artifact boundary audit: failed"]
  },
  {
    name: "evidence refresh check",
    args: ["--experimental-strip-types", "scripts/check-evidence-refresh.ts"],
    requiredOutput: ["Evidence refresh check: pass"],
    forbiddenOutput: ["Evidence refresh check: failed"]
  },
  {
    name: "goal completion audit",
    args: ["--experimental-strip-types", goalCompletionAuditScriptPath],
    requiredOutput: [
      "Goal completion audit: not complete",
      "gate:non-synthetic-passkey-operations: blocked",
      "gate:retirement-review: blocked",
      "node-retirement-gate: blocked"
    ],
    forbiddenOutput: ["Goal completion audit: complete"]
  },
  {
    name: "passkey evidence preflight",
    args: ["--experimental-strip-types", "scripts/check-passkey-non-synthetic-preflight.ts"],
    requiredOutput: [
      "Passkey non-synthetic evidence preflight: pass",
      "This does not prove non-synthetic passkey operations"
    ],
    forbiddenOutput: ["Passkey non-synthetic evidence preflight: failed"]
  },
  {
    name: "node retirement gate",
    args: ["--experimental-strip-types", "scripts/check-node-retirement-gate.ts"],
    requiredOutput: [
      "Node reference retirement gate: blocked",
      "- non-synthetic-passkey-operations: pending",
      "- retirement-review: pending"
    ],
    forbiddenOutput: ["- evidence-refresh: in-progress", "- evidence-refresh: pending", "Node reference retirement gate: ready"]
  }
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

function requireIncludes(source: string, needle: string, message: string): void {
  if (!source.includes(needle)) {
    errors.push(message);
  }
}

for (const path of [
  packageJsonPath,
  gatePath,
  reviewDocPath,
  preflightPath,
  preflightTestPath,
  passkeyVerifierPath,
  stackAreaAuditDocPath,
  stackAreaAuditScriptPath,
  stackAreaAuditTestPath,
  generatedBoundaryDocPath,
  generatedBoundaryScriptPath,
  generatedBoundaryTestPath,
  finalReviewVerifierDocPath,
  finalReviewVerifierScriptPath,
  finalReviewVerifierTestPath,
  goalCompletionAuditDocPath,
  goalCompletionAuditScriptPath,
  goalCompletionAuditTestPath
]) {
  if (!await exists(path)) {
    errors.push(`Missing retirement review preflight path: ${path}`);
  }
}

const packageJson = await readJson<PackageJson>(packageJsonPath);
const gate = await readJson<NodeRetirementGate>(gatePath);
const requiredGates = objectArray<RequiredGate>(gate?.requiredGates);
const passkeyGate = requiredGates.find((item) => item.id === "non-synthetic-passkey-operations");
const reviewGate = requiredGates.find((item) => item.id === "retirement-review");

if (packageJson?.scripts?.["retirement:review-preflight"] !== `node --experimental-strip-types ${preflightPath}`) {
  errors.push("package.json must expose retirement:review-preflight.");
}
if (packageJson?.scripts?.["passkey:evidence:verify"] !== `node --experimental-strip-types ${passkeyVerifierPath}`) {
  errors.push("package.json must expose passkey:evidence:verify before final retirement review.");
}
if (packageJson?.scripts?.["retirement:stack-audit"] !== `node --experimental-strip-types ${stackAreaAuditScriptPath}`) {
  errors.push("package.json must expose retirement:stack-audit before final retirement review.");
}
if (packageJson?.scripts?.["retirement:generated-boundary"] !== `node --experimental-strip-types ${generatedBoundaryScriptPath}`) {
  errors.push("package.json must expose retirement:generated-boundary before final retirement review.");
}
if (packageJson?.scripts?.["retirement:final-review:verify"] !== `node --experimental-strip-types ${finalReviewVerifierScriptPath}`) {
  errors.push("package.json must expose retirement:final-review:verify before final retirement review.");
}
if (packageJson?.scripts?.["goal:completion-audit"] !== `node --experimental-strip-types ${goalCompletionAuditScriptPath}`) {
  errors.push("package.json must expose goal:completion-audit before final retirement review.");
}

if (gate?.status !== "blocked") {
  errors.push("Node retirement gate must remain blocked before passkey evidence and final review are complete.");
}
if (typeof gate?.statusReason !== "string" || !/non-synthetic passkey operations and final retirement review remain incomplete/i.test(gate.statusReason)) {
  errors.push("Node retirement gate statusReason must name the current final blockers.");
}
if (typeof gate?.statusReason === "string" && /evidence-refresh completion/i.test(gate.statusReason)) {
  errors.push("Node retirement gate statusReason must not contain stale evidence-refresh blocker text.");
}

for (const gateId of requiredPassingGateIds) {
  const requiredGate = requiredGates.find((item) => item.id === gateId);
  if (!requiredGate) {
    errors.push(`Missing required gate: ${gateId}.`);
  } else if (requiredGate.status !== "pass") {
    errors.push(`${gateId} must be pass before final retirement review preflight can pass.`);
  }
}

if (passkeyGate?.status !== "pending") {
  errors.push("non-synthetic-passkey-operations must remain pending until the real passkey artifact is recorded.");
}
if (reviewGate?.status !== "pending") {
  errors.push("retirement-review must remain pending until passkey evidence exists and final review is actually performed.");
}
for (const path of [
  reviewDocPath,
  stackAreaAuditDocPath,
  stackAreaAuditScriptPath,
  stackAreaAuditTestPath,
  generatedBoundaryDocPath,
  generatedBoundaryScriptPath,
  generatedBoundaryTestPath,
  finalReviewVerifierDocPath,
  finalReviewVerifierScriptPath,
  finalReviewVerifierTestPath,
  goalCompletionAuditDocPath,
  goalCompletionAuditScriptPath,
  goalCompletionAuditTestPath,
  preflightPath,
  preflightTestPath
]) {
  if (!stringArray(reviewGate?.evidence).includes(path)) {
    errors.push(`retirement-review evidence must include ${path}.`);
  }
}

const reviewDoc = await readFile(reviewDocPath, "utf8").catch(() => "");
requireIncludes(reviewDoc, "Status: blocked", "Retirement review doc must remain blocked.");
requireIncludes(reviewDoc, "does not mark Node retirement ready", "Retirement review doc must avoid claiming readiness.");
requireIncludes(reviewDoc, "npm run retirement:review-preflight", "Retirement review doc must include the preflight command.");
requireIncludes(reviewDoc, "npm run retirement:stack-audit", "Retirement review doc must include the stack area audit command.");
requireIncludes(reviewDoc, "npm run retirement:generated-boundary", "Retirement review doc must include the generated artifact boundary command.");
requireIncludes(reviewDoc, "npm run retirement:final-review:verify", "Retirement review doc must include the final review verifier command.");
requireIncludes(reviewDoc, "npm run goal:completion-audit", "Retirement review doc must include the goal completion audit command.");
requireIncludes(reviewDoc, "npm run passkey:evidence:verify", "Retirement review doc must include the strict passkey artifact verifier command.");
requireIncludes(reviewDoc, "docs/test-evidence/generated/passkey-non-synthetic-evidence.json", "Retirement review doc must name the generated passkey evidence artifact.");
requireIncludes(reviewDoc, "docs/test-evidence/generated/final-node-retirement-review.json", "Retirement review doc must name the generated final review evidence artifact.");
requireIncludes(reviewDoc, "non-synthetic passkey operations", "Retirement review doc must name passkey as a remaining blocker.");
requireIncludes(reviewDoc, "final retirement review", "Retirement review doc must name final review as pending.");

for (const check of delegatedChecks) {
  const result = spawnSync(process.execPath, check.args, {
    encoding: "utf8",
    maxBuffer: 1024 * 1024 * 4
  });
  const output = [result.stdout, result.stderr].filter(Boolean).join("\n");
  if (result.status !== 0) {
    errors.push(`${check.name} failed with status ${result.status ?? "unknown"}.`);
    if (output.trim()) {
      errors.push(output.trim());
    }
    continue;
  }
  for (const requiredOutput of check.requiredOutput) {
    if (!output.includes(requiredOutput)) {
      errors.push(`${check.name} output must include: ${requiredOutput}`);
    }
  }
  for (const forbiddenOutput of check.forbiddenOutput) {
    if (output.includes(forbiddenOutput)) {
      errors.push(`${check.name} output must not include stale state: ${forbiddenOutput}`);
    }
  }
}

if (errors.length > 0) {
  console.error("Node retirement review preflight: failed");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log("Node retirement review preflight: pass");
console.log("Boundary, stack area, generated artifact, evidence refresh, passkey preflight, and retirement gate checks are consistent.");
console.log("This does not mark Node retirement ready; non-synthetic passkey operations and final retirement review remain pending.");
