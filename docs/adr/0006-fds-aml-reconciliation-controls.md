# ADR 0006: FDS, AML, and Reconciliation Controls

## Status

Accepted

## Context

Phase 6 introduces operational risk workflows around customer transfers and daily closing. These workflows must not bypass the ledger invariants from earlier phases.

## Decision

- Customer transfers that hit FDS rules are stored as `HELD` transfer results and do not create ledger postings.
- FDS release and block decisions are high-risk operations and require maker-checker approval.
- FDS release posts the original transfer only after checker approval using a dedicated release idempotency key.
- AML cases are generated from synthetic customer risk and transaction signals, then closed through maker-checker approval.
- EOD reconciliation closes the business date after ledger invariant validation.
- Reconciliation adjustments are posted only as balanced `ADJUSTMENT` ledger transactions on an open business date.

## Consequences

- Customer-visible transfer status and FDS case status remain consistent.
- Closed-day direct mutation is rejected by the same ledger guard used for normal posting commands.
- Reconciliation corrections preserve auditability because no balance is directly mutated.
- The implementation is still in-memory; persistence and replay recovery remain future work.
