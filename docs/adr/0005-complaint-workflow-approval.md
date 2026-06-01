# ADR 0005: Complaint Workflow Approval

## Status

Accepted

## Context

Phase 5 requires customer complaint intake, state transitions, owner assignment, answer draft, approval before response, and customer answer confirmation. The customer portal and staff terminal must share the same case source and show SLA/timeline evidence.

## Decision

Use the complaint workflow helper in `services/complaint-service` and store cases in the shared runtime state.

Controls:

- Intake creates a `RECEIVED` case with SLA due date and timeline.
- Staff transitions are ordered: `RECEIVED -> CLASSIFIED -> ASSIGNED -> IN_REVIEW -> WAITING_APPROVAL`.
- Answer drafts create a `COMPLAINT_ANSWER_SEND` maker-checker approval.
- Only approval execution moves the case to `ANSWERED` and exposes the answer.
- Customer confirmation moves `ANSWERED -> CLOSED`.

## Consequences

Positive:

- Customer and staff channels use one shared complaint case.
- Customer-visible answer cannot bypass approval.
- SLA and timeline are present from case creation.

Tradeoffs:

- Case data is still in-memory until persistence is implemented.
- Approval roles are mock-role based until real session auth is wired.

## Follow-up

- Persist complaint cases, comments, attachments, and timeline.
- Add department transfer and reopen flows.
- Add complaint report exports for the final evidence pack.
