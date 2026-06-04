# PostgreSQL Backup/Restore Drill Evidence

Date: 2026-06-04

## Commands

Default synthetic fixture drill:

```bash
npm run postgres:backup-drill
```

Live PostgreSQL drill, when a disposable source and restore database are available:

```bash
BANKING_LAB_POSTGRES_URL=postgres://... \
BANKING_LAB_RESTORE_POSTGRES_URL=postgres://... \
npm run postgres:backup-drill -- --mode=live
```

## Result

The committed generated artifact is:

- `docs/test-evidence/generated/postgres-backup-restore-drill.json`

The default run validates the backup/restore evidence contract with a synthetic PostgreSQL-shaped fixture. It checks:

- ledger transaction count parity after restore;
- balanced restored ledger postings by transaction and currency;
- audit hash-chain continuity;
- maker-checker approval state parity;
- workflow state parity;
- customer transfer result parity;
- synthetic-only data boundary.

## Live PostgreSQL Boundary

The default fixture mode is not a live PostgreSQL durability proof. Live mode requires:

- `BANKING_LAB_POSTGRES_URL`;
- `BANKING_LAB_RESTORE_POSTGRES_URL`;
- local `pg_dump`;
- local `pg_restore`;
- local `psql`;
- a disposable restore database or schema that may be overwritten.

Live mode writes the same generated JSON evidence after running `pg_dump --format=custom`, restoring with `pg_restore --clean --if-exists`, and querying restored table counts, ledger posting balance, and audit hash-chain continuity.

## 2026-06-04 Live Attempt

The required live command was attempted locally:

```bash
npm run postgres:backup-drill -- --mode=live
```

Result:

- failed with `live-postgres-prerequisites`;
- exact blocker: `BANKING_LAB_POSTGRES_URL and BANKING_LAB_RESTORE_POSTGRES_URL must both be set for live mode.`;
- local `pg_dump`, `pg_restore`, and `psql` are installed;
- `postgresLive=true` is not claimed in this environment.

## Synthetic Boundary

This drill uses synthetic identifiers and synthetic balances only. It must not be run against real customer databases, real customer PII, real bank ledgers, or real financial institution infrastructure.
