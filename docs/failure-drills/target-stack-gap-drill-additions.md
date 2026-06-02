# Target Stack Failure Drill Additions

Review date: 2026-06-02

## Purpose

The existing drill set covers the Node reference flows well. These additions focus on target-stack gaps that must be drilled before Node retirement.

## Drill 1: Spring API Crash After Commit Before Outbox Publish

Failure injected:

- A ledger command commits `ledger_transactions`, `ledger_postings`, `account_balance_projections`, and `outbox_events`.
- The process crashes before any Kafka/Redpanda publish attempt.

Expected evidence:

- Ledger transaction remains balanced and visible after restart.
- Outbox event remains `PENDING`.
- Publisher replay emits the event once.
- Duplicate publisher run does not duplicate downstream consumer side effects.

Required commands:

```bash
./gradlew :services:core-banking:integrationTest --tests '*OutboxRecovery*'
```

Current status: Planned. Outbox row insertion is documented, but recovery publish/replay is not yet proven.

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

## Drill 4: Temporal Worker Restart During Complaint Answer Approval

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
```

Current status: Planned. Node state-machine behavior is the oracle; Temporal or target durable workflow evidence is pending.

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
node scripts/check-qa-evidence-review.mjs
```

Current status: Added as QA recommendation. Existing generated evidence is green for current phases, but it is not yet a full target-stack retirement evidence pack.
