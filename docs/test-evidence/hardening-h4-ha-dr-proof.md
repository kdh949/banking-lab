# Hardening H4 HA/DR Proof Evidence

Date: 2026-06-05

## Scope

H4 adds lab-grade HA/DR proof for the synthetic Spring/PostgreSQL core:

- two independent Spring application contexts, each with its own Hikari pool, run ledger commands against one shared Testcontainers PostgreSQL database;
- cross-instance concurrent withdrawals prove `SERIALIZABLE` transaction behavior prevents double spend;
- cross-instance duplicate idempotency keys prove `pg_advisory_xact_lock` plus bounded retry converges to one ledger result;
- live PostgreSQL backup/restore is rerun against disposable source and restore PostgreSQL containers after H3 system-account changes;
- existing API process crash, Redpanda/outbox worker, Temporal worker/server/PostgreSQL restart, and platform host-crash-shaped drill evidence is connected into one H4 RTO/RPO record.

This remains synthetic-only lab evidence. It does not use real money, real PII, real KYC, real payment networks, real sanctions data, or external financial institution APIs.

## Changed Control Surface

- New H4 drill script: `npm run dr:multi-instance-drill`.
- New generated evidence: `docs/test-evidence/generated/ha-dr-multi-instance-drill.json`.
- New integration suite: `MultiInstanceLedgerHaDrIntegrationTest`.
- `LedgerCommandService.withdraw(...)` now retries retryable serialization/deadlock failures inside a bounded transaction retry loop before surfacing an error.
- Live backup/restore generated evidence was refreshed for V027, where synthetic system accounts increase restored `account_balance_projections` parity from 3 to 10.

## Commands Run

| Command | Result |
| --- | --- |
| `npm run dr:multi-instance-drill` | first run failed: `CannotAcquireLockException` / PostgreSQL `could not serialize access` on concurrent idempotency insert |
| `npm run dr:multi-instance-drill` | pass after bounded retry fix; generated `ha-dr-multi-instance-drill.json`, duration `24616 ms` |
| `npm run postgres:backup-drill:docker-live` | pass; live disposable PostgreSQL backup/restore with `postgresLive=true`, 2 ledger transactions, 4 postings, 10 balance projections, audit/approval/workflow/customer-transfer parity |
| `npm run test:core-banking:integration -- --tests lab.banking.core.ledger.application.LedgerCommandServiceIntegrationTest --rerun-tasks` | pass after bounded retry fix |

## RTO/RPO Summary

| Drill | Target | Measured |
| --- | --- | --- |
| Cross-instance duplicate idempotency | one durable business result, no duplicate posting, bounded retry convergence | pass; one `ledger_transactions` row for the duplicated key, replay converged inside `24616 ms` full drill runtime |
| Cross-instance double spend | no overdraft and no ledger imbalance across independent instances | pass; one withdrawal succeeded, one failed, final balance `200`, unbalanced transaction count `0` |
| Live PostgreSQL backup/restore | RPO 0 for committed synthetic ledger/audit/approval/workflow/transfer rows in backup restore | pass; source/restored counts matched and restored balance/audit invariants passed |
| API process crash after durable commit | RPO 0 for a committed transfer and replay after process restart | existing evidence: `docs/test-evidence/api-process-crash-drill.md`; exact seconds not captured, but health/replay completed within the drill's bounded waits |
| Redpanda/outbox interruption | no lost durable outbox event; duplicate publish absorbed by idempotent consumer | existing evidence: `docs/test-evidence/outbox-worker-failure-drill.md`, `docs/test-evidence/outbox-platform-host-crash-drill.md` |
| Temporal worker/server/PostgreSQL restart | workflow signal/completion survives worker/server/PostgreSQL restart | existing evidence: `docs/test-evidence/temporal-worker-restart-drill.md`, `docs/test-evidence/temporal-container-worker-restart-drill.md`, `docs/test-evidence/temporal-server-restart-drill.md`, `docs/test-evidence/temporal-postgres-restart-drill.md`, `docs/test-evidence/temporal-platform-host-crash-drill.md` |

## Failure And Fix

The first multi-instance idempotency run exposed a real H4 gap:

```text
ERROR: could not serialize access due to read/write dependencies among transactions
Hint: The transaction might succeed if retried.
```

The conflict occurred while both independent Spring contexts inserted the same `idempotency_keys` row under `SERIALIZABLE`. The fix adds a bounded retry wrapper around `LedgerCommandService.withdraw(...)` for retryable serialization/deadlock failures. The rerun passed and showed one durable ledger result for the duplicate key.

## Remaining Limitations

- This H4 slice proves multi-instance correctness with independent Spring contexts and shared PostgreSQL, not a production load balancer or Kubernetes service mesh.
- API process crash, outbox, and Temporal live restart proofs reuse existing executed evidence rather than rerunning every live Compose drill in this H4 commit.
- Exact second-level RTO for API/container restarts is not captured in the older drill docs; H4 records bounded wait success and adds exact duration for the new multi-instance drill.
