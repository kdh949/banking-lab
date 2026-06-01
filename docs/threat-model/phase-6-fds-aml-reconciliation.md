# Phase 6 Threat Model

## Assets

- Ledger transactions and postings
- FDS case decisions
- AML case disposition
- Reconciliation adjustment approvals
- Audit hash chain

## Abuse Cases

| Abuse case | Control |
| --- | --- |
| Reviewer releases own FDS hold | Maker-checker rejects same maker/checker |
| Held transfer posts before review | Held transfer result has no transaction id until approval execution |
| Blocked transfer mutates balance | Block path does not call ledger posting |
| AML case closed without approval | `AML_CASE_CLOSE` is high-risk approval type |
| Closed-day correction mutates old ledger date | Ledger core rejects closed business date |
| Reconciliation balance patched directly | Adjustment uses balanced `ADJUSTMENT` transaction |

## Residual Risk

The current runtime is in-memory. A later persistence phase needs durable case storage, recovery-safe approval execution, and replay protection across process restarts.
