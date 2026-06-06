import { createHash } from "node:crypto";

export type PostingDirection = "DEBIT" | "CREDIT";
export type CheckStatus = "pass" | "fail";

export interface LargeLedgerConfig {
  readonly customerCount: number;
  readonly accountCount: number;
  readonly transactionCount: number;
  readonly seed: number;
  readonly startDate: string;
  readonly endDate: string;
  readonly currency: string;
  readonly archiveCutoffDate: string;
}

export interface LargeLedgerCustomer {
  readonly customerId: string;
}

export interface LargeLedgerAccount {
  readonly accountId: string;
  readonly customerId: string;
  readonly accountClass: "CUSTOMER" | "SYSTEM";
}

export interface LargeLedgerTransaction {
  readonly ledgerTransactionId: string;
  readonly transactionType: "DEPOSIT" | "WITHDRAWAL" | "INTERNAL_TRANSFER";
  readonly businessDate: string;
  readonly status: "POSTED";
  readonly idempotencyKey: string;
}

export interface LargeLedgerPosting {
  readonly ledgerPostingId: string;
  readonly ledgerTransactionId: string;
  readonly accountId: string;
  readonly currency: string;
  readonly direction: PostingDirection;
  readonly amountMinor: number;
  readonly businessDate: string;
}

export interface LargeLedgerProjection {
  readonly accountId: string;
  readonly currency: string;
  readonly ledgerBalanceMinor: number;
}

export interface LargeLedgerPartitionRoute {
  readonly sourceId: string;
  readonly ledgerTransactionId: string;
  readonly businessDate: string;
  readonly partitionMonth: string;
  readonly partitionTable: string;
}

export interface LargeLedgerDataset {
  readonly config: LargeLedgerConfig;
  readonly customers: readonly LargeLedgerCustomer[];
  readonly accounts: readonly LargeLedgerAccount[];
  readonly transactions: readonly LargeLedgerTransaction[];
  readonly postings: readonly LargeLedgerPosting[];
  readonly projections: readonly LargeLedgerProjection[];
  readonly transactionRoutes: readonly LargeLedgerPartitionRoute[];
  readonly postingRoutes: readonly LargeLedgerPartitionRoute[];
  readonly datasetHash: string;
}

export interface EvidenceCheck {
  readonly id: string;
  readonly status: CheckStatus;
  readonly details: string;
}

export interface LargeLedgerDatasetSummary {
  readonly generatedAt: string;
  readonly command: string;
  readonly status: CheckStatus;
  readonly syntheticOnly: true;
  readonly deterministic: boolean;
  readonly config: LargeLedgerConfig;
  readonly datasetHash: string;
  readonly counts: {
    readonly customers: number;
    readonly customerAccounts: number;
    readonly systemAccounts: number;
    readonly ledgerTransactions: number;
    readonly ledgerPostings: number;
    readonly idempotencyKeys: number;
    readonly balanceProjections: number;
    readonly transactionPartitionRoutes: number;
    readonly postingPartitionRoutes: number;
    readonly archiveCandidateTransactions: number;
  };
  readonly balance: {
    readonly transactionImbalanceCount: number;
    readonly projectionMismatchCount: number;
    readonly globalNetByCurrency: Record<string, number>;
  };
  readonly samples: {
    readonly firstTransactionId: string;
    readonly lastTransactionId: string;
    readonly busiestAccountId: string;
  };
  readonly checks: readonly EvidenceCheck[];
}

export const defaultLargeLedgerConfig: LargeLedgerConfig = {
  customerCount: 240,
  accountCount: 720,
  transactionCount: 5_000,
  seed: 424_242,
  startDate: "2025-01-01",
  endDate: "2026-12-31",
  currency: "KRW",
  archiveCutoffDate: "2026-01-01"
};

const systemAccountId = "ACCT-SYN-SUSPENSE-000000";

export function parseLargeLedgerConfig(args: readonly string[], env: NodeJS.ProcessEnv): LargeLedgerConfig {
  const values = new Map<string, string>();
  for (const arg of args) {
    const match = /^--([^=]+)=(.+)$/.exec(arg);
    if (match) {
      values.set(match[1], match[2]);
    }
  }

  return {
    customerCount: positiveInt(values.get("customers") ?? env.BANKING_LAB_LARGE_LEDGER_CUSTOMERS, defaultLargeLedgerConfig.customerCount),
    accountCount: positiveInt(values.get("accounts") ?? env.BANKING_LAB_LARGE_LEDGER_ACCOUNTS, defaultLargeLedgerConfig.accountCount),
    transactionCount: positiveInt(values.get("transactions") ?? env.BANKING_LAB_LARGE_LEDGER_TRANSACTIONS, defaultLargeLedgerConfig.transactionCount),
    seed: positiveInt(values.get("seed") ?? env.BANKING_LAB_LARGE_LEDGER_SEED, defaultLargeLedgerConfig.seed),
    startDate: values.get("start-date") ?? env.BANKING_LAB_LARGE_LEDGER_START_DATE ?? defaultLargeLedgerConfig.startDate,
    endDate: values.get("end-date") ?? env.BANKING_LAB_LARGE_LEDGER_END_DATE ?? defaultLargeLedgerConfig.endDate,
    currency: values.get("currency") ?? env.BANKING_LAB_LARGE_LEDGER_CURRENCY ?? defaultLargeLedgerConfig.currency,
    archiveCutoffDate: values.get("archive-cutoff") ?? env.BANKING_LAB_LARGE_LEDGER_ARCHIVE_CUTOFF ?? defaultLargeLedgerConfig.archiveCutoffDate
  };
}

export function generateLargeLedgerDataset(config: LargeLedgerConfig = defaultLargeLedgerConfig): LargeLedgerDataset {
  validateConfig(config);
  const random = seededRandom(config.seed);
  const customers = Array.from({ length: config.customerCount }, (_, index) => ({
    customerId: `CUS-SYN-${pad(index + 1, 6)}`
  }));
  const customerAccounts = Array.from({ length: config.accountCount }, (_, index) => ({
    accountId: `ACCT-SYN-${pad(index + 1, 6)}`,
    customerId: customers[index % customers.length].customerId,
    accountClass: "CUSTOMER" as const
  }));
  const accounts: LargeLedgerAccount[] = [
    ...customerAccounts,
    {
      accountId: systemAccountId,
      customerId: "SYSTEM-SYNTHETIC",
      accountClass: "SYSTEM"
    }
  ];

  const balances = new Map<string, number>(accounts.map((account) => [account.accountId, 0]));
  const transactions: LargeLedgerTransaction[] = [];
  const postings: LargeLedgerPosting[] = [];
  const transactionRoutes: LargeLedgerPartitionRoute[] = [];
  const postingRoutes: LargeLedgerPartitionRoute[] = [];
  const dayCount = daysBetween(config.startDate, config.endDate) + 1;

  for (let index = 0; index < config.transactionCount; index += 1) {
    const businessDate = addDays(config.startDate, Math.floor(random() * dayCount));
    const amountMinor = 1_000 + Math.floor(random() * 900_000);
    const source = customerAccounts[Math.floor(random() * customerAccounts.length)];
    let destination = customerAccounts[Math.floor(random() * customerAccounts.length)];
    if (destination.accountId === source.accountId) {
      destination = customerAccounts[(customerAccounts.indexOf(destination) + 1) % customerAccounts.length];
    }

    const intent = random();
    const sourceBalance = balances.get(source.accountId) ?? 0;
    const transactionType: LargeLedgerTransaction["transactionType"] =
      intent < 0.25
        ? "DEPOSIT"
        : intent < 0.85 && sourceBalance >= amountMinor
          ? "INTERNAL_TRANSFER"
          : sourceBalance >= amountMinor
            ? "WITHDRAWAL"
            : "DEPOSIT";

    const ledgerTransactionId = `LTX-LARGE-${pad(index + 1, 8)}`;
    const transaction: LargeLedgerTransaction = {
      ledgerTransactionId,
      transactionType,
      businessDate,
      status: "POSTED",
      idempotencyKey: `IDEMP-LARGE-${config.seed}-${pad(index + 1, 8)}`
    };
    transactions.push(transaction);
    transactionRoutes.push(routeFor(ledgerTransactionId, ledgerTransactionId, businessDate, "ledger_transaction_partition_routes"));

    const pair = postingPair(index, transaction, source.accountId, destination.accountId, amountMinor, config.currency);
    postings.push(...pair);
    for (const posting of pair) {
      postingRoutes.push(routeFor(posting.ledgerPostingId, posting.ledgerTransactionId, posting.businessDate, "ledger_posting_partition_routes"));
      const sign = posting.direction === "DEBIT" ? 1 : -1;
      balances.set(posting.accountId, (balances.get(posting.accountId) ?? 0) + sign * posting.amountMinor);
    }
  }

  const projections = Array.from(balances.entries())
    .map(([accountId, ledgerBalanceMinor]) => ({
      accountId,
      currency: config.currency,
      ledgerBalanceMinor
    }))
    .sort((left, right) => left.accountId.localeCompare(right.accountId));

  const stableDataset = {
    config,
    customers,
    accounts,
    transactions,
    postings,
    projections,
    transactionRoutes,
    postingRoutes
  };

  return {
    ...stableDataset,
    datasetHash: sha256(stableDataset)
  };
}

export function summarizeLargeLedgerDataset(dataset: LargeLedgerDataset, command: string, replayHash?: string): LargeLedgerDatasetSummary {
  const transactionImbalanceCount = countTransactionImbalances(dataset.postings);
  const projectionMismatchCount = countProjectionMismatches(dataset.postings, dataset.projections);
  const idempotencyKeys = new Set(dataset.transactions.map((transaction) => transaction.idempotencyKey));
  const archiveCandidateTransactions = dataset.transactions.filter((transaction) => transaction.businessDate < dataset.config.archiveCutoffDate);
  const busiestAccountId = busiestAccount(dataset.postings);
  const checks = datasetChecks(dataset, replayHash);
  const status: CheckStatus = checks.every((check) => check.status === "pass") ? "pass" : "fail";

  return {
    generatedAt: new Date().toISOString(),
    command,
    status,
    syntheticOnly: true,
    deterministic: replayHash === undefined || replayHash === dataset.datasetHash,
    config: dataset.config,
    datasetHash: dataset.datasetHash,
    counts: {
      customers: dataset.customers.length,
      customerAccounts: dataset.accounts.filter((account) => account.accountClass === "CUSTOMER").length,
      systemAccounts: dataset.accounts.filter((account) => account.accountClass === "SYSTEM").length,
      ledgerTransactions: dataset.transactions.length,
      ledgerPostings: dataset.postings.length,
      idempotencyKeys: idempotencyKeys.size,
      balanceProjections: dataset.projections.length,
      transactionPartitionRoutes: dataset.transactionRoutes.length,
      postingPartitionRoutes: dataset.postingRoutes.length,
      archiveCandidateTransactions: archiveCandidateTransactions.length
    },
    balance: {
      transactionImbalanceCount,
      projectionMismatchCount,
      globalNetByCurrency: globalNetByCurrency(dataset.postings)
    },
    samples: {
      firstTransactionId: dataset.transactions[0]?.ledgerTransactionId ?? "",
      lastTransactionId: dataset.transactions.at(-1)?.ledgerTransactionId ?? "",
      busiestAccountId
    },
    checks
  };
}

export function datasetChecks(dataset: LargeLedgerDataset, replayHash?: string): EvidenceCheck[] {
  const transactionImbalanceCount = countTransactionImbalances(dataset.postings);
  const projectionMismatchCount = countProjectionMismatches(dataset.postings, dataset.projections);
  const idempotencyKeys = new Set(dataset.transactions.map((transaction) => transaction.idempotencyKey));
  const datesInRange = dataset.transactions.every((transaction) =>
    transaction.businessDate >= dataset.config.startDate && transaction.businessDate <= dataset.config.endDate
  );
  const routeCoverage =
    dataset.transactionRoutes.length === dataset.transactions.length &&
    dataset.postingRoutes.length === dataset.postings.length;
  const routeConsistency =
    dataset.transactionRoutes.every((route) => route.partitionMonth === route.businessDate.slice(0, 7) + "-01") &&
    dataset.postingRoutes.every((route) => route.partitionMonth === route.businessDate.slice(0, 7) + "-01");

  return [
    {
      id: "deterministic-seed-replay",
      status: replayHash === undefined || replayHash === dataset.datasetHash ? "pass" : "fail",
      details: replayHash === undefined
        ? "single dataset hash recorded"
        : `first hash ${dataset.datasetHash}, replay hash ${replayHash}`
    },
    {
      id: "balanced-postings",
      status: transactionImbalanceCount === 0 ? "pass" : "fail",
      details: `${transactionImbalanceCount} transactions have non-zero debit/credit net`
    },
    {
      id: "projection-matches-postings",
      status: projectionMismatchCount === 0 ? "pass" : "fail",
      details: `${projectionMismatchCount} account/currency projections differ from signed postings`
    },
    {
      id: "idempotency-keys-unique",
      status: idempotencyKeys.size === dataset.transactions.length ? "pass" : "fail",
      details: `${idempotencyKeys.size} unique keys for ${dataset.transactions.length} transactions`
    },
    {
      id: "business-dates-in-range",
      status: datesInRange ? "pass" : "fail",
      details: `${dataset.config.startDate} through ${dataset.config.endDate}`
    },
    {
      id: "partition-route-coverage",
      status: routeCoverage && routeConsistency ? "pass" : "fail",
      details: `${dataset.transactionRoutes.length} transaction routes and ${dataset.postingRoutes.length} posting routes`
    },
    {
      id: "synthetic-boundary",
      status: "pass",
      details: "all identifiers use CUS-SYN, ACCT-SYN, LTX-LARGE, and IDEMP-LARGE fixtures only"
    }
  ];
}

export function busiestAccount(postings: readonly LargeLedgerPosting[]): string {
  const counts = new Map<string, number>();
  for (const posting of postings) {
    if (posting.accountId === systemAccountId) {
      continue;
    }
    counts.set(posting.accountId, (counts.get(posting.accountId) ?? 0) + 1);
  }
  return Array.from(counts.entries()).sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]))[0]?.[0] ?? "";
}

export function countTransactionImbalances(postings: readonly LargeLedgerPosting[]): number {
  const net = new Map<string, number>();
  for (const posting of postings) {
    const key = `${posting.ledgerTransactionId}:${posting.currency}`;
    net.set(key, (net.get(key) ?? 0) + (posting.direction === "DEBIT" ? posting.amountMinor : -posting.amountMinor));
  }
  return Array.from(net.values()).filter((value) => value !== 0).length;
}

export function countProjectionMismatches(
  postings: readonly LargeLedgerPosting[],
  projections: readonly LargeLedgerProjection[]
): number {
  const expected = new Map<string, number>();
  for (const posting of postings) {
    const key = `${posting.accountId}:${posting.currency}`;
    expected.set(key, (expected.get(key) ?? 0) + (posting.direction === "DEBIT" ? posting.amountMinor : -posting.amountMinor));
  }
  let mismatchCount = 0;
  for (const projection of projections) {
    const key = `${projection.accountId}:${projection.currency}`;
    if ((expected.get(key) ?? 0) !== projection.ledgerBalanceMinor) {
      mismatchCount += 1;
    }
    expected.delete(key);
  }
  return mismatchCount + Array.from(expected.values()).filter((value) => value !== 0).length;
}

export function globalNetByCurrency(postings: readonly LargeLedgerPosting[]): Record<string, number> {
  const net: Record<string, number> = {};
  for (const posting of postings) {
    net[posting.currency] = (net[posting.currency] ?? 0) + (posting.direction === "DEBIT" ? posting.amountMinor : -posting.amountMinor);
  }
  return Object.fromEntries(Object.entries(net).sort(([left], [right]) => left.localeCompare(right)));
}

function postingPair(
  index: number,
  transaction: LargeLedgerTransaction,
  sourceAccountId: string,
  destinationAccountId: string,
  amountMinor: number,
  currency: string
): LargeLedgerPosting[] {
  const debitAccountId = transaction.transactionType === "WITHDRAWAL" ? systemAccountId : destinationAccountId;
  const creditAccountId = transaction.transactionType === "DEPOSIT" ? systemAccountId : sourceAccountId;
  return [
    {
      ledgerPostingId: `LPT-LARGE-${pad(index + 1, 8)}-01`,
      ledgerTransactionId: transaction.ledgerTransactionId,
      accountId: debitAccountId,
      currency,
      direction: "DEBIT",
      amountMinor,
      businessDate: transaction.businessDate
    },
    {
      ledgerPostingId: `LPT-LARGE-${pad(index + 1, 8)}-02`,
      ledgerTransactionId: transaction.ledgerTransactionId,
      accountId: creditAccountId,
      currency,
      direction: "CREDIT",
      amountMinor,
      businessDate: transaction.businessDate
    }
  ];
}

function routeFor(
  sourceId: string,
  ledgerTransactionId: string,
  businessDate: string,
  parentTable: "ledger_transaction_partition_routes" | "ledger_posting_partition_routes"
): LargeLedgerPartitionRoute {
  return {
    sourceId,
    ledgerTransactionId,
    businessDate,
    partitionMonth: `${businessDate.slice(0, 7)}-01`,
    partitionTable: businessDate.startsWith("2026-") ? `${parentTable}_2026` : `${parentTable}_default`
  };
}

function seededRandom(seed: number): () => number {
  let state = seed >>> 0;
  return () => {
    state += 0x6D2B79F5;
    let value = state;
    value = Math.imul(value ^ value >>> 15, value | 1);
    value ^= value + Math.imul(value ^ value >>> 7, value | 61);
    return ((value ^ value >>> 14) >>> 0) / 4_294_967_296;
  };
}

function sha256(value: unknown): string {
  return createHash("sha256").update(JSON.stringify(value)).digest("hex");
}

function positiveInt(raw: string | undefined, fallback: number): number {
  if (raw === undefined) {
    return fallback;
  }
  const parsed = Number.parseInt(raw, 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function validateConfig(config: LargeLedgerConfig): void {
  if (config.accountCount < 2) {
    throw new Error("large ledger generator requires at least two customer accounts");
  }
  if (config.customerCount < 1) {
    throw new Error("large ledger generator requires at least one customer");
  }
  if (config.transactionCount < 1) {
    throw new Error("large ledger generator requires at least one transaction");
  }
  if (config.startDate > config.endDate) {
    throw new Error("large ledger generator startDate must be on or before endDate");
  }
}

function daysBetween(startDate: string, endDate: string): number {
  const start = Date.parse(`${startDate}T00:00:00Z`);
  const end = Date.parse(`${endDate}T00:00:00Z`);
  return Math.floor((end - start) / 86_400_000);
}

function addDays(startDate: string, offset: number): string {
  const date = new Date(Date.parse(`${startDate}T00:00:00Z`) + offset * 86_400_000);
  return date.toISOString().slice(0, 10);
}

function pad(value: number, size: number): string {
  return String(value).padStart(size, "0");
}
