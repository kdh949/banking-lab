# Formal Ledger Model

## Artifacts

- `formal/tla/Ledger.tla`
- `formal/tla/Ledger.cfg`
- `formal/Ledger.tla`
- `formal/Ledger.cfg`
- `formal/Idempotency.tla`
- `formal/Idempotency.cfg`
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
- held or failed transfers that must not create ledger postings;
- adjustment postings that require an approved business reference;
- retry behavior for idempotency keys.

## Invariants

The static check requires these invariant names in the model/config:

- `BalancedDoubleEntry`
- `IdempotencySingleBusinessResult`
- `AvailableBalanceNonNegative`
- `ReversalReferencesOriginal`
- `ReversalMirrorsOriginal`
- `ClosedDateNoDirectPosting`
- `BalanceProjectionRecalculable`
- `HeldOrFailedCommandNoPosting`
- `AdjustmentRequiresApprovalReference`
- `SingleBusinessResultPerKey`
- `RetryReturnsSameBusinessResult`
- `NoDuplicateSideEffectForRetry`
- `FailedOrHeldCommandNoPosting`

## Execution

Run:

```bash
npm run formal:ledger
```

If a `tlc` command is available, the script attempts TLC for both root models. If TLC is not installed, the script runs a deterministic bounded state-search checker over the finite ledger and idempotency state space and writes `docs/test-evidence/generated/formal-ledger-tlc-result.json`.

Static-only mode is available only for local diagnostics:

```bash
BANKING_LAB_ALLOW_FORMAL_STATIC_ONLY=true BANKING_LAB_FORMAL_ENGINE=static npm run formal:ledger
```

CI must not use that override, and static-only output is not accepted as milestone evidence.
