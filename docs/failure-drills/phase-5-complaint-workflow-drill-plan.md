# Phase 5 Complaint Workflow Failure Drill Plan

## Drill: Answer Without Approval

- Injection: draft a complaint answer and inspect the case before approval.
- Expected impact: case remains `WAITING_APPROVAL` and `answer` remains null.
- Evidence: `tests/complaintWorkflow.test.mjs`.

## Drill: Maker Self-Approval

- Injection: complaint handler attempts to approve their own answer draft.
- Expected impact: approval is rejected.
- Evidence: `tests/complaintWorkflow.test.mjs`.

## Drill: Customer Closure

- Injection: customer confirms an answered complaint.
- Expected impact: case transitions to `CLOSED` and timeline records closure.
- Evidence: `tests/complaintWorkflow.test.mjs`.
