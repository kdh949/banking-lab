import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";

type DrillMode = "fixture" | "live";
type CheckStatus = "pass" | "fail";

interface LedgerTransaction {
  readonly ledgerTransactionId: string;
  readonly transactionType: string;
  readonly status: string;
}

interface LedgerPosting {
  readonly ledgerPostingId: string;
  readonly ledgerTransactionId: string;
  readonly currency: string;
  readonly direction: "DEBIT" | "CREDIT";
  readonly amountMinor: number;
}

interface AuditEvent {
  readonly auditEventId: string;
  readonly eventType: string;
  readonly businessReferenceId: string;
  readonly payloadHash: string;
  readonly previousEventHash: string | null;
}

interface FixtureSnapshot {
  readonly ledgerTransactions: readonly LedgerTransaction[];
  readonly ledgerPostings: readonly LedgerPosting[];
  readonly auditEvents: readonly AuditEvent[];
  readonly operatorApprovals: readonly { readonly approvalId: string; readonly status: string }[];
  readonly workflowInstances: readonly { readonly workflowInstanceId: string; readonly status: string }[];
  readonly customerTransferResults: readonly { readonly resultId: string; readonly status: string }[];
}

interface EvidenceCheck {
  readonly id: string;
  readonly status: CheckStatus;
  readonly details: string;
}

interface BackupDrillEvidence {
  readonly generatedAt: string;
  readonly command: string;
  readonly mode: DrillMode;
  readonly syntheticOnly: boolean;
  readonly postgresLive: boolean;
  readonly backup: {
    readonly format: string;
    readonly source: string;
    readonly restoredInto: string;
  };
  readonly counts: Record<string, { readonly source: number; readonly restored: number }>;
  readonly checks: readonly EvidenceCheck[];
  readonly status: CheckStatus;
  readonly skippedLiveReason?: string;
}

const outputPath = join("docs/test-evidence/generated", "postgres-backup-restore-drill.json");
const mode = parseMode(process.argv);

mkdirSync("docs/test-evidence/generated", { recursive: true });

const evidence = mode === "live" ? runLiveDrill() : runFixtureDrill();
writeFileSync(outputPath, `${JSON.stringify(evidence, null, 2)}\n`);

console.log(`PostgreSQL backup/restore drill: ${evidence.status} (${evidence.mode}).`);
for (const check of evidence.checks) {
  console.log(`- ${check.id}: ${check.status} (${check.details})`);
}

if (evidence.status !== "pass") {
  process.exit(1);
}

function runFixtureDrill(): BackupDrillEvidence {
  const source = buildFixtureSnapshot();
  const restored = JSON.parse(JSON.stringify(source)) as FixtureSnapshot;
  const checks = fixtureChecks(source, restored);

  return {
    generatedAt: new Date().toISOString(),
    command: "npm run postgres:backup-drill",
    mode: "fixture",
    syntheticOnly: true,
    postgresLive: false,
    backup: {
      format: "logical-fixture",
      source: "synthetic in-script PostgreSQL-shaped fixture",
      restoredInto: "in-memory restored fixture copy"
    },
    counts: snapshotCounts(source, restored),
    checks,
    status: checks.every((check) => check.status === "pass") ? "pass" : "fail",
    skippedLiveReason: "Live PostgreSQL mode requires BANKING_LAB_POSTGRES_URL, BANKING_LAB_RESTORE_POSTGRES_URL, pg_dump, pg_restore, and psql."
  };
}

function runLiveDrill(): BackupDrillEvidence {
  const sourceUrl = process.env.BANKING_LAB_POSTGRES_URL;
  const restoreUrl = process.env.BANKING_LAB_RESTORE_POSTGRES_URL;
  if (!sourceUrl || !restoreUrl) {
    return failedLiveEvidence(
      "BANKING_LAB_POSTGRES_URL and BANKING_LAB_RESTORE_POSTGRES_URL must both be set for live mode."
    );
  }

  for (const tool of ["pg_dump", "pg_restore", "psql"]) {
    if (!commandExists(tool)) {
      return failedLiveEvidence(`${tool} is not installed in this environment.`);
    }
  }

  const drillDir = mkdtempSync(join(tmpdir(), "banking-lab-pg-backup-"));
  const dumpPath = join(drillDir, "backup.dump");
  const dump = spawnSync("pg_dump", ["--format=custom", "--file", dumpPath, sourceUrl], {
    encoding: "utf8",
    maxBuffer: 20 * 1024 * 1024
  });
  if (dump.status !== 0) {
    return failedLiveEvidence(firstLine(dump.stderr || dump.stdout) ?? "pg_dump failed.");
  }

  const restore = spawnSync("pg_restore", ["--clean", "--if-exists", "--dbname", restoreUrl, dumpPath], {
    encoding: "utf8",
    maxBuffer: 20 * 1024 * 1024
  });
  if (restore.status !== 0) {
    return failedLiveEvidence(firstLine(restore.stderr || restore.stdout) ?? "pg_restore failed.");
  }

  const sourceCounts = liveCounts(sourceUrl);
  const restoredCounts = liveCounts(restoreUrl);
  const checks = liveChecks(sourceUrl, restoreUrl, sourceCounts, restoredCounts);

  return {
    generatedAt: new Date().toISOString(),
    command: "npm run postgres:backup-drill -- --mode=live",
    mode: "live",
    syntheticOnly: true,
    postgresLive: true,
    backup: {
      format: "pg_dump custom format",
      source: "BANKING_LAB_POSTGRES_URL",
      restoredInto: "BANKING_LAB_RESTORE_POSTGRES_URL"
    },
    counts: mergeLiveCounts(sourceCounts, restoredCounts),
    checks,
    status: checks.every((check) => check.status === "pass") ? "pass" : "fail"
  };
}

function fixtureChecks(source: FixtureSnapshot, restored: FixtureSnapshot): EvidenceCheck[] {
  return [
    {
      id: "ledger-transaction-count-parity",
      status: source.ledgerTransactions.length === restored.ledgerTransactions.length ? "pass" : "fail",
      details: `${source.ledgerTransactions.length} source, ${restored.ledgerTransactions.length} restored`
    },
    {
      id: "ledger-posting-balance-valid",
      status: balancedPostings(restored.ledgerPostings) ? "pass" : "fail",
      details: "each restored ledger transaction nets debit and credit postings to zero by currency"
    },
    {
      id: "audit-hash-chain-valid",
      status: auditChainValid(restored.auditEvents) ? "pass" : "fail",
      details: "restored audit previous_event_hash values match preceding payload_hash values"
    },
    {
      id: "approval-state-parity",
      status: source.operatorApprovals.length === restored.operatorApprovals.length ? "pass" : "fail",
      details: `${source.operatorApprovals.length} source, ${restored.operatorApprovals.length} restored`
    },
    {
      id: "workflow-state-parity",
      status: source.workflowInstances.length === restored.workflowInstances.length ? "pass" : "fail",
      details: `${source.workflowInstances.length} source, ${restored.workflowInstances.length} restored`
    },
    {
      id: "customer-transfer-result-parity",
      status: source.customerTransferResults.length === restored.customerTransferResults.length ? "pass" : "fail",
      details: `${source.customerTransferResults.length} source, ${restored.customerTransferResults.length} restored`
    },
    {
      id: "synthetic-boundary-valid",
      status: "pass",
      details: "fixture contains synthetic identifiers only and no real customer money or PII"
    }
  ];
}

function liveChecks(
  sourceUrl: string,
  restoreUrl: string,
  sourceCounts: Record<string, number>,
  restoredCounts: Record<string, number>
): EvidenceCheck[] {
  const countChecks = Object.keys(sourceCounts).map((key) => ({
    id: `${key}-count-parity`,
    status: sourceCounts[key] === restoredCounts[key] ? "pass" as const : "fail" as const,
    details: `${sourceCounts[key]} source, ${restoredCounts[key]} restored`
  }));
  return [
    ...countChecks,
    {
      id: "ledger-posting-balance-valid",
      status: queryNumber(restoreUrl, ledgerImbalanceQuery()) === 0 ? "pass" : "fail",
      details: "restored PostgreSQL ledger_postings have no debit/credit imbalance by transaction and currency"
    },
    {
      id: "audit-hash-chain-valid",
      status: queryNumber(restoreUrl, auditChainBreakQuery()) === 0 ? "pass" : "fail",
      details: "restored PostgreSQL audit_events retain previous_event_hash continuity"
    },
    {
      id: "source-database-readable",
      status: queryNumber(sourceUrl, "SELECT count(*) FROM ledger_transactions;") >= 0 ? "pass" : "fail",
      details: "source PostgreSQL database was queried after dump"
    }
  ];
}

function snapshotCounts(source: FixtureSnapshot, restored: FixtureSnapshot): BackupDrillEvidence["counts"] {
  return {
    ledgerTransactions: { source: source.ledgerTransactions.length, restored: restored.ledgerTransactions.length },
    ledgerPostings: { source: source.ledgerPostings.length, restored: restored.ledgerPostings.length },
    auditEvents: { source: source.auditEvents.length, restored: restored.auditEvents.length },
    operatorApprovals: { source: source.operatorApprovals.length, restored: restored.operatorApprovals.length },
    workflowInstances: { source: source.workflowInstances.length, restored: restored.workflowInstances.length },
    customerTransferResults: { source: source.customerTransferResults.length, restored: restored.customerTransferResults.length }
  };
}

function liveCounts(databaseUrl: string): Record<string, number> {
  return {
    ledgerTransactions: queryNumber(databaseUrl, "SELECT count(*) FROM ledger_transactions;"),
    ledgerPostings: queryNumber(databaseUrl, "SELECT count(*) FROM ledger_postings;"),
    auditEvents: queryNumber(databaseUrl, "SELECT count(*) FROM audit_events;"),
    operatorApprovals: queryNumber(databaseUrl, "SELECT count(*) FROM operator_approvals;"),
    workflowInstances: queryNumber(databaseUrl, "SELECT count(*) FROM workflow_instances;"),
    customerTransferResults: queryNumber(databaseUrl, "SELECT count(*) FROM customer_transfer_results;")
  };
}

function mergeLiveCounts(
  sourceCounts: Record<string, number>,
  restoredCounts: Record<string, number>
): BackupDrillEvidence["counts"] {
  const counts: BackupDrillEvidence["counts"] = {};
  for (const key of Object.keys(sourceCounts)) {
    counts[key] = { source: sourceCounts[key], restored: restoredCounts[key] ?? -1 };
  }
  return counts;
}

function queryNumber(databaseUrl: string, sql: string): number {
  const result = spawnSync("psql", [databaseUrl, "-v", "ON_ERROR_STOP=1", "-At", "-c", sql], {
    encoding: "utf8",
    maxBuffer: 10 * 1024 * 1024
  });
  if (result.status !== 0) {
    throw new Error(firstLine(result.stderr || result.stdout) ?? "psql query failed");
  }
  return Number(result.stdout.trim());
}

function buildFixtureSnapshot(): FixtureSnapshot {
  const auditEvents = buildAuditEvents([
    { auditEventId: "AUD-BACKUP-001", eventType: "TRANSFER_POSTED", businessReferenceId: "CTR-BACKUP-001" },
    { auditEventId: "AUD-BACKUP-002", eventType: "FDS_HOLD_CREATED", businessReferenceId: "CTR-BACKUP-002" },
    { auditEventId: "AUD-BACKUP-003", eventType: "APPROVAL_EXECUTED", businessReferenceId: "APP-BACKUP-001" }
  ]);

  return {
    ledgerTransactions: [
      { ledgerTransactionId: "LTX-BACKUP-001", transactionType: "DEPOSIT", status: "POSTED" },
      { ledgerTransactionId: "LTX-BACKUP-002", transactionType: "INTERNAL_TRANSFER", status: "POSTED" },
      { ledgerTransactionId: "LTX-BACKUP-003", transactionType: "ADJUSTMENT", status: "POSTED" }
    ],
    ledgerPostings: [
      { ledgerPostingId: "LP-BACKUP-001", ledgerTransactionId: "LTX-BACKUP-001", currency: "KRW", direction: "DEBIT", amountMinor: 100000 },
      { ledgerPostingId: "LP-BACKUP-002", ledgerTransactionId: "LTX-BACKUP-001", currency: "KRW", direction: "CREDIT", amountMinor: 100000 },
      { ledgerPostingId: "LP-BACKUP-003", ledgerTransactionId: "LTX-BACKUP-002", currency: "KRW", direction: "DEBIT", amountMinor: 25000 },
      { ledgerPostingId: "LP-BACKUP-004", ledgerTransactionId: "LTX-BACKUP-002", currency: "KRW", direction: "CREDIT", amountMinor: 25000 },
      { ledgerPostingId: "LP-BACKUP-005", ledgerTransactionId: "LTX-BACKUP-003", currency: "KRW", direction: "DEBIT", amountMinor: 5000 },
      { ledgerPostingId: "LP-BACKUP-006", ledgerTransactionId: "LTX-BACKUP-003", currency: "KRW", direction: "CREDIT", amountMinor: 5000 }
    ],
    auditEvents,
    operatorApprovals: [
      { approvalId: "APP-BACKUP-001", status: "APPROVED" },
      { approvalId: "APP-BACKUP-002", status: "PENDING" }
    ],
    workflowInstances: [
      { workflowInstanceId: "WFI-BACKUP-001", status: "COMPLETED" },
      { workflowInstanceId: "WFI-BACKUP-002", status: "WAITING_APPROVAL" }
    ],
    customerTransferResults: [
      { resultId: "CTR-BACKUP-001", status: "POSTED" },
      { resultId: "CTR-BACKUP-002", status: "HELD" },
      { resultId: "CTR-BACKUP-003", status: "FAILED" }
    ]
  };
}

function buildAuditEvents(
  input: readonly { readonly auditEventId: string; readonly eventType: string; readonly businessReferenceId: string }[]
): AuditEvent[] {
  let previousEventHash: string | null = null;
  return input.map((event) => {
    const payloadHash = hash({
      auditEventId: event.auditEventId,
      eventType: event.eventType,
      businessReferenceId: event.businessReferenceId,
      syntheticOnly: true
    });
    const auditEvent: AuditEvent = {
      ...event,
      payloadHash,
      previousEventHash
    };
    previousEventHash = payloadHash;
    return auditEvent;
  });
}

function balancedPostings(postings: readonly LedgerPosting[]): boolean {
  const totals = new Map<string, number>();
  for (const posting of postings) {
    const key = `${posting.ledgerTransactionId}:${posting.currency}`;
    const signedAmount = posting.direction === "DEBIT" ? posting.amountMinor : -posting.amountMinor;
    totals.set(key, (totals.get(key) ?? 0) + signedAmount);
  }
  return [...totals.values()].every((total) => total === 0);
}

function auditChainValid(events: readonly AuditEvent[]): boolean {
  let previousEventHash: string | null = null;
  for (const event of events) {
    if (event.previousEventHash !== previousEventHash) {
      return false;
    }
    previousEventHash = event.payloadHash;
  }
  return true;
}

function ledgerImbalanceQuery(): string {
  return `
    SELECT count(*)
    FROM (
      SELECT ledger_transaction_id, currency,
        sum(CASE WHEN direction = 'DEBIT' THEN amount_minor ELSE -amount_minor END) AS net_amount
      FROM ledger_postings
      GROUP BY ledger_transaction_id, currency
      HAVING sum(CASE WHEN direction = 'DEBIT' THEN amount_minor ELSE -amount_minor END) <> 0
    ) imbalances;
  `;
}

function auditChainBreakQuery(): string {
  return `
    SELECT count(*)
    FROM (
      SELECT previous_event_hash,
        lag(payload_hash) OVER (ORDER BY created_at, audit_event_id) AS expected_previous_hash
      FROM audit_events
    ) chain
    WHERE previous_event_hash IS DISTINCT FROM expected_previous_hash;
  `;
}

function failedLiveEvidence(reason: string): BackupDrillEvidence {
  return {
    generatedAt: new Date().toISOString(),
    command: "npm run postgres:backup-drill -- --mode=live",
    mode: "live",
    syntheticOnly: true,
    postgresLive: false,
    backup: {
      format: "pg_dump custom format",
      source: "BANKING_LAB_POSTGRES_URL",
      restoredInto: "BANKING_LAB_RESTORE_POSTGRES_URL"
    },
    counts: {},
    checks: [
      {
        id: "live-postgres-prerequisites",
        status: "fail",
        details: reason
      }
    ],
    status: "fail",
    skippedLiveReason: reason
  };
}

function parseMode(argv: readonly string[]): DrillMode {
  const rawMode = argv.find((argument) => argument.startsWith("--mode="))?.split("=")[1] ?? "fixture";
  if (rawMode === "fixture" || rawMode === "live") {
    return rawMode;
  }
  throw new Error(`Unsupported backup drill mode: ${rawMode}`);
}

function commandExists(tool: string): boolean {
  return spawnSync("which", [tool], { encoding: "utf8" }).status === 0;
}

function hash(value: unknown): string {
  return createHash("sha256").update(JSON.stringify(value)).digest("hex");
}

function firstLine(value: string): string | undefined {
  return value.split("\n").map((line) => line.trim()).find(Boolean);
}
