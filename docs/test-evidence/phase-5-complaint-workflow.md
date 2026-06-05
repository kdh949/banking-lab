# Phase 5 Complaint Workflow Test Evidence

## Acceptance Checks

- Customer and staff channels share the same complaint case.
- Complaint case has SLA and timeline.
- Spring customer complaint list API returns the same case visible to staff and appends masked customer self-service view audit.
- Staff can classify, assign, start review, and draft answer.
- Answer is not visible before maker-checker approval.
- Manager approval sends the answer.
- Customer can confirm the answer and close the case.
- Customer can submit synthetic additional-material metadata for an active
  complaint, which appends `MATERIAL_SUBMITTED` timeline and CMP-103 audit
  without storing real attachment bytes.
- Customer can request reopen for a closed complaint, which creates durable
  reopen metadata, appends `REOPEN_REQUESTED` timeline, and moves the case to
  `REOPENED`.
- Customer can read the CMP-107 complaint type guide through
  `GET /api/customer/complaint-types`.
- Complaint workflow manifests cover customer and staff views.
- Spring customer confirmation API closes an answered case with `customer_confirmed_at`, `CLOSED` timeline, and customer audit evidence.

## Commands

```bash
npm test
npm run validate:manifests
npm run evidence:phase5
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:test --tests 'lab.banking.core.complaint.ComplaintWorkflowParityTest'
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.complaint.CustomerComplaintEntryApiParityIntegrationTest' --tests 'lab.banking.core.complaint.ComplaintCaseApiParityIntegrationTest' --tests 'lab.banking.core.complaint.CustomerComplaintConfirmApiParityIntegrationTest'
npm run test:core-banking:integration -- --tests lab.banking.core.complaint.CustomerComplaintSelfServiceApiIntegrationTest --rerun-tasks
npm run test:core-banking:integration -- --tests 'lab.banking.core.complaint.*' --rerun-tasks
npm run next:complaint-portal:typecheck
npm --workspace @banking-lab/api-client run typecheck
npm run packages:typecheck
npm run test:e2e -- apps/complaint-portal/e2e/complaint-portal-parity.spec.ts
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18099 npm run test:e2e
```

Generated machine-readable evidence is written to `docs/test-evidence/generated/phase-5-complaint-workflow.json`.

## Latest Local Result

- `npm run test:core-banking:integration -- --tests lab.banking.core.complaint.CustomerComplaintSelfServiceApiIntegrationTest --rerun-tasks`:
  first sandboxed run failed because Gradle could not create its file-lock socket;
  rerun after sandbox escalation passed against PostgreSQL Testcontainers.
- `npm run test:core-banking:integration -- --tests 'lab.banking.core.complaint.*' --rerun-tasks`:
  pass after sandbox escalation; existing complaint entry, answer approval,
  confirmation, and new self-service extension integration tests passed together.
- `npm run next:complaint-portal:typecheck`: pass.
- `npm --workspace @banking-lab/api-client run typecheck`: pass.
- `npm run validate:manifests`: pass with 95 manifests.
- `npm run packages:typecheck`: pass.
- `npm test`: pass with 154 tests after static complaint self-service scaffold
  coverage.
- `npm run test:e2e -- apps/complaint-portal/e2e/complaint-portal-parity.spec.ts`:
  pass; 2 manifest/shell tests passed and 5 API/Keycloak live smokes skipped
  because API/Keycloak E2E base URLs were not configured.

## New Self-Service Coverage

`CustomerComplaintSelfServiceApiIntegrationTest` verifies:

- CMP-107 complaint type guide returns synthetic categories, SLA hours, and
  required-material hints;
- CMP-103 material submission requires customer ownership, rejects non-synthetic
  storage references, stores only metadata, updates a `WAITING_CUSTOMER` case to
  `IN_REVIEW`, appends `MATERIAL_SUBMITTED` timeline, and writes CMP-103 audit
  without the free-form material description in the audit payload;
- CMP-106 reopen requires a closed complaint and customer ownership, creates a
  durable reopen request, appends `REOPEN_REQUESTED` timeline, writes CMP-106
  audit, and moves the case to `REOPENED`.

## Remaining Risk

This slice covers customer self-service material/reopen/type-guide APIs and
screen-panel wiring. Staff-side reopen triage, attachment virus scanning
simulation, card/transfer dispute-specific financial correction linkage, and
live API/Keycloak browser execution for the new self-service smoke remain future
work.
