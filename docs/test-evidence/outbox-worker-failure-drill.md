# Outbox Worker Failure Drill

Review date: 2026-06-03

## Scope

This drill verifies durable outbox recovery paths for worker interruption around publish.

The first drill uses PostgreSQL and Redpanda through Testcontainers. The live worker drill uses Docker Compose PostgreSQL, Redpanda, and `core-banking-outbox-worker`. Neither path depends on the Node reference runtime.

## Failure Injected

1. A synthetic outbox row is inserted as `PENDING`.
2. The test writes the same outbox event envelope directly to Redpanda, simulating a publisher worker that received broker acknowledgement.
3. The test intentionally does not mark the outbox row `PUBLISHED`, simulating process death before the database status update.
4. The normal `KafkaOutboxPublisher` runs again, republishes the still-pending event, and marks the outbox row `PUBLISHED`.
5. `KafkaInboxConsumer` consumes both broker records with the same `outboxEventId`.

## Command

```bash
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.eventing.RedpandaOutboxDeliveryIntegrationTest
```

## Result

Passed.

- The outbox row remained `PENDING` after the injected post-ack crash state.
- Replay publish attempted one event and marked it `PUBLISHED`.
- The inbox consumer polled two broker records with the same source event ID.
- Exactly one inbox row was inserted.
- Duplicate replay was counted as `duplicates=1`, proving consumer idempotency absorbs the publish-after-crash duplicate.

## Worker Entrypoint And Metrics

Additional verification on 2026-06-03 added the target-stack outbox worker entrypoint and metrics without using the Node reference runtime.

Commands:

```bash
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test --tests lab.banking.core.eventing.OutboxWorkerMetricsTest --tests lab.banking.core.eventing.OutboxWorkerRunnerTest
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.observability.ObservabilityActuatorIntegrationTest
docker compose --profile platform config
```

Results:

- `OutboxWorkerRunner` delegates durable publish batches through `OutboxPublisherPort` with configured bootstrap servers, topic, client ID, batch size, timeout, DLQ threshold, and retry delay.
- `OutboxWorkerMetricsTest` proves running state, lifecycle counters, batch counters, attempted/published/failed/dead-lettered event counters, and loop-failure counters.
- `ObservabilityActuatorIntegrationTest` proves `/actuator/prometheus` exposes `banking_lab_outbox_worker_running`, `banking_lab_outbox_worker_starts_total`, `banking_lab_outbox_worker_events_attempted_total`, and `banking_lab_outbox_worker_events_published_total` with topic and client ID labels.
- `docker compose --profile platform config` renders `core-banking-outbox-worker` with `BANKING_LAB_OUTBOX_WORKER_ENABLED=true`, Redpanda bootstrap configuration, synthetic-only scope, and a Prometheus scrape target.

## Live Compose Worker Container Restart

Additional verification on 2026-06-03 proved the deployed worker-container restart path before publish.

Commands:

```bash
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar
env COMPOSE_PROJECT_NAME=banking-lab-outbox-worker-drill BANKING_LAB_POSTGRES_PORT=15496 BANKING_LAB_REDPANDA_PORT=19096 BANKING_LAB_REDPANDA_ADMIN_PORT=19696 BANKING_LAB_OUTBOX_TOPIC=banking.lab.outbox-worker-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform up -d --build postgres redpanda core-banking-outbox-worker
env COMPOSE_PROJECT_NAME=banking-lab-outbox-worker-drill BANKING_LAB_POSTGRES_PORT=15496 BANKING_LAB_REDPANDA_PORT=19096 BANKING_LAB_REDPANDA_ADMIN_PORT=19696 BANKING_LAB_OUTBOX_TOPIC=banking.lab.outbox-worker-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform up -d --build --no-deps core-banking-outbox-worker
env BANKING_LAB_LIVE_OUTBOX_COMPOSE_PROJECT=banking-lab-outbox-worker-drill BANKING_LAB_POSTGRES_PORT=15496 BANKING_LAB_REDPANDA_PORT=19096 BANKING_LAB_REDPANDA_ADMIN_PORT=19696 BANKING_LAB_OUTBOX_TOPIC=banking.lab.outbox-worker-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.eventing.LiveOutboxWorkerSmokeIntegrationTest.live outbox worker publishes pending event after Compose worker container restart'
env COMPOSE_PROJECT_NAME=banking-lab-outbox-worker-drill BANKING_LAB_POSTGRES_PORT=15496 BANKING_LAB_REDPANDA_PORT=19096 BANKING_LAB_REDPANDA_ADMIN_PORT=19696 BANKING_LAB_OUTBOX_TOPIC=banking.lab.outbox-worker-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform down
```

Result:

- `LiveOutboxWorkerSmokeIntegrationTest` waited for the Compose-managed Flyway schema, killed `core-banking-outbox-worker`, inserted a synthetic `PENDING` outbox row, and confirmed it stayed `PENDING` while the worker was down.
- The test restarted `core-banking-outbox-worker` with `docker compose up -d --no-deps`, waited for the row to become `PUBLISHED`, verified `published_at` was set, and consumed the corresponding Redpanda record from the host-side external listener.
- `docker-compose.yml` now advertises separate Redpanda listeners for Docker-internal clients (`redpanda:9092`) and host-side drill clients (`127.0.0.1:${BANKING_LAB_REDPANDA_PORT}`).

## Live Compose Post-Broker-Ack Crash And Replay

Additional verification on 2026-06-03 proved the deployed worker crash path after broker acknowledgement and before marking the outbox row `PUBLISHED`.

Commands:

```bash
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test --tests lab.banking.core.eventing.OutboxWorkerRunnerTest
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.eventing.LiveOutboxWorkerSmokeIntegrationTest
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar
env COMPOSE_PROJECT_NAME=banking-lab-outbox-postack-drill BANKING_LAB_POSTGRES_PORT=15497 BANKING_LAB_REDPANDA_PORT=19097 BANKING_LAB_REDPANDA_ADMIN_PORT=19697 BANKING_LAB_OUTBOX_TOPIC=banking.lab.outbox-postack-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform up -d --build postgres redpanda core-banking-outbox-worker
env BANKING_LAB_LIVE_OUTBOX_COMPOSE_PROJECT=banking-lab-outbox-postack-drill BANKING_LAB_POSTGRES_PORT=15497 BANKING_LAB_REDPANDA_PORT=19097 BANKING_LAB_REDPANDA_ADMIN_PORT=19697 BANKING_LAB_OUTBOX_TOPIC=banking.lab.outbox-postack-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.eventing.LiveOutboxWorkerSmokeIntegrationTest
env COMPOSE_PROJECT_NAME=banking-lab-outbox-postack-drill BANKING_LAB_POSTGRES_PORT=15497 BANKING_LAB_REDPANDA_PORT=19097 BANKING_LAB_REDPANDA_ADMIN_PORT=19697 BANKING_LAB_OUTBOX_TOPIC=banking.lab.outbox-postack-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform down
```

Result:

- `KafkaOutboxPublisher` now has a synthetic-only fault injection switch that halts the worker after `producer.send(...).get(...)` succeeds and before `OutboxService.markPublished(...)` runs.
- `LiveOutboxWorkerSmokeIntegrationTest` inserted a synthetic durable `PENDING` outbox row, started `core-banking-outbox-worker` with the fault pointed at that event ID, and observed the worker container exit with code `88`.
- After the forced process halt, the database row remained `PENDING` with `published_at` still null while one Redpanda record for the event was already visible through the host-side listener.
- Restarting the worker without the fault replayed the still-pending row, marked it `PUBLISHED`, set `published_at`, and produced a second Redpanda record with the same `outboxEventId`.
- This proves the deployed post-broker-ack worker crash/replay path. Downstream side-effect safety still depends on idempotent inbox consumers, already covered by `RedpandaOutboxDeliveryIntegrationTest`.

## Retirement Impact

This closes the durable outbox crash-before-mark-published replay drill for the current Redpanda-backed event path, adds a scheduled/manual target-stack worker entrypoint with Micrometer/Prometheus metrics, and proves deployed worker-container restart-before-publish plus post-broker-ack crash/replay paths. The API process crash after durable ledger/outbox commit is covered separately in `docs/test-evidence/api-process-crash-drill.md`, outbox worker trace/log correlation is covered separately in `docs/test-evidence/outbox-trace-log-correlation.md`, and the outbox platform host-crash-shaped recovery drill is recorded in `docs/test-evidence/outbox-platform-host-crash-drill.md`. Node retirement remains blocked until non-synthetic passkey operations and final retirement review are complete.
