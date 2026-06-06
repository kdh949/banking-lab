# Temporal PostgreSQL Restart Drill

Review date: 2026-06-03

## Scope

This drill verifies that live Temporal workflows backed by Docker Compose PostgreSQL survive actual `postgres` container crash/restart before checker approval is completed. It uses an external Compose Temporal server backed by PostgreSQL, a unique task queue, starts the Spring Boot `core-banking-temporal-worker`, starts each current synthetic workflow case type, waits for `WAITING_APPROVAL`, kills the `postgres` container, starts the same PostgreSQL service on the same host port and volume, waits for `pg_isready`, waits for Temporal health to return `SERVING`, verifies the workflow still reports `WAITING_APPROVAL`, sends the checker approval signal, and verifies completion through Temporal history replay.

This proves one target-stack database process crash shape for the Temporal persistence database across all current workflow case types. It does not claim host crash recovery, multi-node PostgreSQL failover, disk-loss recovery, API-backed channel propagation through a database restart, external alert routing, Loki ingestion, dashboard validation, or production-grade backup/restore. API post-commit crash coverage is recorded separately in `docs/test-evidence/api-process-crash-drill.md`, and outbox worker trace/log correlation is recorded separately in `docs/test-evidence/outbox-trace-log-correlation.md`.

## Commands

Compile and env-gated skip path:

```bash
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest'
```

Build the Spring Boot worker artifact used by Compose:

```bash
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar
```

Start an isolated PostgreSQL restart drill stack:

```bash
env COMPOSE_PROJECT_NAME=banking-lab-temporal-broader-restart-drill \
  BANKING_LAB_POSTGRES_PORT=15495 \
  BANKING_LAB_TEMPORAL_PORT=17246 \
  BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-broader-restart-drill \
  BANKING_LAB_TRACING_ENABLED=false \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false \
  docker compose --profile platform up -d --build postgres temporal core-banking-temporal-worker
```

Run the live PostgreSQL restart drill for all current case types:

```bash
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17246 \
  BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-broader-restart-drill \
  BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-broader-restart-drill \
  BANKING_LAB_POSTGRES_PORT=15495 \
  BANKING_LAB_TEMPORAL_PORT=17246 \
  BANKING_LAB_TRACING_ENABLED=false \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false \
  scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal workflow survives Compose PostgreSQL restart before approval completion' \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live FDS release workflow survives Compose PostgreSQL restart before approval completion' \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live FDS block workflow survives Compose PostgreSQL restart before approval completion' \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live AML closure workflow survives Compose PostgreSQL restart before approval completion' \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live reconciliation adjustment workflow survives Compose PostgreSQL restart before approval completion' \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live account hold workflow survives Compose PostgreSQL restart before approval completion' \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live account release workflow survives Compose PostgreSQL restart before approval completion'
```

Capture post-drill container and log evidence:

```bash
env COMPOSE_PROJECT_NAME=banking-lab-temporal-broader-restart-drill docker compose ps --all
env COMPOSE_PROJECT_NAME=banking-lab-temporal-broader-restart-drill docker compose logs --tail=260 core-banking-temporal-worker
env COMPOSE_PROJECT_NAME=banking-lab-temporal-broader-restart-drill docker compose logs --tail=240 temporal
env COMPOSE_PROJECT_NAME=banking-lab-temporal-broader-restart-drill docker compose logs --tail=180 postgres
env COMPOSE_PROJECT_NAME=banking-lab-temporal-broader-restart-drill docker compose logs --no-color postgres | rg 'unexpected postmaster exit|database system was interrupted|automatic recovery|database system is ready|Skipping initialization|redo done'
env COMPOSE_PROJECT_NAME=banking-lab-temporal-broader-restart-drill docker compose logs --no-color core-banking-temporal-worker | rg 'POSTGRES-RESTART|observability.workflow event=(signal|completed)|banking-case-workflows-broader-restart-drill|Started CoreBanking'
```

Clean up the isolated stack:

```bash
env COMPOSE_PROJECT_NAME=banking-lab-temporal-broader-restart-drill docker compose --profile platform down -v
```

## Result

Passed.

All-current-case PostgreSQL restart result:

- The worker container initially polled `banking-case-workflows-broader-restart-drill`.
- Each tested workflow reached `WAITING_APPROVAL`.
- The test killed `postgres` with `docker compose kill` and restarted it with `docker compose up -d --no-deps`.
- PostgreSQL reused the existing data directory, reported the database system was interrupted, ran automatic recovery, completed redo, and returned to accepting connections.
- Temporal logged transient PostgreSQL connection failures such as `database connection lost` and `no usable database connection found` while PostgreSQL was down or recovering.
- The test waited for `pg_isready`, Temporal health `SERVING`, and the workflow query to return `WAITING_APPROVAL` again before approval.
- Checker approval signals were accepted after PostgreSQL and Temporal recovered.
- The Gradle run passed seven live restart tests for:
  - `COMPLAINT_ANSWER` with `CUSTOMER_ANSWER_VISIBLE`
  - `FDS_RELEASE` with `LEDGER_TRANSFER_HANDOFF`
  - `FDS_BLOCK` with `NO_LEDGER_POSTING`
  - `AML_CLOSURE` with `STR_SIMULATION_CLOSURE`
  - `RECONCILIATION_ADJUSTMENT` with `BALANCED_ADJUSTMENT_HANDOFF`
  - `ACCOUNT_HOLD` with `AVAILABLE_BALANCE_HOLD`
  - `ACCOUNT_RELEASE` with `HOLD_RELEASE_HANDOFF`
- Each workflow completed from Temporal history with `finalStatus=COMPLETED` and `syntheticOnly=true`.

## Operational Note

The earlier representative live drill hardened `LiveTemporalWorkerSmokeIntegrationTest` by adding `waitForPostgresService`, which shells through `docker compose exec -T postgres pg_isready -U banking_lab -d banking_lab`, and by refactoring the Compose command helper so timeout handling occurs before reading process output. The later all-current-case run reused that harness unchanged.

## Retirement Impact

This closes live Compose PostgreSQL process restart coverage for all current synthetic Temporal workflow contracts. The platform host-crash-shaped drill is recorded in `docs/test-evidence/temporal-platform-host-crash-drill.md`. Node retirement is now ready for the current synthetic lab scope; this evidence slice remains scoped to its named control and the Node reference stays archived oracle/reference material.
