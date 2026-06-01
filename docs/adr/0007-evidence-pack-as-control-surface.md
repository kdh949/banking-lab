# ADR 0007: Evidence Pack as Control Surface

## Status

Accepted

## Context

The lab is meant to demonstrate bank-grade operational control, not only runnable screens. The final artifact must therefore include evidence that the controls exist and can be verified repeatedly.

## Decision

The repository treats evidence as a first-class deliverable:

- phase-specific generated JSON under `docs/test-evidence/generated`
- human-readable test reports under `docs/test-evidence`
- ADRs for material architecture/control decisions
- regulatory and ASVS mappings
- threat models
- reconciliation reports
- failure drill plans and summary report
- demo scripts for portfolio walkthroughs

## Consequences

- New milestones must include tests and evidence.
- Evidence generation is automated where feasible.
- Remaining gaps are explicit: persistence, real auth, durable recovery, observability stack, and formal model checking are future phases.
