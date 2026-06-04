# Posting-Time Limit Enforcement Evidence

Date: 2026-06-04

Implemented and verified the core controls and operating patterns of a banking system in a synthetic lab environment that uses no real financial network and no real customer data.

## Scope

Phase 1A adds posting-time account limit enforcement to the Kotlin/Spring target stack:

- `V020__limit_usage_counters.sql` adds monthly and per-channel limit policy columns to `account_limits`.
- `limit_usage_counters` tracks daily and monthly cumulative usage by `(account_id, channel, period_kind, business_date)`.
- `LedgerCommandService` enforces per-transaction, daily, monthly, and channel-aware limits inside the same `SERIALIZABLE` transaction as posting.
- `LIMIT_EXCEEDED` is returned as a structured ledger error with `limitKind`, `channel`, `configuredMinor`, `attemptedMinor`, and `remainingMinor`.
- Reversal of counted withdrawal/internal-transfer transactions releases usage counters without mutating ledger source rows.

## Commands Run

```bash
npm run formal:ledger
npm run scripts:typecheck
npm run test:core-banking:integration -- --tests lab.banking.core.ledger.application.LedgerCommandServiceIntegrationTest
```

## Result

- `npm run formal:ledger`: pass, bounded state-search explored 3335 states and 8241 transitions across 15 invariants.
- `npm run scripts:typecheck`: pass.
- `npm run test:core-banking:integration -- --tests lab.banking.core.ledger.application.LedgerCommandServiceIntegrationTest`: pass after rerun outside the sandbox because Gradle file-lock socket creation was blocked by sandbox permissions.

## Invariants Verified

- Over-limit withdrawal returns `LIMIT_EXCEEDED`.
- Failed limit checks leave no ledger transaction, idempotency row, outbox event, or usage counter side effect.
- Daily cumulative breach is rejected after successful prior postings.
- Daily counters reset by business date.
- Monthly counters reset by month.
- Concurrent withdrawal burst cannot exceed the configured daily limit.
- Reversal releases counted usage and allows a subsequent transaction within the restored limit.
- Existing ledger invariants still hold: `Σposting=0`, projected balance from postings, idempotency single result, closed date rejection, and no duplicate reversal.

## Synthetic Boundary

All accounts, customers, channels, and limits are synthetic lab data. No real funds, real PII, real payment network, real KYC, or external financial institution API was used.
