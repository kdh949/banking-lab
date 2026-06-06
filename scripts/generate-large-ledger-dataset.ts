import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import {
  generateLargeLedgerDataset,
  parseLargeLedgerConfig,
  summarizeLargeLedgerDataset
} from "./large-ledger-fixture.ts";

const outputDir = join("docs", "test-evidence", "generated");
const summaryPath = join(outputDir, "large-ledger-dataset-summary.json");
const smokePath = join("docs", "test-evidence", "ledger-large-dataset-smoke.md");
const command = "npm run ledger:large-dataset-smoke";

mkdirSync(outputDir, { recursive: true });

const config = parseLargeLedgerConfig(process.argv.slice(2), process.env);
const dataset = generateLargeLedgerDataset(config);
const replay = generateLargeLedgerDataset(config);
const summary = summarizeLargeLedgerDataset(dataset, command, replay.datasetHash);

writeFileSync(summaryPath, `${JSON.stringify(summary, null, 2)}\n`);
writeFileSync(smokePath, renderSmokeMarkdown(summaryPath, summary));

console.log(`Large-ledger dataset smoke: ${summary.status}.`);
console.log(`- summary: ${summaryPath}`);
console.log(`- markdown: ${smokePath}`);
console.log(`- datasetHash: ${summary.datasetHash}`);

if (summary.status !== "pass") {
  process.exit(1);
}

function renderSmokeMarkdown(summaryPath: string, summary: ReturnType<typeof summarizeLargeLedgerDataset>): string {
  const checkRows = summary.checks
    .map((check) => `| ${check.id} | ${check.status} | ${check.details} |`)
    .join("\n");

  return `# Large Ledger Dataset Smoke

Date: 2026-06-06

Status: ${summary.status}

This is deterministic synthetic large-ledger evidence for Phase 8 of \`docs/codex/remaining-hardening-goals.md\`. It does not use real customer money, real PII, real KYC, real payment/card networks, Open Banking, external financial institution APIs, or production traffic.

## Command

\`\`\`bash
${summary.command}
\`\`\`

## Dataset Shape

| Metric | Value |
| --- | ---: |
| Customers | ${summary.counts.customers} |
| Customer accounts | ${summary.counts.customerAccounts} |
| System accounts | ${summary.counts.systemAccounts} |
| Ledger transactions | ${summary.counts.ledgerTransactions} |
| Ledger postings | ${summary.counts.ledgerPostings} |
| Idempotency keys | ${summary.counts.idempotencyKeys} |
| Balance projections | ${summary.counts.balanceProjections} |
| Archive candidate transactions | ${summary.counts.archiveCandidateTransactions} |

## Determinism

- Seed: ${summary.config.seed}
- Business date range: ${summary.config.startDate} to ${summary.config.endDate}
- Dataset hash: \`${summary.datasetHash}\`
- Summary JSON: \`${summaryPath}\`

## Checks

| Check | Status | Details |
| --- | --- | --- |
${checkRows}

## Evidence Boundary

- This smoke proves generator determinism, balanced postings, idempotency-key uniqueness, projection equality, synthetic identifiers, and partition-route coverage for the generated fixture.
- It is not a production capacity benchmark and does not prove Kubernetes, external IdP, external provider, or live PostgreSQL throughput.
`;
}
