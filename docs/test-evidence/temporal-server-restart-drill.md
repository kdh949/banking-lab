# Temporal Server Restart Drill

Review date: 2026-06-03

## Scope

This drill verifies that a live Temporal workflow survives an actual Docker Compose `temporal` server container restart before checker approval is completed. It uses an external Compose Temporal server backed by PostgreSQL, a unique task queue, starts the Spring Boot `core-banking-temporal-worker`, starts one complaint-answer workflow, waits for `WAITING_APPROVAL`, kills the `temporal` container, restarts the same service on the same host port, waits for Temporal health to return `SERVING`, sends the approval signal, and verifies completion through Temporal history replay.

This proves one target-stack process crash shape for the Temporal server container while PostgreSQL remains healthy. It does not claim host crash recovery, PostgreSQL/database crash recovery, multi-node Temporal failover, alert routing, Loki ingestion, dashboard validation, or all-case server restart coverage.

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

Start an isolated Temporal server restart stack:

```bash
env COMPOSE_PROJECT_NAME=banking-lab-temporal-server-restart-drill \
  BANKING_LAB_POSTGRES_PORT=15493 \
  BANKING_LAB_TEMPORAL_PORT=17244 \
  BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-server-restart-drill \
  BANKING_LAB_TRACING_ENABLED=false \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false \
  docker compose --profile platform up -d --build postgres temporal core-banking-temporal-worker
```

Run the live Temporal server restart drill:

```bash
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17244 \
  BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-server-restart-drill \
  BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-server-restart-drill \
  BANKING_LAB_POSTGRES_PORT=15493 \
  BANKING_LAB_TEMPORAL_PORT=17244 \
  BANKING_LAB_TRACING_ENABLED=false \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false \
  scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal workflow survives Compose Temporal server restart before approval completion'
```

Capture post-drill container and log evidence:

```bash
env COMPOSE_PROJECT_NAME=banking-lab-temporal-server-restart-drill docker compose ps --all
env COMPOSE_PROJECT_NAME=banking-lab-temporal-server-restart-drill docker compose logs --tail=260 core-banking-temporal-worker
env COMPOSE_PROJECT_NAME=banking-lab-temporal-server-restart-drill docker compose logs --tail=220 temporal
```

Clean up the isolated stack:

```bash
env COMPOSE_PROJECT_NAME=banking-lab-temporal-server-restart-drill docker compose --profile platform down -v
```

## Result

Passed after fixing the drill harness to preserve the Compose host-port environment on test-managed service restarts.

Complaint-answer Temporal server restart result:

- The worker container initially polled `banking-case-workflows-server-restart-drill`.
- The workflow reached `WAITING_APPROVAL`.
- The test killed `temporal` with `docker compose kill`.
- The test restarted `temporal` with `docker compose up -d --no-deps`.
- The restarted Temporal server remained exposed on `127.0.0.1:17244` and the health check returned `SERVING`.
- Worker logs recorded transient `UNAVAILABLE` poller failures while the Temporal server was down, then resumed.
- The checker approval signal was accepted after Temporal server recovery.
- The workflow completed from Temporal history with:
  - `finalStatus=COMPLETED`
  - `caseType=COMPLAINT_ANSWER`
  - `businessReferenceId=COMPLAINT-LIVE-TEMPORAL-SERVER-RESTART`
  - `controlEffect=CUSTOMER_ANSWER_VISIBLE`
  - `syntheticOnly=true`
- The replayed checkpoint sequence was:
  - `COMPLAINT_ANSWER:STARTED`
  - `COMPLAINT_ANSWER:WAITING_APPROVAL`
  - `COMPLAINT_ANSWER:COMPLETED`

## Operational Note

The first live server restart attempt failed because the test-managed `docker compose up -d --no-deps temporal` did not preserve `BANKING_LAB_TEMPORAL_PORT`, so the restarted server exposed the default host port `7233` instead of the isolated drill port `17244`. The harness now preserves `BANKING_LAB_TEMPORAL_PORT` and `BANKING_LAB_POSTGRES_PORT` for test-managed Compose restarts. The clean rerun passed.

## Retirement Impact

This closes a representative live Compose Temporal server process restart drill for the synthetic complaint-answer workflow contract. The later PostgreSQL restart drill is recorded in `docs/test-evidence/temporal-postgres-restart-drill.md`. Node retirement remains blocked until host crash shapes, broader database/process-failure variants, non-synthetic passkey operations, evidence-refresh completion, and final retirement review are complete.
