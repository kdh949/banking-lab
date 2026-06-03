# Target Stack Failure Drill Additions

Review date: 2026-06-03

## Purpose

The existing drill set covers the Node reference flows well. These additions focus on target-stack gaps that must be drilled before Node retirement.

## Drill 1: Spring API Or Outbox Worker Crash Around Publish

Failure injected:

- A ledger command commits `ledger_transactions`, `ledger_postings`, `account_balance_projections`, and `outbox_events`.
- The process crashes before any Kafka/Redpanda publish attempt.
- Or an outbox worker receives broker acknowledgment and crashes before marking the row `PUBLISHED`.

Expected evidence:

- Ledger transaction remains balanced and visible after restart.
- Outbox event remains `PENDING`.
- Publisher replay emits the event after restart or republish.
- Duplicate broker records do not duplicate downstream consumer side effects because the inbox insert is idempotent.

Required commands:

```bash
./gradlew :services:core-banking:integrationTest --tests '*OutboxRecovery*'
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.eventing.RedpandaOutboxDeliveryIntegrationTest
```

Current status: Passed for the current synthetic eventing slice. `RedpandaOutboxDeliveryIntegrationTest` proves durable outbox publish, retry, DLQ, replay idempotency, and a crash-before-mark-published drill where a broker-acked event remains `PENDING`, is replayed, and produces exactly one inbox side effect. `OutboxWorkerRunnerTest`, `OutboxWorkerMetricsTest`, `ObservabilityActuatorIntegrationTest`, and `docker compose --profile platform config` prove a target-stack scheduled/manual worker entrypoint, Micrometer/Prometheus actuator metrics, and a platform `core-banking-outbox-worker` service. `LiveOutboxWorkerSmokeIntegrationTest` now proves deployed worker restart-before-publish, post-broker-ack crash/replay, and platform host-crash-shaped recovery where a durable `PENDING` row survives PostgreSQL, Redpanda, and worker downtime, then becomes `PUBLISHED` with a Redpanda record after restart. `LiveCustomerTransferApiCrashIntegrationTest` proves a full API process crash/restart after durable ledger/outbox commit with one ledger transaction, one customer-transfer result, one `PENDING` outbox event, and idempotent replay after restart. `OutboxWorkerTraceLogIntegrationTest` and the live Compose outbox trace drill prove worker batch trace/log correlation for the current synthetic outbox publish path.

## Drill 2: Spring SERIALIZABLE Conflict Retry Policy

Failure injected:

- Concurrent withdrawals hit the same account under `SERIALIZABLE`.
- PostgreSQL returns serialization conflicts for a subset of attempts.

Expected evidence:

- No account overdraw occurs.
- Failed attempts return a structured retryable error or are retried by a documented bounded policy.
- Idempotency keys for retried commands resolve to at most one business result.
- Operator/customer status is not ambiguous.

Required commands:

```bash
./gradlew :services:core-banking:integrationTest --tests '*SerializableWithdrawal*'
```

Current status: Partial. Existing evidence says conflicts prevent overdraw, but retry/operator policy remains open.

## Drill 3: Keycloak Token Revocation During Staff Sensitive Inquiry

Failure injected:

- A staff token is valid for screen entry but revoked or role-changed before a sensitive detail lookup.

Expected evidence:

- API denies access with `AUTHORIZATION_POLICY_VIOLATION`.
- Response follows the structured error contract.
- No unmasked PII is returned.
- Audit event records the denied access attempt without exposing PII.

Required commands:

```bash
./gradlew :services:core-banking:integrationTest --tests '*Authorization*'
npx playwright test apps/staff-terminal/e2e/staff-terminal-parity.spec.ts
```

Current status: Planned. Keycloak realm configuration exists, but resource-server enforcement is not proven.

## Drill 4: Temporal Worker Retry And Restart During Complaint Answer Approval

Failure injected:

- Complaint answer approval is requested.
- Worker restarts before execution completes.

Expected evidence:

- Workflow history survives restart.
- Customer-visible answer remains hidden until approval execution completes.
- Maker/checker separation is preserved after restart.
- Audit timeline links approval request, approval, execution, and customer-visible answer.

Required commands:

```bash
./gradlew :services:core-banking:integrationTest --tests '*ComplaintWorkflowRecovery*'
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.temporal.BankingCaseTemporalWorkflowIntegrationTest
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17235 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal workflow survives worker restart before approval completion'
env COMPOSE_PROJECT_NAME=banking-lab-temporal-container-drill BANKING_LAB_POSTGRES_PORT=15485 BANKING_LAB_TEMPORAL_PORT=17236 BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-container-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform up -d --build postgres temporal core-banking-temporal-worker
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17236 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-container-drill BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-container-drill scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal workflow survives Compose worker container restart before approval completion'
env COMPOSE_PROJECT_NAME=banking-lab-temporal-fds-container-drill BANKING_LAB_POSTGRES_PORT=15490 BANKING_LAB_TEMPORAL_PORT=17241 BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-fds-container-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform up -d --build postgres temporal core-banking-temporal-worker
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17241 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-fds-container-drill BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-fds-container-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live FDS release workflow survives Compose worker container restart before approval completion'
env COMPOSE_PROJECT_NAME=banking-lab-temporal-ops-container-drill BANKING_LAB_POSTGRES_PORT=15491 BANKING_LAB_TEMPORAL_PORT=17242 BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-ops-container-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform up -d --build postgres temporal core-banking-temporal-worker
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17242 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-ops-container-drill BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-ops-container-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live AML closure workflow survives Compose worker container restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live reconciliation adjustment workflow survives Compose worker container restart before approval completion'
env COMPOSE_PROJECT_NAME=banking-lab-temporal-hold-container-drill BANKING_LAB_POSTGRES_PORT=15492 BANKING_LAB_TEMPORAL_PORT=17243 BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-hold-container-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform up -d --build postgres temporal core-banking-temporal-worker
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17243 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-hold-container-drill BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-hold-container-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live FDS block workflow survives Compose worker container restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live account hold workflow survives Compose worker container restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live account release workflow survives Compose worker container restart before approval completion'
env COMPOSE_PROJECT_NAME=banking-lab-temporal-broader-restart-drill BANKING_LAB_POSTGRES_PORT=15495 BANKING_LAB_TEMPORAL_PORT=17246 BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-broader-restart-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false docker compose --profile platform up -d --build postgres temporal core-banking-temporal-worker
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17246 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-broader-restart-drill BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-broader-restart-drill BANKING_LAB_POSTGRES_PORT=15495 BANKING_LAB_TEMPORAL_PORT=17246 BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal workflow survives Compose Temporal server restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live FDS release workflow survives Compose Temporal server restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live FDS block workflow survives Compose Temporal server restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live AML closure workflow survives Compose Temporal server restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live reconciliation adjustment workflow survives Compose Temporal server restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live account hold workflow survives Compose Temporal server restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live account release workflow survives Compose Temporal server restart before approval completion'
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17246 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-broader-restart-drill BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-broader-restart-drill BANKING_LAB_POSTGRES_PORT=15495 BANKING_LAB_TEMPORAL_PORT=17246 BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal workflow survives Compose PostgreSQL restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live FDS release workflow survives Compose PostgreSQL restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live FDS block workflow survives Compose PostgreSQL restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live AML closure workflow survives Compose PostgreSQL restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live reconciliation adjustment workflow survives Compose PostgreSQL restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live account hold workflow survives Compose PostgreSQL restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live account release workflow survives Compose PostgreSQL restart before approval completion'
```

Current status: Passed for the current synthetic Temporal orchestration slice. `BankingCaseTemporalWorkflowIntegrationTest` now includes a synthetic transient Temporal failure drill that retries a complaint-answer workflow, reaches `WAITING_APPROVAL` on the second attempt, preserves maker-checker separation, and completes after checker approval. `LiveTemporalWorkerSmokeIntegrationTest` now includes a live external Temporal SDK worker restart drill that shuts down one SDK worker at `WAITING_APPROVAL`, sends approval while no worker is polling, starts a second SDK worker, and completes from workflow history. It also includes actual Compose `core-banking-temporal-worker` container kill/restart drills for all current case types, Compose `temporal` server container kill/restart drills for all current case types with health returning `SERVING` before approval and completion from Temporal history, Compose `postgres` crash/restart drills for all current case types where PostgreSQL automatic recovery completes before approval, and a platform host-crash-shaped drill where PostgreSQL, Temporal, and the worker are killed together before checker approval and all current workflow case types still complete from Temporal history after restart. Loki-ingested workflow failure logs and the `TemporalWorkflowFailed` ruler alert are proven separately in `docs/test-evidence/observability-stack-smoke.md`. Broader API-backed workflow orchestration should continue as new workflow states are introduced.

## Drill 5: Reconciliation Partial Failure After Day Close

Failure injected:

- Daily close succeeds.
- External simulator file import times out before all reconciliation items are materialized.

Expected evidence:

- Closed business date rejects direct posting.
- Partial import is observable and retryable.
- Duplicate retry does not create duplicate reconciliation items.
- Adjustment can only be posted as a balanced transaction on an open day after maker-checker approval.

Required commands:

```bash
./gradlew :services:core-banking:integrationTest --tests '*ReconciliationRecovery*'
```

Current status: Planned. Node EOD mismatch flow exists; target recovery and simulator timeout handling are not proven.

## Drill 6: Evidence Pack Refuses Missing Target Command Output

Failure injected:

- Evidence pack is generated without required target command artifacts for Spring integration, Playwright, security scans, or observability smoke.

Expected evidence:

- Evidence pack marks the target-stack sections as missing or blocked.
- Node oracle success is not presented as target parity success.
- Retirement recommendation remains blocked.

Required commands:

```bash
npm run evidence:pack
node --experimental-strip-types scripts/check-qa-evidence-review.ts
```

Current status: Added as QA recommendation. Existing generated evidence is green for current phases, but it is not yet a full target-stack retirement evidence pack.

## Drill 7: Temporal Worker Metrics Missing From Prometheus

Failure injected:

- The Temporal worker starts or fails without emitting observable lifecycle state.
- Prometheus scrape configuration points at the legacy Node container or misses the Temporal worker container.

Expected evidence:

- `banking_lab_temporal_worker_running` reports the worker running state.
- Worker start, start-failure, and stop counters are exposed with namespace and task-queue labels.
- Prometheus scrape configuration targets the Spring `core-banking` service and the `core-banking-temporal-worker`, not the retired Node runtime.
- Node retirement remains blocked until broader workflow/container failure evidence is captured.

Required commands:

```bash
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test --tests lab.banking.core.temporal.TemporalWorkerMetricsTest
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.observability.ObservabilityActuatorIntegrationTest
docker compose --profile platform config
```

Current status: Passed for readiness, Prometheus scraping, basic Spring HTTP trace/log correlation, Temporal signal/completion workflow trace/log correlation across all current case types, live Temporal rejection/self-approval failure transition trace/log correlation, outbox worker trace/log correlation, Loki ingestion of workflow failure logs, `TemporalWorkflowFailed` ruler alert firing, Grafana dashboard provisioning, all-current-case Compose worker container restart drills, all-current-case Compose Temporal server restart drills, and all-current-case Compose PostgreSQL restart drills. Micrometer metrics, `/actuator/prometheus`, target Prometheus scrape config, live Prometheus/Grafana/Loki/Tempo readiness, Redpanda readiness, Prometheus `up` plus `banking_lab_temporal_worker_running` queries, Spring `/health` trace/log correlation to Tempo, Temporal workflow signal/completion trace/log correlation for all current case types, live rejection/failure transition trace/log correlation, outbox worker batch trace/log correlation, Loki failure-log ingestion, the firing Loki alert, the provisioned Grafana dashboard, all current live worker container restart paths, the live Temporal server restart paths, and the live PostgreSQL restart paths are covered in `docs/test-evidence/observability-stack-smoke.md`, `docs/test-evidence/opentelemetry-trace-log-correlation.md`, `docs/test-evidence/temporal-workflow-trace-log-correlation.md`, `docs/test-evidence/outbox-trace-log-correlation.md`, `docs/test-evidence/temporal-container-worker-restart-drill.md`, `docs/test-evidence/temporal-server-restart-drill.md`, and `docs/test-evidence/temporal-postgres-restart-drill.md`. Temporal and outbox platform host-crash-shaped drills are now covered for the current synthetic target paths.

## Drill 8: OpenTelemetry Trace And Access Log Correlation

Failure injected:

- A Spring HTTP request produces an application log line without a usable trace ID.
- Or the trace ID in logs cannot be found in Tempo.

Expected evidence:

- The Spring service emits a W3C OpenTelemetry trace for the request.
- The access log records the same trace ID, span ID, request ID, path, status, and synthetic-only marker.
- Tempo `/api/traces/{traceId}` returns the matching trace with `service.name=banking-lab-core-banking`.
- No real PII or authorization tokens appear in the log line.

Required commands:

```bash
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.observability.ObservabilityActuatorIntegrationTest
env COMPOSE_PROJECT_NAME=banking-lab-otel-smoke ... docker compose --profile platform up -d --build postgres tempo core-banking
curl -H 'x-request-id: REQ-OTEL-CORRELATION-001' http://127.0.0.1:18134/health
env COMPOSE_PROJECT_NAME=banking-lab-otel-smoke docker compose logs --no-color core-banking | rg 'REQ-OTEL-CORRELATION-001|observability\.access'
curl http://127.0.0.1:13201/api/traces/{traceId}
```

Current status: Passed for the Spring `/health` HTTP path, Temporal signal/completion workflow paths across all current case types, outbox worker batch paths, live Temporal rejection/self-approval failure transition paths, Loki ingestion of the failed workflow log, a firing Loki ruler alert, Grafana dashboard provisioning, all-current-case worker container restart drills, all-current-case Temporal server container restart drills, and all-current-case PostgreSQL restart drills. External alert routing remains pending before production-like claims.
