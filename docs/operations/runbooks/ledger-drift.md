# Ledger Drift Runbook

Synthetic lab only. Do not use this for real customer balances or real money.

## Symptoms

- `LedgerCommandErrorRateHigh` or ledger projection drift alerts fire.
- Staff/ops screens show projection mismatch or rebuild request pending.
- Balance certificates disagree with posting-derived balances.

## Detection

Run:

```sql
SELECT run_id, status, drift_item_count, requested_at
FROM ledger_projection_drift_runs
ORDER BY requested_at DESC
LIMIT 10;
```

## Immediate Containment

- Stop nonessential synthetic posting load.
- Keep direct ledger table edits prohibited.
- Require maker-checker approval before any rebuild or adjustment.

## Diagnosis Queries

```sql
SELECT account_id, currency, expected_ledger_balance_minor, actual_ledger_balance_minor, drift_amount_minor
FROM ledger_projection_drift_items
WHERE status = 'DRIFT_DETECTED'
ORDER BY ABS(drift_amount_minor) DESC;
```

## Recovery Steps

1. Start a reason-required drift run.
2. If drift is confirmed, request a ledger projection rebuild.
3. Approve with a different checker.
4. Execute the rebuild with an idempotency key.
5. Re-run drift detection.

## Evidence To Capture

- Drift run id and rebuild request id.
- Approval id and checker identity.
- Before/after drift item counts.
- `ledgerRowsMutated=false` for projection rebuilds.

## Rollback

Projection rebuilds are derived from postings. If a rebuild is wrong, rerun from immutable postings after fixing the projection query or migration. Do not edit posted ledger rows.

## Escalation

Escalate to the ledger owner if drift remains after rebuild or if postings are unbalanced.

## Post-incident Review

Record root cause, exact commands run, skipped commands, and whether an invariant or contract test should be added.
