# Final Retirement Review Verifier

Date: 2026-06-03

Status: blocked

## Scope

This evidence boundary defines the machine-verifiable artifact required before the final Node retirement review gate can pass. It does not prove non-synthetic passkey operations, does not mark Node retirement ready, and does not remove the Node reference oracle.

## Command

After non-synthetic passkey evidence exists and the final review has been performed, record the generated review artifact with:

```bash
npm run retirement:final-review:record
```

Then verify it with:

```bash
npm run retirement:final-review:verify
```

The default artifact path is:

```text
docs/test-evidence/generated/final-node-retirement-review.json
```

## Required Artifact

The final review artifact must have:

- `schemaVersion: 1`;
- `status: "pass"`;
- `evidenceKind: "final-node-retirement-review"`;
- `passkeyEvidenceArtifact: "docs/test-evidence/generated/passkey-non-synthetic-evidence.json"`;
- an empty `remainingBlockers` array;
- passing command evidence for `npm run parity`, `npm test`, `npm run validate:manifests`, `npm run evidence:pack`, `npm run retirement:audit`, `npm run retirement:stack-audit`, `npm run retirement:generated-boundary`, `npm run retirement:ready-simulate`, `npm run passkey:evidence:preflight`, `npm run retirement:review-preflight`, and `npm run passkey:evidence:verify`;
- each command marked as run after passkey evidence was recorded;
- true control attestations for ledger balanced postings, balance projections, idempotency, append-only finalized transactions, reason-required audit, default PII masking, maker-checker separation, workflow durability, outbox durability, balanced reconciliation adjustments, target areas without disallowed stack, ignored generated artifacts, synthetic-only data, and absence of Node-only critical dependencies.

The verifier rejects obvious reusable tokens, cookies, credential IDs, attestation objects, passwords, passkey artifacts, JWTs, and unmasked synthetic phone output.

## Recorder Inputs

The recorder requires:

- `BANKING_LAB_FINAL_REVIEW_CONFIRMED=true`;
- `BANKING_LAB_FINAL_REVIEW_PASSKEY_ARTIFACT_VERIFIED=true`;
- `BANKING_LAB_FINAL_REVIEW_REVIEWER`;
- `BANKING_LAB_FINAL_REVIEW_COMMANDS_FILE`, containing a redacted JSON array of command evidence;
- true environment attestations for every required control, including ledger, idempotency, audit, masking, maker-checker, workflow, outbox, reconciliation, target stack, generated artifact, synthetic-only, and Node-only dependency controls.

Each command evidence array entry must be an object with `command`, `status: "pass"`, `exitCode: 0`, `runAfterPasskeyEvidence: true`, and a non-empty `summary`. The recorder and verifier reject duplicate commands, non-object entries, and any extra command evidence item that is not passing.

## Current Result

Blocked. The default final review artifact intentionally does not exist yet because non-synthetic passkey evidence is still missing and final review has not been performed.

## Retirement Impact

`npm run retirement:review-preflight` runs this verifier while the review gate is pending and requires it to fail against the missing default artifact path. `npm run goal:completion-audit` and `npm run node:retirement-gate` must run this verifier if `retirement-review` is later marked `pass`. This keeps the final retirement claim fail-closed until a concrete review artifact exists and passes schema, command, and control checks.
