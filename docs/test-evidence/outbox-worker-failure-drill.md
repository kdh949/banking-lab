# Outbox Worker Failure Drill

Review date: 2026-06-03

## Scope

This drill verifies the durable outbox recovery path for a publisher crash after broker acknowledgement but before the outbox row is marked `PUBLISHED`.

The drill uses PostgreSQL and Redpanda through Testcontainers. It does not depend on the Node reference runtime.

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

## Retirement Impact

This closes the durable outbox crash-before-mark-published failure drill for the current Redpanda-backed event path and adds a scheduled/manual target-stack worker entrypoint with Micrometer/Prometheus metrics. Node retirement remains blocked until host crash shapes, API/outbox deployed worker-container process-failure variants, outbox tracing, non-synthetic passkey operations, evidence-refresh completion, and final retirement review are complete.
