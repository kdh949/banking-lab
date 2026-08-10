# Cross-channel Held-transfer Workbench Progress

Updated: 2026-08-10 (Asia/Seoul)

## Goal boundary

This log tracks the synthetic customer-web, call-center workspace, and staff-terminal flow for one FDS-held internal transfer. The settlement and double-entry ledger vertical slice remains the repository and portfolio priority. This work does not introduce real money, real PII, real KYC, real bank APIs, real payment networks, or a new microservice.

## Current checkpoint

All 10 checkpoints and all 12 Definition-of-Done items are complete on the branch and published in draft PR [#101](https://github.com/kdh949/banking-lab/pull/101). The deterministic cross-channel command, full repository/static matrix, three product builds, Core Banking and Notification Service integration suites, three channel Compose smokes, and settlement-first portfolio gate all pass on the final tree. The checked-in evidence remains synthetic/local unless the linked hosted workflow reports otherwise.

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
| 6 | Single call-center `/workspace` flow with softphone simulator and FDS handoff | complete | call-center typecheck/build, workflow integration and browser route tests |
| 7 | `FDS201`, journey-aware `TX101`/`APR101`/`WRK003`, SoD in UI and API | complete | staff-terminal typecheck/build, FDS PostgreSQL integration tests, Playwright |
| 8 | OIDC code+PKCE with opaque BFF session; negative security tests | complete | three live BFF Compose smokes plus 10 Spring/PostgreSQL negative-control tests |
| 9 | WCAG/keyboard checks plus W3C trace correlation without sensitive logging | complete | 2/2 automated core-page audits, manual 320px review, keyboard Playwright, Spring trace/audit tests |
| 10 | Deterministic seed/up/test/demo, portfolio link, screenshots/video script | complete | final `npm run demo:test:channels` and every full goal gate passed |

## Definition of Done

| # | Status | Final proof |
| --- | --- | --- |
| 1. Same `journeyId` across three channels | complete | Unified Playwright asserts the customer result, call-center journey panel, FDS201, and APR101 against one release journey; screenshots retain the customer/call/staff projections. |
| 2. HELD has zero ledger transactions | complete | The browser sees `not posted`; final PostgreSQL verification rejects any release-case transfer transaction before approval. |
| 3. Other customer denied journey, transfer, account | complete | Unified Playwright expects HTTP 403 for all three token-owned reads; full Core Banking integration remains green. |
| 4. Call lookup requires reason and masks PII | complete | Call-center integration negative test plus unified masked 360 assertion (`010-****`) and audited business reason. |
| 5. Note redacted before persistence | complete | Unified workflow saves a phone-bearing note, observes redaction proof, and PostgreSQL verifies only redacted persistence/journey metadata. |
| 6. FDS maker differs from checker | complete | FDS201 uses `risk01`; APR101 uses `manager01`; SQL enforces inequality and the full integration suite retains self-approval denial. |
| 7. Release retry creates exactly one balanced transaction | complete | Duplicate decision request is rejected; SQL proves one transaction, two KRW postings, debit equals credit, and one stable FDS idempotency key. |
| 8. Block creates no ledger transaction | complete | Independent block maker/checker branch reaches `BLOCKED`; API and SQL prove no ledger transaction. |
| 9. Customer final state and notification update | complete | Release reaches `POSTED`, block reaches `BLOCKED`, and each creates exactly one durable `CustomerTransferStatusChanged` delivery with the same journey. |
| 10. Sensitive actions retain actor/reason/journey/trace/request | complete | SQL verifies the exercised journey/audit rows have actor, reason, journey, request ID, and trace ID; Spring correlation integration is green. |
| 11. Core customer/call screens are keyboard accessible | complete | Checkpoint 9 automated/manual WCAG evidence passed, including labels, names, focus, contrast, 320px reflow, keyboard reachability, and staff keyboard workflow. |
| 12. One deterministic command reproduces the whole flow | complete | Final `npm run demo:test:channels` passed 1/1 browser scenario, database verification, evidence recording, and cleanup. |

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
| 2026-08-10 | call-center typecheck and targeted Node controls | pass | TypeScript passed; 4/4 call-center/redaction and product/lab boundary checks passed. |
| 2026-08-10 | `CallCenterWorkflowIntegrationTest` with JDK 21 and PostgreSQL | pass | 2/2 passed: reason/masking, redaction, maker-checker complaint path, journey-linked FDS handoff, and zero ledger posting. |
| 2026-08-10 | `npm run next:call-center-console:build` | pass | Eight static-generation items; product `/workspace` and isolated `/lab/*` routes compiled. |
| 2026-08-10 | call-center Playwright parity spec with approved local bind | pass | 3/3 configured product/lab/workspace browser tests passed; two live API/Keycloak cases skipped because opt-in endpoints were not configured. |
| 2026-08-10 | `FdsCaseApiParityIntegrationTest` with JDK 21 and PostgreSQL | pass | 3/3 passed: release posts once and balanced, block posts zero, checker rejection returns to investigation with zero ledger mutations and permits re-review. |
| 2026-08-10 | staff-terminal typecheck, package typecheck, and integrated-terminal boundary check | pass | New risk client usage and registry screen compiled; seven app routes and 18 terminal source files retained the product/lab boundary. |
| 2026-08-10 | live-route evidence source check and staff-terminal production build | pass | Static evidence metadata includes FDS journey methods; seven staff-terminal routes generated successfully. |
| 2026-08-10 | staff-terminal Playwright checkpoint 7 first run | fail | 4 passed, one live API case skipped, and one legacy shell assertion still expected the product API-evidence widget removed at checkpoint 2. |
| 2026-08-10 | staff-terminal Playwright checkpoint 7 corrected rerun | pass | 5/5 configured browser tests passed, including FDS201/APR101/WRK003 navigation; one live API case skipped because its opt-in endpoint was not configured. |
| 2026-08-10 | checkpoint 8 static/route bundle, first run | fail | 30/32 passed; two assertions still required browser-public API configuration and direct customer auth-client calls from the product component. |
| 2026-08-10 | corrected checkpoint 8 static/route bundle | pass | 35/35 passed, including new server-side PKCE, opaque cookie, route allowlist, simulator opt-in, and no-browser-bearer guards. |
| 2026-08-10 | integrated-terminal boundary and live-route evidence checks | pass | Twelve staff app route files accepted; BFF routes are bounded and generated live-route metadata passed. This source check alone is not live evidence. |
| 2026-08-10 | package, script, and three channel typechecks | pass | Auth server/BFF modules, channel products, Playwright changes, and repository scripts compiled. |
| 2026-08-10 | three channel production builds | pass | Customer (26), call-center (12), and staff (11) static-generation items include the new server-rendered BFF/session routes. |
| 2026-08-10 | targeted Spring integration suites in restricted sandbox | fail | Gradle could not open its local lock-contention socket (`EPERM`); no test executed. |
| 2026-08-10 | same three targeted Spring/PostgreSQL suites with approved execution | pass | 10/10 passed: customer ownership denial, reason/masking/redaction, self-approval denial, HELD/BLOCKED zero postings, and exactly-once release. |
| 2026-08-10 | customer self-service BFF Compose smoke, restricted sandbox | fail | Gradle lock socket was denied before the app/test started. |
| 2026-08-10 | customer self-service BFF Compose smoke, first approved run | fail | Authentication/session succeeded; the live test found that catch-all proxy reconstruction omitted the `/api` prefix and the capability allowlist correctly denied the route. |
| 2026-08-10 | customer self-service BFF Compose smoke after proxy correction | pass | 1/1 live Chromium flow passed through customer-auth BFF, HttpOnly session, owned accounts, POSTED transfer, HELD transfer, and customer journey timeline. |
| 2026-08-10 | staff-terminal BFF Compose smoke, first approved run | fail | The API succeeded but the migrated assertion scoped itself to the connection strip instead of the result workbench. |
| 2026-08-10 | staff-terminal BFF Compose smoke, second approved run | fail | The corrected assertion exposed concurrent CUS101 audit writes colliding under SERIALIZABLE isolation; no ledger mutation was involved. |
| 2026-08-10 | staff-terminal BFF Compose smoke after ordered audited reads | pass | 1/1 live Chromium flow passed with an explicitly opted-in branch-staff BFF session, reason-required masked result, and audit fields. |
| 2026-08-10 | call-center Keycloak BFF Compose smoke | pass | 1/1 live Chromium flow completed Keycloak Authorization Code + PKCE, returned through the opaque BFF callback, and performed a reason-gated masked customer lookup. |
| 2026-08-10 | first checkpoint 8 full `npm test` after route migration | fail | 223/224 passed; the live-route evidence guard still expected the retired staff evidence-panel test label. |
| 2026-08-10 | corrected live-route evidence test | pass | 2/2 passed with customer/staff opaque BFF routes, controls, and actual Compose pass stamps. |
| 2026-08-10 | second full `npm test` in restricted sandbox | fail | 201/224 passed; all 23 failures were legacy Node oracle HTTP tests denied local `127.0.0.1` bind (`EPERM`). |
| 2026-08-10 | identical full `npm test` with approved local bind | pass | 224/224 passed, 0 skipped. Generated current OpenAPI/journey evidence was retained; unrelated AML timestamp noise was restored. |
| 2026-08-10 | checkpoint 9 package and three channel typechecks | fail/pass | Packages, customer, and call-center passed; staff initially failed on heterogeneous menu literal inference, then passed after explicit `MenuTarget` collection. |
| 2026-08-10 | checkpoint 9 Kotlin main/integration compilation with JDK 21 | pass | Safe request correlation, trace fallback, audit enrichment, CORS, and updated integration tests compiled. The restricted first attempt could not access the Gradle wrapper lock; the approved identical rerun passed. |
| 2026-08-10 | customer-transfer and observability PostgreSQL integration suites | pass | Fixed W3C trace/request values correlated across HELD journey and audit; unsafe bearer-like request ID was absent from logs. No ledger mutation was added. |
| 2026-08-10 | checkpoint 9 BFF/document static tests | pass | 6/6 passed, including safe trace generation/response, no browser Authorization forwarding, and no BFF console logging. |
| 2026-08-10 | first combined CP9 Playwright run | fail | 10 passed, three live opt-ins skipped, two accessibility cases failed because the new audit helper selected an empty optional ARIA reference before visible text. Product keyboard and softphone cases passed. |
| 2026-08-10 | corrected focused accessibility Playwright rerun | pass | 2/2 core pages passed landmark, heading, labeling, ARIA reference, image alternative, contrast, focus-visible, and keyboard-reachability assertions. |
| 2026-08-10 | manual Chromium desktop/320px review and localhost console check | pass | Customer and call-center pages had no horizontal overflow at 320px, retained logical hierarchy/control order, and produced zero localhost console errors/warnings. |
| 2026-08-10 | corrected combined checkpoint 9 Playwright rerun | pass | 12 configured cases passed across core accessibility, staff keyboard navigation, and call-center softphone; three endpoint-dependent live cases were skipped and remain covered by checkpoint 8 Compose smokes. |
| 2026-08-10 | customer, call-center, and staff Next production builds | pass | 26, 12, and 11 routes generated respectively after accessibility, keyboard, and BFF trace changes. |
| 2026-08-10 | first checkpoint 10 unified demo attempt | fail | Notification Service was healthy at `/actuator/health`, but the wrapper waited on the unsupported `/health`; no browser scenario ran. |
| 2026-08-10 | unified demo in restricted sandbox after health fix | fail | Gradle lock-contention socket creation was denied (`EPERM`); no runtime claim. |
| 2026-08-10 | successive approved unified demo diagnostics | fail | Real controls exposed, in order, a checker-token/body actor mismatch, a `127.0.0.1`/`localhost` Next hydration boundary, an ambiguous APR101 button locator, missing checker step-up claims, and an imprecise ledger-field locator. Each run stopped at the failing assertion and cleaned disposable state. |
| 2026-08-10 | `npm run packages:typecheck`, staff typecheck, and demo shell syntax after step-up fix | pass | Auth-client optional simulated step-up claims, the staff BFF configuration, and the wrapper compiled/parsed. |
| 2026-08-10 | `npm run demo:test:channels` with approved local/Docker access | pass | 1/1 separated-actor Chromium journey passed in 17.6s; PostgreSQL printed `cross-channel held-transfer database invariants passed`; actual evidence JSON and six screenshots were recorded. |
| 2026-08-10 | first final `npm test` | fail | 224/225 passed; the new event schema raised the runtime envelope catalog from 18 to 19 while one structural assertion retained the old fixed count. |
| 2026-08-10 | corrected full static/type/contract matrix | pass | `npm test` 225/225, 79 manifests, package/script typechecks, API-client contract check, and all three channel typechecks passed. |
| 2026-08-10 | three channel production builds | pass | Customer generated 27 items including `/dashboard` and `/notifications`; call-center generated 12 including `/workspace`; staff generated 11 including bounded BFF/lab routes. |
| 2026-08-10 | Core Banking unit and full PostgreSQL integration suites | pass | JDK 21 unit build passed in 7s; full integration build passed in 2m 2s with ledger, ownership, audit, maker-checker, isolation, and workflow coverage. |
| 2026-08-10 | Notification Service full integration suite | pass | JDK 21/Testcontainers build passed in 34s, including direct and Redpanda `CustomerTransferStatusChanged` consumption and idempotency. |
| 2026-08-10 | staff, call-center Keycloak, and customer Compose smokes | pass | Each live Chromium scenario passed 1/1 against disposable target services; call-center used real local Keycloak Authorization Code + PKCE. |
| 2026-08-10 | final `npm run demo:test:channels` after pre-PR control review | pass | 1/1 passed in 16.5s; disposition stayed disabled before FDS handoff, the SQL required the stable release idempotency key, database invariants passed, and current six screenshots/machine evidence were regenerated. |
| 2026-08-10 | `npm run portfolio:verify` | pass | 19/19 focused Node/platform tests, contracts/OpenAPI diff, Core/Payment backend suites, ops-console build, and 2/2 settlement Playwright cases passed. |
| 2026-08-10 | focused pre-PR review and corrected rerun | pass | Prevented customer switching during an open call, duplicate handoff/disposition, post-close notes, and premature disposition. Journey-less legacy FDS decisions remain approvable without a journey notification; the new focused PostgreSQL regression passed with the three journey-backed FDS tests. Call-center typecheck/build, focused static tests, Core unit tests, and the unified demo also passed. |
| 2026-08-10 | Git handoff | pass | Committed checkpoint 10, pushed `feat/cross-channel-held-transfer-workbench`, and opened draft PR [#101](https://github.com/kdh949/banking-lab/pull/101) to `main`. Hosted checks are not represented as green by the local evidence. |

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
- OIDC state, PKCE verifier, access token, and opaque session key must remain server-side/HttpOnly; BFF route capability lists cannot be widened by browser input.
- W3C correlation input must be validated or replaced, then match the held-transfer journey/audit trace without copying credentials, request bodies, note content, or raw PII into logs.
- Core customer/call-center controls must retain labels, contrast, focus visibility, and 320px reflow; staff transaction-code, tab, execute, and dialog-close behavior must remain keyboard operable.

## Existing implementation to reuse

- `CustomerTransferService`, `FdsCaseService`, `PersistentApprovalService`, `LedgerCommandService`, and `StaffAccessService` control paths.
- `CallCenterService` redaction, access-audit, interaction, and escalation behavior.
- Notification Service inbox, masking, preferences, delivery history, and synthetic provider controls.
- Staff terminal registry, shell, primitives, transaction-code navigation, and compact desktop layout.
- Customer self-service route forms, API client request/error behavior, channel UI primitives, manifests, and current Compose smoke wrappers.

## Remaining risk

Local completion does not establish hosted CI status, production certification, real notification delivery, external payment movement, or settlement finality. The opaque BFF session store remains intentionally process-local for this single-instance lab; multi-instance use still requires an encrypted expiring shared store. Simulated checker step-up is explicitly dev/test-only, and real WebAuthn assurance remains a separate Keycloak deployment concern.

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

## Checkpoint 6 changed files

- `/workspace` is now one client-side agent flow with a softphone simulator, mandatory business reason, customer query, optional held-transfer `journeyId`, masked context, interaction start, note redaction, and FDS handoff.
- Product code uses the least-capability call-center client and staff journey read; it has no bearer-token, storage, raw API evidence, or manifest controls.
- Call-center CSS provides compact two-column operator layout with labeled native controls, visible disabled states, responsive collapse, and an `aria-live` outcome message.
- Spring integration coverage seeds a held journey and proves interaction/escalation references, redacted-note metadata, absence of raw note text in journey payloads, required staff view reason, and zero ledger transactions.
- Browser and structural tests assert the product/lab boundary, disabled actions before reason/context, softphone simulator, journey control, and absence of browser token storage in product source.

## Checkpoint 6 invariant/control review

- Ledger: call-center start, note, and FDS handoff append only journey/control rows; the integration test proves no ledger transaction is created.
- Privacy: product search renders masked name/phone only; note bodies are redacted server-side and raw phone text is absent from journey payload JSON.
- Reason/audit: search, interaction, note, handoff, and staff journey read all require a business reason; missing journey-read reason returns `POLICY_REASON_REQUIRED`.
- Correlation: the same `journeyId` maps `CALL_CENTER_INTERACTION` and `CALL_CENTER_ESCALATION` references and ordered events without changing the HELD status.
- Auth boundary: product source contains no bearer token or browser storage. Full opaque BFF/OIDC enforcement across all channel calls remains checkpoint 8.

## Checkpoint 7 changed files

- Spring `FdsCaseService` and staff approval execution now support independent-checker rejection of FDS release/block requests, returning the case to investigation without posting.
- FDS integration coverage proves rejection is recoverable, correlated to the original journey, customer-safe, and ledger-free.
- The API client exposes the rejected FDS case alongside the approval result.
- Staff terminal registry and navigation add `FDS201`; its case workbench lists held cases, assigns an owner, and requests release or block approval with a mandatory reason.
- `TX101`, `APR101`, and `WRK003` accept/render `journeyId`; `APR101` displays FDS status, ledger transaction identity, and whether the decision produced a balanced posting or no mutation.
- Browser and live-route evidence guards now include the FDS workbench and retain the product/lab route boundary.

## Checkpoint 7 invariant/control review

- Ledger: release remains the only FDS path that posts, and only after independent approval; block and checker rejection retain zero transfer postings. The release integration assertion still proves balanced double-entry and one stable idempotency key.
- Maker-checker: FDS reviewer `fds-reviewer01` is the maker and branch manager `branch-manager01` is the checker in the UI; the API self-approval negative test remains enforced before posting.
- Rejection: a rejected decision clears the stale approval reference, returns the case to `INVESTIGATING`, keeps transfer status `HELD`, appends `FDS_DECISION_REJECTED`, and permits a fresh controlled decision request.
- Correlation: FDS case, approval, staff inquiry, workflow, and any eventual ledger transaction render from the same durable `journeyId`; customer projection continues to hide internal references and reasons.
- Registry: `FDS201` is defined once in the existing staff registry using the case template; no copied Next route or staff screen manifest was introduced.
- Evidence: the stale Playwright expectation failure and corrected rerun are both recorded. The opt-in live API browser case remains skipped and is not claimed as passed.
- Evidence: local browser product tests passed; the two opt-in live API/Keycloak browser cases were skipped and are not claimed as passed.

## Checkpoint 8 changed files

- Shared auth-client server modules now create random opaque sessions, retain OIDC state and PKCE verifier server-side, exchange authorization codes server-side, and proxy only channel-allowed Spring routes with the server-held bearer credential.
- Customer, call-center, and staff Next apps expose same-origin `/api/session` login/callback/logout routes plus bounded catch-all BFF proxies; customer synthetic signup/login is exchanged server-side and returned without bearer or upstream session IDs.
- Product components consume only public session metadata and no longer construct bearer headers, persist auth in Web Storage, or depend on a public Spring base URL.
- Staff and call-center simulated actors are explicit dev/test-only BFF logins guarded by three opt-ins; legacy lab browser exchange requires its own disabled-by-default opt-in.
- Customer and staff live Playwright flows now authenticate through the BFF. Call-center live Playwright completes real Keycloak code+PKCE through the product callback instead of inspecting a bearer in the lab panel.
- CUS101 orders its two audited reads to avoid racing global audit hash-chain writes under SERIALIZABLE isolation.
- Static security tests and boundary scripts assert HttpOnly/SameSite cookies, state/verifier lifetime, same-origin mutations, no redirects, no public upstream fallback, least-capability paths, and absence of browser credential storage.

## Checkpoint 8 invariant/control review

- Ledger: no posting service or balance projection changed. Ten PostgreSQL integration tests reconfirm HELD/BLOCKED/rejected paths create zero transfer postings and independent release creates one stable balanced transaction.
- Ownership: another-customer transfer/account use is rejected before ledger posting; customer product API calls derive identity from the BFF-held token.
- Reason/masking/audit: CUS101 and call-center live smokes use reason-required masked reads. The call-center integration suite rejects missing reason, verifies default masking, and records access audit.
- Redaction: raw note content is redacted before persistence and is excluded from audit/journey payloads in the passing call-center integration suite.
- Maker-checker: FDS and call-center self-approval attempts return `MAKER_CHECKER_SELF_APPROVAL_REJECTED`; separate branch manager approval remains required.
- Session security: browser product code receives neither bearer tokens nor opaque credential IDs. Cookies are `HttpOnly`, `SameSite=Lax`, and HTTPS-secure; mutation requests require same origin and upstream redirects are not followed.
- Deployment limit: the current server session map is a lab-only single-process implementation. Multi-instance deployment requires an expiring encrypted shared server-side store before production use.
- Evidence: all three channel Compose smokes ran against disposable Spring/PostgreSQL state; call-center additionally used a real disposable Keycloak realm. Failed attempts and corrections remain recorded above.

## Checkpoint 9 changed files

- Shared channel UI focus-visible rules and customer result live-region behavior.
- Call-center softphone states now cover IDLE, RINGING, CONNECTED, HOLD, and AFTER_CALL.
- Staff terminal transaction-code submit, semantic roving workspace tabs, modal focus trap, Escape close, focus restore, and keyboard Playwright proof.
- Shared BFF validation/generation for request ID, W3C `traceparent`, and bounded printable `tracestate`, with safe response correlation.
- Spring request-correlation helper, safe access logging, journey request/trace persistence, automatic audit correlation, and trace CORS headers.
- PostgreSQL integration assertions for one fixed HELD journey trace across journey/audit and negative unsafe-log input.
- Focused automated accessibility audit and `docs/test-evidence/channel-accessibility-trace-evidence.md` manual/runtime evidence.

## Checkpoint 9 invariant/control review

- Ledger: no ledger command or projection code changed. The trace integration uses the existing high-risk HELD path and confirms correlation without introducing a posting.
- Maker-checker: FDS maker/checker behavior is unchanged; keyboard navigation exposes `FDS201` but does not bypass server approval.
- Audit: correlation fields are added before payload hashing and remain part of the append-only hash chain. No audit row is updated in place.
- Privacy/logging: request IDs are syntax-bounded, W3C IDs contain no business data, browser credentials are not forwarded from request headers, and logs omit headers, bodies, query strings, note content, and raw PII.
- Accessibility: two core product pages pass the focused automated audit and 320px manual review. Staff transaction-code execution, tab navigation, and modal exit pass with keyboard only.
- Evidence: the accessibility-helper failure and corrected pass are both retained. A commercial screen-reader compatibility run remains outside this focused portfolio checkpoint.

## Checkpoint 10 changed files

- A single reset/start/seed/test/verify/record/cleanup shell wrapper for disposable Core Banking, PostgreSQL, Redpanda, Notification Service, customer web, call-center, and staff terminal execution.
- One Playwright scenario with separate customer, other-customer, call-agent, FDS-maker, and checker contexts plus release and block branches.
- PostgreSQL verification for balanced exactly-once release, zero-ledger hold/block, maker-checker separation, call-center controls, durable notifications, and journey/audit request/trace correlation.
- A durable `CustomerTransferStatusChanged` outbox contract and Notification Service template/consumer path with preallocated journey-linked delivery identity.
- Customer-owned notification product history, completed call-center identity/disposition/close actions, and explicit simulated step-up claims for the dev/test checker only.
- Six visually reviewed screenshots, machine-readable local evidence, a 90–120 second script, screen-map promotion, and a short settlement-first README link.

## Checkpoint 10 invariant/control review

- Ledger: the database verifier proves the release case has one transaction, two balanced KRW postings, and a stable idempotency key; HELD and BLOCKED cases have zero transfer transactions.
- Maker-checker and step-up: `risk01` requests release/block and `manager01` approves; APR101 uses an explicit fresh simulated WebAuthn step-up claim and no browser bearer exposure.
- Ownership/privacy: another customer is denied journey, transfer, and account reads. The customer sees only safe journey/status data; call-center output is masked and the raw phone-bearing note is redacted before persistence.
- Eventing/notification: the FDS decision and customer notification request are written through the durable outbox. The consumer is inbox-idempotent and preserves the preallocated `NDL-` journey reference.
- Correlation/audit: the unified database verification rejects missing actor/reason/journey/request/trace evidence across the exercised sensitive workflow.
- Evidence boundary: the generated JSON says `localOnly=true` and `hostedCiGreenClaim=false`; Notification delivery remains synthetic and does not claim real SMS delivery.

## Checkpoint 10 residual boundary

The generated JSON says `localOnly=true` and `hostedCiGreenClaim=false`. Notification delivery is a durable synthetic record, not proof that a real SMS provider delivered a message. The demo step-up credential is an explicit local simulator claim and does not replace the repository's real Keycloak WebAuthn evidence boundary.

## Next smallest safe task

Review the hosted checks on draft PR [#101](https://github.com/kdh949/banking-lab/pull/101) and address only evidence-backed failures; do not rewrite the passing local records as hosted evidence.
