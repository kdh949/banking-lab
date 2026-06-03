# ADR 0002: Ledger Core Command Service

## Status

Accepted

## Context

Phase 2 requires customer/account/ledger/posting/balance behavior plus deposit, withdrawal, transfer, idempotency, reversal, and invariant tests. The Phase 1 code had ledger primitives, but command behavior was spread across runtime handlers.

## Decision

Introduce the Node reference `LedgerCore` under `legacy-node-reference/services/core-banking/src/ledgerCore.mjs` and keep the target core-banking implementation in Kotlin/Spring under `services/core-banking/src/main/kotlin`.

The service owns:

- Customer and account registry access.
- Deposit, withdrawal, internal transfer, and reversal commands.
- Idempotency replay for externally retried ledger commands.
- Serialized command execution for the in-memory runtime.
- Sufficient available balance checks.
- Closed business day mutation guard.
- Invariant validation after each successful command.

The Node reference still uses the domain primitives from `legacy-node-reference/packages/banking-domain` for transaction construction and balance projection. The target Spring service uses PostgreSQL/Flyway-backed ledger command paths and must not add Node business modules under `services/`.

## Consequences

Positive:

- Runtime handlers now delegate legacy oracle ledger rules to one command path.
- Concurrent withdrawal tests can assert no overdraw.
- Reversal and idempotency semantics are testable outside HTTP.

Tradeoffs:

- The Phase 2 command lock is intentionally conservative and serializes all in-memory ledger commands.
- The Node reference remains in-memory until retirement; persistence is handled by the Spring target service.

## Follow-up

- Keep PostgreSQL transaction boundaries and row-level locking in the Spring target service.
- Add property-based random command sequences.
- Add account hold and available-balance integration.
