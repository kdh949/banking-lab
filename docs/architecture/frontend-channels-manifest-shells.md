# Frontend Channels Manifest Shells

## Scope

Agent D added Next.js App Router shells for these channel apps:

- `staff-terminal`
- `complaint-portal`
- `ops-console`
- `audit-console`
- `fds-aml-console`

The existing `customer-web` Next scaffold remains unchanged because it already renders from `screen-manifests/customer-web`.

## Runtime Shape

```text
Next App Router page
  -> app-local manifest loader
  -> screen-manifests/<app>/*.json
  -> manifest-rendered channel shell
```

Each channel shell renders declared screen metadata only:

- template type
- transaction code or screen id
- required roles
- audit and reason-required policy
- masking policy
- workflow states
- approval/maker-checker metadata
- declared endpoints, actions, widgets, fields, or sections

The shells remain manifest-first. The six active channel apps now include narrow API-backed smoke panels that use shared TypeScript clients instead of direct page-level fetch logic.

## Control Coverage

The staff terminal exposes the transaction-code workspace, customer context, reason-required lookup count, masked-PII posture, approval inbox presence, and per-screen maker-checker declarations.

The complaint portal exposes customer self-service complaint intake and status manifests with SLA and timeline states.

The ops console exposes daily closing and reconciliation screens, including the requirement that mismatch adjustments use approval-controlled balanced adjustment transactions.

The audit console exposes the hash-chain review inquiry manifest and its append-only evidence columns.

The FDS/AML console exposes held-transfer review, release/block approval, AML STR simulation closure, workflow states, SLA, and masked customer context declarations.

## Coordinator Integration

Coordinator follow-up added root npm scripts and package-lock workspace entries for the five new channel apps.

Each channel app uses app-local `package.json` scripts and TypeScript/TSX source under `src/**`. App-local type checking runs with `tsc --noEmit`; JavaScript/JSX source is no longer accepted for these Next shells.

## API-backed Smoke

The first API-backed slice covered read-model calls:

- `customer-web` calls the Spring customer account detail API and renders a masked account number for `SYN-CUS-001`.
- `staff-terminal` calls the Spring staff customer detail API with a business reason and renders masked phone data plus the audit event ID.
- `staff-terminal` calls the Spring privileged unmask API and renders branch-role denial plus manager time-boxed unmask evidence.
- `complaint-portal` calls the Spring complaint API and renders `CMP-SYN-001`.
- `ops-console` calls the Spring reconciliation API and renders `REC-SYN-001`.
- `audit-console` calls the Spring audit API and renders hash-chain validity plus `AUD-SYN-SEED-001`.
- `fds-aml-console` calls the Spring FDS and AML APIs and renders `FDS-SYN-001` and `AML-SYN-001`.
- Shared packages `@banking-lab/api-client` and `@banking-lab/auth-client` isolate API/auth concerns from the App Router page files.
- The broad FDS-AML smoke still keeps simulator-token smoke coverage for local repeatability, and customer-web now has live Keycloak Authorization Code + PKCE smoke for all current API-backed customer paths through the Next BFF token exchange route.
- Staff-terminal also has live Keycloak Authorization Code + PKCE smoke for masked lookup and branch-maker/manager-checker customer-change approval.
- Complaint-portal also has live Keycloak Authorization Code + PKCE smoke for complaint handler answer drafting, manager approval, and duplicate answer workflow failure-state rendering through its Next BFF token exchange route.
- Ops-console also has live Keycloak Authorization Code + PKCE smoke for ops-operator reconciliation adjustment, manager approval, and adjusted-item workflow failure-state rendering through its Next BFF token exchange route.
- Audit-console also has live Keycloak Authorization Code + PKCE smoke for auditor hash-chain read-model evidence through its Next BFF token exchange route.
- FDS/AML-console also has live Keycloak Authorization Code + PKCE smoke for risk read-model access, FDS release/block approval, AML closure approval, and duplicate workflow failure-state rendering through its Next BFF token exchange route.
- These customer-web, staff-terminal, complaint-portal, ops-console, audit-console, and FDS/AML-console smokes include a synthetic staff-terminal WebAuthn required-action smoke plus local WebAuthn policy/recovery role segregation evidence, but they are not a substitute for non-synthetic passkey operations, operational failure drills, or final retirement review.

Command-oriented browser smoke now runs in `customer-web`, `staff-terminal`, `complaint-portal`, `fds-aml-console`, and `ops-console`:

- The customer web uses the shared API client to call `POST /api/customer/transfers` through the Spring customer route, not the generic ledger route.
- It submits the same command twice with one `CWB-TRF-RETRY-...` idempotency key and verifies the replayed response shows the same `TX-...` transaction ID.
- It also attempts an over-balance transfer and verifies the real Spring route renders `LEDGER_INSUFFICIENT_AVAILABLE_BALANCE`, domain `ledger`, status `409`, and route `/api/customer/transfers`.
- It creates a transfer, reads the posted `TX-...` through `GET /api/customer/transactions`, and verifies the history row includes `INTERNAL_TRANSFER`, `CUSTOMER_WEB`, and `DEBIT`.
- It reads held FDS transfer status through `GET /api/customer/transfers` and verifies `FDS-SYN-001`, `HELD`, and `15000000`.
- It posts a high-amount transfer that becomes `HELD`, posts a negative-amount command that becomes durable `FAILED`, and verifies both statuses through `GET /api/customer/transfers` without ledger postings for either non-posted outcome.
- It posts `POST /api/customer/complaints` and verifies a Spring-backed `CMP-...` complaint reaches `RECEIVED` for `SYN-CUS-001`.
- The Spring customer transfer API verifies customer ownership of the source account before ledger posting.
- The Spring customer complaint API verifies customer ownership, writes complaint timeline/audit evidence, and retries transient SERIALIZABLE audit conflicts.
- The `customer_transfer_results` read model now exposes channel-visible `POSTED`, `HELD`, `FAILED`, and `BLOCKED` outcomes while keeping ledger postings as the balance source of truth.
- The staff terminal uses the shared API client to call `POST /api/staff/customers/SYN-CUS-CMD-001/change-requests`.
- It also calls `POST /api/staff/pii/unmask`, proves a branch-staff denial is visible to the browser as `AUTHORIZATION_POLICY_VIOLATION`, and then renders manager-approved `UNMASKED_TIMEBOXED` synthetic PII with a 300 second TTL.
- The privileged unmask command now uses the bounded SERIALIZABLE staff-access retry path so parallel browser channel smoke does not leak transient audit hash-chain conflicts as HTTP 500s.
- It attempts self-approval with the maker actor and verifies `MAKER_CHECKER_SELF_APPROVAL_REJECTED`.
- It then approves the generated `APR-...` approval through `POST /api/staff/approvals/{approvalId}/approve` with a separate manager actor.
- The Playwright assertion verifies `SYN-CUS-CMD-001` returns a masked updated phone and executed status.
- A live Keycloak staff-terminal smoke signs in `branch01`, renders masked lookup from the signed token, signs in `manager01`, executes privileged unmask with the signed manager token, requests the same customer-change command as branch maker, and approves it as manager checker with Spring simulator tokens disabled.
- The page uses the shared API client to call `POST /api/staff/complaints/CMP-SYN-CMD-001/answer-drafts`.
- It then approves the generated `APR-...` approval through `POST /api/staff/approvals/{approvalId}/approve`.
- The Playwright assertion verifies `CMP-SYN-CMD-001` reaches `ANSWERED`.
- The complaint portal also calls `POST /api/staff/complaints/CMP-SYN-FAIL-001/answer-drafts` for an already answered synthetic case.
- The Playwright assertion verifies the real Spring route renders `WORKFLOW_STATE_VIOLATION`, domain `workflow`, status `409`, and route `/api/staff/complaints/CMP-SYN-FAIL-001/answer-drafts`.
- A live Keycloak complaint-portal smoke signs in `complaint01`, renders the complaint case from the signed token, signs in `manager01`, drafts the same answer command as complaint-handler maker, approves it as manager checker, and renders the duplicate answer failure-state with Spring simulator tokens disabled.
- The FDS/AML console uses the shared API client to call `POST /api/staff/fds-cases/FDS-SYN-CMD-001/release-requests`.
- It then approves the generated `APR-...` approval through the same staff approval API.
- The Playwright assertion verifies `FDS-SYN-CMD-001` reaches `RELEASED` and exposes a posted `TX-...` ledger transaction.
- The FDS/AML console also uses the shared API client to call `POST /api/staff/fds-cases/FDS-SYN-BLOCK-CMD-001/block-requests`.
- It approves the generated `APR-...` approval through the staff approval API.
- The Playwright assertion verifies `FDS-SYN-BLOCK-CMD-001` reaches `BLOCKED` and displays `not posted`.
- The FDS/AML console also calls `POST /api/staff/fds-cases/FDS-SYN-FAIL-001/release-requests` for an already released synthetic case.
- The Playwright assertion verifies the real Spring route renders `WORKFLOW_STATE_VIOLATION`, domain `workflow`, status `409`, and route `/api/staff/fds-cases/FDS-SYN-FAIL-001/release-requests`.
- The FDS/AML console also uses the shared API client to call `POST /api/staff/aml-cases/AML-SYN-CMD-001/closure-requests`.
- It then approves the generated `APR-...` approval through the staff approval API.
- The Playwright assertion verifies `AML-SYN-CMD-001` reaches `CLOSED` and exposes `STR_SIMULATED`.
- The FDS/AML console also calls `POST /api/staff/aml-cases/AML-SYN-FAIL-001/closure-requests` for an already closed synthetic case.
- The Playwright assertion verifies the real Spring route renders `WORKFLOW_STATE_VIOLATION`, domain `workflow`, status `409`, and route `/api/staff/aml-cases/AML-SYN-FAIL-001/closure-requests`.
- The ops console uses the shared API client to call `POST /api/ops/reconciliation-items/REC-SYN-CMD-001/adjustment-requests`.
- It then approves the generated `APR-...` approval through the staff approval API.
- The Playwright assertion verifies `REC-SYN-CMD-001` reaches `ADJUSTED` and exposes a posted `TX-...` ledger transaction.
- The ops console also calls `POST /api/ops/reconciliation-items/REC-SYN-FAIL-001/adjustment-requests` for an already adjusted synthetic item.
- The Playwright assertion verifies the real Spring route renders `WORKFLOW_STATE_VIOLATION`, domain `workflow`, status `409`, and route `/api/ops/reconciliation-items/REC-SYN-FAIL-001/adjustment-requests`.
- A live Keycloak ops-console smoke signs in `ops01`, renders the reconciliation item from the signed token, signs in `manager01`, requests the same reconciliation adjustment as ops maker, approves it as manager checker, and renders the adjusted-item failure-state with Spring simulator tokens disabled.
- A live Keycloak audit-console smoke signs in `auditor01`, renders hash-chain validity and `AUD-SYN-SEED-001` from the signed token, and keeps Spring simulator tokens disabled.
- A live Keycloak FDS/AML-console smoke signs in `risk01`, renders risk cases, signs in `compliance01`, requests FDS release/block and AML closure as risk maker, approves them as compliance checker, and renders duplicate FDS/AML failure-state with Spring simulator tokens disabled.
- A post-smoke Spring `/health` check kept `auditHashChainValid=true`, backed by the PostgreSQL audit hash-chain lock row.

Verified commands:

```bash
npm run next:staff-terminal:typecheck
npm run next:staff-terminal:build
npm run next:customer-web:typecheck
npm run next:customer-web:build
npm run next:complaint-portal:typecheck
npm run next:complaint-portal:build
npm run next:ops-console:typecheck
npm run next:ops-console:build
npm run next:audit-console:typecheck
npm run next:audit-console:build
npm run next:fds-aml-console:typecheck
npm run next:fds-aml-console:build
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18084 npm run test:e2e
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18086 npm run test:e2e
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18087 npm run test:e2e
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18088 npm run test:e2e
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18089 npm run test:e2e
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18090 npm run test:e2e
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18091 npm run test:e2e
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18092 npm run test:e2e
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18093 npm run test:e2e
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18094 npm run test:e2e
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18095 npm run test:e2e
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18110 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18111 npx playwright test apps/complaint-portal/e2e/complaint-portal-parity.spec.ts -g "interactive Keycloak"
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18112 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18113 npx playwright test apps/ops-console/e2e/ops-console-parity.spec.ts -g "interactive Keycloak"
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18114 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18115 npx playwright test apps/audit-console/e2e/audit-console-parity.spec.ts -g "interactive Keycloak"
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18124 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://127.0.0.1:18125 npx playwright test apps/fds-aml-console/e2e/fds-aml-console-parity.spec.ts -g "interactive Keycloak"
```

## Next Work

Broaden remaining workflow, retry, and operator exception flows beyond the first API-backed command/failure-state smokes. WebAuthn required-action browser evidence and synthetic WebAuthn policy/recovery role segregation now exist for the staff-terminal/local lab path; non-synthetic passkey operations are not claimed.

Latest customer-web API-backed coverage includes complaint entry and answered-complaint confirmation through the shared client, with Spring writing customer ownership-checked state, timeline, and audit evidence. The Keycloak browser slice currently covers all current customer-web API-backed paths.
