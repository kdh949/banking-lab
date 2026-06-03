# Phase 3 Staff Terminal Test Evidence

## Acceptance Checks

- Staff customer detail rejects missing reason and returns masked data when reason is present.
- Staff PII unmask rejects insufficient role and succeeds for a manager with reason.
- Staff account inquiry and transaction inquiry require reason and create audit events.
- Customer information change request does not mutate customer data immediately.
- Manager approval applies the customer change and records execution audit evidence.
- Missing reason, unauthorized unmask, and maker self-approval denials do not create audit or approval side effects before the valid checker path.
- Staff terminal manifests cover inquiry, command, approval, and audit screens.

## Commands

```bash
npm test
npm run validate:manifests
npm run evidence:phase3
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.staff.StaffAccessApiParityIntegrationTest'
```

## 2026-06-03 Target Stack Result

- Initial sandboxed Gradle execution failed before test execution because Gradle could not create its local file-lock socket.
- `scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.staff.StaffAccessApiParityIntegrationTest'` passed under the approved execution path.

Generated machine-readable evidence is written to `docs/test-evidence/generated/phase-3-staff-terminal.json`.
