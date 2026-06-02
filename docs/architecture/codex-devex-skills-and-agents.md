# Codex Devex Skills and Agents

## Runtime Boundary

This slice adds developer-experience metadata only:

```text
Coordinator
  -> repo-scoped skills under .agents/skills
  -> project agent examples under .codex/agents
  -> local index in .codex/config.toml
  -> developer guide under docs/development
```

It does not change runtime behavior, tests, root package scripts, source implementation files, generated evidence, or migration gates.

## Skill Coverage

- `ledger-invariant-review` covers double-entry postings, projected balances, idempotency, reversals, adjustments, closed days, isolation tests, and outbox persistence.
- `manifest-screen-generator` covers reusable inquiry, command, case, and parameter screen definitions with authorization, audit, masking, workflow, and approval metadata.
- `maker-checker-review` covers separation of duties, business reasons, privileged unmasking, complaint answer approval, FDS/AML actions, and reconciliation adjustment approval.
- `structured-error-contract-review` covers stable error response shape and required error families.
- `evidence-pack-builder` covers proof commands, skipped-test reporting, milestone evidence, and node retirement gate discipline.
- `migration-parity-review` covers the 42 Node reference scenarios and the Kotlin/Next target parity gate.

## Agent Examples

The `.codex/agents/*.toml` files are project examples for future coordinator-spawned subagents:

- `banking_explorer`: read-heavy discovery.
- `banking_worker`: scoped additive implementation.
- `banking_reviewer`: invariant and parity review.
- `banking_security_reviewer`: security/control review.

## Controls Preserved

- Node reference runtime remains required until the retirement gate is ready.
- Shared root and implementation files stay coordinator-owned.
- Skills require evidence to cite commands actually run.
- All workflows keep synthetic-only boundaries.
