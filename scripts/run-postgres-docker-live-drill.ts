import { spawnSync } from "node:child_process";
import { readdirSync } from "node:fs";
import { createServer } from "node:net";
import { join } from "node:path";

const composeFile = join("infra", "docker-compose", "postgres-backup-drill.yml");
const projectName = `banking-lab-pg-drill-${process.pid}`;
const sourcePort = await reservePort();
const restorePort = await reservePort();
const sourceUrl = `postgres://banking_lab:banking_lab@127.0.0.1:${sourcePort}/banking_lab`;
const restoreUrl = `postgres://banking_lab:banking_lab@127.0.0.1:${restorePort}/banking_lab`;
const composeEnv = {
  ...process.env,
  BANKING_LAB_BACKUP_DRILL_SOURCE_PORT: String(sourcePort),
  BANKING_LAB_BACKUP_DRILL_RESTORE_PORT: String(restorePort)
};
const composeArgs = ["compose", "-f", composeFile, "-p", projectName];

try {
  run("docker", [...composeArgs, "up", "-d"], { env: composeEnv });
  waitForPostgres(sourceUrl, "source");
  waitForPostgres(restoreUrl, "restore");
  applyMigrations(sourceUrl);
  seedSyntheticBackupFixture(sourceUrl);

  run(process.execPath, ["--experimental-strip-types", "scripts/run-postgres-backup-drill.ts", "--mode=live"], {
    env: {
      ...process.env,
      BANKING_LAB_POSTGRES_URL: sourceUrl,
      BANKING_LAB_RESTORE_POSTGRES_URL: restoreUrl
    },
    stdio: "inherit"
  });
} finally {
  run("docker", [...composeArgs, "down", "-v", "--remove-orphans"], {
    env: composeEnv,
    allowFailure: true
  });
}

function applyMigrations(databaseUrl: string): void {
  const migrationFiles = readdirSync("db/migrations")
    .filter((file) => /^V\d+__.*\.sql$/.test(file))
    .sort((left, right) => migrationVersion(left) - migrationVersion(right));

  for (const file of migrationFiles) {
    run("psql", [databaseUrl, "-v", "ON_ERROR_STOP=1", "-f", join("db", "migrations", file)]);
  }
}

function seedSyntheticBackupFixture(databaseUrl: string): void {
  run("psql", [databaseUrl, "-v", "ON_ERROR_STOP=1", "-c", syntheticSeedSql()]);
}

function syntheticSeedSql(): string {
  return `
    INSERT INTO customers (customer_id, customer_name, customer_grade, risk_grade)
    VALUES
      ('CUS-PGBR-A', 'Synthetic Backup Alpha', 'STANDARD', 'LOW'),
      ('CUS-PGBR-B', 'Synthetic Backup Beta', 'STANDARD', 'LOW'),
      ('CUS-PGBR-BANK', 'Synthetic Bank Suspense', 'SYSTEM', 'LOW');

    INSERT INTO accounts (account_id, customer_id, account_no, currency, status)
    VALUES
      ('ACC-PGBR-FROM', 'CUS-PGBR-A', 'LAB-PGBR-000001', 'KRW', 'ACTIVE'),
      ('ACC-PGBR-TO', 'CUS-PGBR-B', 'LAB-PGBR-000002', 'KRW', 'ACTIVE'),
      ('BANK-PGBR-SUSPENSE', 'CUS-PGBR-BANK', 'LAB-PGBR-SUSPENSE', 'KRW', 'ACTIVE');

    INSERT INTO account_limits (
      account_id, daily_transfer_limit_minor, single_transfer_limit_minor, monthly_transfer_limit_minor
    )
    VALUES
      ('ACC-PGBR-FROM', 1000000, 500000, 31000000),
      ('ACC-PGBR-TO', 1000000, 500000, 31000000),
      ('BANK-PGBR-SUSPENSE', 1000000, 500000, 31000000);

    INSERT INTO ledger_transactions (
      ledger_transaction_id, transaction_type, business_reference_id, idempotency_key,
      business_date, status, requested_by, requested_channel, posted_at, reason
    )
    VALUES
      (
        'TX-PGBR-OPEN-001', 'DEPOSIT', 'PGBR-OPEN-001', 'IDEMP-PGBR-OPEN-001',
        CURRENT_DATE, 'POSTED', 'synthetic-ops', 'POSTGRES_BACKUP_DRILL', now(),
        'Synthetic opening movement for live backup drill'
      ),
      (
        'TX-PGBR-TRANSFER-001', 'INTERNAL_TRANSFER', 'CTR-PGBR-001', 'IDEMP-PGBR-TRANSFER-001',
        CURRENT_DATE, 'POSTED', 'synthetic-customer', 'POSTGRES_BACKUP_DRILL', now(),
        'Synthetic posted transfer for live backup drill'
      );

    INSERT INTO ledger_postings (
      ledger_posting_id, ledger_transaction_id, account_id, currency, direction, amount_minor, posting_type
    )
    VALUES
      ('LP-PGBR-OPEN-D', 'TX-PGBR-OPEN-001', 'BANK-PGBR-SUSPENSE', 'KRW', 'DEBIT', 200000, 'OPENING'),
      ('LP-PGBR-OPEN-C', 'TX-PGBR-OPEN-001', 'ACC-PGBR-FROM', 'KRW', 'CREDIT', 200000, 'OPENING'),
      ('LP-PGBR-TRF-D', 'TX-PGBR-TRANSFER-001', 'ACC-PGBR-FROM', 'KRW', 'DEBIT', 100000, 'PRINCIPAL'),
      ('LP-PGBR-TRF-C', 'TX-PGBR-TRANSFER-001', 'ACC-PGBR-TO', 'KRW', 'CREDIT', 100000, 'PRINCIPAL');

    INSERT INTO account_balance_projections (
      account_id, currency, ledger_balance_minor, available_balance_minor, hold_amount_minor, last_posting_id
    )
    VALUES
      ('BANK-PGBR-SUSPENSE', 'KRW', -200000, -200000, 0, 'LP-PGBR-OPEN-D'),
      ('ACC-PGBR-FROM', 'KRW', 100000, 100000, 0, 'LP-PGBR-TRF-D'),
      ('ACC-PGBR-TO', 'KRW', 100000, 100000, 0, 'LP-PGBR-TRF-C');

    INSERT INTO audit_events (
      audit_event_id, event_type, actor_type, actor_id, actor_role,
      screen_id, business_reference_id, customer_id, account_id, reason,
      payload_hash, previous_event_hash, payload_json
    )
    VALUES
      (
        'AUD-PGBR-001', 'POSTGRES_BACKUP_DRILL_SEED', 'SYSTEM', 'synthetic-backup-drill', 'SYSTEM',
        'OPS-101', 'PGBR-OPEN-001', 'CUS-PGBR-A', 'ACC-PGBR-FROM',
        'Synthetic live PostgreSQL backup/restore drill seed',
        encode(sha256(convert_to('{"syntheticOnly":true,"event":"seed"}', 'UTF8')), 'hex'),
        NULL,
        '{"syntheticOnly":true,"event":"seed"}'::jsonb
      ),
      (
        'AUD-PGBR-002', 'POSTGRES_BACKUP_DRILL_VERIFY', 'SYSTEM', 'synthetic-backup-drill', 'SYSTEM',
        'OPS-101', 'CTR-PGBR-001', 'CUS-PGBR-B', 'ACC-PGBR-TO',
        'Synthetic live PostgreSQL backup/restore drill verification row',
        encode(sha256(convert_to('{"syntheticOnly":true,"event":"verify"}', 'UTF8')), 'hex'),
        encode(sha256(convert_to('{"syntheticOnly":true,"event":"seed"}', 'UTF8')), 'hex'),
        '{"syntheticOnly":true,"event":"verify"}'::jsonb
      );

    INSERT INTO operator_approvals (
      approval_id, business_type, business_reference_id, requested_by, request_reason,
      before_snapshot_json, after_snapshot_json, status, approved_by, approved_at, audit_event_id
    )
    VALUES (
      'APR-PGBR-001', 'POSTGRES_BACKUP_DRILL', 'CTR-PGBR-001', 'maker-pgbr',
      'Synthetic approval parity row for live backup drill',
      '{"syntheticOnly":true,"before":"requested"}'::jsonb,
      '{"syntheticOnly":true,"after":"approved"}'::jsonb,
      'APPROVED', 'checker-pgbr', now(), 'AUD-PGBR-002'
    );

    INSERT INTO workflow_instances (
      workflow_instance_id, workflow_type, business_reference_id, temporal_workflow_id,
      temporal_run_id, status, started_by
    )
    VALUES (
      'WFI-PGBR-001', 'POSTGRES_BACKUP_DRILL', 'CTR-PGBR-001',
      'synthetic-pgbr-workflow', 'synthetic-pgbr-run', 'COMPLETED', 'synthetic-ops'
    );

    INSERT INTO customer_transfer_results (
      result_id, idempotency_key, command_hash, customer_id, from_account_id, to_account_id,
      amount_minor, currency, status, ledger_transaction_id, message, requested_by,
      requested_channel, business_reference_id, business_date
    )
    VALUES (
      'CTR-PGBR-001', 'IDEMP-PGBR-TRANSFER-001', 'hash-pgbr-transfer',
      'CUS-PGBR-A', 'ACC-PGBR-FROM', 'ACC-PGBR-TO', 100000, 'KRW',
      'POSTED', 'TX-PGBR-TRANSFER-001', 'Synthetic posted transfer restored',
      'synthetic-customer', 'POSTGRES_BACKUP_DRILL', 'CTR-PGBR-001', CURRENT_DATE
    );
  `;
}

function waitForPostgres(databaseUrl: string, label: string): void {
  for (let attempt = 1; attempt <= 60; attempt += 1) {
    const result = spawnSync("psql", [databaseUrl, "-v", "ON_ERROR_STOP=1", "-At", "-c", "SELECT 1;"], {
      encoding: "utf8"
    });
    if (result.status === 0) {
      return;
    }
    Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 1000);
  }
  throw new Error(`Timed out waiting for ${label} PostgreSQL to accept connections.`);
}

function run(
  command: string,
  args: string[],
  options: {
    env?: NodeJS.ProcessEnv;
    stdio?: "inherit" | "pipe";
    allowFailure?: boolean;
  } = {}
): void {
  const result = spawnSync(command, args, {
    encoding: "utf8",
    env: options.env ?? process.env,
    stdio: options.stdio ?? "pipe",
    maxBuffer: 100 * 1024 * 1024
  });
  if ((result.status ?? 1) !== 0 && !options.allowFailure) {
    const output = [result.stderr, result.stdout].filter(Boolean).join("\n").trim();
    throw new Error(`${command} ${args.join(" ")} failed: ${firstLine(output) ?? result.error?.message ?? "unknown error"}`);
  }
}

function migrationVersion(file: string): number {
  return Number(file.match(/^V(\d+)__/)?.[1] ?? "0");
}

function firstLine(value: string): string | undefined {
  return value.split("\n").map((line) => line.trim()).find(Boolean);
}

function reservePort(): Promise<number> {
  return new Promise((resolve, reject) => {
    const server = createServer();
    server.unref();
    server.on("error", reject);
    server.listen(0, "127.0.0.1", () => {
      const address = server.address();
      if (typeof address === "object" && address) {
        const port = address.port;
        server.close(() => resolve(port));
      } else {
        server.close(() => reject(new Error("Failed to reserve a local TCP port.")));
      }
    });
  });
}
