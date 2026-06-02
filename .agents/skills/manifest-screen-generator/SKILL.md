---
name: manifest-screen-generator
description: Generate or review banking-lab screen manifest definitions and reusable screen infrastructure for inquiry, command, case, and parameter templates. Use when adding staff, customer, complaint, audit, ops, FDS/AML, or admin screens without hand-coding one-off UI behavior.
---

# Manifest Screen Generator

## Scope

Use for manifest-driven screens in `/Users/donghyunkim/Documents/banking-lab`.

Read first:

1. `docs/codex/parallel-subagent-implementation-plan.md`
2. `docs/architecture/phase-3-staff-terminal.md`
3. `docs/architecture/phase-4-customer-web.md`
4. `docs/architecture/phase-5-complaint-workflow.md`
5. `docs/architecture/phase-6-fds-aml-reconciliation.md`
6. Existing `screen-manifests/**` and `scripts/validate-manifests.mjs`

## Required Templates

Use these templates rather than screen-specific UI forks:

- Inquiry: search, result table, detail panel, masking, reason, audit.
- Command: target lookup, before/after snapshot, reason, validation, approval, audit.
- Case: status, owner, SLA, comments, attachments, timeline, approval.
- Parameter: current value, scheduled value, effective date, approval, rollback.

## Manifest Rules

- Declare roles and authorization metadata.
- Declare audit events for sensitive lookup and command actions.
- Mark PII fields masked by default.
- Require business reason for staff customer/account/transaction lookup.
- Attach maker-checker metadata for high-risk operations.
- Attach workflow metadata for complaint, FDS, AML, and reconciliation cases.
- Keep screen definitions synthetic; do not add real bank product names, real PII, or real external network integrations.
- Expand manifests additively and avoid copy-paste screens that bypass templates.

## Commands

```bash
npm run validate:manifests
npm test
```

When Next shells are affected and dependencies exist:

```bash
npm run next:customer-web:typecheck
npm run next:customer-web:build
```

## Output

Return:

- Manifest files changed or reviewed
- Template coverage
- Authorization, audit, masking, workflow, and approval metadata coverage
- Validation commands run
- Parity scenarios covered
- Remaining renderer or coordinator-owned needs
