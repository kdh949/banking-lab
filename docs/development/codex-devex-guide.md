# Codex Devex Guide

## Purpose

This repo has project-scoped Codex helpers for repeatable banking-lab work. They do not replace the coordinator plan or the Node reference oracle. They make future review, screen generation, parity checking, and evidence reporting more consistent.

## Skills

Use repo skills by name when the task matches the slice:

- `$ledger-invariant-review`: ledger, posting, idempotency, reversal, adjustment, daily closing, PostgreSQL isolation, and outbox review.
- `$manifest-screen-generator`: manifest-driven inquiry, command, case, and parameter screens.
- `$maker-checker-review`: high-risk operations, separation of duties, privileged access, workflow approval, and audit review.
- `$structured-error-contract-review`: API/domain error shape and stable error family parity.
- `$evidence-pack-builder`: evidence commands, milestone proof, failure drills, and node retirement gate evidence.
- `$migration-parity-review`: Kotlin/Spring and Next.js migration parity against the Node oracle.

The skill files live under `.agents/skills/*/SKILL.md`.

## Agent Examples

Project-scoped example profiles live under `.codex/agents`:

- `banking_explorer.toml`: read-only orientation and ownership discovery.
- `banking_worker.toml`: scoped additive implementation under coordinator-assigned paths.
- `banking_reviewer.toml`: invariant, parity, manifest, and evidence review.
- `banking_security_reviewer.toml`: masking, authorization, audit, synthetic-only, and security evidence review.

Treat these TOML files as launch templates for coordinator-spawned subagents. The coordinator must still provide the actual task, allowed write paths, and branch/worktree context.

## Standard Reporting

Every subagent should return:

- Changed files
- Commands run
- Passing tests
- Failing/skipped tests with reason
- Domain invariants affected
- Security/control impact
- Parity scenarios covered
- Remaining risk
- Coordinator-owned changes requested
- Next smallest safe task

## Guardrails

- Do not expand the Node `.mjs` runtime as the target implementation.
- Do not delete Node reference assets until `docs/migration/node-retirement-gate.json` is ready.
- Do not edit shared root files from a subagent slice. Write requests under `docs/codex/coordination-notes/`.
- Do not mark evidence as passed without running commands or citing existing evidence.
- Keep all examples synthetic.
