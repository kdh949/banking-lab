# Node Retirement Review

Date: 2026-06-03

Status: blocked

## Scope

This document tracks the final Node retirement review gate. The review must confirm that ledger, idempotency, audit, masking, maker-checker, workflow, reconciliation, and evidence behavior no longer depends on Node-only code.

This document does not mark Node retirement ready.

## Current Preflight

Run:

```bash
npm run retirement:review-preflight
```

The preflight combines the current retirement boundary audit, stack retirement area audit, evidence-refresh check, passkey evidence preflight, and retirement gate check. It verifies that target source directories stay free of legacy Node/runtime dependencies by implementation area, current evidence is refreshed, current mapped parity remains target-backed, and the retirement gate is blocked only by non-synthetic passkey operations and final retirement review.

Run the area-specific stack audit directly when reviewing the "no disallowed stack by area" requirement:

```bash
npm run retirement:stack-audit
```

Run the generated artifact boundary audit when `.next` or build output exists locally:

```bash
npm run retirement:generated-boundary
```

Run the completion audit before any final completion claim:

```bash
npm run goal:completion-audit
```

After real passkey evidence is recorded and before this review is marked pass, run:

```bash
npm run passkey:evidence:verify
```

That verifier must pass against `docs/test-evidence/generated/passkey-non-synthetic-evidence.json`; a missing artifact is still a blocker.

After the final reviewer reruns the required post-passkey commands and records the control decisions, verify the generated final review artifact:

```bash
npm run retirement:final-review:verify
```

That verifier must pass against `docs/test-evidence/generated/final-node-retirement-review.json`; a missing artifact is still a blocker if `retirement-review` is marked pass.

## Current Result

Not ready. The final review cannot be marked passed until:

- non-synthetic passkey operations are proven with a real platform authenticator or hardware security key;
- `docs/test-evidence/generated/passkey-non-synthetic-evidence.json` exists and passes `npm run passkey:evidence:verify` plus the retirement gate validation;
- `docs/test-evidence/generated/final-node-retirement-review.json` exists and passes `npm run retirement:final-review:verify`;
- the final reviewer reruns the full parity, manifest, evidence, retirement boundary, stack area, generated artifact boundary, and retirement gate commands after passkey evidence exists;
- `npm run goal:completion-audit -- --require-complete` passes after all retirement blockers are resolved;
- the final reviewer confirms no critical behavior depends on Node-only code.

## Controls To Reconfirm In Final Review

- Ledger movements are represented by balanced target-stack postings.
- Balances remain projections from postings.
- Externally retried commands remain idempotent.
- Finalized transactions are append-only and corrected by reversal or adjustment.
- Staff data access requires a reason and writes audit events.
- PII is masked by default.
- High-risk operations require maker-checker separation.
- Workflow state is durable in target-stack storage and Temporal/state-machine evidence.
- Events use durable outbox persistence before publication.
- Reconciliation corrections use balanced adjustments.
- Target `apps/`, `services/`, `packages/`, `analytics/`, `infra/`, `contracts/`, and `db/` do not depend on legacy Node runtime or `.mjs` reference code outside the approved oracle/support paths; generated `.next`/`build` output remains ignored and untracked.

## Retirement Impact

Node retirement remains blocked by non-synthetic passkey operations and final retirement review. The Node reference runtime must remain available as the oracle until the gate becomes ready.
