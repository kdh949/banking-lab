# Outbox Trace Log Correlation Smoke

Review date: 2026-06-03

## Scope

This smoke verifies that the target Spring Boot outbox worker emits trace/log correlation for synthetic outbox publish batches.

It covers the scheduled/manual `core-banking-outbox-worker` batch path, including the live Docker Compose worker publishing a durable `PENDING` outbox row to Redpanda and logging the same outbox event ID with a trace ID and span ID. It does not claim host crash recovery, external alert routing, non-local notification delivery, or production deployment readiness.

## Commands

```bash
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test --tests lab.banking.core.eventing.OutboxWorkerRunnerTest

scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.observability.OutboxWorkerTraceLogIntegrationTest

scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.eventing.LiveOutboxWorkerSmokeIntegrationTest

scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar

env COMPOSE_PROJECT_NAME=banking-lab-outbox-trace-drill \
  BANKING_LAB_POSTGRES_PORT=15500 \
  BANKING_LAB_REDPANDA_PORT=19100 \
  BANKING_LAB_REDPANDA_ADMIN_PORT=19700 \
  BANKING_LAB_OUTBOX_TOPIC=banking.lab.outbox-trace-drill \
  BANKING_LAB_TRACING_ENABLED=true \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false \
  docker compose --profile platform up -d --build postgres redpanda core-banking-outbox-worker

env BANKING_LAB_LIVE_OUTBOX_COMPOSE_PROJECT=banking-lab-outbox-trace-drill \
  BANKING_LAB_POSTGRES_PORT=15500 \
  BANKING_LAB_REDPANDA_PORT=19100 \
  BANKING_LAB_REDPANDA_ADMIN_PORT=19700 \
  BANKING_LAB_OUTBOX_TOPIC=banking.lab.outbox-trace-drill \
  BANKING_LAB_TRACING_ENABLED=true \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false \
  scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest \
  --tests 'lab.banking.core.eventing.LiveOutboxWorkerSmokeIntegrationTest.live outbox worker batch logs carry trace and span ids after publish'

env COMPOSE_PROJECT_NAME=banking-lab-outbox-trace-drill \
  docker compose logs --no-color --tail=220 core-banking-outbox-worker

env COMPOSE_PROJECT_NAME=banking-lab-outbox-trace-drill \
  BANKING_LAB_POSTGRES_PORT=15500 \
  BANKING_LAB_REDPANDA_PORT=19100 \
  BANKING_LAB_REDPANDA_ADMIN_PORT=19700 \
  BANKING_LAB_OUTBOX_TOPIC=banking.lab.outbox-trace-drill \
  BANKING_LAB_TRACING_ENABLED=true \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false \
  docker compose --profile platform down -v
```

## Result

Passed.

- `OutboxWorkerTraceLogIntegrationTest` passed and asserts the outbox worker batch log includes `observability.outbox.worker`, `event=batch`, topic, client ID, attempted/published/failed/dead-lettered counts, outbox event IDs, `syntheticOnly=true`, a 32-character lowercase hex `traceId`, and a 16-character lowercase hex `spanId`.
- The env-gated `LiveOutboxWorkerSmokeIntegrationTest` compile/skip run passed.
- The live Compose outbox trace drill started PostgreSQL, Redpanda, and `core-banking-outbox-worker` on isolated synthetic ports.
- The live drill inserted one synthetic durable `PENDING` outbox row, restarted the worker with tracing enabled and OTLP export disabled, waited until the row became `PUBLISHED`, consumed the corresponding Redpanda record, and verified the worker log line contained the outbox event ID plus trace/span IDs.
- The captured worker log included `traceId=c6493c63802a9c590d3c1c697db53c15`, `spanId=8840ed5ce947119a`, `event=batch`, `published=1`, and `syntheticOnly=true`.
- The temporary Compose stack and volumes were removed with `docker compose --profile platform down -v`.

## Retirement Impact

This closes outbox worker trace/log correlation evidence for the current synthetic target-stack outbox publish path. Node retirement remains blocked until non-synthetic passkey operations and final retirement review are complete.
