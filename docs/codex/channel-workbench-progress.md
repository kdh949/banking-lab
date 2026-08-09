# Cross-channel Held-transfer Workbench Progress

Updated: 2026-08-10 (Asia/Seoul)

## Goal boundary

This log tracks the synthetic customer-web, call-center workspace, and staff-terminal flow for one FDS-held internal transfer. The settlement and double-entry ledger vertical slice remains the repository and portfolio priority. This work does not introduce real money, real PII, real KYC, real bank APIs, real payment networks, or a new microservice.

## Current checkpoint

Checkpoint 1 complete. Checkpoint 2 (product and evidence route separation) is next. No Definition of Done item is marked complete yet because the target cross-channel runtime has not been implemented and verified.

## Inspected baseline

- `README.md` leads with Payment Settlement & Ledger Reliability and already positions the broader channel platform as secondary.
- Spring/PostgreSQL already supports customer transfers, FDS HELD cases, FDS assignment, release/block requests, maker-checker approval, exactly-once ledger release through a stable FDS idempotency key, and no ledger posting for block.
- The call-center bounded module already supports reason-required masked search, interactions, redacted notes, after-call tasks, maker-checker escalation, close, and history.
- The staff terminal already exposes registry-driven `CUS101`, `ACC101`, `TX101`, `APR101`, `WRK002`, `WRK003`, and `CALL101`-`CALL106` screens through the shared API client.
- The notification service already consumes durable synthetic outbox events, deduplicates them through its inbox, renders masked synthetic messages, and exposes customer-owned delivery history.
- Customer and staff product paths still mix product UI with manifest catalogs, raw API evidence controls, simulator-token controls, and browser-readable bearer-token state.
- `packages/api-client/src/index.ts` is a 4,668-line monolith; staff terminal `screens.tsx` is a 1,486-line mixed-domain component.

## Verified gaps

1. No `journeyId`, journey table, reference mapping, or append-only journey event exists anywhere in the target stack.
2. `docs/architecture/payment-settlement-state-model.md`, required by the goal input, is missing.
3. The staff terminal has no `FDS201` registry screen even though the Spring FDS API and shared client methods already exist.
4. Customer transfer DTOs do not expose a customer-safe journey inquiry number; the transfer status DTO currently exposes internal `riskScore`.
5. Call-center interactions and FDS/approval/ledger references are not projected under one business journey.
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
| 2 | Product routes separated from `/lab/evidence`, `/lab/manifests`, `/lab/api-simulator` | pending | Next typecheck/build and Playwright route assertions |
| 3 | Domain API-client modules, UI package boundaries, staff screen split | pending | package typecheck, public import compatibility tests |
| 4 | Durable journey projection, mappings, events, customer ownership, staff reason/audit | pending | Flyway + JUnit/Testcontainers negative and correlation tests |
| 5 | Customer login/dashboard/accounts/transfers/support product UX | pending | customer-web typecheck/build and owned held-transfer browser tests |
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

## Next checkpoint

Move manifest catalogs, API evidence exercisers, and simulator controls out of customer/call-center product routes into explicit `/lab/*` routes while preserving existing evidence tests. Add the customer and call-center product shells needed for later journey behavior.
