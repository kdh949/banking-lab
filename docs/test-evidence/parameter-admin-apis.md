# Parameter Admin API Evidence

Review date: 2026-06-04

## Scope

Phase 4 converts the manifest-only parameter screens into API-backed command surfaces:

- `OPS-301` reconciliation parameters;
- `AUD-201` audit retention parameters;
- `FDS-301` FDS rule parameters;
- `ADM-201` security policy parameters;
- `ADM-301` menu/role parameters.

All values are synthetic lab controls. No real security policy, real PII, real fraud-network rule feed, external financial institution API, or real payment network integration is used.

## Implemented Target Evidence

- `db/migrations/V024__parameter_admin_controls.sql` creates one `*_parameters` and one `*_parameter_versions` table pair for each required domain.
- `parameter_change_requests` records maker requests with idempotency key, rollback plan, effective date, approval ID, and applied version ID.
- `ParameterAdminService` exposes current/scheduled values, history, maker change requests, approval-backed version insertion, rejection, and rollback-as-new-version.
- `ParameterAdminController` implements the existing manifest endpoints for `OPS-301`, `AUD-201`, `FDS-301`, `ADM-201`, and `ADM-301`.
- `StaffAccessService` applies/rejects the new parameter business types through the existing maker-checker approval route.
- `CustomerTransferService` reads `fds.highAmountMinor` from approved effective-dated FDS parameters instead of a static constant.
- `packages/api-client` has typed parameter DTOs and endpoint methods for all five domains.

## Commands Run

```bash
npm run test:core-banking:integration -- --tests lab.banking.core.parameters.ParameterAdminIntegrationTest
npm run packages:typecheck
npm run validate:manifests
npm run test:screen-engine
npm run scripts:typecheck
```

## Result

- `ParameterAdminIntegrationTest`: passed after sandbox escalation for Gradle file-lock socket access.
- First run failed at Kotlin compile because companion object values exposed a private config type; fixed by making those values private.
- Second run failed because a test fixture column name did not match `V020` account-limit columns; fixed to use `customer_web_*` limit columns.
- Third run failed because `TRUNCATE ... CASCADE` removed seed parameter versions through approval FKs; fixed by using ordered `DELETE` reset.
- `npm run packages:typecheck`: passed.
- `npm run validate:manifests`: passed with 87 manifests.
- `npm run test:screen-engine`: passed.
- `npm run scripts:typecheck`: passed.

## Invariants Verified

- All five parameter namespaces expose current and history read APIs with audit events.
- Parameter change requests create pending maker-checker approvals and do not apply a version before approval.
- Unauthorized customer actor is rejected before parameter mutation.
- Maker cannot approve their own FDS rule parameter change.
- Approved future effective date is visible as scheduled and does not change current-day FDS behavior.
- Approved current-day FDS threshold change changes transfer behavior from posted to held for the same synthetic amount.
- Rollback restores a previous parameter value by inserting a new approved version.
- FDS held transfer path creates no unsafe ledger posting; posted transfers remain balanced.
- Parameter audit events are appended and marked synthetic-only.

## Remaining Risk

This slice proves the common parameter workflow and the FDS high-amount rule integration. Future hardening should add UI smoke buttons per ops/audit/FDS/admin app, richer validation for structured JSON menu-role parameters, and Temporal/activity evidence if parameter application is later delegated to scheduled workers.
