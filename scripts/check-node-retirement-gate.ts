import { spawnSync } from "node:child_process";
import { access, readFile } from "node:fs/promises";

type RequiredGate = {
  id: string;
  status: string;
};

type NodeRetirementGate = {
  status: string;
  statusReason: string;
  nodeReferenceRuntime: {
    paths: string[];
  };
  requiredGates: RequiredGate[];
};

const gatePath = "docs/migration/node-retirement-gate.json";
const boundaryAuditPath = "scripts/check-retirement-boundary-audit.ts";
const passkeyVerifierPath = "scripts/verify-passkey-non-synthetic-evidence.ts";
const finalReviewVerifierPath = "scripts/verify-final-retirement-review.ts";
const passkeyGateId = "non-synthetic-passkey-operations";
const finalReviewGateId = "retirement-review";
const passkeyEvidenceDocPath = "docs/test-evidence/passkey-non-synthetic-operations.md";
const passkeyEvidenceArtifactPath = "docs/test-evidence/generated/passkey-non-synthetic-evidence.json";
const finalReviewArtifactPath = "docs/test-evidence/generated/final-node-retirement-review.json";
const gate = JSON.parse(await readFile(gatePath, "utf8")) as NodeRetirementGate;

async function exists(path: string): Promise<boolean> {
  try {
    await access(path);
    return true;
  } catch {
    return false;
  }
}

type PasskeyEvidenceRecord = {
  schemaVersion?: unknown;
  status?: unknown;
  testDate?: unknown;
  evidenceKind?: unknown;
  authenticatorKind?: unknown;
  usedBrowserVirtualAuthenticator?: unknown;
  usedPlaywrightCdpWebAuthn?: unknown;
  simulatorTokensEnabled?: unknown;
  keycloakRequiredActionCompleted?: unknown;
  springSignedTokenAccepted?: unknown;
  syntheticOnly?: unknown;
  redactionConfirmed?: unknown;
  commands?: unknown;
  staffPanelAssertions?: unknown;
};

async function validateNonSyntheticPasskeyGate(): Promise<string[]> {
  const errors: string[] = [];
  const passkeyGate = gate.requiredGates.find((item) => item.id === passkeyGateId);

  if (!passkeyGate) {
    return [`Missing required retirement gate: ${passkeyGateId}`];
  }

  if (!await exists(passkeyEvidenceDocPath)) {
    errors.push(`Missing non-synthetic passkey evidence boundary: ${passkeyEvidenceDocPath}`);
  } else {
    const evidenceDoc = await readFile(passkeyEvidenceDocPath, "utf8");
    if (passkeyGate.status !== "pass" && !/Status:\s+blocked/i.test(evidenceDoc)) {
      errors.push(`${passkeyEvidenceDocPath} must keep Status: blocked until real passkey evidence is captured.`);
    }
    if (!/not non-synthetic passkey evidence/i.test(evidenceDoc)) {
      errors.push(`${passkeyEvidenceDocPath} must explicitly state that virtual-authenticator smoke is not non-synthetic passkey evidence.`);
    }
  }

  if (passkeyGate.status !== "pass") {
    if (!/non-synthetic passkey operations/i.test(gate.statusReason)) {
      errors.push("Gate statusReason must name non-synthetic passkey operations while the passkey gate is incomplete.");
    }
    return errors;
  }

  if (!await exists(passkeyEvidenceArtifactPath)) {
    errors.push(`Passkey gate is pass, but ${passkeyEvidenceArtifactPath} is missing.`);
    return errors;
  }

  const verifierErrors = runPasskeyEvidenceVerifier();
  if (verifierErrors.length > 0) {
    errors.push(...verifierErrors);
    return errors;
  }

  let evidence: PasskeyEvidenceRecord;
  try {
    evidence = JSON.parse(await readFile(passkeyEvidenceArtifactPath, "utf8")) as PasskeyEvidenceRecord;
  } catch (error) {
    errors.push(`Could not parse ${passkeyEvidenceArtifactPath}: ${(error as Error).message}`);
    return errors;
  }

  const authenticatorKind = typeof evidence.authenticatorKind === "string" ? evidence.authenticatorKind : "";
  const commands = Array.isArray(evidence.commands) ? evidence.commands : [];
  const staffPanelAssertions = evidence.staffPanelAssertions && typeof evidence.staffPanelAssertions === "object"
    ? evidence.staffPanelAssertions as Record<string, unknown>
    : {};

  if (evidence.schemaVersion !== 1) errors.push("Passkey evidence schemaVersion must be 1.");
  if (evidence.status !== "pass") errors.push("Passkey evidence status must be pass.");
  if (!/^\d{4}-\d{2}-\d{2}/.test(String(evidence.testDate ?? ""))) errors.push("Passkey evidence testDate must start with YYYY-MM-DD.");
  if (evidence.evidenceKind !== "manual-live-passkey") errors.push("Passkey evidenceKind must be manual-live-passkey.");
  if (!["platform", "hardware-security-key"].includes(authenticatorKind)) errors.push("Passkey authenticatorKind must be platform or hardware-security-key.");
  if (/virtual|simulated|cdp/i.test(authenticatorKind)) errors.push("Passkey authenticatorKind must not be virtual, simulated, or CDP-backed.");
  if (evidence.usedBrowserVirtualAuthenticator !== false) errors.push("usedBrowserVirtualAuthenticator must be false.");
  if (evidence.usedPlaywrightCdpWebAuthn !== false) errors.push("usedPlaywrightCdpWebAuthn must be false.");
  if (evidence.simulatorTokensEnabled !== false) errors.push("simulatorTokensEnabled must be false.");
  if (evidence.keycloakRequiredActionCompleted !== true) errors.push("keycloakRequiredActionCompleted must be true.");
  if (evidence.springSignedTokenAccepted !== true) errors.push("springSignedTokenAccepted must be true.");
  if (evidence.syntheticOnly !== true) errors.push("syntheticOnly must be true.");
  if (evidence.redactionConfirmed !== true) errors.push("redactionConfirmed must be true.");
  if (commands.length === 0 || !commands.every((item) => typeof item === "string" && item.trim().length > 0)) {
    errors.push("Passkey evidence commands must include at least one non-empty command.");
  }
  for (const key of [
    "webAuthnLoaded",
    "managerSubjectObserved",
    "bearerTokenTypeObserved",
    "syntheticCustomerObserved",
    "maskedPiiObserved",
    "auditEventObserved"
  ]) {
    if (staffPanelAssertions[key] !== true) {
      errors.push(`staffPanelAssertions.${key} must be true.`);
    }
  }

  return errors;
}

function runPasskeyEvidenceVerifier(): string[] {
  const result = spawnSync(process.execPath, ["--experimental-strip-types", passkeyVerifierPath], {
    encoding: "utf8",
    env: {
      ...process.env,
      BANKING_LAB_PASSKEY_EVIDENCE_ARTIFACT: passkeyEvidenceArtifactPath
    },
    maxBuffer: 1024 * 1024
  });
  if (result.status === 0) {
    return [];
  }
  const output = [result.stderr, result.stdout]
    .filter((value) => value && value.trim().length > 0)
    .join("\n")
    .trim();
  return [
    "Passkey evidence artifact must pass strict verification before the passkey gate can pass.",
    output || result.error?.message || "Passkey evidence verifier failed without output."
  ];
}

async function validateFinalRetirementReviewGate(): Promise<string[]> {
  const errors: string[] = [];
  const reviewGate = gate.requiredGates.find((item) => item.id === finalReviewGateId);

  if (!reviewGate) {
    return [`Missing required retirement gate: ${finalReviewGateId}`];
  }

  if (reviewGate.status !== "pass") {
    if (!/final retirement review/i.test(gate.statusReason)) {
      errors.push("Gate statusReason must name final retirement review while the review gate is incomplete.");
    }
    return errors;
  }

  const verifierErrors = runFinalReviewVerifier();
  if (verifierErrors.length > 0) {
    errors.push(...verifierErrors);
  }
  return errors;
}

function runFinalReviewVerifier(): string[] {
  const result = spawnSync(process.execPath, ["--experimental-strip-types", finalReviewVerifierPath], {
    encoding: "utf8",
    env: {
      ...process.env,
      BANKING_LAB_FINAL_REVIEW_ARTIFACT: finalReviewArtifactPath
    },
    maxBuffer: 1024 * 1024
  });
  if (result.status === 0) {
    return [];
  }
  const output = [result.stderr, result.stdout]
    .filter((value) => value && value.trim().length > 0)
    .join("\n")
    .trim();
  return [
    "Final retirement review artifact must pass strict verification before the review gate can pass.",
    output || result.error?.message || "Final retirement review verifier failed without output."
  ];
}

function runReadyBoundaryAudit(): string[] {
  const result = spawnSync(process.execPath, ["--experimental-strip-types", boundaryAuditPath], {
    encoding: "utf8",
    maxBuffer: 1024 * 1024
  });
  if (result.status === 0) {
    return [];
  }
  const output = [result.stderr, result.stdout]
    .filter((value) => value && value.trim().length > 0)
    .join("\n")
    .trim();
  return [
    "Retirement boundary audit must pass before the Node reference gate can be ready.",
    output || result.error?.message || "Boundary audit failed without output."
  ];
}

const missingReferencePaths: string[] = [];
for (const referencePath of gate.nodeReferenceRuntime.paths) {
  if (!await exists(referencePath)) {
    missingReferencePaths.push(referencePath);
  }
}

const incompleteGates = gate.requiredGates.filter((item) => item.status !== "pass");
const ready = gate.status === "ready";
const passkeyErrors = await validateNonSyntheticPasskeyGate();
const finalReviewErrors = await validateFinalRetirementReviewGate();

if (missingReferencePaths.length > 0 && !ready) {
  console.error("Node reference retirement gate: failed");
  console.error("Reference runtime paths are missing before parity is complete:");
  for (const referencePath of missingReferencePaths) {
    console.error(`- ${referencePath}`);
  }
  process.exit(1);
}

if (passkeyErrors.length > 0) {
  console.error("Node reference retirement gate: failed");
  console.error("Non-synthetic passkey evidence guard failed:");
  for (const error of passkeyErrors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

if (finalReviewErrors.length > 0) {
  console.error("Node reference retirement gate: failed");
  console.error("Final retirement review guard failed:");
  for (const error of finalReviewErrors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

if (ready && incompleteGates.length > 0) {
  console.error("Node reference retirement gate: failed");
  console.error("Gate status is ready but required gates are incomplete:");
  for (const item of incompleteGates) {
    console.error(`- ${item.id}: ${item.status}`);
  }
  process.exit(1);
}

if (ready) {
  const boundaryAuditErrors = runReadyBoundaryAudit();
  if (boundaryAuditErrors.length > 0) {
    console.error("Node reference retirement gate: failed");
    console.error("Ready boundary audit failed:");
    for (const error of boundaryAuditErrors) {
      console.error(error);
    }
    process.exit(1);
  }
}

if (!ready) {
  console.log("Node reference retirement gate: blocked");
  console.log(gate.statusReason);
  console.log("Incomplete gates:");
  for (const item of incompleteGates) {
    console.log(`- ${item.id}: ${item.status}`);
  }
  process.exit(0);
}

console.log("Node reference retirement gate: ready");
console.log("All required retirement gates have passing evidence.");
