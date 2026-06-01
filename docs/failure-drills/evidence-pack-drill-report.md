# Evidence Pack Failure Drill Report

## Summary

The current drill set covers ledger integrity, idempotency, staff auditability, customer transfer states, complaint approval, FDS/AML review, and reconciliation adjustment.

| Drill | Expected evidence |
| --- | --- |
| Duplicate transfer retry | same transfer result, no duplicate ledger posting |
| Concurrent withdrawal | no overdraw, ledger invariants valid |
| Staff lookup without reason | API rejects request |
| Maker self-approval | approval rejects same maker/checker |
| Complaint answer before approval | customer response stays null |
| FDS release before approval | held transfer has no transaction id |
| FDS block | no ledger posting |
| Closed day direct posting | API rejects closed business date |
| Reconciliation adjustment | balanced `ADJUSTMENT` transaction on open date |

## Commands

```bash
npm test
npm run evidence:phase6
npm run evidence:pack
```

## Residual Drill Gaps

- Process crash between approval and execution.
- External simulator timeout/retry.
- Backup and restore.
- Observability alert drill.
- Long-running EOD partial failure.
