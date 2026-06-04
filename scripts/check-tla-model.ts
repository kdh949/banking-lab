import { spawnSync } from "node:child_process";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";

const tlaPath = path.join("formal", "tla", "Ledger.tla");
const cfgPath = path.join("formal", "tla", "Ledger.cfg");
const evidencePath = path.join("docs", "test-evidence", "generated", "formal-ledger-model.json");

const requiredTokens = [
  "BalancedDoubleEntry",
  "IdempotencySingleBusinessResult",
  "ReversalReferencesOriginal",
  "ClosedDateNoDirectPosting",
  "BalanceProjectionRecalculable",
  "HeldOrFailedTransferNoPosting",
  "Accounts",
  "Idempotency",
  "Reversal",
  "Closed"
];

const tla = await readFile(tlaPath, "utf8");
const cfg = await readFile(cfgPath, "utf8");
const missingTokens = requiredTokens.filter((token) => !tla.includes(token) && !cfg.includes(token));
if (missingTokens.length > 0) {
  throw new Error(`Ledger TLA+ model is missing required tokens: ${missingTokens.join(", ")}`);
}

const tlcCommand = process.env.BANKING_LAB_TLC_CMD || "tlc";
const tlc = spawnSync(tlcCommand, ["-config", "Ledger.cfg", "Ledger.tla"], {
  cwd: path.join(process.cwd(), "formal", "tla"),
  encoding: "utf8"
});

const tlcError = tlc.error as NodeJS.ErrnoException | undefined;
const tlcAvailable = !tlcError || tlcError.code !== "ENOENT";
if (tlcAvailable && tlc.status !== 0) {
  await writeEvidence({
    staticCheck: "pass",
    tlc: "failed",
    command: `${tlcCommand} -config Ledger.cfg Ledger.tla`,
    stderr: tlc.stderr,
    stdout: tlc.stdout
  });
  throw new Error("TLC model check failed. See docs/test-evidence/generated/formal-ledger-model.json.");
}

await writeEvidence({
  staticCheck: "pass",
  tlc: tlcAvailable ? "pass" : "skipped",
  command: tlcAvailable ? `${tlcCommand} -config Ledger.cfg Ledger.tla` : `${tlcCommand} not found`,
  invariants: requiredTokens.filter((token) => token.endsWith("Entry") || token.endsWith("Result") || token.endsWith("Original") || token.endsWith("Posting") || token.endsWith("Recalculable")),
  note: tlcAvailable
    ? "TLC executed in addition to repository-level static artifact checks."
    : "TLC is not installed in this environment. This is a static formal artifact check only, not a completed formal verification run."
});

console.log(`Formal ledger model static check passed. TLC ${tlcAvailable ? "passed" : "skipped"}.`);

async function writeEvidence(payload: Record<string, unknown>) {
  await mkdir(path.dirname(evidencePath), { recursive: true });
  await writeFile(
    evidencePath,
    `${JSON.stringify({
      generatedAt: new Date().toISOString(),
      model: tlaPath,
      config: cfgPath,
      ...payload
    }, null, 2)}\n`,
    "utf8"
  );
}
