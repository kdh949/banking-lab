# Goal Completion Audit

Date: 2026-06-03

Status: blocked

## Scope

This evidence checks whether the active goal can be truthfully marked complete. It is intentionally stricter than the current migration evidence checks: all mapped parity and target-stack area checks must pass, all retirement gates must pass, the real non-synthetic passkey artifact must exist and verify, and the Node retirement gate must be ready.

This document does not mark Node retirement ready.

## Commands

```bash
npm run goal:completion-audit
```

Use this failure-closed mode before any final completion claim:

```bash
npm run goal:completion-audit -- --require-complete
```

## Current Result

`Goal completion audit: not complete`

Passing items:

- Area-based stack retirement audit is passing.
- Generated artifact boundary audit is passing.
- The parity scenario map covers 42 mapped Node reference scenarios with target `pass` status.
- Current target-stack evidence gates for Spring health, structured errors, Next manifest rendering, API-backed channel parity, and evidence refresh are passing.
- The Node retirement gate now requires strict final-review artifact recording and verification if `retirement-review` is later marked `pass`.

Failure-closed checks:

- If `non-synthetic-passkey-operations` becomes `pass`, the audit also runs the strict passkey artifact verifier before accepting that requirement.
- The audit reads `npm run node:retirement-gate` output and accepts completion only when it reports `ready`.

Blocked items:

- `non-synthetic-passkey-operations` is still pending and `docs/test-evidence/generated/passkey-non-synthetic-evidence.json` is not committed.
- `retirement-review` is still pending.
- `docs/migration/node-retirement-gate.json` remains `blocked`, not `ready`.

## Retirement Impact

The audit prevents the active objective from being marked complete while passkey evidence and final retirement review are missing. It preserves the approved Node oracle/support paths until the retirement gate is ready.
