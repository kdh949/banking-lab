# ADR 0002: Ledger Core Command Service

## Status

Accepted

## Context

Phase 2 requires customer/account/ledger/posting/balance behavior plus deposit, withdrawal, transfer, idempotency, reversal, and invariant tests. The Phase 1 code had ledger primitives, but command behavior was spread across runtime handlers.

## Decision

Introduce `LedgerCore` in `services/core-banking/src/ledgerCore.mjs`.

The service owns:

- Customer and account registry access.
- Deposit, withdrawal, internal transfer, and reversal commands.
- Idempotency replay for externally retried ledger commands.
- Serialized command execution for the in-memory runtime.
- Sufficient available balance checks.
- Closed business day mutation guard.
- Invariant validation after each successful command.

The service still uses the domain primitives from `packages/banking-domain` for transaction construction and balance projection.

## Consequences

Positive:

- Runtime handlers now delegate ledger rules to one command path.
- Concurrent withdrawal tests can assert no overdraw.
- Reversal and idempotency semantics are testable outside HTTP.

Tradeoffs:

- The Phase 2 command lock is intentionally conservative and serializes all in-memory ledger commands.
- Persistence remains an adapter concern for a later slice.

## Follow-up

- Add PostgreSQL transaction boundaries and row-level locking.
- Add property-based random command sequences.
- Add account hold and available-balance integration.
