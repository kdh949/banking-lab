# Temporal Platform Host-Crash-Shaped Drill

Review date: 2026-06-03

## Scope

This drill verifies that the current synthetic Temporal workflow contracts survive a Docker Compose platform outage shaped like a host crash: PostgreSQL, the Temporal server, and `core-banking-temporal-worker` are killed together after workflows reach `WAITING_APPROVAL`, then restarted on the same volumes, ports, and task queue before checker approval is submitted.

This is not a physical host power-loss, disk-loss, multi-node failover, backup/restore, or production HA claim. It proves the local target stack can recover current Temporal workflow history after the key single-host Compose services are abruptly stopped and restarted.

## Commands

Compile and env-gated skip path:

```bash
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live all Temporal workflows survive Compose platform host crash restart before approval completion'
```

Start the isolated platform host-crash drill stack:

```bash
env COMPOSE_PROJECT_NAME=banking-lab-temporal-host-crash-drill \
  BANKING_LAB_POSTGRES_PORT=15510 \
  BANKING_LAB_TEMPORAL_PORT=17260 \
  BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-host-crash-drill \
  BANKING_LAB_TRACING_ENABLED=false \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false \
  docker compose --profile platform up -d --build postgres temporal core-banking-temporal-worker
```

Run the live platform host-crash drill:

```bash
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17260 \
  BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-host-crash-drill \
  BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-host-crash-drill \
  BANKING_LAB_POSTGRES_PORT=15510 \
  BANKING_LAB_TEMPORAL_PORT=17260 \
  BANKING_LAB_TRACING_ENABLED=false \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false \
  scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live all Temporal workflows survive Compose platform host crash restart before approval completion'
```

Capture post-drill evidence:

```bash
env COMPOSE_PROJECT_NAME=banking-lab-temporal-host-crash-drill docker compose ps --all
env COMPOSE_PROJECT_NAME=banking-lab-temporal-host-crash-drill docker compose logs --no-color --tail=360 core-banking-temporal-worker
```

Clean up:

```bash
env COMPOSE_PROJECT_NAME=banking-lab-temporal-host-crash-drill \
  BANKING_LAB_POSTGRES_PORT=15510 \
  BANKING_LAB_TEMPORAL_PORT=17260 \
  BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-host-crash-drill \
  BANKING_LAB_TRACING_ENABLED=false \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false \
  docker compose --profile platform down -v
```

## Result

Passed.

- The focused compile/skip run passed.
- The live run started all current workflow case types and waited for `WAITING_APPROVAL`.
- The test killed `core-banking-temporal-worker`, `temporal`, and `postgres` before checker approval.
- The stack restarted with the same PostgreSQL volume, Temporal port, and task queue.
- `docker compose ps --all` showed PostgreSQL healthy and the restarted Temporal server and worker running on `127.0.0.1:17260`.
- Worker logs showed transient `UNAVAILABLE: Network closed for unknown reason` poller failures during the outage.
- After restart, checker approval signals were accepted and all current workflow case types completed:
  - `COMPLAINT_ANSWER` with `CUSTOMER_ANSWER_VISIBLE`
  - `FDS_RELEASE` with `LEDGER_TRANSFER_HANDOFF`
  - `FDS_BLOCK` with `NO_LEDGER_POSTING`
  - `AML_CLOSURE` with `STR_SIMULATION_CLOSURE`
  - `RECONCILIATION_ADJUSTMENT` with `BALANCED_ADJUSTMENT_HANDOFF`
  - `ACCOUNT_HOLD` with `AVAILABLE_BALANCE_HOLD`
  - `ACCOUNT_RELEASE` with `HOLD_RELEASE_HANDOFF`
- Completion logs carried `finalStatus=COMPLETED` and `syntheticOnly=true` for every case type.

## Retirement Impact

This closes the current Temporal platform host-crash-shaped restart evidence slice for synthetic workflows. Node retirement remains blocked until non-synthetic passkey operations, evidence-refresh completion, and final retirement review are complete.
