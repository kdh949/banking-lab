import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { spawnSync } from "node:child_process";

type CommandEvidence = {
  command?: unknown;
  status?: unknown;
  exitCode?: unknown;
  summary?: unknown;
  runAfterPasskeyEvidence?: unknown;
};

type FinalReviewArtifact = {
  schemaVersion: 1;
  status: "pass";
  reviewDate: string;
  evidenceKind: "final-node-retirement-review";
  passkeyEvidenceArtifact: string;
  reviewer: string;
  commands: Required<CommandEvidence>[];
  controlAttestations: Record<string, true>;
  remainingBlockers: [];
};

const outputPath = process.env.BANKING_LAB_FINAL_REVIEW_OUTPUT
  ?? "docs/test-evidence/generated/final-node-retirement-review.json";
const commandsFile = process.env.BANKING_LAB_FINAL_REVIEW_COMMANDS_FILE;
const passkeyArtifactPath = process.env.BANKING_LAB_PASSKEY_EVIDENCE_ARTIFACT
  ?? "docs/test-evidence/generated/passkey-non-synthetic-evidence.json";
const passkeyVerifierPath = "scripts/verify-passkey-non-synthetic-evidence.ts";

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

const requiredControls: Array<[string, string]> = [
  ["ledgerBalancedPostings", "BANKING_LAB_FINAL_REVIEW_LEDGER_BALANCED_POSTINGS"],
  ["balancesProjectedFromPostings", "BANKING_LAB_FINAL_REVIEW_BALANCES_PROJECTED_FROM_POSTINGS"],
  ["idempotentExternalCommands", "BANKING_LAB_FINAL_REVIEW_IDEMPOTENT_EXTERNAL_COMMANDS"],
  ["appendOnlyFinalizedTransactions", "BANKING_LAB_FINAL_REVIEW_APPEND_ONLY_FINALIZED_TRANSACTIONS"],
  ["reasonRequiredAudit", "BANKING_LAB_FINAL_REVIEW_REASON_REQUIRED_AUDIT"],
  ["piiMaskedByDefault", "BANKING_LAB_FINAL_REVIEW_PII_MASKED_BY_DEFAULT"],
  ["makerCheckerSeparation", "BANKING_LAB_FINAL_REVIEW_MAKER_CHECKER_SEPARATION"],
  ["workflowDurability", "BANKING_LAB_FINAL_REVIEW_WORKFLOW_DURABILITY"],
  ["outboxDurability", "BANKING_LAB_FINAL_REVIEW_OUTBOX_DURABILITY"],
  ["reconciliationAdjustmentsBalanced", "BANKING_LAB_FINAL_REVIEW_RECONCILIATION_ADJUSTMENTS_BALANCED"],
  ["targetAreasNoDisallowedStack", "BANKING_LAB_FINAL_REVIEW_TARGET_AREAS_NO_DISALLOWED_STACK"],
  ["generatedArtifactsIgnored", "BANKING_LAB_FINAL_REVIEW_GENERATED_ARTIFACTS_IGNORED"],
  ["syntheticOnly", "BANKING_LAB_FINAL_REVIEW_SYNTHETIC_ONLY"],
  ["nodeOnlyCriticalDependencyRemoved", "BANKING_LAB_FINAL_REVIEW_NODE_ONLY_CRITICAL_DEPENDENCY_REMOVED"]
];

function envBoolean(name: string): boolean | undefined {
  const value = process.env[name];
  if (value === undefined) return undefined;
  if (value === "true") return true;
  if (value === "false") return false;
  throw new Error(`${name} must be true or false.`);
}

function requireBoolean(name: string, expected: boolean): void {
  const actual = envBoolean(name);
  if (actual !== expected) {
    throw new Error(`${name} must be ${expected}.`);
  }
}

function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value || value.trim().length === 0) {
    throw new Error(`${name} is required.`);
  }
  return value.trim();
}

function parseReviewDate(): string {
  const value = process.env.BANKING_LAB_FINAL_REVIEW_DATE ?? new Date().toISOString().slice(0, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    throw new Error("BANKING_LAB_FINAL_REVIEW_DATE must be YYYY-MM-DD.");
  }
  return value;
}

function assertNoSensitiveMaterial(label: string, source: string): void {
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
      throw new Error(`${label} appears to contain an unredacted token, credential, cookie, password, reusable passkey artifact, or unmasked phone value.`);
    }
  }
}

function parseCommands(source: string): Required<CommandEvidence>[] {
  let parsed: unknown;
  try {
    parsed = JSON.parse(source);
  } catch (error) {
    throw new Error(`Could not parse final review command evidence JSON: ${(error as Error).message}`);
  }
  if (!Array.isArray(parsed)) {
    throw new Error("Final review command evidence must be a JSON array.");
  }

  const commands = parsed.filter((item): item is CommandEvidence => item && typeof item === "object");
  if (commands.length !== parsed.length) {
    throw new Error("Every final review command evidence item must be an object.");
  }
  const seenCommands = new Set<string>();
  for (const evidence of commands) {
    if (typeof evidence.command !== "string" || evidence.command.trim().length === 0) {
      throw new Error("Every final review command evidence item must include a non-empty command.");
    }
    if (seenCommands.has(evidence.command)) {
      throw new Error(`Final review command evidence must not include duplicate command ${evidence.command}.`);
    }
    seenCommands.add(evidence.command);
    if (evidence.status !== "pass") {
      throw new Error(`${evidence.command} status must be pass.`);
    }
    if (evidence.exitCode !== 0) {
      throw new Error(`${evidence.command} exitCode must be 0.`);
    }
    if (evidence.runAfterPasskeyEvidence !== true) {
      throw new Error(`${evidence.command} must be marked runAfterPasskeyEvidence=true.`);
    }
    if (typeof evidence.summary !== "string" || evidence.summary.trim().length === 0) {
      throw new Error(`${evidence.command} summary must be a non-empty string.`);
    }
  }

  for (const requiredCommand of requiredCommands) {
    const evidence = commands.find((item) => item.command === requiredCommand);
    if (!evidence) {
      throw new Error(`Final review command evidence must include ${requiredCommand}.`);
    }
  }
  return commands as Required<CommandEvidence>[];
}

function controlAttestations(): Record<string, true> {
  const controls: Record<string, true> = {};
  for (const [control, envName] of requiredControls) {
    requireBoolean(envName, true);
    controls[control] = true;
  }
  return controls;
}

function verifyPasskeyArtifact(): void {
  const result = spawnSync(process.execPath, ["--experimental-strip-types", passkeyVerifierPath], {
    encoding: "utf8",
    env: {
      ...process.env,
      BANKING_LAB_PASSKEY_EVIDENCE_ARTIFACT: passkeyArtifactPath
    },
    maxBuffer: 1024 * 1024
  });
  if (result.status === 0) {
    return;
  }
  const output = [result.stderr, result.stdout]
    .filter((value) => value && value.trim().length > 0)
    .join("\n")
    .trim();
  throw new Error([
    "Passkey evidence artifact must pass strict verification before final retirement review can be recorded.",
    output || result.error?.message || "Passkey evidence verifier failed without output."
  ].join("\n"));
}

async function main(): Promise<void> {
  if (!commandsFile) {
    throw new Error("BANKING_LAB_FINAL_REVIEW_COMMANDS_FILE is required.");
  }

  requireBoolean("BANKING_LAB_FINAL_REVIEW_CONFIRMED", true);
  requireBoolean("BANKING_LAB_FINAL_REVIEW_PASSKEY_ARTIFACT_VERIFIED", true);
  verifyPasskeyArtifact();

  const reviewer = requireEnv("BANKING_LAB_FINAL_REVIEW_REVIEWER");
  const reviewDate = parseReviewDate();
  const commandsSource = await readFile(commandsFile, "utf8");
  assertNoSensitiveMaterial("final review command evidence", commandsSource);
  const commands = parseCommands(commandsSource);

  const artifact: FinalReviewArtifact = {
    schemaVersion: 1,
    status: "pass",
    reviewDate,
    evidenceKind: "final-node-retirement-review",
    passkeyEvidenceArtifact: passkeyArtifactPath,
    reviewer,
    commands,
    controlAttestations: controlAttestations(),
    remainingBlockers: []
  };

  const serialized = `${JSON.stringify(artifact, null, 2)}\n`;
  assertNoSensitiveMaterial("final review artifact", serialized);
  await mkdir(path.dirname(outputPath), { recursive: true });
  await writeFile(outputPath, serialized);
  console.log(`Wrote ${outputPath}`);
}

main().catch((error: unknown) => {
  console.error(`Final retirement review recording failed: ${(error as Error).message}`);
  process.exit(1);
});
