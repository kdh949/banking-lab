# Temporal Container Worker Restart Drill

Review date: 2026-06-03

## Scope

This drill verifies that live Temporal workflows survive an actual Docker Compose `core-banking-temporal-worker` container restart before checker approval is completed. It uses an external Compose Temporal server, a unique task queue, starts the Spring Boot worker container, starts one workflow, waits for `WAITING_APPROVAL`, kills the worker container, sends the approval signal while the worker container is down, starts the same worker service again on the same task queue, and verifies completion through Temporal history replay.

This proves container-level worker restart continuity for all current synthetic Temporal workflow paths: complaint answer, FDS release, FDS block, AML closure, reconciliation adjustment, account hold, and account release. It does not claim host crash recovery, database crash recovery, alert routing, Loki ingestion, dashboard validation, or workflow-specific trace/log propagation.

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

Start an isolated Temporal and worker stack:

```bash
env COMPOSE_PROJECT_NAME=banking-lab-temporal-container-drill \
  BANKING_LAB_POSTGRES_PORT=15485 \
  BANKING_LAB_TEMPORAL_PORT=17236 \
  BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-container-drill \
  BANKING_LAB_TRACING_ENABLED=false \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false \
  docker compose --profile platform up -d --build postgres temporal core-banking-temporal-worker
```

Run the live complaint-answer container restart drill:

```bash
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17236 \
  BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-container-drill \
  BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-container-drill \
  scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal workflow survives Compose worker container restart before approval completion'
```

Start an isolated FDS Temporal and worker stack:

```bash
env COMPOSE_PROJECT_NAME=banking-lab-temporal-fds-container-drill \
  BANKING_LAB_POSTGRES_PORT=15490 \
  BANKING_LAB_TEMPORAL_PORT=17241 \
  BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-fds-container-drill \
  BANKING_LAB_TRACING_ENABLED=false \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false \
  docker compose --profile platform up -d --build postgres temporal core-banking-temporal-worker
```

Run the live FDS release container restart drill:

```bash
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17241 \
  BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-fds-container-drill \
  BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-fds-container-drill \
  BANKING_LAB_TRACING_ENABLED=false \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false \
  scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live FDS release workflow survives Compose worker container restart before approval completion'
```

Start an isolated AML/reconciliation Temporal and worker stack:

```bash
env COMPOSE_PROJECT_NAME=banking-lab-temporal-ops-container-drill \
  BANKING_LAB_POSTGRES_PORT=15491 \
  BANKING_LAB_TEMPORAL_PORT=17242 \
  BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-ops-container-drill \
  BANKING_LAB_TRACING_ENABLED=false \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false \
  docker compose --profile platform up -d --build postgres temporal core-banking-temporal-worker
```

Run the live AML closure and reconciliation adjustment container restart drills:

```bash
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17242 \
  BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-ops-container-drill \
  BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-ops-container-drill \
  BANKING_LAB_TRACING_ENABLED=false \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false \
  scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live AML closure workflow survives Compose worker container restart before approval completion' \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live reconciliation adjustment workflow survives Compose worker container restart before approval completion'
```

Start an isolated FDS block/account hold/account release Temporal and worker stack:

```bash
env COMPOSE_PROJECT_NAME=banking-lab-temporal-hold-container-drill \
  BANKING_LAB_POSTGRES_PORT=15492 \
  BANKING_LAB_TEMPORAL_PORT=17243 \
  BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-hold-container-drill \
  BANKING_LAB_TRACING_ENABLED=false \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false \
  docker compose --profile platform up -d --build postgres temporal core-banking-temporal-worker
```

Run the live FDS block, account hold, and account release container restart drills:

```bash
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17243 \
  BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-hold-container-drill \
  BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-hold-container-drill \
  BANKING_LAB_TRACING_ENABLED=false \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false \
  scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live FDS block workflow survives Compose worker container restart before approval completion' \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live account hold workflow survives Compose worker container restart before approval completion' \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live account release workflow survives Compose worker container restart before approval completion'
```

Capture post-drill container and log evidence:

```bash
env COMPOSE_PROJECT_NAME=banking-lab-temporal-container-drill docker compose ps --all
env COMPOSE_PROJECT_NAME=banking-lab-temporal-container-drill docker compose logs --no-color core-banking-temporal-worker
env COMPOSE_PROJECT_NAME=banking-lab-temporal-container-drill docker compose logs --no-color temporal
env COMPOSE_PROJECT_NAME=banking-lab-temporal-fds-container-drill docker compose ps --all
env COMPOSE_PROJECT_NAME=banking-lab-temporal-fds-container-drill docker compose logs --no-color core-banking-temporal-worker
env COMPOSE_PROJECT_NAME=banking-lab-temporal-fds-container-drill docker compose logs --no-color temporal
env COMPOSE_PROJECT_NAME=banking-lab-temporal-ops-container-drill docker compose ps --all
env COMPOSE_PROJECT_NAME=banking-lab-temporal-ops-container-drill docker compose logs --tail=320 core-banking-temporal-worker
env COMPOSE_PROJECT_NAME=banking-lab-temporal-ops-container-drill docker compose logs --no-color temporal
env COMPOSE_PROJECT_NAME=banking-lab-temporal-hold-container-drill docker compose ps --all
env COMPOSE_PROJECT_NAME=banking-lab-temporal-hold-container-drill docker compose logs --tail=420 core-banking-temporal-worker
```

Clean up the isolated stack:

```bash
env COMPOSE_PROJECT_NAME=banking-lab-temporal-container-drill docker compose --profile platform down -v
env COMPOSE_PROJECT_NAME=banking-lab-temporal-fds-container-drill docker compose --profile platform down -v
env COMPOSE_PROJECT_NAME=banking-lab-temporal-ops-container-drill docker compose --profile platform down -v
env COMPOSE_PROJECT_NAME=banking-lab-temporal-hold-container-drill docker compose --profile platform down -v
```

## Result

Passed after fixing the drill harness to preserve the task queue and tracing environment on test-managed `docker compose up`.

Complaint-answer result:

- The worker container initially polled `banking-case-workflows-container-drill`.
- The workflow reached `WAITING_APPROVAL`.
- The test killed `core-banking-temporal-worker` with `docker compose kill`.
- The approval signal was accepted while the worker container was down.
- The test restarted `core-banking-temporal-worker` with `docker compose up -d --no-deps`.
- The restarted worker polled `banking-case-workflows-container-drill`.
- The workflow completed from Temporal history with:
  - `finalStatus=COMPLETED`
  - `caseType=COMPLAINT_ANSWER`
  - `controlEffect=CUSTOMER_ANSWER_VISIBLE`
  - `syntheticOnly=true`
- The replayed checkpoint sequence was:
  - `COMPLAINT_ANSWER:STARTED`
  - `COMPLAINT_ANSWER:WAITING_APPROVAL`
  - `COMPLAINT_ANSWER:COMPLETED`

FDS release result:

- The worker container initially polled `banking-case-workflows-fds-container-drill`.
- The workflow reached `WAITING_APPROVAL`.
- The test killed `core-banking-temporal-worker` with `docker compose kill`.
- The FDS release approval signal was accepted while the worker container was down.
- The test restarted `core-banking-temporal-worker` with `docker compose up -d --no-deps`.
- The restarted worker polled `banking-case-workflows-fds-container-drill`.
- The workflow completed from Temporal history with:
  - `finalStatus=COMPLETED`
  - `caseType=FDS_RELEASE`
  - `controlEffect=LEDGER_TRANSFER_HANDOFF`
  - `syntheticOnly=true`
- The replayed checkpoint sequence was:
  - `FDS_RELEASE:STARTED`
  - `FDS_RELEASE:WAITING_APPROVAL`
  - `FDS_RELEASE:COMPLETED`

AML closure result:

- The worker container initially polled `banking-case-workflows-ops-container-drill`.
- The workflow reached `WAITING_APPROVAL`.
- The test killed `core-banking-temporal-worker` with `docker compose kill`.
- The AML closure approval signal was accepted while the worker container was down.
- The test restarted `core-banking-temporal-worker` with `docker compose up -d --no-deps`.
- The restarted worker polled `banking-case-workflows-ops-container-drill`.
- The workflow completed from Temporal history with:
  - `finalStatus=COMPLETED`
  - `caseType=AML_CLOSURE`
  - `controlEffect=STR_SIMULATION_CLOSURE`
  - `syntheticOnly=true`
- The replayed checkpoint sequence was:
  - `AML_CLOSURE:STARTED`
  - `AML_CLOSURE:WAITING_APPROVAL`
  - `AML_CLOSURE:COMPLETED`

Reconciliation adjustment result:

- The worker container initially polled `banking-case-workflows-ops-container-drill`.
- The workflow reached `WAITING_APPROVAL`.
- The test killed `core-banking-temporal-worker` with `docker compose kill`.
- The reconciliation adjustment approval signal was accepted while the worker container was down.
- The test restarted `core-banking-temporal-worker` with `docker compose up -d --no-deps`.
- The restarted worker polled `banking-case-workflows-ops-container-drill`.
- The workflow completed from Temporal history with:
  - `finalStatus=COMPLETED`
  - `caseType=RECONCILIATION_ADJUSTMENT`
  - `controlEffect=BALANCED_ADJUSTMENT_HANDOFF`
  - `syntheticOnly=true`
- The replayed checkpoint sequence was:
  - `RECONCILIATION_ADJUSTMENT:STARTED`
  - `RECONCILIATION_ADJUSTMENT:WAITING_APPROVAL`
  - `RECONCILIATION_ADJUSTMENT:COMPLETED`

FDS block result:

- The worker container initially polled `banking-case-workflows-hold-container-drill`.
- The workflow reached `WAITING_APPROVAL`.
- The test killed `core-banking-temporal-worker` with `docker compose kill`.
- The FDS block approval signal was accepted while the worker container was down.
- The test restarted `core-banking-temporal-worker` with `docker compose up -d --no-deps`.
- The restarted worker polled `banking-case-workflows-hold-container-drill`.
- The workflow completed from Temporal history with:
  - `finalStatus=COMPLETED`
  - `caseType=FDS_BLOCK`
  - `controlEffect=NO_LEDGER_POSTING`
  - `syntheticOnly=true`
- The replayed checkpoint sequence was:
  - `FDS_BLOCK:STARTED`
  - `FDS_BLOCK:WAITING_APPROVAL`
  - `FDS_BLOCK:COMPLETED`

Account hold result:

- The worker container initially polled `banking-case-workflows-hold-container-drill`.
- The workflow reached `WAITING_APPROVAL`.
- The test killed `core-banking-temporal-worker` with `docker compose kill`.
- The account hold approval signal was accepted while the worker container was down.
- The test restarted `core-banking-temporal-worker` with `docker compose up -d --no-deps`.
- The restarted worker polled `banking-case-workflows-hold-container-drill`.
- The workflow completed from Temporal history with:
  - `finalStatus=COMPLETED`
  - `caseType=ACCOUNT_HOLD`
  - `controlEffect=AVAILABLE_BALANCE_HOLD`
  - `syntheticOnly=true`
- The replayed checkpoint sequence was:
  - `ACCOUNT_HOLD:STARTED`
  - `ACCOUNT_HOLD:WAITING_APPROVAL`
  - `ACCOUNT_HOLD:COMPLETED`

Account release result:

- The worker container initially polled `banking-case-workflows-hold-container-drill`.
- The workflow reached `WAITING_APPROVAL`.
- The test killed `core-banking-temporal-worker` with `docker compose kill`.
- The account release approval signal was accepted while the worker container was down.
- The test restarted `core-banking-temporal-worker` with `docker compose up -d --no-deps`.
- The restarted worker polled `banking-case-workflows-hold-container-drill`.
- The workflow completed from Temporal history with:
  - `finalStatus=COMPLETED`
  - `caseType=ACCOUNT_RELEASE`
  - `controlEffect=HOLD_RELEASE_HANDOFF`
  - `syntheticOnly=true`
- The replayed checkpoint sequence was:
  - `ACCOUNT_RELEASE:STARTED`
  - `ACCOUNT_RELEASE:WAITING_APPROVAL`
  - `ACCOUNT_RELEASE:COMPLETED`

## Operational Note

The first live complaint-answer drill attempt timed out because the test-managed `docker compose up -d --no-deps core-banking-temporal-worker` restart did not pass `BANKING_LAB_TEMPORAL_TASK_QUEUE`, so the restarted worker polled the default `banking-case-workflows` queue instead of the isolated drill queue. The harness now passes `BANKING_LAB_TEMPORAL_TASK_QUEUE` through every test-managed Compose command.

The first live FDS rerun after adding the second case passed, but the restarted worker emitted a Tempo endpoint lookup error because the test-managed restart did not preserve disabled tracing export settings. The harness now also preserves `BANKING_LAB_TRACING_ENABLED`, `BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED`, and `BANKING_LAB_OTLP_TRACES_ENDPOINT` when present. The later Temporal server restart drill also proved the shared harness must preserve `BANKING_LAB_POSTGRES_PORT` and `BANKING_LAB_TEMPORAL_PORT` for isolated Compose projects. The clean reruns passed without those restart-side misconfigurations.

## Retirement Impact

This closes the live Compose worker container restart drill for all current synthetic Temporal workflow contracts: complaint answer, FDS release, FDS block, AML closure, reconciliation adjustment, account hold, and account release. The platform host-crash-shaped drill is recorded in `docs/test-evidence/temporal-platform-host-crash-drill.md`. Node retirement is now ready for the current synthetic lab scope; this evidence slice remains scoped to its named control and the Node reference stays archived oracle/reference material.
