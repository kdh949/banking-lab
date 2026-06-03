# Temporal Workflow Trace Log Correlation Smoke

Review date: 2026-06-03

## Scope

This smoke verifies that the Spring Boot `core-banking-temporal-worker` emits OpenTelemetry spans to Tempo and logs matching trace/span IDs for Temporal workflow signal, completion, rejection, and failure events.

It covers the current synthetic banking workflow case types:

- `COMPLAINT_ANSWER`
- `FDS_RELEASE`
- `FDS_BLOCK`
- `AML_CLOSURE`
- `RECONCILIATION_ADJUSTMENT`
- `ACCOUNT_HOLD`
- `ACCOUNT_RELEASE`

The integration test also covers an `AML_CLOSURE` rejection path and an `FDS_RELEASE` self-approval failure path, asserting both logs carry trace/span IDs, final status, control effect, workflow metadata, business reference ID, error type, and `syntheticOnly=true`.

This evidence does not claim external alert routing, every workflow transition, host crash-shape coverage, or propagation from a browser/API request into the Temporal workflow trace. Loki ingestion, local alert evaluation, and dashboard provisioning are covered separately in `docs/test-evidence/observability-stack-smoke.md`; API post-commit crash coverage is recorded separately in `docs/test-evidence/api-process-crash-drill.md`; outbox worker trace/log correlation is recorded separately in `docs/test-evidence/outbox-trace-log-correlation.md`.

## Commands

Targeted integration test:

```bash
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest \
  --tests lab.banking.core.observability.TemporalWorkflowTraceLogIntegrationTest
```

Compile/env-gated live test coverage:

```bash
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest \
  --tests lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest \
  --tests lab.banking.core.observability.TemporalWorkflowTraceLogIntegrationTest
```

Build the worker artifact:

```bash
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:bootJar
```

Start an isolated Temporal/Tempo worker stack:

```bash
env COMPOSE_PROJECT_NAME=banking-lab-workflow-all-trace-smoke \
  BANKING_LAB_POSTGRES_PORT=15487 \
  BANKING_LAB_TEMPORAL_PORT=17238 \
  BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-all-trace-smoke \
  BANKING_LAB_TEMPO_PORT=13203 \
  BANKING_LAB_TEMPO_OTLP_GRPC_PORT=14321 \
  BANKING_LAB_TEMPO_OTLP_HTTP_PORT=14322 \
  BANKING_LAB_TRACING_ENABLED=true \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=true \
  docker compose --profile platform up -d --build postgres temporal tempo core-banking-temporal-worker
```

Run the live workflow smoke against the worker task queue:

```bash
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17238 \
  BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-all-trace-smoke \
  scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal worker executes all approval workflow case types from server task queue'
```

Run the live rejection/failure transition smoke:

```bash
env COMPOSE_PROJECT_NAME=banking-lab-workflow-transition-trace-smoke \
  BANKING_LAB_POSTGRES_PORT=15488 \
  BANKING_LAB_TEMPORAL_PORT=17239 \
  BANKING_LAB_TEMPORAL_TASK_QUEUE=banking-case-workflows-transition-trace-smoke \
  BANKING_LAB_TEMPO_PORT=13204 \
  BANKING_LAB_TEMPO_OTLP_GRPC_PORT=14323 \
  BANKING_LAB_TEMPO_OTLP_HTTP_PORT=14324 \
  BANKING_LAB_TRACING_ENABLED=true \
  BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=true \
  docker compose --profile platform up -d --build postgres temporal tempo core-banking-temporal-worker

env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17239 \
  BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-transition-trace-smoke \
  scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal worker executes rejection and failure transitions from server task queue'
```

Capture workflow logs and Tempo traces:

```bash
env COMPOSE_PROJECT_NAME=banking-lab-workflow-all-trace-smoke \
  docker compose logs --no-color core-banking-temporal-worker | \
  rg 'observability\.workflow|LIVE-TRACE'

curl --retry 12 --retry-delay 2 --retry-all-errors -fsS \
  http://127.0.0.1:13203/ready

curl -fsS http://127.0.0.1:13203/api/traces/{traceId}

env COMPOSE_PROJECT_NAME=banking-lab-workflow-all-trace-smoke docker compose --profile platform down -v
```

## Result

Passed.

- `TemporalWorkflowTraceLogIntegrationTest` passed and asserts Temporal workflow logs include:
  - `observability.workflow`
  - `event=signal`
  - `event=completed`
  - all current `TemporalBankingCaseType` values
  - a business reference ID
  - a 32-character lowercase hex `traceId`
  - a 16-character lowercase hex `spanId`
  - `syntheticOnly=true`
- The same integration test asserts an `AML_CLOSURE` rejection log includes `finalStatus=REJECTED` and `controlEffect=NO_EFFECT`.
- The same integration test asserts an `FDS_RELEASE` self-approval failure log includes `finalStatus=FAILED`, `controlEffect=NO_EFFECT`, and `errorType=MAKER_CHECKER_SELF_APPROVAL_REJECTED`.
- `LiveTemporalWorkerSmokeIntegrationTest.live Temporal worker executes all approval workflow case types from server task queue` passed against the Compose Temporal server and `core-banking-temporal-worker` container.
- `LiveTemporalWorkerSmokeIntegrationTest.live Temporal worker executes rejection and failure transitions from server task queue` passed against the Compose Temporal server and `core-banking-temporal-worker` container.
- Live worker logs included signal and completion logs for all seven current case types on task queue `banking-case-workflows-all-trace-smoke`.
- Tempo `/api/traces/{traceId}` returned `200` for all seven signal traces and all seven completion traces.
- Live worker logs included `AML_CLOSURE` rejection signal/completion logs and `FDS_RELEASE` self-approval signal/failed logs on task queue `banking-case-workflows-transition-trace-smoke`.
- Tempo `/api/traces/{traceId}` returned `200` for the rejection signal, rejection completion, self-approval signal, and failed workflow traces.

## Live Trace Evidence

| Case type | Signal trace ID | Completion trace ID | Completion control effect |
| --- | --- | --- | --- |
| `COMPLAINT_ANSWER` | `d17687258d4e19173b675fbf4b0b5652` | `d3f8b68c03e1875b629164d2998749a9` | `CUSTOMER_ANSWER_VISIBLE` |
| `FDS_RELEASE` | `98e8f3d2a68a0f13e7ce76cae4597d47` | `3b162a4fa85a0f512ad64866202f207a` | `LEDGER_TRANSFER_HANDOFF` |
| `FDS_BLOCK` | `691c706826171e95c9bcb8b4ca269a3d` | `6781259cfd11fbf65370a2b574ecb68d` | `NO_LEDGER_POSTING` |
| `AML_CLOSURE` | `8f1ea02fd90afda9c69f4e94001a26c3` | `7db27258c1b71ea296ad313c4901e02d` | `STR_SIMULATION_CLOSURE` |
| `RECONCILIATION_ADJUSTMENT` | `3406cf0701772e46917ea7f52d4b7605` | `76af4add718218d0b22a029c8afe00d6` | `BALANCED_ADJUSTMENT_HANDOFF` |
| `ACCOUNT_HOLD` | `17fcea68f627e7d490d32ca10f35e50b` | `4baf4a3416dc3ffc70911a9ccaa0b2e6` | `AVAILABLE_BALANCE_HOLD` |
| `ACCOUNT_RELEASE` | `26b89ca94f64fdf43eac6c31feb3801b` | `48ab56369523c3290a60a294870631cc` | `HOLD_RELEASE_HANDOFF` |

Every returned Tempo trace included:

- `service.name=banking-lab-core-banking-temporal-worker`
- `synthetic.only=true`
- span name `banking-lab.temporal.workflow.signal` or `banking-lab.temporal.workflow.completed`
- `temporal.workflow.event=signal` or `completed`
- `temporal.workflow.task_queue=banking-case-workflows-all-trace-smoke`
- `banking.business_reference_id=LIVE-TRACE-{CASE_TYPE}-001`
- `banking.case_type={CASE_TYPE}`
- `banking.control_effect=SIGNAL_ACCEPTED` for signal spans
- the expected completion `banking.control_effect` shown above

The temporary Compose stack was removed with `docker compose --profile platform down -v`.

## Live Rejection And Failure Transition Evidence

| Transition | Event trace ID | Case type | Business reference ID | Control effect | Error type |
| --- | --- | --- | --- | --- | --- |
| rejection signal | `cc0bb522adfe3e1c7dc57437d9903b0d` | `AML_CLOSURE` | `LIVE-TRACE-AML_REJECT-001` | `SIGNAL_ACCEPTED` | `none` |
| rejection completion | `55c37e0a68cc077e005d2b945446349c` | `AML_CLOSURE` | `LIVE-TRACE-AML_REJECT-001` | `NO_EFFECT` | `none` |
| self-approval signal | `1371b87e45d8c9acea56e32aba64d610` | `FDS_RELEASE` | `LIVE-TRACE-FDS_SELF_APPROVAL-001` | `SIGNAL_ACCEPTED` | `none` |
| failed workflow | `9ba170bdb6881d0246477a0c603fd8cf` | `FDS_RELEASE` | `LIVE-TRACE-FDS_SELF_APPROVAL-001` | `NO_EFFECT` | `MAKER_CHECKER_SELF_APPROVAL_REJECTED` |

Every returned transition trace included:

- `service.name=banking-lab-core-banking-temporal-worker`
- `synthetic.only=true`
- span name `banking-lab.temporal.workflow.signal`, `banking-lab.temporal.workflow.completed`, or `banking-lab.temporal.workflow.failed`
- `temporal.workflow.event=signal`, `completed`, or `failed`
- `temporal.workflow.task_queue=banking-case-workflows-transition-trace-smoke`
- `banking.business_reference_id`
- `banking.case_type`
- `banking.control_effect`
- `banking.error_type`

The temporary transition Compose stack was removed with `docker compose --profile platform down -v`.

## Retirement Impact

This closes workflow-specific trace/log correlation for Temporal signal and approval-completion events across the current banking case types, plus live rejection and self-approval failure transitions. Node retirement remains blocked until host crash shapes, non-synthetic passkey operations, evidence-refresh completion, and final retirement review are complete.
