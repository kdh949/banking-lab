import { mkdirSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { performance } from "node:perf_hooks";
import {
  busiestAccount,
  countTransactionImbalances,
  defaultLargeLedgerConfig,
  generateLargeLedgerDataset,
  parseLargeLedgerConfig,
  type CheckStatus,
  type EvidenceCheck,
  type LargeLedgerDataset,
  type LargeLedgerPosting
} from "./large-ledger-fixture.ts";

interface QueryEvidence {
  readonly id: string;
  readonly status: CheckStatus;
  readonly durationMillis: number;
  readonly fullScanRows: number;
  readonly indexedCandidateRows: number;
  readonly resultRows: number;
  readonly indexEvidence: readonly string[];
  readonly plan: string;
}

interface PartitionReadinessEvidence {
  readonly generatedAt: string;
  readonly command: string;
  readonly status: CheckStatus;
  readonly syntheticOnly: true;
  readonly datasetHash: string;
  readonly config: typeof defaultLargeLedgerConfig;
  readonly migrationEvidence: {
    readonly migrationFilesScanned: readonly string[];
    readonly sourceTablePartitionDecision: string;
  };
  readonly checks: readonly EvidenceCheck[];
  readonly queryEvidence: readonly QueryEvidence[];
  readonly residualRisks: readonly string[];
}

const command = "npm run ledger:query-benchmark";
const outputPath = join("docs", "test-evidence", "generated", "ledger-query-benchmark.json");
const config = parseLargeLedgerConfig(process.argv.slice(2), process.env);
const dataset = generateLargeLedgerDataset(config);
const migrations = readMigrations();
const checks = readinessChecks(dataset, migrations.text);
const queryEvidence = [
  accountStatementQueryEvidence(dataset),
  reconciliationDateRangeQueryEvidence(dataset),
  archiveCandidateQueryEvidence(dataset)
];
const status: CheckStatus = checks.every((check) => check.status === "pass") &&
  queryEvidence.every((query) => query.status === "pass")
  ? "pass"
  : "fail";

const evidence: PartitionReadinessEvidence = {
  generatedAt: new Date().toISOString(),
  command,
  status,
  syntheticOnly: true,
  datasetHash: dataset.datasetHash,
  config,
  migrationEvidence: {
    migrationFilesScanned: migrations.files,
    sourceTablePartitionDecision:
      "ledger_transactions and ledger_postings remain FK-compatible source tables; V027 adds range-partitioned route tables keyed by business_date as the current low-risk migration path."
  },
  checks,
  queryEvidence,
  residualRisks: [
    "Native range partitioning of ledger_transactions or ledger_postings would require a future composite-key migration because many current foreign keys reference ledger_transaction_id directly.",
    "This is an in-memory synthetic benchmark smoke and schema-readiness check, not production throughput evidence.",
    "Live EXPLAIN ANALYZE evidence should be captured later against a disposable PostgreSQL dataset if an environment is provisioned."
  ]
};

mkdirSync(join("docs", "test-evidence", "generated"), { recursive: true });
writeFileSync(outputPath, `${JSON.stringify(evidence, null, 2)}\n`);

console.log(`Ledger partition/archive readiness: ${status}.`);
for (const check of checks) {
  console.log(`- ${check.id}: ${check.status} (${check.details})`);
}
for (const query of queryEvidence) {
  console.log(`- ${query.id}: ${query.status} (${query.resultRows} rows, ${query.durationMillis.toFixed(3)} ms)`);
}
console.log(`Evidence written: ${outputPath}`);

if (status !== "pass") {
  process.exit(1);
}

function readMigrations(): { readonly files: readonly string[]; readonly text: string } {
  const dir = join("db", "migrations");
  const files = readdirSync(dir)
    .filter((file) => file.endsWith(".sql"))
    .sort()
    .map((file) => join(dir, file).replaceAll("\\", "/"));
  return {
    files,
    text: files.map((file) => readFileSync(file, "utf8")).join("\n")
  };
}

function readinessChecks(dataset: LargeLedgerDataset, migrationText: string): EvidenceCheck[] {
  const archiveCandidates = dataset.transactions.filter((transaction) => transaction.businessDate < dataset.config.archiveCutoffDate);
  const archivePostingIds = new Set(archiveCandidates.map((transaction) => transaction.ledgerTransactionId));
  const archivePostings = dataset.postings.filter((posting) => archivePostingIds.has(posting.ledgerTransactionId));
  const routeTableMigrationPresent =
    migrationText.includes("CREATE TABLE ledger_transaction_partition_routes") &&
    migrationText.includes("CREATE TABLE ledger_posting_partition_routes") &&
    migrationText.includes("PARTITION BY RANGE (business_date)");
  const routeTriggersPresent =
    migrationText.includes("CREATE TRIGGER ledger_transactions_partition_route") &&
    migrationText.includes("CREATE TRIGGER ledger_postings_partition_route");

  return [
    {
      id: "business-date-index-coverage",
      status: migrationText.includes("idx_ledger_transactions_business_date") &&
        migrationText.includes("idx_reconciliation_items_business_date")
        ? "pass"
        : "fail",
      details: "ledger transaction and reconciliation date-range indexes are present in Flyway migrations"
    },
    {
      id: "partition-route-schema-present",
      status: routeTableMigrationPresent && routeTriggersPresent ? "pass" : "fail",
      details: "V027 route tables and insert triggers preserve FK-compatible source ledger rows"
    },
    {
      id: "partition-route-consistency",
      status: partitionRoutesConsistent(dataset) ? "pass" : "fail",
      details: `${dataset.transactionRoutes.length} transaction routes and ${dataset.postingRoutes.length} posting routes match generated business dates`
    },
    {
      id: "old-date-archive-candidate-query",
      status: archiveCandidates.length > 0 && countTransactionImbalances(archivePostings) === 0 ? "pass" : "fail",
      details: `${archiveCandidates.length} transactions before ${dataset.config.archiveCutoffDate} remain balanced archive candidates`
    },
    {
      id: "account-statement-index-coverage",
      status: migrationText.includes("idx_ledger_postings_account") &&
        migrationText.includes("idx_ledger_transactions_business_date")
        ? "pass"
        : "fail",
      details: "statement lookup can use posting account/currency and transaction business-date indexes"
    }
  ];
}

function accountStatementQueryEvidence(dataset: LargeLedgerDataset): QueryEvidence {
  const accountId = busiestAccount(dataset.postings);
  const start = "2026-01-01";
  const end = "2026-03-31";
  const postingsByAccount = new Map<string, LargeLedgerPosting[]>();
  for (const posting of dataset.postings) {
    const list = postingsByAccount.get(posting.accountId) ?? [];
    list.push(posting);
    postingsByAccount.set(posting.accountId, list);
  }

  const candidateRows = postingsByAccount.get(accountId) ?? [];
  const started = performance.now();
  const result = candidateRows
    .filter((posting) => posting.businessDate >= start && posting.businessDate <= end)
    .sort((left, right) => left.businessDate.localeCompare(right.businessDate) || left.ledgerPostingId.localeCompare(right.ledgerPostingId));
  const durationMillis = performance.now() - started;

  return {
    id: "account-statement-date-range",
    status: result.length > 0 && candidateRows.length < dataset.postings.length ? "pass" : "fail",
    durationMillis,
    fullScanRows: dataset.postings.length,
    indexedCandidateRows: candidateRows.length,
    resultRows: result.length,
    indexEvidence: ["idx_ledger_postings_account", "idx_ledger_transactions_business_date"],
    plan: `lookup postings by account_id=${accountId}, then constrain joined ledger_transactions.business_date between ${start} and ${end}`
  };
}

function reconciliationDateRangeQueryEvidence(dataset: LargeLedgerDataset): QueryEvidence {
  const start = "2026-04-01";
  const end = "2026-06-30";
  const transactionsByDate = new Map<string, number>();
  for (const transaction of dataset.transactions) {
    transactionsByDate.set(transaction.businessDate, (transactionsByDate.get(transaction.businessDate) ?? 0) + 1);
  }

  const started = performance.now();
  let resultRows = 0;
  for (const [businessDate, count] of transactionsByDate) {
    if (businessDate >= start && businessDate <= end) {
      resultRows += count;
    }
  }
  const durationMillis = performance.now() - started;

  return {
    id: "reconciliation-business-date-range",
    status: resultRows > 0 && transactionsByDate.size < dataset.transactions.length ? "pass" : "fail",
    durationMillis,
    fullScanRows: dataset.transactions.length,
    indexedCandidateRows: transactionsByDate.size,
    resultRows,
    indexEvidence: ["idx_reconciliation_items_business_date", "idx_ledger_transactions_business_date"],
    plan: `use business_date range ${start} through ${end} for reconciliation item and ledger transaction comparison`
  };
}

function archiveCandidateQueryEvidence(dataset: LargeLedgerDataset): QueryEvidence {
  const started = performance.now();
  const candidates = dataset.transactions.filter((transaction) => transaction.businessDate < dataset.config.archiveCutoffDate);
  const candidateIds = new Set(candidates.map((transaction) => transaction.ledgerTransactionId));
  const archivePostings = dataset.postings.filter((posting) => candidateIds.has(posting.ledgerTransactionId));
  const durationMillis = performance.now() - started;

  return {
    id: "archive-candidate-business-date-cutoff",
    status: candidates.length > 0 && countTransactionImbalances(archivePostings) === 0 ? "pass" : "fail",
    durationMillis,
    fullScanRows: dataset.transactions.length,
    indexedCandidateRows: candidates.length,
    resultRows: candidates.length,
    indexEvidence: ["idx_ledger_transactions_business_date", "ledger_transaction_partition_routes"],
    plan: `select posted ledger transactions where business_date < ${dataset.config.archiveCutoffDate}, then archive only balanced source rows plus route metadata`
  };
}

function partitionRoutesConsistent(dataset: LargeLedgerDataset): boolean {
  const transactionDateById = new Map(dataset.transactions.map((transaction) => [transaction.ledgerTransactionId, transaction.businessDate]));
  const postingDateById = new Map(dataset.postings.map((posting) => [posting.ledgerPostingId, posting.businessDate]));
  return dataset.transactionRoutes.every((route) =>
    transactionDateById.get(route.sourceId) === route.businessDate &&
    route.partitionMonth === `${route.businessDate.slice(0, 7)}-01` &&
    route.partitionTable === (route.businessDate.startsWith("2026-") ? "ledger_transaction_partition_routes_2026" : "ledger_transaction_partition_routes_default")
  ) && dataset.postingRoutes.every((route) =>
    postingDateById.get(route.sourceId) === route.businessDate &&
    route.partitionMonth === `${route.businessDate.slice(0, 7)}-01` &&
    route.partitionTable === (route.businessDate.startsWith("2026-") ? "ledger_posting_partition_routes_2026" : "ledger_posting_partition_routes_default")
  );
}
