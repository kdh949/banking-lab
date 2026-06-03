# Workflow Cases Kotlin Port

Date: 2026-06-02

## Scope

Agent B added Kotlin state-machine coverage for the workflow/case controls currently proven by:

- `tests/makerChecker.test.mjs`
- `tests/complaintWorkflow.test.mjs`
- `tests/fdsAmlReconciliation.test.mjs`

The Node runtime remains the executable oracle until Spring controllers, persistence repositories, and parity tests are wired end to end.

## Implemented Controls

- Maker-checker high-risk approval submission, rejection of self-approval, and command audit event capture.
- Complaint answer drafting with `COMPLAINT_ANSWER_SEND` approval before customer-visible answer release.
- Customer complaint confirmation after answer release.
- FDS held transfer release/block approval lifecycle.
- AML high-risk customer case investigation, comment, closure approval, and synthetic STR disposition flag.
- Reconciliation mismatch ownership and approved adjustment command handoff for an open business date.

## Structured Error Semantics

The Kotlin workflows throw `BankingLabDomainException` with stable contract codes:

- `POLICY_REASON_REQUIRED`
- `REQUEST_VALIDATION_FAILED`
- `RESOURCE_NOT_FOUND`
- `MAKER_CHECKER_SELF_APPROVAL_REJECTED`
- `WORKFLOW_STATE_VIOLATION`

These map to `docs/migration/structured-api-error-contract.md` and preserve Node reference semantics for the assigned workflow slice.

## Persistence Readiness

`db/migrations/V007__workflow_case_lifecycle.sql` adds:

- `complaint_cases`
- `complaint_case_timeline`
- `fds_case_timeline`
- `aml_case_comments`
- `reconciliation_adjustment_requests`

FDS, AML, and reconciliation base tables remain owned by the existing V005 migration. Ledger posting for approved FDS release and reconciliation adjustment remains owned by the ledger service; this slice emits `InternalTransferCommand` and `AdjustmentCommand` handoff objects instead of posting directly.

## Temporal Contract Readiness

`BankingCaseTemporalWorkflow` adds a Temporal SDK workflow contract for:

- complaint answer approval;
- FDS release and block;
- AML closure;
- reconciliation adjustment;
- account hold;
- account release.

The workflow waits in `WAITING_APPROVAL` until it receives an approval or rejection signal, rejects maker self-approval, and returns the expected target-stack control effect for each case type.

## Live Temporal Worker Readiness

`BankingCaseTemporalWorker` is now a property-gated Spring Boot `SmartLifecycle` component. When `BANKING_LAB_TEMPORAL_WORKER_ENABLED=true`, it connects to the configured Temporal target and registers `BankingCaseTemporalWorkflowImpl` on `banking-case-workflows`.

`db/migrations/V010__temporal_workflow_references.sql` persists `temporal_workflow_id` and `temporal_run_id` for complaint, FDS, AML, reconciliation adjustment, and account hold records. `TemporalWorkflowReferenceService` attaches those references and fails with the structured `RESOURCE_NOT_FOUND` family when the business record does not exist.

`TemporalWorkflowTraceLogger` wraps the Spring-managed Temporal worker workflow implementation so signal, completion, and rejection events emit `observability.workflow` logs with trace/span IDs and OpenTelemetry spans when tracing is enabled.

## Evidence

Reference oracle command:

```bash
node --test tests/makerChecker.test.mjs tests/complaintWorkflow.test.mjs tests/fdsAmlReconciliation.test.mjs
```

Result with local listen permission: 10 passing, 0 failing.

Kotlin host command:

```bash
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.temporal.BankingCaseTemporalWorkflowIntegrationTest
```

Result: passed with Temporal `TestWorkflowEnvironment`.

Live Temporal command:

```bash
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17233 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest
```

Result: passed against Docker Compose Temporal server plus the Spring Boot `core-banking-temporal-worker` container.

Live worker restart drill command:

```bash
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17235 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal workflow survives worker restart before approval completion'
```

Result: passed against a live Docker Compose Temporal server using a unique task queue. The first SDK worker reached `WAITING_APPROVAL`, shut down, a checker approval signal was accepted while no worker was polling, and a second SDK worker completed the workflow from Temporal history.

Live Compose worker container restart drill command:

```bash
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17236 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-container-drill BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-container-drill scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal workflow survives Compose worker container restart before approval completion'
```

Result: passed against a live Docker Compose Temporal server plus the `core-banking-temporal-worker` container. The worker container was killed after `WAITING_APPROVAL`, a checker approval signal was accepted while the worker container was down, the worker service was restarted on the same task queue, and the workflow completed from Temporal history.

Live FDS Compose worker container restart drill command:

```bash
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17241 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-fds-container-drill BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-fds-container-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live FDS release workflow survives Compose worker container restart before approval completion'
```

Result: passed against a live Docker Compose Temporal server plus the `core-banking-temporal-worker` container. The worker container was killed after the FDS release workflow reached `WAITING_APPROVAL`, a checker approval signal was accepted while the worker container was down, the worker service restarted on the same task queue, and the workflow completed with `controlEffect=LEDGER_TRANSFER_HANDOFF` from Temporal history.

Live AML/reconciliation Compose worker container restart drill command:

```bash
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17242 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-ops-container-drill BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-ops-container-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live AML closure workflow survives Compose worker container restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live reconciliation adjustment workflow survives Compose worker container restart before approval completion'
```

Result: passed against a live Docker Compose Temporal server plus the `core-banking-temporal-worker` container. The worker container was killed and restarted for each path after `WAITING_APPROVAL`, checker approval signals were accepted while the worker container was down, and the workflows completed from Temporal history with `controlEffect=STR_SIMULATION_CLOSURE` for `AML_CLOSURE` and `controlEffect=BALANCED_ADJUSTMENT_HANDOFF` for `RECONCILIATION_ADJUSTMENT`.

Live FDS block/account hold/account release Compose worker container restart drill command:

```bash
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17243 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-hold-container-drill BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-hold-container-drill BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live FDS block workflow survives Compose worker container restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live account hold workflow survives Compose worker container restart before approval completion' --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live account release workflow survives Compose worker container restart before approval completion'
```

Result: passed against a live Docker Compose Temporal server plus the `core-banking-temporal-worker` container. The worker container was killed and restarted for each path after `WAITING_APPROVAL`, checker approval signals were accepted while the worker container was down, and the workflows completed from Temporal history with `controlEffect=NO_LEDGER_POSTING` for `FDS_BLOCK`, `controlEffect=AVAILABLE_BALANCE_HOLD` for `ACCOUNT_HOLD`, and `controlEffect=HOLD_RELEASE_HANDOFF` for `ACCOUNT_RELEASE`.

Live Temporal server container restart drill command:

```bash
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17244 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-server-restart-drill BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-server-restart-drill BANKING_LAB_POSTGRES_PORT=15493 BANKING_LAB_TEMPORAL_PORT=17244 BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal workflow survives Compose Temporal server restart before approval completion'
```

Result: passed against a live Docker Compose Temporal server plus the `core-banking-temporal-worker` container. The complaint-answer workflow reached `WAITING_APPROVAL`, the `temporal` container was killed and restarted on the same host port, Temporal health returned `SERVING`, the worker resumed after transient `UNAVAILABLE` poller warnings, checker approval was accepted, and the workflow completed from Temporal history with `controlEffect=CUSTOMER_ANSWER_VISIBLE`.

Live PostgreSQL container restart drill command:

```bash
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17245 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-postgres-restart-drill BANKING_LAB_LIVE_TEMPORAL_COMPOSE_PROJECT=banking-lab-temporal-postgres-restart-drill BANKING_LAB_POSTGRES_PORT=15494 BANKING_LAB_TEMPORAL_PORT=17245 BANKING_LAB_TRACING_ENABLED=false BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal workflow survives Compose PostgreSQL restart before approval completion'
```

Result: passed against a live Docker Compose Temporal server plus the `core-banking-temporal-worker` container. The complaint-answer workflow reached `WAITING_APPROVAL`, the `postgres` container was killed and restarted on the same host port and volume, PostgreSQL reported automatic recovery and returned to accepting connections, Temporal recovered from transient database connection errors, checker approval was accepted, and the workflow completed from Temporal history with `controlEffect=CUSTOMER_ANSWER_VISIBLE`.

Temporal workflow trace/log command:

```bash
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.observability.TemporalWorkflowTraceLogIntegrationTest
```

Result: passed. The test proves workflow signal and completion logs for complaint answer, FDS release/block, AML closure, reconciliation adjustment, account hold, and account release carry 32-character trace IDs, 16-character span IDs, Temporal workflow metadata, business reference ID, case type, control effect, and `syntheticOnly=true`. It also proves an AML closure rejection log carries `finalStatus=REJECTED` and `controlEffect=NO_EFFECT`.

Live all-case trace/log command:

```bash
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17238 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-all-trace-smoke scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal worker executes all approval workflow case types from server task queue'
```

Result: passed against a live Docker Compose Temporal server plus the `core-banking-temporal-worker` container. Worker logs and Tempo `/api/traces/{traceId}` responses proved signal and completion spans for all current case types on task queue `banking-case-workflows-all-trace-smoke`.

Live rejection/failure trace/log command:

```bash
env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17239 BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows-transition-trace-smoke scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal worker executes rejection and failure transitions from server task queue'
```

Result: passed against a live Docker Compose Temporal server plus the `core-banking-temporal-worker` container. Worker logs and Tempo `/api/traces/{traceId}` responses proved `AML_CLOSURE` rejection signal/completion spans and an `FDS_RELEASE` self-approval failed span with `banking.error_type=MAKER_CHECKER_SELF_APPROVAL_REJECTED` on task queue `banking-case-workflows-transition-trace-smoke`.

Persistence command:

```bash
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests lab.banking.core.temporal.TemporalWorkflowReferencePersistenceIntegrationTest
```

Result: passed for complaint, FDS, AML, reconciliation adjustment, and account hold workflow reference persistence.

## Remaining Integration Work

- Broaden workflow/container failure drills beyond the current worker/server/PostgreSQL restart paths into host crash shapes and additional database/process-failure variants; Loki ingestion, local alert evaluation, and dashboard provisioning are covered in `docs/test-evidence/observability-stack-smoke.md`.
- Add API-backed channel tests that start and observe these workflows.
