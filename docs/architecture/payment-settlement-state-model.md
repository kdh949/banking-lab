# Payment, Ledger, and Settlement State Model

This document distinguishes the held-transfer channel journey from the repository's primary payment-settlement slice. No state below claims real external fund movement or settlement finality.

## Internal customer transfer

```text
REQUESTED
  ├─ low synthetic risk -> POSTED
  ├─ high synthetic risk -> HELD -> INVESTIGATING
  │                          ├─ RELEASE_REQUESTED -> POSTED
  │                          └─ BLOCK_REQUESTED   -> BLOCKED
  └─ validation failure -> FAILED
```

- `POSTED` means one balanced internal ledger transaction exists.
- `HELD`, `FAILED`, and `BLOCKED` mean no ledger transaction exists for the transfer.
- `journeyId` correlates channel references but does not change ledger or settlement state.

## Bill-payment settlement portfolio slice

```text
payment instruction
  -> LEDGER_POSTED
  -> independent clearing CSV line
  -> INCLUDED_IN_BATCH
  -> three-way reconciliation
  -> MATCHED or owned exception
```

- `LEDGER_POSTED` means Core Banking accepted a balanced synthetic transaction.
- `INCLUDED_IN_BATCH` means an accepted independent external line contributed to a calculated position.
- Neither state means an external institution moved money or final settlement occurred.

## Cross-model rules

1. A held internal transfer cannot appear as a posted ledger transaction, settlement line, or settlement batch item.
2. A released internal transfer is complete at internal ledger posting for this channel scenario; it is not retroactively labeled externally settled.
3. Payment settlement reconciliation keeps payment, ledger, and independent clearing sources separate.
4. Journey projection rows, channel status rows, and reconciliation rows are read models/control records, never balance sources of truth.
5. Corrections use reversal or balanced adjustment transactions on an open business date; finalized ledger rows are immutable.
