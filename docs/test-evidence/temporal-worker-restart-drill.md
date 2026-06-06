# Temporal Worker Restart Drill

Review date: 2026-06-03

## Scope

This drill verifies that a live Temporal workflow survives worker replacement before checker approval is completed. It uses an external Temporal server and a unique task queue, starts one SDK worker, shuts it down after the workflow reaches `WAITING_APPROVAL`, sends the approval signal while no worker is polling, starts a second SDK worker on the same task queue, and verifies completion through Temporal history replay.

This proves Temporal SDK worker restart continuity for the synthetic complaint-answer workflow. Docker Compose container restart is covered separately in `docs/test-evidence/temporal-container-worker-restart-drill.md`. This document does not claim host crash recovery, alerting, or trace/log correlation.

## Command

```bash
env COMPOSE_PROJECT_NAME=banking-lab-temporal-restart-smoke \
  BANKING_LAB_POSTGRES_PORT=15482 \
  BANKING_LAB_TEMPORAL_PORT=17235 \
  docker compose --profile platform up -d postgres temporal

env BANKING_LAB_LIVE_TEMPORAL_TARGET=127.0.0.1:17235 \
  BANKING_LAB_LIVE_TEMPORAL_TASK_QUEUE=banking-case-workflows \
  scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest \
  --tests 'lab.banking.core.temporal.LiveTemporalWorkerSmokeIntegrationTest.live Temporal workflow survives worker restart before approval completion'
```

## Result

Passed.

- The workflow started on a unique restart task queue.
- The first SDK worker processed the workflow to `WAITING_APPROVAL`.
- The first worker factory was shut down before approval completion.
- The approval signal was accepted while no worker was polling.
- The second SDK worker resumed the workflow and completed it.
- The result preserved maker-checker control effect:
  - `finalStatus=COMPLETED`
  - `caseType=COMPLAINT_ANSWER`
  - `controlEffect=CUSTOMER_ANSWER_VISIBLE`
  - `syntheticOnly=true`
- The replayed checkpoint sequence was:
  - `COMPLAINT_ANSWER:STARTED`
  - `COMPLAINT_ANSWER:WAITING_APPROVAL`
  - `COMPLAINT_ANSWER:COMPLETED`

## Retirement Impact

This closes the live Temporal SDK worker restart drill for the current synthetic workflow contract. Node retirement is now ready for the current synthetic lab scope; this evidence slice remains scoped to its named control and the Node reference stays archived oracle/reference material.
