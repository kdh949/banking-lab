# API Process Crash Drill

Review date: 2026-06-03

## Scope

This drill verifies the live Docker Compose Spring API path when the `core-banking` process dies after a customer transfer has durably committed ledger, projection, customer-transfer result, and outbox state, but before the HTTP response is returned to the caller.

The drill uses synthetic accounts and a synthetic customer only. It does not use the Node reference runtime and does not claim host crash recovery, multi-node failover, or production deployment readiness. Outbox worker trace/log correlation is recorded separately in `docs/test-evidence/outbox-trace-log-correlation.md`.

## Failure Injected

1. `CustomerTransferController` receives a customer transfer request with a configured synthetic idempotency key.
2. `CustomerTransferService.transfer(...)` commits the PostgreSQL transaction.
3. The controller halts the JVM before writing a successful HTTP response.
4. The test verifies the committed database state while the first API process has exited.
5. The `core-banking` service is restarted without the fault.
6. The same request is retried with the same idempotency key.

## Commands

```bash
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test --tests lab.banking.core.customer.CustomerTransferFaultPropertiesTest
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.customer.LiveCustomerTransferApiCrashIntegrationTest
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar
env COMPOSE_PROJECT_NAME=banking-lab-api-crash-drill BANKING_LAB_POSTGRES_PORT=15499 BANKING_LAB_CORE_BANKING_PORT=18139 BANKING_LAB_SYNTHETIC_SEED_ENABLED=true BANKING_LAB_SECURITY_ENABLED=false BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform up -d --build postgres core-banking
env BANKING_LAB_LIVE_API_CRASH_COMPOSE_PROJECT=banking-lab-api-crash-drill BANKING_LAB_POSTGRES_PORT=15499 BANKING_LAB_CORE_BANKING_PORT=18139 BANKING_LAB_SYNTHETIC_SEED_ENABLED=true BANKING_LAB_SECURITY_ENABLED=false BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.customer.LiveCustomerTransferApiCrashIntegrationTest
env COMPOSE_PROJECT_NAME=banking-lab-api-crash-drill BANKING_LAB_POSTGRES_PORT=15499 BANKING_LAB_CORE_BANKING_PORT=18139 BANKING_LAB_SYNTHETIC_SEED_ENABLED=true BANKING_LAB_SECURITY_ENABLED=false BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform down -v
```

## Result

Passed.

- The first request did not complete successfully because the service halted after the durable commit.
- The `core-banking` container exited with code `89`.
- PostgreSQL contained exactly one `ledger_transactions` row, one `customer_transfer_results` row, and one `outbox_events` row for the idempotency key.
- The customer transfer result was `POSTED` and referenced the same ledger transaction.
- The outbox event stayed durable as `PENDING` after the API process crash.
- Source and destination balances moved exactly once.
- Restarting `core-banking` without the fault and retrying the same request returned HTTP 200 with `replayed=true`, the same transaction ID, unchanged row counts, and unchanged balances after replay.

## Retirement Impact

This closes the API process crash after durable ledger/outbox commit blocker for the current synthetic customer-transfer path. Node retirement remains blocked until non-synthetic passkey operations and final retirement review are complete.
