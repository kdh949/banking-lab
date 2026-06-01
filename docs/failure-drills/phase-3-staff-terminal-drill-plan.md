# Phase 3 Staff Terminal Failure Drill Plan

## Drill: Sensitive Inquiry Without Reason

- Injection: call customer detail, account inquiry, or transaction inquiry without reason.
- Expected impact: request is rejected and no sensitive data is returned.
- Evidence: `tests/staffTerminal.test.mjs`.

## Drill: Unauthorized Unmask

- Injection: request PII unmask as `BRANCH_STAFF`.
- Expected impact: request is rejected with no unmasked payload.
- Evidence: `tests/staffTerminal.test.mjs`.

## Drill: Maker Applies Customer Change Directly

- Injection: submit a customer information change request and inspect customer data before approval.
- Expected impact: customer data remains unchanged until a different manager approves.
- Evidence: `tests/staffTerminal.test.mjs`.
