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

The preflight combines the current retirement boundary audit, evidence-refresh check, passkey evidence preflight, and retirement gate check. It verifies that target source directories stay free of legacy Node/runtime dependencies, current evidence is refreshed, current mapped parity remains target-backed, and the retirement gate is blocked only by non-synthetic passkey operations and final retirement review.

After real passkey evidence is recorded and before this review is marked pass, run:

```bash
npm run passkey:evidence:verify
```

That verifier must pass against `docs/test-evidence/generated/passkey-non-synthetic-evidence.json`; a missing artifact is still a blocker.

## Current Result

Not ready. The final review cannot be marked passed until:

- non-synthetic passkey operations are proven with a real platform authenticator or hardware security key;
- `docs/test-evidence/generated/passkey-non-synthetic-evidence.json` exists and passes `npm run passkey:evidence:verify` plus the retirement gate validation;
- the final reviewer reruns the full parity, manifest, evidence, retirement boundary, and retirement gate commands after passkey evidence exists;
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
- Target `apps/`, `services/`, and `packages/` do not depend on legacy Node runtime or `.mjs` reference code.

## Retirement Impact

Node retirement remains blocked by non-synthetic passkey operations and final retirement review. The Node reference runtime must remain available as the oracle until the gate becomes ready.
