import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

type CommandEvidenceTemplate = {
  command: string;
  status: "TODO_REPLACE_WITH_pass";
  exitCode: "TODO_REPLACE_WITH_0";
  runAfterPasskeyEvidence: "TODO_REPLACE_WITH_true";
  summary: string;
};

const outputDir = process.env.BANKING_LAB_FINAL_REVIEW_TEMPLATE_DIR ?? "tmp/final-retirement-review-manual";
const reviewDate = process.env.BANKING_LAB_FINAL_REVIEW_DATE ?? new Date().toISOString().slice(0, 10);
const reviewer = process.env.BANKING_LAB_FINAL_REVIEW_REVIEWER ?? "TODO_REPLACE_WITH_FINAL_REVIEWER";
const passkeyArtifactPath = process.env.BANKING_LAB_PASSKEY_EVIDENCE_ARTIFACT
  ?? "docs/test-evidence/generated/passkey-non-synthetic-evidence.json";
const commandsPath = path.join(outputDir, "redacted-final-review-commands.template.json");
const recordCommandPath = path.join(outputDir, "record-final-review-command.template.sh");

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

const controlEnv = [
  "BANKING_LAB_FINAL_REVIEW_LEDGER_BALANCED_POSTINGS",
  "BANKING_LAB_FINAL_REVIEW_BALANCES_PROJECTED_FROM_POSTINGS",
  "BANKING_LAB_FINAL_REVIEW_IDEMPOTENT_EXTERNAL_COMMANDS",
  "BANKING_LAB_FINAL_REVIEW_APPEND_ONLY_FINALIZED_TRANSACTIONS",
  "BANKING_LAB_FINAL_REVIEW_REASON_REQUIRED_AUDIT",
  "BANKING_LAB_FINAL_REVIEW_PII_MASKED_BY_DEFAULT",
  "BANKING_LAB_FINAL_REVIEW_MAKER_CHECKER_SEPARATION",
  "BANKING_LAB_FINAL_REVIEW_WORKFLOW_DURABILITY",
  "BANKING_LAB_FINAL_REVIEW_OUTBOX_DURABILITY",
  "BANKING_LAB_FINAL_REVIEW_RECONCILIATION_ADJUSTMENTS_BALANCED",
  "BANKING_LAB_FINAL_REVIEW_TARGET_AREAS_NO_DISALLOWED_STACK",
  "BANKING_LAB_FINAL_REVIEW_GENERATED_ARTIFACTS_IGNORED",
  "BANKING_LAB_FINAL_REVIEW_SYNTHETIC_ONLY",
  "BANKING_LAB_FINAL_REVIEW_NODE_ONLY_CRITICAL_DEPENDENCY_REMOVED"
];

function validateReviewDate(value: string): string {
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return value;
  }
  throw new Error("BANKING_LAB_FINAL_REVIEW_DATE must be YYYY-MM-DD.");
}

function commandTemplate(command: string): CommandEvidenceTemplate {
  return {
    command,
    status: "TODO_REPLACE_WITH_pass",
    exitCode: "TODO_REPLACE_WITH_0",
    runAfterPasskeyEvidence: "TODO_REPLACE_WITH_true",
    summary: `Replace this summary after ${command} passes after verified non-synthetic passkey evidence exists.`
  };
}

const commands = requiredCommands.map(commandTemplate);
const controlExports = controlEnv
  .map((name) => `  ${name}=TODO_REPLACE_WITH_true \\`)
  .join("\n");
const recordCommand = `env BANKING_LAB_FINAL_REVIEW_CONFIRMED=TODO_REPLACE_WITH_true \\
  BANKING_LAB_FINAL_REVIEW_PASSKEY_ARTIFACT_VERIFIED=TODO_REPLACE_WITH_true \\
  BANKING_LAB_FINAL_REVIEW_REVIEWER=${reviewer} \\
  BANKING_LAB_FINAL_REVIEW_DATE=${validateReviewDate(reviewDate)} \\
  BANKING_LAB_FINAL_REVIEW_COMMANDS_FILE=${commandsPath} \\
  BANKING_LAB_PASSKEY_EVIDENCE_ARTIFACT=${passkeyArtifactPath} \\
${controlExports}
  npm run retirement:final-review:record
`;

async function main(): Promise<void> {
  await mkdir(outputDir, { recursive: true });
  await writeFile(commandsPath, `${JSON.stringify(commands, null, 2)}\n`);
  await writeFile(recordCommandPath, recordCommand);

  console.log("Final retirement review evidence templates prepared.");
  console.log(`Commands template: ${commandsPath}`);
  console.log(`Recorder command template: ${recordCommandPath}`);
  console.log("Templates contain TODO values and do not prove final Node retirement readiness.");
}

main().catch((error: unknown) => {
  console.error(`Final retirement review evidence template preparation failed: ${(error as Error).message}`);
  process.exit(1);
});
