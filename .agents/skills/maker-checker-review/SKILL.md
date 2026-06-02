---
name: maker-checker-review
description: Review banking-lab high-risk operations for maker-checker approval, separation of duties, reason-required actions, audit events, workflow visibility, and structured policy errors. Use for approval, complaint, FDS, AML, reconciliation, staff profile change, parameter change, or privileged unmask work.
---

# Maker Checker Review

## Scope

Use when work touches high-risk operations, approvals, privileged staff actions, or workflow state.

Read first:

1. `docs/migration/structured-api-error-contract.md`
2. `docs/architecture/phase-3-staff-terminal.md`
3. `docs/architecture/phase-5-complaint-workflow.md`
4. `docs/architecture/phase-6-fds-aml-reconciliation.md`
5. `tests/makerChecker.test.mjs`
6. Relevant complaint/FDS/AML/reconciliation tests

## Control Checklist

- Maker and checker are different actors.
- Approval requires a role allowed for the business type.
- Self-approval returns `MAKER_CHECKER_SELF_APPROVAL_REJECTED`.
- Missing reason returns `POLICY_REASON_REQUIRED` when the operation is sensitive.
- Approval records include requester, approver, business type, target, reason, decision, timestamps, and audit correlation.
- Customer-visible complaint answer is not sent before approval.
- FDS release posts exactly once after approval; FDS block never posts ledger entries.
- AML closure requires approval and keeps STR simulation synthetic.
- Reconciliation adjustment approval results in balanced adjustment postings only.
- Staff unmask and sensitive lookup are timeboxed, masked by default, and audited.

## Commands

```bash
npm test
npm run parity
```

For target-stack workflow work when available:

```bash
./gradlew :services:core-banking:test
./gradlew :services:core-banking:integrationTest
```

## Output

Lead with approval-control gaps. Include:

- Business types reviewed
- Self-approval and missing-reason behavior
- Audit evidence
- Structured error contract coverage
- Parity scenarios covered
- Coordinator-owned changes requested
