import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { mkdtemp, readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { promisify } from "node:util";
import test from "node:test";

const execFileAsync = promisify(execFile);

test("synthetic load smoke writes bounded evidence with banking invariants", async () => {
  const directory = await mkdtemp(path.join(tmpdir(), "banking-lab-load-"));
  const output = path.join(directory, "load-test-summary.json");
  const markdownOutput = path.join(directory, "load-test-summary.md");

  await execFileAsync(process.execPath, [
    "tests/load/mixed-banking-traffic.mjs",
    "--iterations",
    "2",
    "--concurrency",
    "2",
    "--output",
    output,
    "--markdown-output",
    markdownOutput
  ]);

  const summary = JSON.parse(await readFile(output, "utf8"));
  const markdown = await readFile(markdownOutput, "utf8");

  assert.equal(summary.syntheticDataOnly, true);
  assert.equal(summary.results.status, "pass");
  assert.equal(summary.results.scenarioRuns, 24);
  assert.equal(summary.results.totalRequests, 34);
  assert.equal(summary.results.failedScenarioRuns, 0);
  assert.equal(summary.domainChecks.ledgerInvariantValid, true);
  assert.equal(summary.domainChecks.auditHashChainValid, true);
  assert.equal(summary.domainChecks.idempotencyReplayResponses, 8);
  assert.equal(summary.domainChecks.fdsHeldCaseVisible, true);
  assert.equal(summary.domainChecks.approvalInboxVisible, true);
  assert.match(markdown, /not a capacity benchmark/i);
  assert.match(markdown, /No real money, real PII, real KYC/i);
});
