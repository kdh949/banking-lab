# Outbox Platform Host-Crash-Shaped Drill

Review date: 2026-06-03

## Scope

This drill verifies that a durable `PENDING` outbox row survives a Docker Compose platform outage shaped like a host crash: PostgreSQL, Redpanda, and `core-banking-outbox-worker` are down before publish, then restarted on the same volumes, ports, and topic. The worker must publish the pending event, mark it `PUBLISHED`, and expose the corresponding Redpanda record after recovery.

This is not a physical host power-loss, disk-loss, multi-node Kafka failover, backup/restore, or production HA claim. It proves the current local synthetic outbox path can recover a pending event after the key single-host Compose eventing services are abruptly stopped and restarted.

## Commands

Compile and env-gated skip path:

```bash
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest \
  --tests 'lab.banking.core.eventing.LiveOutboxWorkerSmokeIntegrationTest.live outbox worker publishes pending event after Compose platform host crash restart'
```

Start the isolated outbox platform host-crash drill stack:

```bash
env COMPOSE_PROJECT_NAME=banking-lab-outbox-host-crash-drill \
  BANKING_LAB_POSTGRES_PORT=15511 \
  BANKING_LAB_REDPANDA_PORT=19110 \
  BANKING_LAB_REDPANDA_ADMIN_PORT=19710 \
  BANKING_LAB_OUTBOX_TOPIC=banking.lab.outbox-host-crash-drill \
  BANKING_LAB_TRACING_ENABLED=false \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false \
  docker compose --profile platform up -d --build postgres redpanda core-banking-outbox-worker
```

Run the live outbox platform host-crash drill:

```bash
env BANKING_LAB_LIVE_OUTBOX_COMPOSE_PROJECT=banking-lab-outbox-host-crash-drill \
  BANKING_LAB_POSTGRES_PORT=15511 \
  BANKING_LAB_REDPANDA_PORT=19110 \
  BANKING_LAB_REDPANDA_ADMIN_PORT=19710 \
  BANKING_LAB_OUTBOX_TOPIC=banking.lab.outbox-host-crash-drill \
  BANKING_LAB_TRACING_ENABLED=false \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false \
  scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest \
  --tests 'lab.banking.core.eventing.LiveOutboxWorkerSmokeIntegrationTest.live outbox worker publishes pending event after Compose platform host crash restart'
```

Capture post-drill evidence:

```bash
env COMPOSE_PROJECT_NAME=banking-lab-outbox-host-crash-drill docker compose ps --all
env COMPOSE_PROJECT_NAME=banking-lab-outbox-host-crash-drill docker compose logs --no-color --tail=260 core-banking-outbox-worker
env COMPOSE_PROJECT_NAME=banking-lab-outbox-host-crash-drill docker compose logs --no-color --tail=180 redpanda
```

Clean up:

```bash
env COMPOSE_PROJECT_NAME=banking-lab-outbox-host-crash-drill \
  BANKING_LAB_POSTGRES_PORT=15511 \
  BANKING_LAB_REDPANDA_PORT=19110 \
  BANKING_LAB_REDPANDA_ADMIN_PORT=19710 \
  BANKING_LAB_OUTBOX_TOPIC=banking.lab.outbox-host-crash-drill \
  BANKING_LAB_TRACING_ENABLED=false \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false \
  docker compose --profile platform down -v
```

## Result

Passed.

- The focused compile/skip run passed.
- The live run killed `core-banking-outbox-worker`, inserted a synthetic durable `PENDING` outbox row, then killed `redpanda` and `postgres`.
- The stack restarted PostgreSQL, Redpanda, and `core-banking-outbox-worker` on the same ports and topic.
- `docker compose ps --all` showed PostgreSQL healthy, Redpanda running, and the restarted outbox worker running.
- Redpanda logs showed recovery from an existing data directory and the Kafka listener returning.
- The worker applied Flyway against the existing PostgreSQL schema after restart.
- The worker published the pending outbox event, marked it `PUBLISHED`, set `published_at`, and the host-side Kafka consumer observed one Redpanda record containing the same `outboxEventId`.
- Worker logs recorded `observability.outbox.worker event=batch ... attempted=1 published=1 failed=0 deadLettered=0 ... syntheticOnly=true`.
- The captured event ID was `OBX-HOST-CRASH-9CB5A46E-5BFB-4F62-9676-6EE04776D6E6`.

## Retirement Impact

This closes the current outbox platform host-crash-shaped recovery evidence slice for durable pending events. Node retirement remains blocked until non-synthetic passkey operations and final retirement review are complete.
