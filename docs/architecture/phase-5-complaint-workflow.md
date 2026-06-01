# Phase 5 Complaint Workflow Architecture

## Workflow

```text
Customer portal intake
  -> RECEIVED case with SLA
  -> Staff classify
  -> Staff assign owner
  -> Staff start review
  -> Staff draft answer
  -> Maker-checker approval
  -> ANSWERED
  -> Customer confirmation
  -> CLOSED
```

## Runtime APIs

- `POST /api/complaints`
- `GET /api/complaints`
- `GET /api/complaints/{caseId}`
- `GET /api/staff/complaints`
- `POST /api/staff/complaints/{caseId}/classify`
- `POST /api/staff/complaints/{caseId}/assign`
- `POST /api/staff/complaints/{caseId}/start-review`
- `POST /api/staff/complaints/{caseId}/answer-drafts`
- `POST /api/staff/approvals/{approvalId}/approve`
- `POST /api/customer/complaints/{caseId}/confirm`

## Control Points

- SLA is set at intake.
- Timeline records workflow transitions and actions.
- Answer draft does not set customer-visible answer.
- `COMPLAINT_ANSWER_SEND` approval is required before answer send.
- Customer and staff views read the same `complaints` case collection.
