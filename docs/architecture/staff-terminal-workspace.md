# Staff Terminal Workspace

## What Changed

The staff terminal now starts as a transaction-code workspace rather than a static manifest card page.

Implemented behavior:

- transaction code and screen-name search;
- search results from the manifest catalog;
- tab creation for opened business screens;
- tab switching without leaving the workstation;
- customer context rail;
- reason-required panels;
- inquiry search/result/detail rendering;
- command forms, validation messages, action status, and approval panel;
- case status, owner, SLA, comments, approval, and timeline rendering;
- parameter current/scheduled/history/rollback rendering;
- masking state display;
- structured error contract display;
- API-backed `APR001` approval inbox rendering for list, selection, approval execution, and related audit events;
- API-backed `AUD001` audit log rendering for event list, event selection, and hash-chain status display.

## Control Model

The workstation reads `ScreenManifest` metadata and renders controls consistently:

- `audit.reasonRequired` drives the business-reason panel.
- `audit.maskingPolicy` and `audit.piiAccess` drive masked PII display.
- `approval.required` and `approval.makerChecker` drive maker-checker panels.
- `workflow.states` drives the case timeline.
- `api.command` and `query.endpoint` drive API-backed versus declared-only status.

Declared-only screens remain visible but cannot be represented as successful execution.

## API-backed Smoke Continuity

The existing staff API smoke component remains in the workstation dashboard and continues to call Spring APIs through `@banking-lab/api-client` when `NEXT_PUBLIC_BANKING_API_BASE_URL` is configured.

Covered existing flows:

- masked staff customer lookup;
- privileged PII unmask;
- customer information change approval;
- Keycloak staff/checker propagation when configured.

The `APR001` and `AUD001` tabs now also use the shared `@banking-lab/api-client` inside the manifest workspace instead of remaining declared-only screens. `APR001` uses the real approval list/detail/approve API and reads audit events after execution; `AUD001` uses the real audit-event API and keeps hash-chain status visible to auditors.
