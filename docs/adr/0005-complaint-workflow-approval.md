# ADR 0005: Complaint Workflow Approval

## Status

Accepted for the original complaint workflow slice. Superseded for target-path runtime by the Spring complaint module and PostgreSQL-backed workflow/case persistence.

## Current Status

Current target implementation lives under `services/core-banking/src/main/kotlin/lab/banking/core/complaint`, with workflow references and complaint source data backed by Flyway migrations and Spring integration evidence. The legacy Node workflow remains oracle/reference material only.

## Context

Phase 5 requires customer complaint intake, state transitions, owner assignment, answer draft, approval before response, and customer answer confirmation. The customer portal and staff terminal must share the same case source and show SLA/timeline evidence.

## Decision

Use the complaint workflow helper in the current Spring complaint module for target behavior. The original Node helper remains an oracle/reference path, not the target case store.

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

- The original Node case data is in-memory by design because it is only an oracle/reference path.
- Target complaint APIs use Spring security roles and Keycloak/OIDC token paths in current evidence.

## Follow-up

- Keep expanding complaint comments, attachments, and timeline evidence in the Spring target module.
- Add department transfer and reopen flows.
- Add complaint report exports for the final evidence pack.
