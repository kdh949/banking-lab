# Parameter Admin API Evidence

Review date: 2026-06-06

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
- `ParameterAdminService` validates `ADM-301` structured authorization parameters before creating approvals: `roleMenuMap` and `approvalRoleMatrix` accept JSON object or delimited role maps, while `reasonRequiredScreens` accepts JSON arrays or comma-delimited screen IDs and rejects duplicates.
- `ParameterAdminController` implements the existing manifest endpoints for `OPS-301`, `AUD-201`, `FDS-301`, `ADM-201`, and `ADM-301`.
- `StaffAccessService` applies/rejects the new parameter business types through the existing maker-checker approval route.
- `CustomerTransferService` reads `fds.highAmountMinor` from approved effective-dated FDS parameters instead of a static constant.
- `packages/api-client` has typed parameter DTOs and endpoint methods for all five domains.
- `apps/ops-console` now exposes a conditional `OPS-301` browser smoke panel that reads `reconciliationParameters` and can submit a future-effective `autoMatchToleranceMinor` change request through `requestReconciliationParameterChange` when a Spring API URL is configured.
- `apps/audit-console` now exposes a conditional `AUD-201` browser smoke panel that reads `auditParameters` and can submit a future-effective `retentionYears` change request through `requestAuditParameterChange` when a Spring API URL is configured.
- `apps/admin-console` now exposes a conditional `ADM-201` browser smoke panel that reads `securityParameters` and can submit a future-effective `staffSessionTtlSeconds` change request through `requestSecurityParameterChange` when a Spring API URL is configured.
- `apps/admin-console` now exposes a conditional `ADM-301` browser smoke panel that reads `authorizationParameters` and can submit a future-effective `reasonRequiredScreens` change request through `requestAuthorizationParameterChange` when a Spring API URL is configured.
- `apps/fds-aml-console` now exposes a conditional `FDS-301` browser smoke panel that reads `fdsParameters` and can submit a future-effective `highAmountMinor` change request through `requestFdsParameterChange` when a Spring API URL is configured.
- `packages/auth-client` simulator tokens can carry optional synthetic `auth_time`, `iat`, `amr`, and `acr` claims so local browser smokes can model the same step-up claims enforced by Spring high-risk parameter routes.

## Commands Run

```bash
npm run test:core-banking:integration -- --tests lab.banking.core.parameters.ParameterAdminIntegrationTest
npm run packages:typecheck
npm run validate:manifests
npm run test:screen-engine
npm run scripts:typecheck
npm run next:fds-aml-console:typecheck
npm run next:ops-console:typecheck
npm run next:audit-console:typecheck
npm run next:admin-console:typecheck
node --test tests/nextScaffold.test.mjs
npm run test:e2e -- apps/fds-aml-console/e2e/fds-aml-console-parity.spec.ts
npm run test:e2e -- apps/ops-console/e2e/ops-console-parity.spec.ts
npm run test:e2e -- apps/audit-console/e2e/audit-console-parity.spec.ts
npm run test:e2e -- apps/admin-console/e2e/admin-console-parity.spec.ts
npm run test:core-banking:integration -- --tests lab.banking.core.parameters.ParameterAdminIntegrationTest --rerun-tasks
npm run test:parameter-command-e2e-compose
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
- `npm run next:fds-aml-console:typecheck`: passed.
- `npm run next:ops-console:typecheck`: passed.
- `npm run next:audit-console:typecheck`: passed after removing a duplicate `AUD-201` panel label that conflicted with manifest-card strict locators.
- `npm run next:admin-console:typecheck`: passed.
- `npm run packages:typecheck`: passed after adding optional simulator step-up claims to `@banking-lab/auth-client`.
- `node --test tests/nextScaffold.test.mjs`: passed with static coverage for the new `api-backed-fds-parameters`, `api-backed-reconciliation-parameters`, `api-backed-audit-parameters`, `api-backed-security-parameters`, and `api-backed-authorization-parameters` panels plus API client calls.
- `npm run test:e2e -- apps/fds-aml-console/e2e/fds-aml-console-parity.spec.ts`: passed after sandbox escalation for Chromium; 2 shell tests passed and 8 API/Keycloak tests, including the new FDS parameter command smoke, were skipped because `BANKING_LAB_E2E_API_BASE_URL` was not set.
- `npm run test:e2e -- apps/ops-console/e2e/ops-console-parity.spec.ts`: passed with 2 shell tests and 6 API/Keycloak tests, including the new OPS parameter command smoke, skipped because `BANKING_LAB_E2E_API_BASE_URL`, `BANKING_LAB_E2E_PAYMENT_API_BASE_URL`, and `BANKING_LAB_E2E_KEYCLOAK_BASE_URL` were not set.
- `npm run test:e2e -- apps/audit-console/e2e/audit-console-parity.spec.ts`: first run failed because the new panel duplicated exact `AUD-201` text already used by the manifest card; after relabeling the panel field to `Parameters`, rerun passed with 2 shell tests and 4 API/Reporting/Keycloak tests, including the new AUD parameter command smoke, skipped because `BANKING_LAB_E2E_API_BASE_URL`, `BANKING_LAB_E2E_REPORTING_API_BASE_URL`, and `BANKING_LAB_E2E_KEYCLOAK_BASE_URL` were not set.
- `npm run test:e2e -- apps/admin-console/e2e/admin-console-parity.spec.ts`: passed with 2 shell tests and 5 API/Reporting/Keycloak tests, including the new ADM-201 and ADM-301 parameter command smokes, skipped because `BANKING_LAB_E2E_API_BASE_URL`, `BANKING_LAB_E2E_REPORTING_API_BASE_URL`, and `BANKING_LAB_E2E_KEYCLOAK_BASE_URL` were not set.
- `npm run test:core-banking:integration -- --tests lab.banking.core.parameters.ParameterAdminIntegrationTest --rerun-tasks`: approved escalated rerun passed.
- `npm run test:core-banking:integration -- --tests lab.banking.core.parameters.ParameterAdminIntegrationTest --rerun-tasks`: approved escalated rerun passed after adding server-side `ADM-301` value validation; compile warnings are limited to pre-existing analytics evidence code.
- `npm run test:parameter-command-e2e-compose`: approved escalated run passed with 5 Playwright tests against a live Docker Compose Spring API after adding authenticated role-specific parameter endpoint preflight. Earlier attempts exposed an `ADM-301` transient readiness failure and an OPS preflight route-role mismatch; the final wrapper uses `FDS_REVIEWER`, `OPS_MANAGER`, `AUDITOR`, and `COMPLIANCE_MANAGER` simulator tokens while keeping Spring security and step-up enforcement enabled.

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
- The ops console now keeps `OPS-301` parameter operation behind a configured Spring API URL and creates only a future-effective browser smoke request, so the panel does not mutate current-day reconciliation tolerance during local shell rendering.
- The audit console now keeps `AUD-201` parameter operation behind a configured Spring API URL and creates only a future-effective browser smoke request, so the panel does not mutate current-day retention parameters during local shell rendering.
- The admin console now keeps `ADM-201` security parameter operation behind a configured Spring API URL and creates only a future-effective browser smoke request, so the panel does not mutate current-day staff session policy during local shell rendering.
- The admin console now keeps `ADM-301` authorization parameter operation behind a configured Spring API URL and creates only a future-effective browser smoke request, so the panel does not mutate current-day menu/role policy during local shell rendering.
- The FDS/AML console now keeps `FDS-301` parameter operation behind a configured Spring API URL and creates only a future-effective browser smoke request, so the panel does not mutate current-day FDS behavior during local shell rendering.
- `ADM-301` authorization parameter changes are validated before approval creation, including screen ID shape, high-risk approval business type membership, non-empty role maps, and duplicate screen/value rejection.
- The live compose wrapper now executes all five FDS/OPS/AUD/ADM parameter command browser paths against a running Spring API with synthetic seed data, security enabled, simulator tokens explicitly enabled only for this dev smoke, and fresh step-up claims on high-risk parameter change requests.

## Remaining Risk

This slice proves the common parameter workflow plus the FDS high-amount, OPS reconciliation tolerance, audit retention, admin security policy, admin menu-role browser wiring, server-side `ADM-301` structured value validation, and live Spring API browser execution for all five parameter command paths. Future hardening should add Temporal/activity evidence if parameter application is later delegated to scheduled workers.
