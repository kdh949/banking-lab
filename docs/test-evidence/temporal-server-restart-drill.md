# Temporal Server Restart Drill

Review date: 2026-06-03

## Scope

This drill verifies that live Temporal workflows survive actual Docker Compose `temporal` server container restarts before checker approval is completed. It uses an external Compose Temporal server backed by PostgreSQL, a unique task queue, starts the Spring Boot `core-banking-temporal-worker`, starts each current synthetic workflow case type, waits for `WAITING_APPROVAL`, kills the `temporal` container, restarts the same service on the same host port, waits for Temporal health to return `SERVING`, sends the approval signal, and verifies completion through Temporal history replay.

This proves one target-stack process crash shape for the Temporal server container across all current workflow case types while PostgreSQL remains healthy. It does not claim host crash recovery, PostgreSQL/database crash recovery, multi-node Temporal failover, external alert routing, Loki ingestion, or dashboard validation. API post-commit crash coverage is recorded separately in `docs/test-evidence/api-process-crash-drill.md`, and outbox worker trace/log correlation is recorded separately in `docs/test-evidence/outbox-trace-log-correlation.md`.

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
env COMPOSE_PROJECT_NAME=banking-lab-temporal-broader-restart-drill \
  BANKING_LAB_POSTGRES_PORT=15495 \
  BANKING_LAB_TEMPORAL_PORT=17246 \
  BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-broader-restart-drill \
  BANKING_LAB_TRACING_ENABLED=false \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false \
  docker compose --profile platform up -d --build postgres temporal core-banking-temporal-worker
```

Run the live Temporal server restart drill for all current case types:

```bash
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17246 \
  BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-broader-restart-drill \
  BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-broader-restart-drill \
  BANKING_LAB_POSTGRES_PORT=15495 \
  BANKING_LAB_TEMPORAL_PORT=17246 \
  BANKING_LAB_TRACING_ENABLED=false \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false \
  scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal workflow survives Compose Temporal server restart before approval completion' \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live FDS release workflow survives Compose Temporal server restart before approval completion' \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live FDS block workflow survives Compose Temporal server restart before approval completion' \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live AML closure workflow survives Compose Temporal server restart before approval completion' \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live reconciliation adjustment workflow survives Compose Temporal server restart before approval completion' \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live account hold workflow survives Compose Temporal server restart before approval completion' \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live account release workflow survives Compose Temporal server restart before approval completion'
```

Capture post-drill container and log evidence:

```bash
env COMPOSE_PROJECT_NAME=banking-lab-temporal-broader-restart-drill docker compose ps --all
env COMPOSE_PROJECT_NAME=banking-lab-temporal-broader-restart-drill docker compose logs --tail=260 core-banking-temporal-worker
env COMPOSE_PROJECT_NAME=banking-lab-temporal-broader-restart-drill docker compose logs --tail=220 temporal
```

Clean up the isolated stack:

```bash
env COMPOSE_PROJECT_NAME=banking-lab-temporal-broader-restart-drill docker compose --profile platform down -v
```

## Result

Passed.

All-current-case Temporal server restart result:

- The worker container initially polled `banking-case-workflows-broader-restart-drill`.
- Each tested workflow reached `WAITING_APPROVAL` before the Temporal server restart.
- The test killed `temporal` with `docker compose kill` and restarted it with `docker compose up -d --no-deps`.
- The restarted Temporal server remained exposed on `127.0.0.1:17246` and the health check returned `SERVING` before approval.
- Worker logs recorded transient `UNAVAILABLE` poller failures while the Temporal server was down, then resumed.
- Checker approval signals were accepted after Temporal server recovery.
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

An earlier representative drill attempt failed because the test-managed `docker compose up -d --no-deps temporal` did not preserve `BANKING_LAB_TEMPORAL_PORT`, so the restarted server exposed the default host port `7233` instead of the isolated drill port. The harness now preserves `BANKING_LAB_TEMPORAL_PORT` and `BANKING_LAB_POSTGRES_PORT` for test-managed Compose restarts. The later all-current-case run passed.

## Retirement Impact

This closes live Compose Temporal server process restart coverage for all current synthetic Temporal workflow contracts. The PostgreSQL restart drill is recorded in `docs/test-evidence/temporal-postgres-restart-drill.md`, and the platform host-crash-shaped drill is recorded in `docs/test-evidence/temporal-platform-host-crash-drill.md`. Node retirement is now ready for the current synthetic lab scope; this evidence slice remains scoped to its named control and the Node reference stays archived oracle/reference material.
