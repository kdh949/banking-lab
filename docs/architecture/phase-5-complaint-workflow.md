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

- `POST /api/customer/complaints`
- `GET /api/customer/complaints`
- `POST /api/customer/complaints/{caseId}/confirm`
- `POST /api/customer/complaints/{caseId}/materials`
- `POST /api/customer/complaints/{caseId}/reopen-requests`
- `GET /api/customer/complaint-types`
- `GET /api/staff/complaints`
- `GET /api/staff/complaints/{caseId}`
- `POST /api/staff/complaints/{caseId}/classify`
- `POST /api/staff/complaints/{caseId}/assign`
- `POST /api/staff/complaints/{caseId}/start-review`
- `POST /api/staff/complaints/{caseId}/answer-drafts`
- `POST /api/staff/approvals/{approvalId}/approve`

`POST /api/customer/complaints` supports optional `sourceReference` metadata for
`TRANSFER_DISPUTE` and `CARD_DISPUTE`. Transfer disputes may reference a
customer-owned `CUSTOMER_TRANSFER` result or `LEDGER_TRANSACTION`; card disputes
may reference a customer-owned `CARD_AUTHORIZATION` or `CARD_CAPTURE`. The
reference is persisted in `complaint_cases.source_reference_json` with
`syntheticOnly=true`; it does not mutate the source transfer, card, or ledger
rows. Any refund, reversal, or adjustment still goes through the existing
balanced ledger correction and maker-checker approval paths.

## Control Points

- SLA is set at intake.
- Timeline records workflow transitions and actions.
- Answer draft does not set customer-visible answer.
- `COMPLAINT_ANSWER_SEND` approval is required before answer send.
- Customer and staff views read the same `complaints` case collection.
- Customer complaint list appends self-service `COMPLAINT_VIEW` audit without storing complaint descriptions in audit payload.
- Customer additional-material submission stores synthetic attachment metadata only,
  appends `MATERIAL_SUBMITTED` timeline, and never stores real attachment bytes.
- Customer reopen requests are allowed only for closed complaints, create durable
  reopen-request metadata, append `REOPEN_REQUESTED` timeline, and move the case
  to `REOPENED`.
- Complaint type guide is a synthetic static catalog used by CMP-107 and does not
  call an external complaint intake or regulatory system.

## Legacy Reference Boundary

The Node reference still exposes `/api/complaints` for oracle tests. The target Spring APIs split customer and staff complaint routes under `/api/customer/complaints` and `/api/staff/complaints` with Keycloak/OIDC ownership and role controls.
