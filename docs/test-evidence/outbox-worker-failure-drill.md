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

## Retirement Impact

This closes the durable outbox crash-before-mark-published failure drill for the current Redpanda-backed event path. Node retirement remains blocked until full Node reference parity, remaining workflow/failure-state channel parity, host crash shapes and broader database/process-failure variants, non-synthetic passkey operations, and final retirement review are complete.
