# Temporal PostgreSQL Restart Drill

Review date: 2026-06-03

## Scope

This drill verifies that a live Temporal workflow backed by Docker Compose PostgreSQL survives an actual `postgres` container crash/restart before checker approval is completed. It uses an external Compose Temporal server backed by PostgreSQL, a unique task queue, starts the Spring Boot `core-banking-temporal-worker`, starts one complaint-answer workflow, waits for `WAITING_APPROVAL`, kills the `postgres` container, starts the same PostgreSQL service on the same host port and volume, waits for `pg_isready`, waits for Temporal health to return `SERVING`, verifies the workflow still reports `WAITING_APPROVAL`, sends the checker approval signal, and verifies completion through Temporal history replay.

This proves one target-stack database process crash shape for the Temporal persistence database. It does not claim host crash recovery, multi-node PostgreSQL failover, disk-loss recovery, all workflow case types under database restart, API-backed channel propagation through a database restart, external alert routing, Loki ingestion, dashboard validation, or production-grade backup/restore.

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
env COMPOSE_PROJECT_NAME=banking-lab-temporal-postgres-restart-drill \
  BANKING_LAB_POSTGRES_PORT=15494 \
  BANKING_LAB_TEMPORAL_PORT=17245 \
  BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-postgres-restart-drill \
  BANKING_LAB_TRACING_ENABLED=false \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false \
  docker compose --profile platform up -d --build postgres temporal core-banking-temporal-worker
```

Run the live PostgreSQL restart drill:

```bash
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17245 \
  BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-postgres-restart-drill \
  BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-postgres-restart-drill \
  BANKING_LAB_POSTGRES_PORT=15494 \
  BANKING_LAB_TEMPORAL_PORT=17245 \
  BANKING_LAB_TRACING_ENABLED=false \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false \
  scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal workflow survives Compose PostgreSQL restart before approval completion'
```

Capture post-drill container and log evidence:

```bash
env COMPOSE_PROJECT_NAME=banking-lab-temporal-postgres-restart-drill docker compose ps --all
env COMPOSE_PROJECT_NAME=banking-lab-temporal-postgres-restart-drill docker compose logs --tail=260 core-banking-temporal-worker
env COMPOSE_PROJECT_NAME=banking-lab-temporal-postgres-restart-drill docker compose logs --tail=240 temporal
env COMPOSE_PROJECT_NAME=banking-lab-temporal-postgres-restart-drill docker compose logs --tail=180 postgres
env COMPOSE_PROJECT_NAME=banking-lab-temporal-postgres-restart-drill docker compose logs --no-color postgres | rg 'unexpected postmaster exit|database system was interrupted|automatic recovery|database system is ready|Skipping initialization|redo done'
env COMPOSE_PROJECT_NAME=banking-lab-temporal-postgres-restart-drill docker compose logs --no-color core-banking-temporal-worker | rg 'POSTGRES-RESTART|observability.workflow event=(signal|completed)|banking-case-workflows-postgres-restart-drill|Started CoreBanking'
```

Clean up the isolated stack:

```bash
env COMPOSE_PROJECT_NAME=banking-lab-temporal-postgres-restart-drill docker compose --profile platform down -v
```

## Result

Passed.

Complaint-answer PostgreSQL restart result:

- The worker container initially polled `banking-case-workflows-postgres-restart-drill`.
- The workflow reached `WAITING_APPROVAL`.
- The test killed `postgres` with `docker compose kill`.
- The test restarted `postgres` with `docker compose up -d --no-deps`.
- PostgreSQL reused the existing data directory, reported the database system was interrupted, ran automatic recovery, completed redo, and returned to accepting connections.
- Temporal logged transient PostgreSQL connection failures such as `database connection lost` and `no usable database connection found` while PostgreSQL was down or recovering.
- The test waited for `pg_isready`, Temporal health `SERVING`, and the workflow query to return `WAITING_APPROVAL` again before approval.
- The checker approval signal was accepted after PostgreSQL and Temporal recovered.
- The workflow completed from Temporal history with:
  - `finalStatus=COMPLETED`
  - `caseType=COMPLAINT_ANSWER`
  - `businessReferenceId=COMPLAINT-LIVE-TEMPORAL-POSTGRES-RESTART`
  - `controlEffect=CUSTOMER_ANSWER_VISIBLE`
  - `syntheticOnly=true`
- The replayed checkpoint sequence was:
  - `COMPLAINT_ANSWER:STARTED`
  - `COMPLAINT_ANSWER:WAITING_APPROVAL`
  - `COMPLAINT_ANSWER:COMPLETED`

## Operational Note

The live drill also hardened `LiveTemporalWorkerSmokeIntegrationTest` by adding `waitForPostgresService`, which shells through `docker compose exec -T postgres pg_isready -U banking_lab -d banking_lab`, and by refactoring the Compose command helper so timeout handling occurs before reading process output.

## Retirement Impact

This closes a representative live Compose PostgreSQL process restart drill for the synthetic complaint-answer Temporal workflow contract. Node retirement remains blocked until host crash shapes, broader database/process-failure variants, non-synthetic passkey operations, evidence-refresh completion, and final retirement review are complete.
