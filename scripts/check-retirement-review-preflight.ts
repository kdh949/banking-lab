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
const passkeyArtifactPath = "docs/test-evidence/generated/passkey-non-synthetic-evidence.json";
const stackAreaAuditDocPath = "docs/test-evidence/stack-retirement-area-audit.md";
const stackAreaAuditScriptPath = "scripts/check-stack-retirement-by-area.ts";
const stackAreaAuditTestPath = "tests/stackRetirementAreaAudit.test.mjs";
const generatedBoundaryDocPath = "docs/test-evidence/generated-artifact-boundary.md";
const generatedBoundaryScriptPath = "scripts/check-generated-artifact-boundary.ts";
const generatedBoundaryTestPath = "tests/generatedArtifactBoundary.test.mjs";
const finalReviewVerifierDocPath = "docs/test-evidence/final-retirement-review-verifier.md";
const finalReviewPrepareScriptPath = "scripts/prepare-final-retirement-review-evidence.ts";
const finalReviewRecorderScriptPath = "scripts/record-final-retirement-review.ts";
const finalReviewVerifierScriptPath = "scripts/verify-final-retirement-review.ts";
const finalReviewArtifactPath = "docs/test-evidence/generated/final-node-retirement-review.json";
const readySimulationScriptPath = "scripts/check-node-retirement-ready-simulation.ts";
const finalReviewPrepareTestPath = "tests/finalRetirementReviewPrepare.test.mjs";
const finalReviewRecorderTestPath = "tests/finalRetirementReviewRecorder.test.mjs";
const finalReviewVerifierTestPath = "tests/finalRetirementReviewVerifier.test.mjs";
const readySimulationTestPath = "tests/nodeRetirementReadySimulation.test.mjs";
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

type DelegatedCheck = {
  name: string;
  args: string[];
  requiredOutput: string[];
  forbiddenOutput: string[];
};

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

function stringValue(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function requireIncludes(source: string, needle: string, message: string): void {
  if (!source.includes(needle)) {
    errors.push(message);
  }
}

function buildDelegatedChecks(state: {
  gateReady: boolean;
  passkeyPassed: boolean;
  reviewPassed: boolean;
}): DelegatedCheck[] {
  const incompleteGateLines = [
    ...(state.passkeyPassed ? [] : ["- non-synthetic-passkey-operations: pending"]),
    ...(state.reviewPassed ? [] : ["- retirement-review: pending"])
  ];
  const staleIncompleteLines = [
    ...(state.passkeyPassed ? ["- non-synthetic-passkey-operations: pending", "gate:non-synthetic-passkey-operations: blocked"] : []),
    ...(state.reviewPassed ? ["- retirement-review: pending", "gate:retirement-review: blocked"] : [])
  ];

  return [
    {
      name: "retirement boundary audit",
      args: ["--experimental-strip-types", "scripts/check-retirement-boundary-audit.ts"],
      requiredOutput: [
        `Retirement boundary audit: ${state.gateReady ? "ready" : "blocked"}`,
        "Reference boundary: pass",
        "Target stack anchors: pass",
        "Evidence paths: pass",
        "Parity map: pass (43/43 mapped scenarios)",
        ...incompleteGateLines
      ],
      forbiddenOutput: ["- evidence-refresh: in-progress", "- evidence-refresh: pending", ...staleIncompleteLines]
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
        `Node retirement gate: ${state.gateReady ? "ready" : "blocked"}`
      ],
      forbiddenOutput: ["Stack retirement area audit: failed", ...staleIncompleteLines]
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
        `Goal completion audit: ${state.gateReady ? "complete" : "not complete"}`,
        `gate:non-synthetic-passkey-operations: ${state.passkeyPassed ? "pass" : "blocked"}`,
        `gate:retirement-review: ${state.reviewPassed ? "pass" : "blocked"}`,
        `node-retirement-gate: ${state.gateReady ? "pass" : "blocked"}`
      ],
      forbiddenOutput: [
        state.gateReady ? "Goal completion audit: not complete" : "Goal completion audit: complete",
        ...staleIncompleteLines
      ]
    },
    {
      name: "passkey evidence preflight",
      args: ["--experimental-strip-types", "scripts/check-passkey-non-synthetic-preflight.ts"],
      requiredOutput: [
        "Passkey non-synthetic evidence preflight: pass",
        state.passkeyPassed
          ? "Manual-live-passkey evidence artifact is recorded and strict verification passed."
          : "This does not prove non-synthetic passkey operations"
      ],
      forbiddenOutput: ["Passkey non-synthetic evidence preflight: failed"]
    },
    {
      name: "node retirement gate",
      args: ["--experimental-strip-types", "scripts/check-node-retirement-gate.ts"],
      requiredOutput: [
        `Node reference retirement gate: ${state.gateReady ? "ready" : "blocked"}`,
        ...incompleteGateLines
      ],
      forbiddenOutput: [
        "- evidence-refresh: in-progress",
        "- evidence-refresh: pending",
        state.gateReady ? "Node reference retirement gate: blocked" : "Node reference retirement gate: ready",
        ...staleIncompleteLines
      ]
    },
    {
      name: "node retirement ready-state simulation",
      args: ["--experimental-strip-types", readySimulationScriptPath],
      requiredOutput: [
        "Node retirement ready-state simulation: pass",
        "Fixture gate and redacted synthetic evidence artifacts can satisfy the ready gate path."
      ],
      forbiddenOutput: ["Node retirement ready-state simulation: failed"]
    }
  ];
}

function assertPendingFinalReviewVerifierFailsClosed(): void {
  const result = spawnSync(process.execPath, ["--experimental-strip-types", finalReviewVerifierScriptPath], {
    encoding: "utf8",
    maxBuffer: 1024 * 1024
  });
  const output = [result.stdout, result.stderr].filter(Boolean).join("\n");

  if (result.status === 0) {
    errors.push("Final retirement review verifier must not pass while the final review artifact is missing and the review gate is pending.");
    return;
  }
  if (!output.includes("Final retirement review verification failed")) {
    errors.push("Final retirement review verifier must emit a strict failure message while the artifact is missing.");
  }
  if (!output.includes(`Could not read final retirement review artifact at ${finalReviewArtifactPath}`)) {
    errors.push(`Final retirement review verifier must fail against the default artifact path: ${finalReviewArtifactPath}.`);
  }
}

function assertPasskeyVerifierPasses(): void {
  const result = spawnSync(process.execPath, ["--experimental-strip-types", passkeyVerifierPath], {
    encoding: "utf8",
    maxBuffer: 1024 * 1024
  });
  const output = [result.stdout, result.stderr].filter(Boolean).join("\n");

  if (result.status !== 0 || !output.includes("Passkey non-synthetic evidence verification: pass")) {
    errors.push("Passkey evidence verifier must pass before the final retirement review preflight can pass after passkey gate completion.");
    if (output.trim()) {
      errors.push(output.trim());
    }
  }
}

function assertFinalReviewVerifierPasses(): void {
  const result = spawnSync(process.execPath, ["--experimental-strip-types", finalReviewVerifierScriptPath], {
    encoding: "utf8",
    maxBuffer: 1024 * 1024
  });
  const output = [result.stdout, result.stderr].filter(Boolean).join("\n");

  if (result.status !== 0 || !output.includes("Final retirement review verification: pass")) {
    errors.push("Final retirement review verifier must pass once retirement-review is marked pass.");
    if (output.trim()) {
      errors.push(output.trim());
    }
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
const passkeyGateStatus = stringValue(passkeyGate?.status);
const reviewGateStatus = stringValue(reviewGate?.status);
const gateStatus = stringValue(gate?.status);
const passkeyPassed = passkeyGateStatus === "pass";
const reviewPassed = reviewGateStatus === "pass";
const gateReady = gateStatus === "ready";

if (packageJson?.scripts?.["retirement:review-preflight"] !== `node --experimental-strip-types ${preflightPath}`) {
  errors.push("package.json must expose retirement:review-preflight.");
}
if (packageJson?.scripts?.["retirement:final-review:prepare"] !== `node --experimental-strip-types ${finalReviewPrepareScriptPath}`) {
  errors.push("package.json must expose retirement:final-review:prepare before final retirement review.");
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
if (packageJson?.scripts?.["retirement:final-review:record"] !== `node --experimental-strip-types ${finalReviewRecorderScriptPath}`) {
  errors.push("package.json must expose retirement:final-review:record before final retirement review.");
}
if (packageJson?.scripts?.["retirement:final-review:verify"] !== `node --experimental-strip-types ${finalReviewVerifierScriptPath}`) {
  errors.push("package.json must expose retirement:final-review:verify before final retirement review.");
}
if (packageJson?.scripts?.["retirement:ready-simulate"] !== `node --experimental-strip-types ${readySimulationScriptPath}`) {
  errors.push("package.json must expose retirement:ready-simulate before final retirement review.");
}
if (packageJson?.scripts?.["goal:completion-audit"] !== `node --experimental-strip-types ${goalCompletionAuditScriptPath}`) {
  errors.push("package.json must expose goal:completion-audit before final retirement review.");
}

if (gateStatus !== "blocked" && gateStatus !== "ready") {
  errors.push("Node retirement gate must be blocked before final review completion or ready after all retirement gates pass.");
}
if (!passkeyPassed && gateStatus !== "blocked") {
  errors.push("Node retirement gate must remain blocked before passkey evidence is complete.");
}
if (reviewPassed && !gateReady) {
  errors.push("Node retirement gate must be ready once retirement-review is marked pass.");
}
if (gateReady && (!passkeyPassed || !reviewPassed)) {
  errors.push("Node retirement gate must not be ready until passkey and final review gates are pass.");
}
if (!passkeyPassed && (typeof gate?.statusReason !== "string" || !/non-synthetic passkey operations and final retirement review remain incomplete/i.test(gate.statusReason))) {
  errors.push("Node retirement gate statusReason must name passkey operations and final retirement review while both remain incomplete.");
}
if (passkeyPassed && !reviewPassed && (typeof gate?.statusReason !== "string" || !/final retirement review/i.test(gate.statusReason))) {
  errors.push("Node retirement gate statusReason must name final retirement review after passkey evidence passes.");
}
if (gateReady && (typeof gate?.statusReason !== "string" || !/all required retirement gates/i.test(gate.statusReason))) {
  errors.push("Node retirement gate statusReason must explain that all required retirement gates are passing when ready.");
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

if (passkeyGateStatus !== "pending" && passkeyGateStatus !== "pass") {
  errors.push("non-synthetic-passkey-operations must be pending before real evidence or pass after strict verification.");
}
if (passkeyPassed) {
  if (!await exists(passkeyArtifactPath)) {
    errors.push(`Passkey evidence artifact is missing after passkey gate pass: ${passkeyArtifactPath}.`);
  }
  if (!stringArray(passkeyGate?.evidence).includes(passkeyArtifactPath)) {
    errors.push(`non-synthetic-passkey-operations evidence must include ${passkeyArtifactPath}.`);
  }
  assertPasskeyVerifierPasses();
}
if (reviewGateStatus !== "pending" && reviewGateStatus !== "pass") {
  errors.push("retirement-review must be pending before final review or pass after strict final review verification.");
}
if (reviewPassed) {
  if (!passkeyPassed) {
    errors.push("retirement-review cannot pass before non-synthetic-passkey-operations passes.");
  }
  if (!await exists(finalReviewArtifactPath)) {
    errors.push(`Final retirement review artifact is missing after retirement-review gate pass: ${finalReviewArtifactPath}.`);
  }
  if (!stringArray(reviewGate?.evidence).includes(finalReviewArtifactPath)) {
    errors.push(`retirement-review evidence must include ${finalReviewArtifactPath}.`);
  }
  assertFinalReviewVerifierPasses();
} else {
  assertPendingFinalReviewVerifierFailsClosed();
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
  preflightPath,
  preflightTestPath
]) {
  if (!stringArray(reviewGate?.evidence).includes(path)) {
    errors.push(`retirement-review evidence must include ${path}.`);
  }
}

const reviewDoc = await readFile(reviewDocPath, "utf8").catch(() => "");
if (reviewPassed) {
  requireIncludes(reviewDoc, "Status: pass", "Retirement review doc must be marked pass after strict final review verification.");
  requireIncludes(reviewDoc, "Node retirement gate is ready", "Retirement review doc must record the ready result after final review passes.");
} else {
  requireIncludes(reviewDoc, "Status: blocked", "Retirement review doc must remain blocked before final review verification passes.");
  requireIncludes(reviewDoc, "does not mark Node retirement ready", "Retirement review doc must avoid claiming readiness before final review passes.");
}
requireIncludes(reviewDoc, "npm run retirement:review-preflight", "Retirement review doc must include the preflight command.");
requireIncludes(reviewDoc, "npm run retirement:final-review:prepare", "Retirement review doc must include the final review prepare command.");
requireIncludes(reviewDoc, "npm run retirement:stack-audit", "Retirement review doc must include the stack area audit command.");
requireIncludes(reviewDoc, "npm run retirement:generated-boundary", "Retirement review doc must include the generated artifact boundary command.");
requireIncludes(reviewDoc, "npm run retirement:final-review:record", "Retirement review doc must include the final review recorder command.");
requireIncludes(reviewDoc, "npm run retirement:final-review:verify", "Retirement review doc must include the final review verifier command.");
requireIncludes(reviewDoc, "npm run retirement:ready-simulate", "Retirement review doc must include the ready-state simulation command.");
requireIncludes(reviewDoc, "npm run goal:completion-audit", "Retirement review doc must include the goal completion audit command.");
requireIncludes(reviewDoc, "npm run passkey:evidence:verify", "Retirement review doc must include the strict passkey artifact verifier command.");
requireIncludes(reviewDoc, "docs/test-evidence/generated/passkey-non-synthetic-evidence.json", "Retirement review doc must name the generated passkey evidence artifact.");
requireIncludes(reviewDoc, "docs/test-evidence/generated/final-node-retirement-review.json", "Retirement review doc must name the generated final review evidence artifact.");
requireIncludes(reviewDoc, "non-synthetic passkey operations", "Retirement review doc must name passkey operations.");
requireIncludes(reviewDoc, "final retirement review", "Retirement review doc must name final review.");

const finalReviewPrepareScript = await readFile(finalReviewPrepareScriptPath, "utf8").catch(() => "");
for (const marker of [
  "redacted-final-review-commands.template.json",
  "record-final-review-command.template.sh",
  "TODO_REPLACE_WITH_pass",
  "TODO_REPLACE_WITH_0",
  "TODO_REPLACE_WITH_true",
  "npm run passkey:evidence:verify",
  "npm run retirement:final-review:record",
  "BANKING_LAB_FINAL_REVIEW_PASSKEY_ARTIFACT_VERIFIED",
  "BANKING_LAB_FINAL_REVIEW_NODE_ONLY_CRITICAL_DEPENDENCY_REMOVED"
]) {
  requireIncludes(finalReviewPrepareScript, marker, `Final review prepare script must include marker: ${marker}.`);
}

const delegatedChecks = buildDelegatedChecks({
  gateReady,
  passkeyPassed,
  reviewPassed
});

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
console.log("Boundary, stack area, generated artifact, ready-state simulation, evidence refresh, passkey preflight, strict final review verifier, and retirement gate checks are consistent.");
if (gateReady) {
  console.log("Node retirement gate is ready; passkey and final retirement review artifacts verify.");
} else if (passkeyPassed) {
  console.log("This does not mark Node retirement ready; final retirement review remains pending.");
} else {
  console.log("This does not mark Node retirement ready; non-synthetic passkey operations and final retirement review remain pending.");
}
