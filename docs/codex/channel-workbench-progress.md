# Cross-channel Held-transfer Workbench Progress

Updated: 2026-08-10 (Asia/Seoul)

## Goal boundary

This log tracks the synthetic customer-web, call-center workspace, and staff-terminal flow for one FDS-held internal transfer. The settlement and double-entry ledger vertical slice remains the repository and portfolio priority. This work does not introduce real money, real PII, real KYC, real bank APIs, real payment networks, or a new microservice.

## Current checkpoint

Checkpoints 1 through 5 are complete. Checkpoint 6 (single call-center agent workspace) is next. Customer product routes now submit the high-risk new-beneficiary signal and render the customer-safe HELD journey timeline; notification correlation remains a later checkpoint. No Definition of Done item is marked complete yet; no end-to-end claim will be made before all three product channels and the deterministic demo gate pass.

## Inspected baseline

- `README.md` leads with Payment Settlement & Ledger Reliability and already positions the broader channel platform as secondary.
- Spring/PostgreSQL already supports customer transfers, FDS HELD cases, FDS assignment, release/block requests, maker-checker approval, exactly-once ledger release through a stable FDS idempotency key, and no ledger posting for block.
- The call-center bounded module already supports reason-required masked search, interactions, redacted notes, after-call tasks, maker-checker escalation, close, and history.
- The staff terminal already exposes registry-driven `CUS101`, `ACC101`, `TX101`, `APR101`, `WRK002`, `WRK003`, and `CALL101`-`CALL106` screens through the shared API client.
- The notification service already consumes durable synthetic outbox events, deduplicates them through its inbox, renders masked synthetic messages, and exposes customer-owned delivery history.
- Customer and staff product paths still mix product UI with manifest catalogs, raw API evidence controls, simulator-token controls, and browser-readable bearer-token state.
- At baseline, `packages/api-client/src/index.ts` was a 4,668-line monolith and staff terminal `screens.tsx` was a 1,486-line mixed-domain component. Checkpoint 3 established compatibility-preserving seams for both.

## Verified gaps

1. No `journeyId`, journey table, reference mapping, or append-only journey event exists anywhere in the target stack.
2. `docs/architecture/payment-settlement-state-model.md`, required by the goal input, is missing.
3. The staff terminal has no `FDS201` registry screen even though the Spring FDS API and shared client methods already exist.
4. Customer product UI does not yet render the new customer-safe journey inquiry number and timeline.
5. The durable journey projection is present, but notification delivery references are not yet correlated.
6. FDS release/block does not emit a dedicated customer transfer status outbox event, and Notification Service has no matching template/routing entry.
7. Product routes expose manifest tables and API exercisers; call-center has no `/workspace` product route.
8. Customer-web and call-center browser JavaScript can receive and persist bearer tokens in session state. Product routes do not yet use an opaque server session/BFF boundary.
9. Accessibility and keyboard coverage is structural and partial; no held-transfer-specific WCAG/keyboard gate exists.
10. No deterministic `demo:test:channels` command proves all 12 goal Definition-of-Done controls end to end.
11. `docs/migration/kotlin-next-playbook.md` contains contributor-machine absolute paths in historical source-plan examples.

## Checkpoints

| Checkpoint | Scope | Status | Primary proof |
| --- | --- | --- | --- |
| 1 | Product/portfolio scope, state labels, journey/security/screen docs, portable examples | complete | documentation tests and repository scans |
| 2 | Product routes separated from `/lab/evidence`, `/lab/manifests`, `/lab/api-simulator` | complete | static boundary tests, Next typecheck/build, updated Playwright route assertions |
| 3 | Domain API-client modules, UI package boundaries, staff screen split | complete | 221 Node tests, package/script typecheck, contract check, three Next builds |
| 4 | Durable journey projection, mappings, events, customer ownership, staff reason/audit | complete | Flyway + JUnit/Testcontainers negative and correlation tests |
| 5 | Customer login/dashboard/accounts/transfers/support product UX | complete | customer-web typecheck/build, structural and browser route tests |
| 6 | Single call-center `/workspace` flow with softphone simulator and FDS handoff | pending | call-center typecheck/build and workflow integration/E2E |
| 7 | `FDS201`, journey-aware `TX101`/`APR101`/`WRK003`, SoD in UI and API | pending | staff-terminal typecheck/build, JUnit and Playwright |
| 8 | OIDC code+PKCE with opaque BFF session; negative security tests | pending | ownership/reason/masking/redaction/SoD/session tests |
| 9 | WCAG/keyboard checks plus W3C trace correlation without sensitive logging | pending | automated accessibility checks, keyboard Playwright, trace/audit tests |
| 10 | Deterministic seed/up/test/demo, portfolio link, screenshots/video script | pending | `npm run demo:test:channels` plus full goal gates |

## Planned command evidence

The exact status and timestamp of every execution will be appended below. Existing checked-in evidence is reference context only and is not counted as a new pass.

```bash
npm test
npm run validate:manifests
npm run packages:typecheck
npm run scripts:typecheck
npm run contracts:check-client
npm run next:customer-web:typecheck
npm run next:call-center-console:typecheck
npm run next:staff-terminal:typecheck
npm run next:customer-web:build
npm run next:call-center-console:build
npm run next:staff-terminal:build
npm run test:core-banking:unit
npm run test:core-banking:integration
npm run test:notification-service:integration
npm run test:staff-terminal:api-e2e-compose
npm run test:call-center-console:keycloak-e2e-compose
npm run test:customer-web:self-service-api-e2e-compose
npm run demo:test:channels
npm run portfolio:verify
```

## Command log

| Time | Command | Result | Evidence/notes |
| --- | --- | --- | --- |
| 2026-08-10 | baseline source and document inspection (`rg`, `sed`, `wc`) | pass | No runtime behavior claim; used only to establish the gaps above. |
| 2026-08-10 | `node --test tests/channelWorkbenchDocumentation.test.mjs tests/migrationFoundation.test.mjs tests/portfolioSettlementSurface.test.mjs` | fail | New documentation guard had a JavaScript template-literal syntax error; existing migration and settlement tests passed. |
| 2026-08-10 | same targeted test command, first rerun | fail | 14/15 passed; one new case-sensitive documentation regex failed. |
| 2026-08-10 | same targeted test command, corrected rerun | pass | 15/15 passed. |
| 2026-08-10 | `npm run validate:manifests` | pass | 79 manifests validated; no staff-terminal manifests were added. |
| 2026-08-10 | `npm run packages:typecheck` | pass | screen-engine, form-engine, api-client, and auth-client passed. |
| 2026-08-10 | `npm run scripts:typecheck` | pass | Root script TypeScript project passed. |
| 2026-08-10 | `npm test` | pass | 215/215 Node/oracle and structural tests passed; 0 skipped. |
| 2026-08-10 | `node --test tests/channelWorkbenchDocumentation.test.mjs` | pass | 3/3 passed after final progress-log wording update. |
| 2026-08-10 | `npm --workspace @banking-lab/{customer-web,call-center-console,staff-terminal} run typecheck` | pass | All three channel workspaces passed independently. |
| 2026-08-10 | `node --test tests/channelProductLabBoundary.test.mjs tests/nextScaffold.test.mjs tests/callCenterConsole.test.mjs` | fail | 15/16 passed; one legacy static assertion still expected the previous call-center Playwright test name. |
| 2026-08-10 | same targeted route-boundary command, corrected rerun | pass | 16/16 passed. Product roots reject lab panels; lab routes retain evidence/manifests. |
| 2026-08-10 | `npm run integrated-terminal:boundary-check` | pass | Seven allowed staff app route files; product terminal excludes the API evidence widget. |
| 2026-08-10 | `npm run packages:typecheck` | pass | Shared screen, form, API, and auth packages remain compatible. |
| 2026-08-10 | three channel `next build` commands | pass | Customer (21 static-generation items), call-center (8), and staff (7) generated `/lab/*`; call-center generated `/workspace`. |
| 2026-08-10 | first checkpoint 3 targeted test bundle | fail | 32/36 passed; four existing Node HTTP tests could not bind `127.0.0.1` in the restricted sandbox (`EPERM`). |
| 2026-08-10 | `node --test tests/fdsAmlReconciliation.test.mjs` with approved local bind | pass | 5/5 passed; release/block, AML, and reconciliation behavior was unchanged. |
| 2026-08-10 | `npm run packages:typecheck` and `npm run scripts:typecheck` | pass | Domain client subpaths, public barrel, channel imports, and scripts compiled. |
| 2026-08-10 | `npm run contracts:check-client` | pass | 202 operationIds matched 165 client methods/exemptions using the new implementation path. |
| 2026-08-10 | checkpoint 3 targeted structural suite | pass | 37/37 passed across module boundaries, route boundaries, onboarding, scaffold, and portfolio surface. |
| 2026-08-10 | `npm run integrated-terminal:boundary-check` | pass | Seven app routes and 18 terminal source files matched the bounded layout. |
| 2026-08-10 | three channel `next build` commands after module split | pass | All product/lab routes generated successfully with the new UI entry points. |
| 2026-08-10 | `node --experimental-strip-types scripts/check-live-route-api-evidence.ts` | pass | Static/live-evidence boundary accepted split staff API screens and lab routes; this is not a new live Compose run. |
| 2026-08-10 | `npm test` | pass | 221/221 passed, 0 skipped. Generated AML timestamp-only noise was restored and is not part of the checkpoint. |
| 2026-08-10 | `./gradlew :services:core-banking:compileKotlin` with shell JDK 26 | fail | Toolchain initialization rejected unsupported JDK `26.0.1`; no code/test claim. |
| 2026-08-10 | same compile with repository-required JDK 21 | pass | Journey, customer transfer, call-center, and FDS Kotlin sources compiled. |
| 2026-08-10 | `:services:core-banking:compileIntegrationTestKotlin` with JDK 21 | pass | Updated customer ownership and FDS journey integration tests compiled. |
| 2026-08-10 | customer + FDS targeted integration suites, first run | fail | 6/7 passed; one new Jayway JSONPath filter assertion counted the unfiltered array. Runtime journey data was correct. |
| 2026-08-10 | FDS targeted integration suite, second run | fail | 1/2 failed because another filtered JSONPath assertion used unsupported indexing; database evidence and response body showed the ledger reference. |
| 2026-08-10 | FDS targeted integration suite after assertion correction | pass | 2/2 passed: held/block ledger-free, release balanced and exactly once, maker-checker enforced, customer projection redacted. |
| 2026-08-10 | FDS targeted integration suite with append-only mutation check | pass | 2/2 passed; PostgreSQL rejected journey-event UPDATE and retained release/block invariants. |
| 2026-08-10 | `api-client` typecheck plus contract lint/client check | pass | OpenAPI valid; 204 operationIds matched 167 client methods/exemptions. |
| 2026-08-10 | checkpoint 4 documentation guard after progress update | fail | 2/3 passed; the guard requires the exact no-Definition-of-Done-claim wording. |
| 2026-08-10 | checkpoint 4 documentation guard, corrected rerun | pass | 3/3 passed with checkpoint evidence wording retained. |
| 2026-08-10 | customer-web typecheck plus targeted structural tests | pass | TypeScript passed; 15/15 product/lab boundary and Next scaffold tests passed. |
| 2026-08-10 | `npm run next:customer-web:build` | pass | 22 static-generation items; `/support` and held-transfer result routes compiled. |
| 2026-08-10 | customer Playwright parity spec in restricted sandbox | fail | Next server could not bind `0.0.0.0:3001` (`EPERM`); no browser assertion ran. |
| 2026-08-10 | same customer Playwright parity spec with approved local bind | pass | 4/4 configured product/lab/form browser tests passed; 11 live API/Keycloak/payment/notification cases skipped because their opt-in endpoints were not configured. |

## Domain and security invariants affected

- Held transfers must remain ledger-free until an independent checker approves release.
- Release retries must converge on one balanced ledger transaction; block must never post.
- Journey tables are projections/correlation records only and must not become a balance source of truth.
- Finalized ledger rows remain append-only; no direct balance mutation is in scope.
- Customer journey reads must use token ownership; staff reads require a reason and audit.
- Customer responses must omit risk score, reviewer identity, and internal approval notes.
- Call-center notes remain redacted before persistence and absent from audit payloads.
- Maker and checker remain different actors, at API and UI boundaries.
- Domain events remain durable outbox writes; notification consumption remains idempotent and synthetic-only.
- Product browser JavaScript must not read or store bearer tokens after the BFF checkpoint.

## Existing implementation to reuse

- `CustomerTransferService`, `FdsCaseService`, `PersistentApprovalService`, `LedgerCommandService`, and `StaffAccessService` control paths.
- `CallCenterService` redaction, access-audit, interaction, and escalation behavior.
- Notification Service inbox, masking, preferences, delivery history, and synthetic provider controls.
- Staff terminal registry, shell, primitives, transaction-code navigation, and compact desktop layout.
- Customer self-service route forms, API client request/error behavior, channel UI primitives, manifests, and current Compose smoke wrappers.

## Remaining risk

The largest risk is transactionally correlating transfer, FDS, approval, ledger, call-center, and notification state without weakening existing isolation/idempotency behavior. The BFF conversion also changes browser-auth boundaries across existing evidence flows and must preserve an explicit lab-only simulator path.

## Checkpoint 1 changed files

- `docs/product/channel-workbench.md`
- `docs/product/held-transfer-journey.md`
- `docs/product/screen-map.md`
- `docs/architecture/channel-security.md`
- `docs/architecture/payment-settlement-state-model.md`
- `docs/codex/channel-workbench-progress.md`
- `.agents/skills/ledger-invariant-review/SKILL.md`
- `.agents/skills/manifest-screen-generator/SKILL.md`
- `docs/migration/kotlin-next-playbook.md`
- `docs/migration/parity-scenarios.json`
- `tests/channelWorkbenchDocumentation.test.mjs`

## Checkpoint 1 invariant/control review

- Ledger: documentation states zero postings for held/block and exactly one balanced transaction for release; no runtime ledger code changed.
- Maker-checker: FDS release/block separation of duties and the stable self-approval error are explicit; no approval behavior changed.
- Manifests: staff terminal remains registry-driven without staff manifests; all 79 existing manifests validate.
- Structured errors: the security contract maps missing reason, authorization, self-approval, workflow state, and not-found behavior to stable families.
- Evidence: only commands run on this branch are recorded; the two failed test attempts remain visible.

## Checkpoint 2 changed files

- Customer product root and `CustomerSelfServiceHomeSurface`, plus `CustomerManifestCatalog` and `/lab/{evidence,manifests,api-simulator}`.
- Call-center product root, `/workspace`, `CallCenterWorkspace`, `CallCenterManifestCatalog`, and `/lab/{evidence,manifests,api-simulator}`.
- Staff terminal portal screen and `/lab/{evidence,manifests,api-simulator}`.
- Next scaffold, channel route boundary, terminal boundary, and three channel Playwright specifications.

## Checkpoint 2 invariant/control review

- Ledger: no Spring, PostgreSQL, posting, or balance code changed.
- Maker-checker: product text retains controlled FDS escalation; approval execution behavior is unchanged.
- Manifests: customer/call-center catalogs remain available only under `/lab/manifests`; staff remains registry-driven.
- Structured errors: no API or error response was changed.
- Security: raw API smoke and simulator-token controls no longer render on product roots. This is route isolation, not yet the opaque BFF session conversion.
- Accessibility: new route pages use named landmarks/headings; focused browser accessibility and keyboard validation remains checkpoint 9.
- Evidence: the initial legacy assertion failure and corrected pass are both recorded; live browser E2E was updated but not claimed as executed in this checkpoint.

## Checkpoint 3 changed files

- `packages/api-client/src/client.ts`, root compatibility barrel, and domain entry points for customer, call-center, staff, risk, and operations.
- API-client package exports plus contract/evidence scripts and structural tests that resolve the new implementation path.
- `packages/channel-ui/src/primitives.tsx`, `design-tokens.css`, `customer-ui.tsx`, and `operator-workbench.tsx` with backward-compatible root exports.
- Customer product imports now use the customer UI and least-capability customer API entry point.
- Staff static navigation remains in `screens.tsx`; Spring API workbench screens moved to `api-screens.tsx` and use staff/call-center clients separately.
- `docs/adr/0009-channel-client-and-ui-boundaries.md` records the reuse decision and rejected duplicate-package option.

## Checkpoint 3 invariant/control review

- Ledger: no ledger command, posting, balance projection, or persistence logic changed; the elevated FDS suite reconfirmed release/block behavior.
- Maker-checker: staff client still exposes separate approval commands and the existing self-approval protection remains covered by the full Node suite.
- Manifests: no manifest schema or count changed.
- Structured errors: the root `BankingApiError` export remains compatible; staff screens consume it through the constrained staff entry point.
- Security: product customer and staff API callers now receive only declared domain methods at the TypeScript/runtime object boundary. Lab evidence retains the broad compatibility client intentionally.
- UI: one token/primitive source is retained; customer/operator entry points avoid duplicate abstractions. Default product navigation no longer advertises manifest/evidence labels.
- Evidence: the sandbox bind failure and approved identical rerun are both recorded. No live Compose claim was made.

## Checkpoint 4 changed files

- Flyway `V043` adds synthetic-only business journeys, globally correlated references, append-only events, and nullable journey mappings on customer transfer results, FDS cases, and call-center interactions.
- Spring journey repository/service/controllers expose customer-owned redacted status and reason-required audited staff views.
- Customer transfer creation now returns `journeyId` for HELD results and no longer returns internal `riskScore` in customer transfer status.
- FDS assignment, decision request, release, and block append correlated journey events; release adds one ledger reference while block adds none.
- Call-center interaction start, redacted notes, and FDS handoff requests correlate without copying raw note text into journey/audit payloads.
- OpenAPI and least-capability customer, staff, call-center, and risk clients expose the corresponding journey reads.
- Customer and FDS PostgreSQL integration tests cover ownership, missing staff reason, redaction, append-only events, maker-checker, release balance, and block non-posting.

## Checkpoint 4 invariant/control review

- Ledger: journey rows are correlation projections only. HELD and BLOCKED paths retain zero transfer ledger transactions; approved release creates one balanced double-entry transaction and one ledger reference.
- Maker-checker: FDS approval remains independent; self-approval still returns `MAKER_CHECKER_SELF_APPROVAL_REJECTED` before any posting.
- Privacy: customer status no longer includes `riskScore`; customer journey references omit FDS/approval/call-center identifiers and events omit staff role, reason, request ID, and internal source references.
- Audit: customer-owned journey reads append `CUSTOMER_JOURNEY_VIEWED`; staff reads require a business reason and append `STAFF_JOURNEY_VIEWED`.
- Call-center: raw note text is redacted before persistence and is never copied into journey payloads; only redaction metadata is correlated.
- Durability: journey events are append-only at the PostgreSQL trigger boundary and are updated in the same serializable business transactions as their source transitions.
- Evidence: two test-assertion failures and their corrected passing reruns remain recorded; no full-suite or UI claim is made here.

## Checkpoint 5 changed files

- Customer transfer form defaults to the synthetic high-risk amount and a checked new-beneficiary FDS signal, while retaining explicit idempotency and internal-recipient lookup controls.
- HELD responses display the public `journeyId`; the result route loads the token-owned customer journey and renders a customer-safe status timeline.
- `/support` consolidates transfer-status, complaint, and notification navigation without surfacing staff/FDS internals.
- Customer workflow metadata names the `customerJourney` API and customer-safe timeline control.
- Playwright and Node structural coverage assert the new support route, FDS signal, journey client call, and absence of `riskScore` in customer product source.
- The live customer self-service E2E scenario now includes a high-risk new-beneficiary HELD transfer and owned journey assertions when its opt-in API/staff tokens are configured.

## Checkpoint 5 invariant/control review

- Ledger: the UI distinguishes HELD from POSTED and labels the transaction as not posted; it never fabricates a ledger transaction ID.
- Privacy: only the customer projection is called. The timeline component has no `riskScore`, reviewer identity, approval reason, or internal FDS/call-center reference field.
- Ownership: product calls use the authenticated customer client; backend ownership denial was already exercised in checkpoint 4. The opt-in live browser case is coded but was skipped because no API/Keycloak endpoints were configured.
- Idempotency: the customer-provided key remains visible/editable and preserved; a dedicated new-key action remains available.
- Accessibility: transfer signals use labeled native checkboxes, the timeline uses a table and `<time>`, and the support cards use headings/links. Full keyboard/WCAG automation remains checkpoint 9.
- Evidence: the initial local-bind failure and approved browser rerun are recorded separately; skipped live integrations are not claimed as passed.

## Next checkpoint

Turn `/workspace` into one call-center agent flow: softphone simulator, reason-required masked customer 360, journey-linked interaction, redacted note, and FDS handoff without exposing raw tokens or lab evidence controls.
