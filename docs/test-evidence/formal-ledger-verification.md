# Formal Ledger Verification Evidence

Date: 2026-06-05

## Command

```bash
npm run formal:ledger
```

## Result

Pass. The command produced `docs/test-evidence/generated/formal-ledger-tlc-result.json` with `staticOnly: false`.

`npm run formal:ledger` found the supplied local `~/Downloads/tla2tools.jar` through `scripts/check-tla-model.ts`, resolved Java to the local Homebrew OpenJDK 21 executable, and ran actual TLC for both root models:

- `/opt/homebrew/opt/openjdk@21/bin/java -cp /Users/donghyunkim/Downloads/tla2tools.jar tlc2.TLC -config Ledger.cfg Ledger.tla`
- `/opt/homebrew/opt/openjdk@21/bin/java -cp /Users/donghyunkim/Downloads/tla2tools.jar tlc2.TLC -config Idempotency.cfg Idempotency.tla`

Both TLC runs passed. The command also ran the built-in bounded state-search checker, which explored 3335 states and 8241 transitions across the ledger and idempotency models.

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

This is synthetic formal evidence for a finite lab model. It does not prove real banking production correctness. Static-only mode is explicitly blocked for CI and milestone evidence; it is only available for local diagnostics with `BANKING_LAB_ALLOW_FORMAL_STATIC_ONLY=true BANKING_LAB_FORMAL_ENGINE=static`. The TLC runner may be supplied as a `tlc` executable, `BANKING_LAB_TLC_CMD`, `BANKING_LAB_TLC_JAR`, a repo-local `tools/tla2tools.jar` or `formal/tla2tools.jar`, or the local developer download path `~/Downloads/tla2tools.jar`. Java for TLC jar execution may be supplied by `BANKING_LAB_JAVA_CMD`, `JAVA_HOME`, or the local OpenJDK fallback.
