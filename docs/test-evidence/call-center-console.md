# Call-Center Console Evidence

Review date: 2026-06-10

Scope: synthetic-only call-center workflow with a dedicated Next.js shell. This is not a real contact-center system and does not connect to real telephony, real recordings, real customer PII, real KYC/AML providers, card networks, payment networks, regulator filing systems, or external financial-institution APIs.

## Implemented

- PostgreSQL/Flyway migration `V040__call_center_workflow.sql` adds `call_center_interactions`, `call_center_notes`, `call_center_aftercall_tasks`, `call_center_escalations`, and `call_center_access_audit`.
- Spring bounded module `lab.banking.core.callcenter` exposes:
  - `GET /api/staff/call-center/customers/search`
  - `POST /api/staff/call-center/interactions`
  - `GET /api/staff/call-center/interactions/{interactionId}`
  - `POST /api/staff/call-center/interactions/{interactionId}/notes`
  - `POST /api/staff/call-center/interactions/{interactionId}/aftercall-tasks`
  - `POST /api/staff/call-center/interactions/{interactionId}/escalations`
  - `POST /api/staff/call-center/interactions/{interactionId}/close`
  - `GET /api/staff/call-center/customers/{customerId}/history`
- `@banking-lab/api-client` has shared client methods for the same routes.
- `screen-manifests/call-center-console/CALL-101..CALL-106` declares reusable inquiry, command, and case templates for customer search, interaction detail, note entry, aftercall task, history, and escalation.
- `apps/call-center-console` renders those manifests through `packages/channel-ui` on port 3008.
- `ApiBackedCallCenterPanel` can run a synthetic CALL-101 through CALL-106 browser smoke when `NEXT_PUBLIC_BANKING_API_BASE_URL` is configured.
- `apps/call-center-console/src/app/api/auth/keycloak-token/route.ts` exchanges Keycloak Authorization Code + PKCE tokens for the `call-center-console` public client.
- `infra/keycloak/realm-banking-lab.json` declares the `call-center-console` client, `call-agent01`, `call-manager01`, `CALL_CENTER_AGENT`, and `CALL_CENTER_MANAGER` for live synthetic browser smoke.
- `ApiBackedCallCenterPanel` can run a signed agent/manager Keycloak workflow smoke when both `NEXT_PUBLIC_BANKING_API_BASE_URL` and `NEXT_PUBLIC_BANKING_KEYCLOAK_BASE_URL` are configured. The smoke preserves only same-tab synthetic Keycloak token state with token expiry metadata across the agent and manager Authorization Code redirects.
- OpenAPI contract includes all call-center routes with reason-required, synthetic-only, masking/redaction, and structured-error metadata.

## Controls

- Every read/write path requires a business reason.
- Customer search and history expose masked customer data only.
- `CALL_CENTER_AGENT` can search, start sessions, add notes, create aftercall tasks, and close sessions.
- `CALL_CENTER_MANAGER`, `BRANCH_MANAGER`, `COMPLAINT_HANDLER`, and `COMPLIANCE_MANAGER` can create escalations.
- `AUDITOR` is read-only; command attempts fail before mutation.
- Free-form note bodies are redacted for PII-like phone, resident-number, email, and numeric-account patterns before persistence.
- Audit payloads store note length, redaction status, and pattern count, but do not copy raw note text.
- Complaint escalation creates a synthetic complaint case with `source_reference_json.syntheticOnly=true` and `sourceType=CALL_CENTER_INTERACTION`.

## Evidence

Generated summary: `docs/test-evidence/generated/call-center-console.json`.

Commands run for this slice:

```bash
npm run validate:manifests
npm run packages:typecheck
npm run contracts:check-client
npm run contracts:lint
npm run contracts:diff-openapi
scripts/run-core-banking-tests.sh :services:core-banking:integrationTest --tests lab.banking.core.callcenter.CallCenterWorkflowIntegrationTest
npm run next:call-center-console:typecheck
npm run next:call-center-console:build
node --test tests/callCenterConsole.test.mjs tests/nextScaffold.test.mjs
scripts/run-core-banking-tests.sh :services:core-banking:test --tests lab.banking.core.security.KeycloakRealmPolicyTest
npx playwright test apps/call-center-console/e2e/call-center-console-parity.spec.ts
npm run test:call-center-console:keycloak-e2e-compose
```

The Gradle command failed inside the sandbox with the known file-lock socket denial and passed after approved unsandboxed rerun.
The Keycloak realm policy test also failed inside the sandbox with the same Gradle file-lock socket denial and passed after approved unsandboxed rerun.
The first Playwright command failed inside the sandbox with `listen EPERM`; the approved unsandboxed rerun passed the manifest/shell checks, with the live API workflow test skipped because `BANKING_LAB_E2E_API_BASE_URL` was not set and the live Keycloak workflow test skipped because `BANKING_LAB_E2E_API_BASE_URL` and `BANKING_LAB_E2E_KEYCLOAK_BASE_URL` were not set.
The first live wrapper run in this slice reached the browser workflow but failed because the manager Keycloak redirect reset the in-memory agent token and left `Run Keycloak call-center workflow smoke` disabled. After adding bounded same-tab synthetic token-state persistence with expiry metadata, the approved rerun of `npm run test:call-center-console:keycloak-e2e-compose` passed: Gradle `:services:core-banking:bootJar` was up to date, disposable PostgreSQL, Keycloak, and core-banking started through Docker Compose with simulator tokens disabled, direct-grant preflight checks for `call-agent01` and `call-manager01` reached the Spring call-center search API, and Playwright reported `1 passed (20.8s)` for the live Authorization Code + PKCE agent/manager workflow smoke.

## Remaining Limits

- Escalation is role-gated but not maker-checker in this first slice.
- The live evidence is local disposable Compose evidence, not hosted CI evidence and not a production identity deployment.
- This is a synthetic workflow only; no real telephony, call recording, contact-center SaaS, real PII, real KYC/AML provider, real payment network, or real regulator integration is present.
