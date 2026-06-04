# Formal Ledger Verification Evidence

Date: 2026-06-04

## Command

```bash
npm run formal:ledger
```

## Result

Pass. The command produced `docs/test-evidence/generated/formal-ledger-tlc-result.json` with `staticOnly: false`.

Local `tlc` was not installed in this environment, so the command used the built-in bounded state-search checker instead of accepting a static artifact fallback. The checker explored 3335 states and 8241 transitions across the ledger and idempotency models.

## Invariants Checked

- `BalancedDoubleEntry`
- `IdempotencySingleBusinessResult`
- `AvailableBalanceNonNegative`
- `ReversalReferencesOriginal`
- `ReversalMirrorsOriginal`
- `ClosedDateNoDirectPosting`
- `BalanceProjectionRecalculable`
- `HeldOrFailedCommandNoPosting`
- `AdjustmentRequiresApprovalReference`
- `LimitUsageWithinConfigured`
- `LimitUsageMatchesPostedDebits`
- `SingleBusinessResultPerKey`
- `RetryReturnsSameBusinessResult`
- `NoDuplicateSideEffectForRetry`
- `FailedOrHeldCommandNoPosting`

## Control Boundary

This is synthetic formal evidence for a finite lab model. It does not prove real banking production correctness. Static-only mode is explicitly blocked for CI and milestone evidence; it is only available for local diagnostics with `BANKING_LAB_ALLOW_FORMAL_STATIC_ONLY=true BANKING_LAB_FORMAL_ENGINE=static`.
