# OpenTelemetry Trace Log Correlation Smoke

Review date: 2026-06-03

## Scope

This smoke verifies the target Spring Boot service emits an OpenTelemetry trace to Tempo and logs the same trace ID for the same synthetic HTTP request.

It covers the `core-banking` HTTP `/health` path. Temporal workflow trace/log correlation is covered separately in `docs/test-evidence/temporal-workflow-trace-log-correlation.md`. This document does not claim Loki ingestion, alert routing, or dashboard validation.

## Commands

```bash
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.observability.ObservabilityActuatorIntegrationTest

scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar

env COMPOSE_PROJECT_NAME=banking-lab-otel-smoke \
  BANKING_LAB_POSTGRES_PORT=15484 \
  BANKING_LAB_CORE_BANKING_PORT=18134 \
  BANKING_LAB_TEMPO_PORT=13201 \
  BANKING_LAB_TEMPO_OTLP_GRPC_PORT=14319 \
  BANKING_LAB_TEMPO_OTLP_HTTP_PORT=14320 \
  docker compose --profile platform up -d --build postgres tempo core-banking

curl --retry 30 --retry-delay 2 --retry-connrefused -fsS \
  -H 'x-request-id: REQ-OTEL-CORRELATION-001' \
  http://127.0.0.1:18134/health

env COMPOSE_PROJECT_NAME=banking-lab-otel-smoke \
  docker compose logs --no-color core-banking | \
  rg 'REQ-OTEL-CORRELATION-001|observability\.access'

curl -v --retry 3 --retry-delay 1 --retry-all-errors -fsS \
  http://127.0.0.1:13201/api/traces/2e139b9fee0dc5d1f2c1e782257bc1a5

env COMPOSE_PROJECT_NAME=banking-lab-otel-smoke docker compose --profile platform down -v
```

## Result

Passed.

- `ObservabilityActuatorIntegrationTest` passed and asserts the access log includes:
  - `observability.access`
  - `requestId=REQ-OTEL-CORRELATION`
  - a 32-character lowercase hex `traceId`
  - a 16-character lowercase hex `spanId`
- Live `/health` returned `status=ok`, `syntheticOnly=true`, `auditHashChainValid=true`, and `migrationTarget=kotlin-spring-boot`.
- The Spring log line for `REQ-OTEL-CORRELATION-001` included:
  - `traceId=2e139b9fee0dc5d1f2c1e782257bc1a5`
  - `spanId=6f03c105739aa354`
  - `path=/health`
  - `status=200`
- Tempo `/api/traces/2e139b9fee0dc5d1f2c1e782257bc1a5` returned `200` and included:
  - `service.name=banking-lab-core-banking`
  - `synthetic.only=true`
  - span name `http get /health`
  - span status `200`
  - URI attribute `/health`
- The temporary Compose stack was removed with `docker compose --profile platform down -v`.

## Retirement Impact

This closes basic OpenTelemetry trace/log correlation evidence for the Spring `core-banking` HTTP path. Node retirement remains blocked until host crash shapes, API process crash after durable ledger/outbox commit, outbox tracing, non-synthetic passkey operations, evidence-refresh completion, and final retirement review are complete.
