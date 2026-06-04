# Hardening H4 HA/DR RTO-RPO Drill Record

Date: 2026-06-05

## Synthetic Boundary

All drills use disposable synthetic PostgreSQL, Spring, Redpanda, and Temporal lab targets. Do not run these commands against real customer databases, real bank ledgers, real PII, real payment networks, or external financial institution systems.

## Drill Matrix

| Failure mode | Evidence | RTO/RPO result |
| --- | --- | --- |
| Concurrent commands across core instances | `npm run dr:multi-instance-drill`, `docs/test-evidence/generated/ha-dr-multi-instance-drill.json` | RPO 0 for ledger rows/postings/projections; duplicate idempotency converged within `24616 ms` full drill runtime |
| API instance crash after durable commit | `docs/test-evidence/api-process-crash-drill.md` | RPO 0; committed transfer replayed after core-banking restart with one ledger transaction and one outbox row |
| PostgreSQL backup/restore | `npm run postgres:backup-drill:docker-live`, `docs/test-evidence/generated/postgres-backup-restore-drill.json` | RPO 0 for restored ledger/audit/approval/workflow/transfer evidence; restored balance and audit checks passed |
| Redpanda/outbox lag or interruption | `docs/test-evidence/outbox-worker-failure-drill.md`, `docs/test-evidence/outbox-platform-host-crash-drill.md` | durable outbox rows survive worker interruption; duplicate broker records are absorbed by inbox idempotency |
| Temporal worker/server/PostgreSQL restart | `docs/test-evidence/temporal-worker-restart-drill.md`, `docs/test-evidence/temporal-container-worker-restart-drill.md`, `docs/test-evidence/temporal-server-restart-drill.md`, `docs/test-evidence/temporal-postgres-restart-drill.md`, `docs/test-evidence/temporal-platform-host-crash-drill.md` | workflows resume to completion through worker, Temporal server, PostgreSQL, and platform host-crash-shaped restarts |

## New H4 Execution

The first multi-instance run failed because a duplicate idempotency key across independent Spring contexts exposed an uncaught PostgreSQL `SERIALIZABLE` conflict. `LedgerCommandService.withdraw(...)` now retries retryable serialization failures with a bounded retry loop.

Rerun:

```bash
npm run dr:multi-instance-drill
```

Result:

- pass;
- two independent Spring contexts with separate Hikari pools shared one Testcontainers PostgreSQL database;
- one of two 800-minor withdrawals from a 1000-minor account succeeded and one failed;
- the duplicate idempotency-key run produced one ledger transaction and one replay;
- unbalanced transaction count stayed `0`.

## Live Backup/Restore Rerun

```bash
npm run postgres:backup-drill:docker-live
```

Result:

- pass;
- `postgresLive=true`;
- restored source counts matched: 2 ledger transactions, 4 postings, 10 balance projections, 2 audit events, 1 approval, 1 workflow, 1 customer transfer result;
- restored `account_balance_projections == sum(postings)`;
- restored audit hash chain remained valid.

## Non-Claims

- This is not a production HA architecture certification.
- This does not claim real multi-region failover, real financial network recovery, or real disaster-recovery compliance.
- The new multi-instance proof uses independent Spring contexts rather than an actual gateway. Existing live API crash evidence covers a deployed Docker Compose process restart path.
