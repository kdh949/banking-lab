import { readFile } from "node:fs/promises";

type CommandEvidence = {
  command?: unknown;
  status?: unknown;
  exitCode?: unknown;
  summary?: unknown;
  runAfterPasskeyEvidence?: unknown;
};

type FinalReviewArtifact = {
  schemaVersion?: unknown;
  status?: unknown;
  reviewDate?: unknown;
  evidenceKind?: unknown;
  passkeyEvidenceArtifact?: unknown;
  reviewer?: unknown;
  commands?: unknown;
  controlAttestations?: unknown;
  remainingBlockers?: unknown;
};

const artifactPath = process.env.BANKING_LAB_FINAL_REVIEW_ARTIFACT
  ?? "docs/test-evidence/generated/final-node-retirement-review.json";
const passkeyArtifactPath = "docs/test-evidence/generated/passkey-non-synthetic-evidence.json";

const requiredCommands = [
  "npm run parity",
  "npm test",
  "npm run validate:manifests",
  "npm run evidence:pack",
  "npm run retirement:audit",
  "npm run retirement:stack-audit",
  "npm run retirement:generated-boundary",
  "npm run retirement:ready-simulate",
  "npm run passkey:evidence:preflight",
  "npm run retirement:review-preflight",
  "npm run passkey:evidence:verify"
];

const requiredControls = [
  "ledgerBalancedPostings",
  "balancesProjectedFromPostings",
  "idempotentExternalCommands",
  "appendOnlyFinalizedTransactions",
  "reasonRequiredAudit",
  "piiMaskedByDefault",
  "makerCheckerSeparation",
  "workflowDurability",
  "outboxDurability",
  "reconciliationAdjustmentsBalanced",
  "targetAreasNoDisallowedStack",
  "generatedArtifactsIgnored",
  "syntheticOnly",
  "nodeOnlyCriticalDependencyRemoved"
];

function assertNoSensitiveMaterial(source: string): string[] {
  const errors: string[] = [];
  const forbidden = [
    /\baccess_token\b/i,
    /\brefresh_token\b/i,
    /\bid_token\b/i,
    /\bcookie\b/i,
    /\bset-cookie\b/i,
    /\bauthorization:\s*bearer\s+[A-Za-z0-9._-]+/i,
    /\bpassword=/i,
    /manager-webauthn01-pass/i,
    /credentialId/i,
    /attestationObject/i,
    /clientDataJSON/i,
    /\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b/,
    /010-0000-1001/
  ];
  for (const pattern of forbidden) {
    if (pattern.test(source)) {
      errors.push("Final review artifact appears to contain an unredacted token, credential, cookie, password, reusable passkey artifact, or unmasked phone value.");
      break;
    }
  }
  return errors;
}

function objectRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" ? value as Record<string, unknown> : {};
}

function commandEvidenceArray(value: unknown): CommandEvidence[] {
  return Array.isArray(value)
    ? value.filter((item): item is CommandEvidence => item && typeof item === "object")
    : [];
}

function validateFinalReview(artifact: FinalReviewArtifact, source: string): string[] {
  const errors = assertNoSensitiveMaterial(source);
  const commands = commandEvidenceArray(artifact.commands);
  const controls = objectRecord(artifact.controlAttestations);
  const remainingBlockers = Array.isArray(artifact.remainingBlockers) ? artifact.remainingBlockers : undefined;

  if (artifact.schemaVersion !== 1) errors.push("schemaVersion must be 1.");
  if (artifact.status !== "pass") errors.push("status must be pass.");
  if (!/^\d{4}-\d{2}-\d{2}$/.test(String(artifact.reviewDate ?? ""))) errors.push("reviewDate must be YYYY-MM-DD.");
  if (artifact.evidenceKind !== "final-node-retirement-review") errors.push("evidenceKind must be final-node-retirement-review.");
  if (artifact.passkeyEvidenceArtifact !== passkeyArtifactPath) {
    errors.push(`passkeyEvidenceArtifact must be ${passkeyArtifactPath}.`);
  }
  if (typeof artifact.reviewer !== "string" || artifact.reviewer.trim().length === 0) {
    errors.push("reviewer must be a non-empty string.");
  }
  if (!remainingBlockers) {
    errors.push("remainingBlockers must be an array.");
  } else if (remainingBlockers.length > 0) {
    errors.push("remainingBlockers must be empty for final retirement review pass evidence.");
  }

  const seenCommands = new Set<string>();
  for (const evidence of commands) {
    if (typeof evidence.command !== "string" || evidence.command.trim().length === 0) {
      errors.push("Every command evidence item must include a non-empty command.");
      continue;
    }
    if (seenCommands.has(evidence.command)) {
      errors.push(`commands must not include duplicate command ${evidence.command}.`);
    }
    seenCommands.add(evidence.command);
    if (evidence.status !== "pass") errors.push(`${evidence.command} status must be pass.`);
    if (evidence.exitCode !== 0) errors.push(`${evidence.command} exitCode must be 0.`);
    if (evidence.runAfterPasskeyEvidence !== true) {
      errors.push(`${evidence.command} must be run after passkey evidence is recorded.`);
    }
    if (typeof evidence.summary !== "string" || evidence.summary.trim().length === 0) {
      errors.push(`${evidence.command} summary must be a non-empty string.`);
    }
  }

  for (const command of requiredCommands) {
    const evidence = commands.find((item) => item.command === command);
    if (!evidence) {
      errors.push(`commands must include ${command}.`);
      continue;
    }
  }

  for (const control of requiredControls) {
    if (controls[control] !== true) {
      errors.push(`controlAttestations.${control} must be true.`);
    }
  }

  return errors;
}

async function main(): Promise<void> {
  let source: string;
  try {
    source = await readFile(artifactPath, "utf8");
  } catch (error) {
    throw new Error(`Could not read final retirement review artifact at ${artifactPath}: ${(error as Error).message}`);
  }

  let artifact: FinalReviewArtifact;
  try {
    artifact = JSON.parse(source) as FinalReviewArtifact;
  } catch (error) {
    throw new Error(`Could not parse final retirement review artifact at ${artifactPath}: ${(error as Error).message}`);
  }

  const errors = validateFinalReview(artifact, source);
  if (errors.length > 0) {
    console.error("Final retirement review verification: failed");
    for (const error of errors) {
      console.error(`- ${error}`);
    }
    process.exit(1);
  }

  console.log("Final retirement review verification: pass");
  console.log(`Verified final retirement review artifact: ${artifactPath}`);
}

main().catch((error: unknown) => {
  console.error(`Final retirement review verification failed: ${(error as Error).message}`);
  process.exit(1);
});
