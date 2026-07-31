import assert from "node:assert/strict";
import { readdir, readFile, stat } from "node:fs/promises";
import path from "node:path";
import test from "node:test";

const read = (file) => readFile(file, "utf8");

test("portfolio runtime and workflows are pinned to the focused Node 24 boundary", async () => {
  const packageJson = JSON.parse(await read("package.json"));
  const lock = JSON.parse(await read("package-lock.json"));
  const portfolioWorkflow = await read(".github/workflows/portfolio-gate.yml");
  const fullWorkflow = await read(".github/workflows/ci.yml");

  assert.equal(packageJson.engines.node, ">=24 <25");
  assert.equal(packageJson.packageManager, "npm@10.9.2");
  assert.equal(lock.packages[""].engines.node, ">=24 <25");
  assert.equal(lock.packages[""].packageManager, "npm@10.9.2");
  assert.equal((await read(".nvmrc")).trim(), "24");
  assert.equal((await read(".node-version")).trim(), "24");

  for (const script of [
    "portfolio:verify:node",
    "portfolio:verify:backend",
    "portfolio:verify:web",
    "portfolio:verify",
    "test:e2e:portfolio"
  ]) {
    assert.ok(packageJson.scripts[script], `missing package script ${script}`);
  }

  assert.match(portfolioWorkflow, /name: Portfolio gate/);
  assert.match(portfolioWorkflow, /pull_request:/);
  assert.match(portfolioWorkflow, /node-contracts:/);
  assert.match(portfolioWorkflow, /core-payment:/);
  assert.match(portfolioWorkflow, /ops-console:/);
  assert.match(portfolioWorkflow, /settlement-playwright:/);
  assert.match(portfolioWorkflow, /npm run portfolio:verify:node/);
  assert.match(portfolioWorkflow, /npm run portfolio:verify:backend/);
  assert.match(portfolioWorkflow, /npm run test:e2e:portfolio/);
  assert.doesNotMatch(fullWorkflow, /pull_request:/);
  assert.match(fullWorkflow, /name: Full validation/);
  assert.match(fullWorkflow, /push:[\s\S]*- main/);
  assert.match(fullWorkflow, /schedule:[\s\S]*cron:/);
  assert.match(fullWorkflow, /formal-model:/);
  assert.match(fullWorkflow, /security-evidence:/);
});

test("test harnesses no longer require child-process stderr to be completely empty", async () => {
  const testFiles = await listFiles("tests", (file) => file.endsWith(".test.mjs"));
  const forbidden = /assert\.(?:equal|strictEqual)\(\s*(?:result\.)?stderr\s*,\s*["']["']/u;

  for (const file of testFiles) {
    assert.doesNotMatch(await read(file), forbidden, `${file} must validate exit status and evidence instead of clean stderr`);
  }
});

test("generic Helm fallback resolves values and quote filters without a local Helm binary", async () => {
  const renderer = await read("scripts/render-helm-template.ts");

  assert.match(renderer, /parseSimpleYamlScalars/);
  assert.match(renderer, /Unsupported Helm template filter in fallback renderer/);
  assert.match(renderer, /Fallback Helm renderer left unresolved template delimiters/);
  assert.match(renderer, /filter === "quote"/);
  assert.doesNotMatch(renderer, /scalarFromSection/);
});

test("typed settlement client is exported and used by the operations workbench", async () => {
  const module = await read("packages/api-client/src/payment-settlement.ts");
  const index = await read("packages/api-client/src/index.ts");
  const workbench = await read("apps/ops-console/src/components/PaymentSettlementWorkbench.tsx");
  const page = await read("apps/ops-console/src/app/page.tsx");
  const e2e = await read("apps/ops-console/e2e/settlement-operations.spec.ts");
  const config = await read("playwright.portfolio.config.ts");

  for (const method of [
    "importExternalSettlementCsv",
    "externalSettlementImport",
    "createSettlementBatchRun",
    "settlementBatchRun",
    "createPaymentReconciliationRun",
    "paymentReconciliationRun",
    "paymentReconciliationExceptions"
  ]) {
    assert.match(module, new RegExp(`${method}\\(`, "u"));
  }
  for (const mismatch of [
    "MISSING_PAYMENT",
    "MISSING_LEDGER",
    "MISSING_EXTERNAL",
    "AMOUNT_MISMATCH",
    "STATUS_MISMATCH",
    "DUPLICATE_EXTERNAL",
    "VALUE_DATE_MISMATCH",
    "LATE_SETTLEMENT"
  ]) {
    assert.match(module, new RegExp(`"${mismatch}"`, "u"));
  }

  assert.match(index, /export \* from "\.\/payment-settlement"/);
  assert.match(index, /createPaymentSettlementMethods/);
  assert.match(index, /PaymentInstructionStatus = "POSTING_REQUESTED" \| "LEDGER_POSTED"/);

  for (const method of [
    "client.importExternalSettlementCsv",
    "client.createSettlementBatchRun",
    "client.createPaymentReconciliationRun",
    "client.paymentReconciliationExceptions"
  ]) {
    assert.match(workbench, new RegExp(method.replace(".", "\\."), "u"));
  }
  assert.match(workbench, /Spring API connected/);
  assert.match(workbench, /Guided synthetic fixture/);
  assert.match(workbench, /INCLUDED_IN_BATCH does not claim payout finality/);
  assert.match(page, /<PaymentSettlementWorkbench \/>/);
  assert.match(page, /Payment settlement and ledger reconciliation/);
  assert.match(e2e, /POST \/api\/payments\/settlement\/imports/);
  assert.match(e2e, /POST \/api\/payments\/reconciliation\/runs/);
  assert.match(e2e, /Refresh exception queue/);
  assert.match(config, /settlement-operations\.spec\.ts/);
});

test("README presents the settlement vertical slice and ships a 90-second guided GIF", async () => {
  const readme = await read("README.md");
  const fullOverview = await read("docs/full-lab-overview.md");
  const gif = await readFile("docs/assets/settlement-ops-demo.gif");
  const gifStat = await stat("docs/assets/settlement-ops-demo.gif");

  assert.match(readme, /^# Payment Settlement & Ledger Reliability Lab/m);
  assert.match(readme, /Authenticated payment request[\s\S]*3-way reconciliation[\s\S]*exception owner/);
  assert.match(readme, /docs\/assets\/settlement-ops-demo\.gif/);
  assert.match(readme, /npm run portfolio:verify/);
  assert.match(readme, /INCLUDED_IN_BATCH[\s\S]*Neither state claims/);
  assert.match(readme, /full banking lab overview/);
  assert.match(fullOverview, /^# Full Banking Lab Overview/m);
  assert.match(fullOverview, /# Bank-grade Core Banking Lab/);

  assert.equal(gif.subarray(0, 6).toString("ascii"), "GIF89a");
  assert.ok(gifStat.size > 50_000, "guided GIF should contain the full visual walkthrough");
});

async function listFiles(root, predicate) {
  const entries = await readdir(root, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const item = path.join(root, entry.name);
    if (entry.isDirectory()) {
      files.push(...await listFiles(item, predicate));
    } else if (predicate(item)) {
      files.push(item);
    }
  }
  return files;
}
