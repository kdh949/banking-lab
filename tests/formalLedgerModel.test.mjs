import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { mkdtemp, readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
const script = "scripts/check-tla-model.ts";

test("formal ledger checker writes executable non-static evidence", async () => {
  const dir = await mkdtemp(path.join(tmpdir(), "banking-lab-formal-"));
  const resultPath = path.join(dir, "formal-ledger-tlc-result.json");
  const legacyPath = path.join(dir, "formal-ledger-model.json");

  const { stdout, stderr } = await execFileAsync(
    process.execPath,
    ["--experimental-strip-types", script],
    {
      env: {
        ...process.env,
        BANKING_LAB_FORMAL_ENGINE: "bounded",
        BANKING_LAB_FORMAL_EVIDENCE_PATH: resultPath,
        BANKING_LAB_FORMAL_LEGACY_EVIDENCE_PATH: legacyPath
      },
      maxBuffer: 1024 * 1024
    }
  );
  assert.match(stdout, /Formal ledger executable model check passed/);

  const result = JSON.parse(await readFile(resultPath, "utf8"));
  assert.equal(result.staticCheck, "pass");
  assert.equal(result.staticOnly, false);
  assert.equal(result.boundedModelChecker.status, "pass");
  assert.equal(result.boundedModelChecker.engine, "bounded-state-search");
  assert.ok(result.boundedModelChecker.models[0].statesExplored > 0);
  assert.ok(result.boundedModelChecker.models[1].statesExplored > 0);

  const invariantNames = result.invariantResults.map((item) => item.name);
  assert.ok(invariantNames.includes("BalancedDoubleEntry"));
  assert.ok(invariantNames.includes("AvailableBalanceNonNegative"));
  assert.ok(invariantNames.includes("AdjustmentRequiresApprovalReference"));
  assert.ok(invariantNames.includes("NoDuplicateSideEffectForRetry"));
});

test("formal checker rejects static-only mode in CI", async () => {
  const dir = await mkdtemp(path.join(tmpdir(), "banking-lab-formal-ci-"));
  const resultPath = path.join(dir, "formal-ledger-tlc-result.json");

  await assert.rejects(
    execFileAsync(
      process.execPath,
      ["--experimental-strip-types", script],
      {
        env: {
          ...process.env,
          CI: "true",
          BANKING_LAB_ALLOW_FORMAL_STATIC_ONLY: "true",
          BANKING_LAB_FORMAL_ENGINE: "static",
          BANKING_LAB_FORMAL_EVIDENCE_PATH: resultPath
        },
        maxBuffer: 1024 * 1024
      }
    ),
    /Static-only formal checks are not allowed/
  );
});

test("formal model gate is wired to CI and root TLA artifacts", async () => {
  const packageJson = JSON.parse(await readFile("package.json", "utf8"));
  const workflow = await readFile(".github/workflows/ci.yml", "utf8");
  const ledgerTla = await readFile("formal/Ledger.tla", "utf8");
  const idempotencyTla = await readFile("formal/Idempotency.tla", "utf8");
  const verificationDoc = await readFile("docs/test-evidence/formal-ledger-verification.md", "utf8");

  assert.equal(packageJson.scripts["formal:ledger"], `node --experimental-strip-types ${script}`);
  assert.match(workflow, /npm run formal:ledger/);
  assert.match(workflow, /formal-ledger-tlc-result\.json/);
  assert.doesNotMatch(workflow, /BANKING_LAB_ALLOW_FORMAL_STATIC_ONLY/);
  assert.match(ledgerTla, /AdjustmentRequiresApprovalReference/);
  assert.match(ledgerTla, /AvailableBalanceNonNegative/);
  assert.match(idempotencyTla, /NoDuplicateSideEffectForRetry/);
  assert.match(verificationDoc, /staticOnly: false/);
});
