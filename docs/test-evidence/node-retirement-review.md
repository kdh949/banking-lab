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

The preflight combines the current retirement boundary audit, stack retirement area audit, generated artifact boundary audit, evidence-refresh check, passkey evidence preflight, strict final retirement review verifier, completion audit, ready-state simulation, and retirement gate check. It verifies that target source directories stay free of legacy Node/runtime dependencies by implementation area, generated output is not treated as target source, current evidence is refreshed, current mapped parity remains target-backed, the final review verifier fails closed while the generated artifact is missing, and the retirement gate is blocked only by non-synthetic passkey operations and final retirement review.

Run the area-specific stack audit directly when reviewing the "no disallowed stack by area" requirement:

```bash
npm run retirement:stack-audit
```

Run the generated artifact boundary audit when `.next` or build output exists locally:

```bash
npm run retirement:generated-boundary
```

Run the ready-state simulation to verify the future passkey/final-review artifact path without changing the real blocked gate:

```bash
npm run retirement:ready-simulate
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

Prepare the final review command evidence template before rerunning the post-passkey review commands:

```bash
npm run retirement:final-review:prepare
```

This writes ignored local templates under `tmp/final-retirement-review-manual/`:

- `redacted-final-review-commands.template.json`
- `record-final-review-command.template.sh`

The generated template keeps `status`, `exitCode`, `runAfterPasskeyEvidence`, and control attestations as `TODO_REPLACE_WITH_*` values. These TODO values must be replaced only after the reviewer reruns the required commands after verified non-synthetic passkey evidence exists; unchanged templates are rejected by the final review recorder and do not mark Node retirement ready.

After the final reviewer reruns the required post-passkey commands and records the control decisions, generate the final review artifact:

```bash
npm run retirement:final-review:record
```

Then verify the generated final review artifact:

```bash
npm run retirement:final-review:verify
```

That verifier must pass against `docs/test-evidence/generated/final-node-retirement-review.json`; a missing artifact is still a blocker if `retirement-review` is marked pass.

While `retirement-review` is still pending, the preflight also runs the strict final retirement review verifier and requires it to fail against the missing default artifact path. This proves the verifier is executable and fail-closed before any future ready claim.

## Current Result

Not ready. The final review cannot be marked passed until:

- non-synthetic passkey operations are proven with a real platform authenticator or hardware security key;
- `docs/test-evidence/generated/passkey-non-synthetic-evidence.json` exists and passes `npm run passkey:evidence:verify` plus the retirement gate validation;
- `docs/test-evidence/generated/final-node-retirement-review.json` exists and passes `npm run retirement:final-review:verify`;
- the final reviewer reruns the full parity, manifest, evidence, retirement boundary, stack area, generated artifact boundary, ready-state simulation, passkey preflight, final-review preflight, and passkey verifier commands after passkey evidence exists;
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
