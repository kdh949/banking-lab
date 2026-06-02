# ADR 0008: Kotlin and Next.js Migration Through Parity Gates

## Status

Accepted

## Context

The current lab is implemented as a runnable Node.js ES module reference with static app shells. The intended architecture is Kotlin/Spring Boot for backend services and TypeScript/Next.js for frontend apps.

The Node implementation already proves important controls: double-entry ledger behavior, idempotency, audit hash chaining, masking, maker-checker approval, complaint workflow, FDS/AML simulation, reconciliation, screen manifests, and generated evidence.

## Decision

Migrate by parity, not by replacement.

- Keep the Node reference runtime until all mapped Kotlin/Spring and Next.js parity checks pass.
- Add executable migration gates before removing or rewriting reference behavior.
- Use `docs/migration/parity-scenarios.json` to map the 42 current Node scenarios to target backend and frontend tests.
- Use a structured API error contract before porting controllers.
- Treat `docs/migration/node-retirement-gate.json` as the operational block against premature Node deletion.

## Consequences

- The repository temporarily contains both Node reference code and target-stack migration artifacts.
- Kotlin/Spring Boot code must preserve the reference route semantics before OpenAPI or typed clients are treated as stable.
- Next.js screens must remain manifest-driven.
- Node removal is a separate milestone with evidence, not cleanup during the first migration slice.
