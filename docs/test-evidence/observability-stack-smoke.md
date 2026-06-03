# Observability Stack Smoke

Review date: 2026-06-03

## Scope

This smoke verifies the local target-stack observability path for Spring Boot and Temporal worker metrics. It proves container readiness, Prometheus scraping, Loki ingestion of Temporal workflow failure logs, Loki ruler alert evaluation, and Grafana dashboard provisioning for:

- `core-banking`
- `core-banking-temporal-worker`
- Redpanda
- Prometheus
- Grafana
- Loki
- Promtail
- Tempo

This does not claim external alert routing, non-local alert notification delivery, or full Temporal/container worker failure recovery. Basic Spring HTTP trace/log correlation is covered separately in `docs/test-evidence/opentelemetry-trace-log-correlation.md`, and Temporal workflow trace/log correlation is covered in `docs/test-evidence/temporal-workflow-trace-log-correlation.md`.

## Commands

```bash
env COMPOSE_PROJECT_NAME=banking-lab-observability-smoke \
  BANKING_LAB_POSTGRES_PORT=15481 \
  BANKING_LAB_CORE_BANKING_PORT=18133 \
  BANKING_LAB_TEMPORAL_PORT=17234 \
  BANKING_LAB_PROMETHEUS_PORT=19090 \
  BANKING_LAB_GRAFANA_PORT=13001 \
  BANKING_LAB_LOKI_PORT=13100 \
  BANKING_LAB_TEMPO_PORT=13200 \
  BANKING_LAB_TEMPO_OTLP_GRPC_PORT=14317 \
  BANKING_LAB_TEMPO_OTLP_HTTP_PORT=14318 \
  docker compose --profile platform up -d --build \
  postgres temporal core-banking core-banking-temporal-worker prometheus grafana loki promtail tempo

env COMPOSE_PROJECT_NAME=banking-lab-observability-smoke \
  BANKING_LAB_REDPANDA_PORT=19092 \
  BANKING_LAB_REDPANDA_ADMIN_PORT=19644 \
  docker compose --profile platform up -d redpanda

curl --retry 12 --retry-delay 5 --retry-all-errors -fsS http://127.0.0.1:18133/health
curl --retry 12 --retry-delay 5 --retry-all-errors -fsS http://127.0.0.1:19090/-/ready
curl --retry 12 --retry-delay 5 --retry-all-errors -fsS http://127.0.0.1:13001/api/health
curl --retry 12 --retry-delay 5 --retry-all-errors -fsS http://127.0.0.1:13100/ready
curl --retry 12 --retry-delay 5 --retry-all-errors -fsS http://127.0.0.1:13200/ready
curl --retry 12 --retry-delay 5 --retry-all-errors -fsS http://127.0.0.1:19644/v1/status/ready
curl --retry 12 --retry-delay 5 --retry-all-errors -fsS 'http://127.0.0.1:19090/api/v1/query?query=up'
curl --retry 12 --retry-delay 5 --retry-all-errors -fsS 'http://127.0.0.1:19090/api/v1/query?query=banking_lab_temporal_worker_running'
curl --retry 12 --retry-delay 5 --retry-all-errors -fsS http://127.0.0.1:19090/api/v1/targets

env COMPOSE_PROJECT_NAME=banking-lab-loki-workflow-smoke \
  BANKING_LAB_POSTGRES_PORT=15489 \
  BANKING_LAB_TEMPORAL_PORT=17240 \
  BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-loki-smoke \
  BANKING_LAB_LOKI_PORT=13101 \
  BANKING_LAB_GRAFANA_PORT=13002 \
  BANKING_LAB_TEMPO_PORT=13205 \
  BANKING_LAB_TEMPO_OTLP_GRPC_PORT=14325 \
  BANKING_LAB_TEMPO_OTLP_HTTP_PORT=14326 \
  BANKING_LAB_TRACING_ENABLED=true \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=true \
  docker compose --profile platform up -d --build \
  postgres temporal tempo loki promtail grafana core-banking-temporal-worker

curl --retry 12 --retry-delay 5 --retry-all-errors -fsS http://127.0.0.1:13101/ready
curl --retry 12 --retry-delay 5 --retry-all-errors -fsS http://127.0.0.1:13002/api/health
curl --retry 12 --retry-delay 5 --retry-all-errors -fsS http://127.0.0.1:13205/ready
curl -fsS -u admin:admin http://127.0.0.1:13002/api/datasources/uid/Loki
curl -fsS -u admin:admin http://127.0.0.1:13002/api/dashboards/uid/temporal-workflow-observability
curl -fsS http://127.0.0.1:13101/loki/api/v1/rules

env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17240 \
  BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-loki-smoke \
  scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal worker executes rejection and failure transitions from server task queue'

curl -G \
  --data-urlencode 'query={compose_service="core-banking-temporal-worker"} |= "observability.workflow" |= "event=failed"' \
  --data-urlencode limit=20 \
  http://127.0.0.1:13101/loki/api/v1/query_range

curl -G \
  --data-urlencode 'query=count_over_time({compose_service="core-banking-temporal-worker"} |= "observability.workflow" |= "event=failed" [5m]) > 0' \
  http://127.0.0.1:13101/loki/api/v1/query

curl --retry 10 --retry-delay 5 --retry-all-errors -fsS http://127.0.0.1:13101/prometheus/api/v1/alerts
```

## Results

- `core-banking` returned `status=ok`, `syntheticOnly=true`, `auditHashChainValid=true`, and `migrationTarget=kotlin-spring-boot`.
- Prometheus returned `Prometheus Server is Ready.`
- Grafana returned `database=ok`.
- Loki returned `ready` after transient `503` warm-up responses.
- Tempo returned `ready` after transient `503` warm-up responses.
- Redpanda returned `{"status":"ready"}`.
- Prometheus `up` query returned `1` for:
  - `core-banking:8081`
  - `core-banking-temporal-worker:8081`
  - `redpanda:9644`
- Prometheus `banking_lab_temporal_worker_running` query returned:
  - `core-banking:8081` = `0`
  - `core-banking-temporal-worker:8081` = `1`
- Prometheus targets reported `health=up` and empty `lastError` for:
  - `http://core-banking:8081/actuator/prometheus`
  - `http://core-banking-temporal-worker:8081/actuator/prometheus`
  - `http://redpanda:9644/metrics`
- Grafana provisioned datasource UID `Loki` and dashboard UID `temporal-workflow-observability` with the `Temporal Workflow Failures` Loki panel.
- Loki loaded rule group `temporal-workflow-alerts` with alert `TemporalWorkflowFailed`.
- The live Temporal rejection/failure smoke passed against task queue `banking-case-workflows-loki-smoke`.
- Loki `query_range` returned the failed workflow log for `businessReferenceId=LIVE-TRACE-FDS_SELF_APPROVAL-001`, `finalStatus=FAILED`, `errorType=MAKER_CHECKER_SELF_APPROVAL_REJECTED`, and `syntheticOnly=true`.
- Loki `count_over_time(... [5m]) > 0` returned value `1` for `compose_service=core-banking-temporal-worker`.
- Loki `/prometheus/api/v1/alerts` returned `TemporalWorkflowFailed` in `firing` state with `severity=warning`, `control=temporal-workflow`, and `synthetic_only=true`.
- Promtail successfully ingested the live banking-lab worker log stream into Loki. It emitted a startup warning for stale unrelated local Docker history; that warning did not affect the live banking-lab stream or alert result.

## Retirement Impact

This closes the local observability readiness, Prometheus scrape, Loki ingestion, Loki ruler alert, and Grafana dashboard validation blockers for the target stack. Node retirement remains blocked until host crash shapes and broader database/process-failure variants, non-synthetic passkey operations, evidence-refresh completion, and final retirement review are complete.
