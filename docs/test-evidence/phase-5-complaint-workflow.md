# Phase 5 Complaint Workflow Test Evidence

## Acceptance Checks

- Customer and staff channels share the same complaint case.
- Complaint case has SLA and timeline.
- Staff can classify, assign, start review, and draft answer.
- Answer is not visible before maker-checker approval.
- Manager approval sends the answer.
- Customer can confirm the answer and close the case.
- Complaint workflow manifests cover customer and staff views.

## Commands

```bash
npm test
npm run validate:manifests
npm run evidence:phase5
```

Generated machine-readable evidence is written to `docs/test-evidence/generated/phase-5-complaint-workflow.json`.
