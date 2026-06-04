# Formal Ledger Model

## Artifacts

- `formal/tla/Ledger.tla`
- `formal/tla/Ledger.cfg`
- `scripts/check-tla-model.ts`

## Modeled Concepts

The TLA+ artifact models:

- accounts;
- transactions;
- postings;
- debit/credit posting sides;
- balance projection from postings;
- idempotency keys;
- reversals;
- closed business dates;
- held or failed transfers that must not create ledger postings.

## Invariants

The static check requires these invariant names in the model/config:

- `BalancedDoubleEntry`
- `IdempotencySingleBusinessResult`
- `ReversalReferencesOriginal`
- `ClosedDateNoDirectPosting`
- `BalanceProjectionRecalculable`
- `HeldOrFailedTransferNoPosting`

## Execution

Run:

```bash
npm run formal:ledger
```

If a `tlc` command is available, the script attempts `tlc -config Ledger.cfg Ledger.tla`. If TLC is not installed, the script performs a repository-level static artifact check and records TLC as skipped. A skipped TLC run is not formal verification completion.
