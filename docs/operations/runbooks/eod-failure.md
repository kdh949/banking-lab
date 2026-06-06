# End-of-day Failure Runbook

Synthetic lab only. Closed dates and postings model bank-grade controls but do not represent real operations.

## Symptoms

- EOD closing remains `FAILED` or `WAITING_APPROVAL`.
- Ledger commands fail against a closed or inconsistent business date.
- `LedgerCommandLatencyHigh` appears during closing load.

## Detection

```sql
SELECT business_date, status, closed_by, closed_at, ledger_total_hash
FROM daily_closings
ORDER BY business_date DESC
LIMIT 10;
```

## Immediate Containment

- Stop synthetic posting jobs for the affected business date.
- Do not reopen a closed date by direct SQL.
- Require maker-checker for EOD closing approval.

## Diagnosis Queries

```sql
SELECT step_id, business_date, step_name, status, error_message, updated_at
FROM eod_closing_steps
ORDER BY updated_at DESC
LIMIT 20;
```

## Recovery Steps

1. Inspect failed EOD step and structured error.
2. Resolve upstream drift, outbox, or parameter issue.
3. Submit a new EOD request with a fresh idempotency key if needed.
4. Approve with an independent checker.
5. Verify closed-date direct posting rejection still holds.

## Evidence To Capture

- EOD request id, approval id, and business date.
- Failed step details.
- Follow-up ledger invariant test command.

## Rollback

Use reversal or adjustment transactions for financial corrections. Do not mutate finalized ledger transactions or balances.

## Escalation

Escalate to ledger and operations owners if EOD blocks projection rebuild or audit export evidence.

## Post-incident Review

Record whether missing EOD state or Temporal workflow coverage caused the failure.
