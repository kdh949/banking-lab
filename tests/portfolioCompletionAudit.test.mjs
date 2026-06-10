import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { readFile } from "node:fs/promises";
import test from "node:test";

const script = "scripts/check-portfolio-completion-audit.ts";
const evidencePath = "docs/test-evidence/generated/portfolio-completion-audit.json";
const docPath = "docs/test-evidence/portfolio-completion-audit.md";

test("portfolio completion audit classifies PLAN final conditions without overclaiming", async () => {
  const result = spawnSync("npm", ["run", "portfolio:completion-audit"], {
    encoding: "utf8",
    maxBuffer: 1024 * 1024 * 4
  });

  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.match(result.stdout, /Portfolio completion audit: not-complete/);
  assert.match(result.stdout, /hosted-ci-evidence: blocked/);
  assert.match(result.stdout, /live-route-api-execution: partial/);
  assert.match(result.stdout, /openapi-dto-diff-gate: pass/);
  assert.match(result.stdout, /event-envelope-runtime-gate: pass/);
  assert.match(result.stdout, /call-center-api-backed-workflow: pass/);

  const evidence = JSON.parse(await readFile(evidencePath, "utf8"));
  assert.equal(evidence.syntheticOnly, true);
  assert.equal(evidence.issue, "https://github.com/kdh949/banking-lab/issues/86");
  assert.equal(evidence.overallStatus, "not-complete");
  assert.ok(evidence.statusCounts.pass >= 10);
  assert.equal(evidence.statusCounts.blocked, 1);
  assert.equal(evidence.statusCounts.partial, 1);
  assert.equal(evidence.statusCounts.failed, 0);

  const statuses = new Map(evidence.requirements.map((item) => [item.id, item.status]));
  assert.equal(statuses.get("hosted-ci-evidence"), "blocked");
  assert.equal(statuses.get("live-route-api-execution"), "partial");
  assert.equal(statuses.get("final-command-refresh"), "pass");
  assert.equal(statuses.get("openapi-dto-diff-gate"), "pass");
  assert.equal(statuses.get("event-envelope-runtime-gate"), "pass");
  assert.equal(statuses.get("synthetic-only-boundary"), "pass");
});

test("portfolio completion audit require-complete fails while blocked or partial evidence remains", () => {
  const result = spawnSync("npm", ["run", "portfolio:completion-audit", "--", "--require-complete"], {
    encoding: "utf8",
    maxBuffer: 1024 * 1024 * 4
  });

  assert.notEqual(result.status, 0);
  assert.match(result.stdout, /Portfolio completion audit: not-complete/);
  assert.match(result.stderr, /Portfolio completion is not complete/);
  assert.match(result.stderr, /hosted-ci-evidence: blocked/);
  assert.match(result.stderr, /live-route-api-execution: partial/);
  assert.doesNotMatch(result.stderr, /final-command-refresh: partial/);
});

test("portfolio completion audit is documented and wired as a separate PLAN gate", async () => {
  const packageJson = JSON.parse(await readFile("package.json", "utf8"));
  const doc = await readFile(docPath, "utf8");
  const scriptSource = await readFile(script, "utf8");

  assert.equal(packageJson.scripts["portfolio:completion-audit"], `node --experimental-strip-types ${script}`);
  assert.match(doc, /Status: not complete/);
  assert.match(doc, /separate from the older Node retirement `goal:completion-audit` gate/);
  assert.match(doc, /runner_id: 0/);
  assert.match(doc, /skipped\s+Playwright is not pass evidence/);
  assert.match(doc, /final-command-refresh.*now `pass`/s);
  assert.match(doc, /Non-Overclaim Rule/);
  assert.match(scriptSource, /planCondition/);
  assert.match(scriptSource, /docs\/test-evidence\/generated\/portfolio-completion-audit\.json/);
});
