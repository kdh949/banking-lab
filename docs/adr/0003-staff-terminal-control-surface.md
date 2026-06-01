# ADR 0003: Staff Terminal Control Surface

## Status

Accepted

## Context

Phase 3 requires the staff terminal to behave like a banking integrated terminal, not a generic admin page. Staff access to customer/account/transaction data must require a reason and produce audit events. Customer information changes must follow maker-checker approval before mutation.

## Decision

Extend the runtime with staff-specific APIs and keep every staff screen represented as a manifest.

Implemented controls:

- Customer detail view requires reason and returns masked PII.
- PII unmask requires privileged role and reason, returns a timeboxed response, and emits `PII_UNMASK_REQUESTED`.
- Account and transaction inquiry require reason and emit `ACCOUNT_VIEW` and `TRANSACTION_VIEW`.
- Customer information change creates a `CUSTOMER_INFO_CHANGE` approval request.
- Manager approval applies the customer change and emits `COMMAND_APPROVED` and `COMMAND_EXECUTED`.

## Consequences

Positive:

- Sensitive staff workflows now have explicit audit evidence.
- Customer changes cannot mutate state at request time.
- New staff screens continue to scale through manifests.

Tradeoffs:

- Runtime auth remains mock auth; role checks are request-payload based for now.
- Unmask approval is simplified to privileged-role approval rather than a separate approval workflow.

## Follow-up

- Bind actor identity to session tokens instead of request payloads.
- Add masking access log persistence when the database adapter is introduced.
- Add real tab navigation backed directly by manifest-rendered form definitions.
