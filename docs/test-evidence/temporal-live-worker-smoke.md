# Temporal Live Worker Smoke Evidence

Date: 2026-06-02

## Scope

This evidence records the first live Temporal server and Spring Boot worker smoke for the target stack. It does not mark Node retirement ready.

## Changes Proven

- Docker Compose `platform` profile starts PostgreSQL on an overrideable host port and Temporal on an overrideable host port.
- Temporal `auto-setup` uses the PostgreSQL v12 driver setting and a mounted dynamic config file.
- The Spring Boot `core-banking-temporal-worker` container includes the boot jar plus Flyway migrations.
- Flyway applies the target banking schema through `V010__temporal_workflow_references.sql` inside the container.
- `BankingCaseTemporalWorker` registers `BankingCaseTemporalWorkflowImpl` on task queue `banking-case-workflows`.
- `LiveTemporalWorkerSmokeIntegrationTest` starts a live workflow through `127.0.0.1:17233`, waits for `WAITING_APPROVAL`, sends a checker approval signal, and observes `COMPLETED`.
- `TemporalWorkflowReferencePersistenceIntegrationTest` proves workflow ID/run ID persistence is visible through complaint, FDS, AML, reconciliation, and account hold domain tables.
- `TemporalWorkerMetrics` exposes worker running/start/failure/stop metrics through Micrometer.
- `ObservabilityActuatorIntegrationTest` proves `/actuator/prometheus` exposes `banking_lab_temporal_worker_*` metrics.
- `BankingCaseTemporalWorkflowIntegrationTest` now includes a synthetic transient failure drill that fails the first workflow attempt, recovers through Temporal retry policy, waits for checker approval, and completes with the expected control effect.
- `LiveTemporalWorkerSmokeIntegrationTest` now includes a live worker restart drill that shuts down the first SDK worker at `WAITING_APPROVAL`, sends approval while no worker is polling, starts a second SDK worker on the same task queue, and completes through Temporal history replay.

## Commands

```bash
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar
env BANKING_LAB_POSTGRES_PORT=15432 BANKING_LAB_TEMPORAL_PORT=17233 docker compose --profile platform config
env BANKING_LAB_POSTGRES_PORT=15432 BANKING_LAB_TEMPORAL_PORT=17233 docker compose --profile platform up -d postgres temporal
env BANKING_LAB_POSTGRES_PORT=15432 BANKING_LAB_TEMPORAL_PORT=17233 docker compose --profile platform up -d --build core-banking-temporal-worker
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17233 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.temporal.TemporalWorkflowReferencePersistenceIntegrationTest --tests lab.banking.core.temporal.BankingCaseTemporalWorkflowIntegrationTest
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test --tests lab.banking.core.temporal.TemporalWorkerMetricsTest
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.observability.ObservabilityActuatorIntegrationTest
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17235 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal workflow survives worker restart before approval completion'
```

## Result

Passed. Temporal server started, default namespace registered, Spring Boot worker started, Flyway applied 9 migrations through v010, and the live smoke workflow completed through the external Temporal endpoint. The later metrics/retry slice also passed targeted unit and integration tests for Micrometer worker metrics, `/actuator/prometheus`, and synthetic Temporal retry recovery. `docs/test-evidence/temporal-worker-restart-drill.md` records the live SDK worker restart drill, `docs/test-evidence/temporal-container-worker-restart-drill.md` records live worker container restart drills, `docs/test-evidence/temporal-server-restart-drill.md` records the representative live Temporal server container restart drill, and `docs/test-evidence/temporal-postgres-restart-drill.md` records the representative live PostgreSQL restart drill. `docs/test-evidence/observability-stack-smoke.md` records the later live Prometheus/Grafana/Loki/Tempo readiness and Prometheus scrape smoke.

## Remaining Gaps

- Worker restart is captured for the synthetic complaint-answer Temporal contract; broader worker failure and container crash drills are not yet captured.
- OpenTelemetry trace/log correlation is not yet captured.
- API-backed channel flows do not yet start or observe these workflows.
- Node retirement remains blocked until non-synthetic passkey operations and final retirement review are complete.
