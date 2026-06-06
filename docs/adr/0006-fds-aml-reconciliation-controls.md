# ADR 0006: FDS, AML, and Reconciliation Controls

## Status

Accepted for the original Phase 6 controls. Superseded for target-path runtime by the Spring FDS, AML, reconciliation, approval, workflow, Temporal, and PostgreSQL/Flyway implementation.

## Current Status

Current target implementation lives under `services/core-banking/src/main/kotlin/lab/banking/core/{fds,aml,reconciliation,workflow,temporal}` with PostgreSQL-backed state, maker-checker approvals, Temporal workflow references, and API/channel evidence. The legacy Node implementation remains oracle/reference material only.

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
- The original Node implementation remains in-memory only as an oracle/reference path. Target FDS, AML, reconciliation, approvals, workflow references, and ledger effects are PostgreSQL/Flyway-backed in current evidence.
