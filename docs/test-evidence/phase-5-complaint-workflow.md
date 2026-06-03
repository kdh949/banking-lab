# Phase 5 Complaint Workflow Test Evidence

## Acceptance Checks

- Customer and staff channels share the same complaint case.
- Complaint case has SLA and timeline.
- Spring customer complaint list API returns the same case visible to staff and appends masked customer self-service view audit.
- Staff can classify, assign, start review, and draft answer.
- Answer is not visible before maker-checker approval.
- Manager approval sends the answer.
- Customer can confirm the answer and close the case.
- Complaint workflow manifests cover customer and staff views.
- Spring customer confirmation API closes an answered case with `customer_confirmed_at`, `CLOSED` timeline, and customer audit evidence.

## Commands

```bash
npm test
npm run validate:manifests
npm run evidence:phase5
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test --tests 'lab.banking.core.complaint.ComplaintWorkflowParityTest'
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.complaint.CustomerComplaintEntryApiParityIntegrationTest' --tests 'lab.banking.core.complaint.ComplaintCaseApiParityIntegrationTest' --tests 'lab.banking.core.complaint.CustomerComplaintConfirmApiParityIntegrationTest'
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18099 npm run test:e2e
```

Generated machine-readable evidence is written to `docs/test-evidence/generated/phase-5-complaint-workflow.json`.
