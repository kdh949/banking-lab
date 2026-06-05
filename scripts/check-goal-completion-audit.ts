import { spawnSync } from "node:child_process";
import { access, readFile } from "node:fs/promises";

type RequiredGate = {
  id?: unknown;
  status?: unknown;
};

type NodeRetirementGate = {
  status?: unknown;
  requiredGates?: unknown;
};

type ParitySuite = {
  targetStatus?: unknown;
  scenarioCount?: unknown;
};

type ParityScenarioMap = {
  nodeReferenceTestCount?: unknown;
  suites?: unknown;
};

type Requirement = {
  id: string;
  status: "pass" | "blocked" | "failed";
  detail: string;
};

const requireComplete = process.argv.includes("--require-complete");
const gatePath = "docs/migration/node-retirement-gate.json";
const parityPath = "docs/migration/parity-scenarios.json";
const passkeyArtifactPath = "docs/test-evidence/generated/passkey-non-synthetic-evidence.json";
const passkeyVerifierPath = "scripts/verify-passkey-non-synthetic-evidence.ts";
const finalReviewArtifactPath = "docs/test-evidence/generated/final-node-retirement-review.json";
const finalReviewVerifierPath = "scripts/verify-final-retirement-review.ts";
const nodeRetirementGatePath = "scripts/check-node-retirement-gate.ts";

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
  } catch {
    return undefined;
  }
}

function objectArray<T>(value: unknown): T[] {
  return Array.isArray(value) ? value.filter((item): item is T => typeof item === "object" && item !== null) : [];
}

function stringValue(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function numberValue(value: unknown): number {
  return typeof value === "number" ? value : Number.NaN;
}

function runCheck(name: string, args: string[], requiredOutput: string): Requirement {
  const result = spawnSync(process.execPath, args, {
    encoding: "utf8",
    maxBuffer: 1024 * 1024 * 4
  });
  const output = [result.stdout, result.stderr].filter(Boolean).join("\n");
  if (result.status !== 0) {
    return {
      id: name,
      status: "failed",
      detail: `${name} command failed with status ${result.status ?? "unknown"}`
    };
  }
  if (!output.includes(requiredOutput)) {
    return {
      id: name,
      status: "failed",
      detail: `${name} output did not include ${requiredOutput}`
    };
  }
  return {
    id: name,
    status: "pass",
    detail: requiredOutput
  };
}

function runCommand(name: string, args: string[]): { status: number | null; output: string } {
  const result = spawnSync(process.execPath, args, {
    encoding: "utf8",
    maxBuffer: 1024 * 1024 * 4
  });
  const output = [result.stdout, result.stderr].filter(Boolean).join("\n");
  return {
    status: result.status,
    output: output || result.error?.message || `${name} produced no output`
  };
}

const gate = await readJson<NodeRetirementGate>(gatePath);
const parity = await readJson<ParityScenarioMap>(parityPath);
const requiredGates = objectArray<RequiredGate>(gate?.requiredGates);
const paritySuites = objectArray<ParitySuite>(parity?.suites);
const requirements: Requirement[] = [];

requirements.push(runCheck(
  "stack-retirement-by-area",
  ["--experimental-strip-types", "scripts/check-stack-retirement-by-area.ts"],
  "Stack retirement area audit: pass"
));

requirements.push(runCheck(
  "generated-artifact-boundary",
  ["--experimental-strip-types", "scripts/check-generated-artifact-boundary.ts"],
  "Generated artifact boundary audit: pass"
));

requirements.push(runCheck(
  "retirement-ready-state-simulation",
  ["--experimental-strip-types", "scripts/check-node-retirement-ready-simulation.ts"],
  "Node retirement ready-state simulation: pass"
));

requirements.push(runCheck(
  "passkey-non-synthetic-preflight",
  ["--experimental-strip-types", "scripts/check-passkey-non-synthetic-preflight.ts"],
  "Passkey non-synthetic evidence preflight: pass"
));

const mappedScenarioCount = paritySuites.reduce((sum, suite) => sum + numberValue(suite.scenarioCount), 0);
const parityPass = parity?.nodeReferenceTestCount === 43
  && mappedScenarioCount === 43
  && paritySuites.length > 0
  && paritySuites.every((suite) => suite.targetStatus === "pass");
requirements.push({
  id: "mapped-parity",
  status: parityPass ? "pass" : "failed",
  detail: parityPass
    ? "43 mapped Node reference scenarios have targetStatus=pass"
    : "Parity scenario map is incomplete or not all targetStatus values are pass"
});

for (const gateId of [
  "kotlin-spring-health",
  "node-reference-parity",
  "structured-error-contract",
  "next-manifest-renderer",
  "api-backed-channel-parity",
  "evidence-refresh"
]) {
  const requiredGate = requiredGates.find((item) => item.id === gateId);
  requirements.push({
    id: `gate:${gateId}`,
    status: requiredGate?.status === "pass" ? "pass" : "failed",
    detail: `${gateId}: ${stringValue(requiredGate?.status) || "missing"}`
  });
}

const passkeyGate = requiredGates.find((item) => item.id === "non-synthetic-passkey-operations");
const passkeyArtifactExists = await exists(passkeyArtifactPath);
if (passkeyGate?.status === "pass" && passkeyArtifactExists) {
  const verifier = runCommand("passkey evidence verifier", [
    "--experimental-strip-types",
    passkeyVerifierPath
  ]);
  requirements.push({
    id: "gate:non-synthetic-passkey-operations",
    status: verifier.status === 0 && verifier.output.includes("Passkey non-synthetic evidence verification: pass") ? "pass" : "failed",
    detail: verifier.status === 0
      ? "manual-live-passkey evidence artifact exists and verifies"
      : "manual-live-passkey evidence artifact failed strict verification"
  });
} else {
  requirements.push({
    id: "gate:non-synthetic-passkey-operations",
    status: "blocked",
    detail: "manual-live-passkey evidence artifact is missing or gate is not pass"
  });
}

const reviewGate = requiredGates.find((item) => item.id === "retirement-review");
const finalReviewArtifactExists = await exists(finalReviewArtifactPath);
if (reviewGate?.status === "pass" && finalReviewArtifactExists) {
  const verifier = runCommand("final retirement review verifier", [
    "--experimental-strip-types",
    finalReviewVerifierPath
  ]);
  requirements.push({
    id: "gate:retirement-review",
    status: verifier.status === 0 && verifier.output.includes("Final retirement review verification: pass") ? "pass" : "failed",
    detail: verifier.status === 0
      ? "final retirement review artifact exists and verifies"
      : "final retirement review artifact failed strict verification"
  });
} else {
  requirements.push({
    id: "gate:retirement-review",
    status: "blocked",
    detail: "final retirement review artifact is missing or gate is not pass"
  });
}

const nodeRetirementGate = runCommand("node retirement gate", [
  "--experimental-strip-types",
  nodeRetirementGatePath
]);
requirements.push({
  id: "node-retirement-gate",
  status: nodeRetirementGate.status !== 0
    ? "failed"
    : nodeRetirementGate.output.includes("Node reference retirement gate: ready") ? "pass" : "blocked",
  detail: nodeRetirementGate.status !== 0
    ? "node retirement gate command failed"
    : `node retirement gate status: ${nodeRetirementGate.output.includes("Node reference retirement gate: ready") ? "ready" : stringValue(gate?.status) || "missing"}`
});

const failed = requirements.filter((item) => item.status === "failed");
const blocked = requirements.filter((item) => item.status === "blocked");
const complete = failed.length === 0 && blocked.length === 0;

console.log(`Goal completion audit: ${complete ? "complete" : "not complete"}`);
for (const requirement of requirements) {
  console.log(`- ${requirement.id}: ${requirement.status} (${requirement.detail})`);
}

if (failed.length > 0) {
  console.error("Goal completion audit failed:");
  for (const requirement of failed) {
    console.error(`- ${requirement.id}: ${requirement.detail}`);
  }
  process.exit(1);
}

if (requireComplete && blocked.length > 0) {
  console.error("Goal completion is still blocked:");
  for (const requirement of blocked) {
    console.error(`- ${requirement.id}: ${requirement.detail}`);
  }
  process.exit(1);
}
