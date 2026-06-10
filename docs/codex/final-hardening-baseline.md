# Final Hardening Baseline

Review date: 2026-06-10

Baseline lineage: originally created on `codex/final-hardening-baseline` and
refreshed on `codex/final-hardening-baseline-refresh` after PR #69 through
PR #73.

Scope: PLAN Phase 0 baseline for making the synthetic banking lab verifiable,
document-consistent, and demo-ready. This document is an inventory and scope
lock. It does not claim newly passed runtime evidence until commands are rerun
on this branch.

## Source Order Applied

The baseline was prepared from the repository-required order:

1. `PLAN.md`
2. `BANKING_LAB_CODEX_PROMPT.md`
3. `AGENTS.md`
4. Node oracle tests under `tests/*.test.mjs`
5. Architecture, ADR, migration, evidence, CI, package, contract, app, service,
   and screen-manifest files listed in PLAN Phase 0

When these files disagree, the active priority is `PLAN.md` over older Node,
README, or historical evidence claims.

## Current Implementation Summary

The target-stack shape is present and broad:

- Kotlin/Spring Boot services exist for core banking, payment, notification,
  and reporting under `services/*/src/main/kotlin`.
- PostgreSQL/Flyway migrations cover core banking tables through synthetic
  onboarding, account opening, customer auth, ledger controls, outbox/inbox,
  approval, workflow, audit, products, fees, cards, loans, EOD, reconciliation,
  security hardening, reporting, and service-specific bounded contexts.
- Core ledger commands are implemented around balanced postings, idempotency,
  reversals, adjustments, closed-day protection, projection integrity, audit,
  outbox, and Testcontainers-backed integration tests.
- Kafka/Redpanda outbox and bounded-context event producers/consumers exist
  for core, payment, notification, and reporting surfaces, with structural
  AsyncAPI/event schema gates.
- Temporal workflow references and worker code exist for banking case workflows
  and EOD-related workflow surfaces.
- Keycloak/JWKS-style resource server support exists across Spring services,
  with simulator-token paths intended for explicit dev/test use only.
- Next.js channel apps exist for customer web, staff terminal, complaint portal,
  ops console, audit console, FDS/AML console, admin console, and
  call-center console.
- `customer-web` includes route pages for signup, login, accounts, transfers,
  complaints, security, cards, loans, payments, and notifications.
- `staff-terminal` is currently an iWorks-style integrated terminal shell with
  `/` and `/api/terminal-status`; Spring staff-control APIs remain backend
  covered but are not exposed as the previous staff manifest route set.
- `call-center-console` now has a dedicated Next.js shell on port 3008,
  manifests `CALL-101` through `CALL-106`, shared API-client methods, and a
  Spring/PostgreSQL workflow for masked customer search, interaction start and
  detail, redacted notes, aftercall tasks, maker-checker escalation, close,
  history, and reason-required access audit. It also has a Keycloak public client, synthetic
  call-center agent/manager users, a Next token exchange route, and an
  env-gated Playwright source path for signed agent/manager workflow smoke.
  `npm run test:call-center-console:keycloak-e2e-compose` now records local
  disposable Compose evidence for live Keycloak/JWKS agent/manager propagation
  with simulator tokens disabled.
- Shared TypeScript packages include screen engine, form engine, API client,
  and auth client support.
- OpenAPI files exist for core banking, payment, notification, and reporting.
- AsyncAPI and event JSON schemas exist for the current event envelope catalog.
- Screen manifests remain for customer, complaint, ops, audit, FDS/AML, admin,
  and call-center channels; staff-terminal manifests are not present in the
  current target UI model.
- Python/DuckDB/scikit-learn AML/FDS analytics and data-quality evidence
  tooling exist under `analytics/aml-fds-python`.
- Docker Compose, Kubernetes, Helm, Terraform, Argo CD, observability, security,
  and evidence scripts are present.

## Current Documentation And Evidence Tensions

The repository is advanced, but documentation and evidence still need active
maintenance against the current PLAN.

- `README.md`, `docs/implementation-coverage-matrix.md`, this baseline,
  `docs/test-evidence/final-hardening-scorecard.md`, and
  `docs/demo-scenarios/demo-video-script.md` now use explicit status boundaries,
  but they must be refreshed after each hardening PR.
- `docs/implementation-coverage-matrix.md` now declares the current status
  vocabulary, including `route-backed-live-gated`, `integrated-terminal`, and
  `backend-control-covered`; tests guard that every table row uses a declared
  value.
- Existing evidence documents contain long historical command logs from earlier
  branches. They are useful as prior evidence, but they must not be presented
  as commands rerun for this branch.
- Hosted GitHub Actions are documented in prior evidence as blocked by account
  billing/spending-limit restrictions. That is a blocked hosted-CI condition,
  not a green CI result.
- Several Playwright live API flows are env-gated. A skipped live API test is
  not proof of route-to-live-API execution.
- DTO-level OpenAPI diffing and runtime event-envelope validation now exist for
  the current checked subsets and runtime fixtures. Full core-banking
  Spring/Jackson/springdoc DTO parity and exhaustive live broker envelope
  coverage remain open.
- `call-center-console` has a dedicated shell, backend workflow, OpenAPI/API
  client coverage, manifest Playwright smoke, Keycloak realm/client coverage,
  token exchange route coverage, and local live Compose Keycloak/JWKS browser
  execution evidence for the call-center route, including separate escalation
  maker/checker actors.

## Remaining Hardening Gaps

The active PLAN identifies these portfolio-completion gaps:

1. README, coverage matrix, and evidence/status documents need consistency
   cleanup without inflating implementation status.
2. Hosted CI evidence must be separated from local evidence, and blocked hosted
   CI must remain explicitly blocked rather than marked green.
3. `customer-web` route evidence should be refreshed against a running
   synthetic stack before demo recording. The current `staff-terminal` is an
   integrated-terminal shell, so staff route-to-API claims must use backend
   control evidence or a new operator workflow rather than the retired manifest
   routes.
4. OpenAPI DTO-level generated diffing and runtime event-envelope validation
   need broader coverage beyond the current checked subsets and fixtures.
5. The call-center workflow and live local Keycloak route evidence are
   implemented, including maker-checker escalation; the remaining boundary is
   local-only Compose evidence and limited operational failure-depth evidence.
6. Final scorecard and demo script exist, but must be regenerated from actual
   verified capabilities and residual limitations after each major hardening PR.

## Scope For This Workstream

This workstream should proceed in small, commit-sized branches:

- Maintain the baseline, coverage matrix, README, scorecard, and demo script as
  one evidence set.
- Keep hosted CI status separate from local fallback commands until GitHub
  Actions actually starts and finishes runner jobs.
- Refresh customer-web live route evidence against a disposable synthetic stack
  before demo recording.
- Treat the current staff-terminal as an integrated-terminal boundary with a
  bounded Spring API evidence panel; keep broader staff-control command
  evidence in backend/control rows unless a dedicated operator workflow is
  intentionally added.
- Broaden DTO diffing and runtime envelope validation without weakening the
  existing structural gates.
- Rerun call-center live API/Keycloak evidence before demo or release evidence
  refreshes, keeping the local-only Compose boundary explicit.

## Explicitly Out Of Scope For Phase 0

This baseline does not:

- change Spring domain behavior;
- change ledger postings, balance projection, or idempotency semantics;
- add new customer, staff, payment, reporting, notification, FDS/AML, or
  call-center functionality;
- mark hosted CI as green;
- regenerate generated evidence artifacts;
- claim live API route execution where env-gated tests are skipped.

## Synthetic-Only Boundary

All future changes must preserve these boundaries:

- no real customer money;
- no real personal data;
- no real KYC, sanctions, credit, card, payment, Open Banking, or financial
  network provider;
- no real production secrets, tokens, private keys, or certificates;
- all external providers remain simulator, fixture, mock, or local-only;
- simulator login/token paths remain explicit dev/test opt-in only;
- evidence uses synthetic identifiers and masked values.

## Evidence Policy

Evidence updates must distinguish:

- commands actually run on the current branch;
- historical evidence from previous branches;
- structural validation;
- browser route/shell validation;
- env-gated tests that were skipped;
- live API execution against a running synthetic stack;
- hosted CI execution;
- hosted CI blocked before runner startup.

Generated JSON evidence may be committed only when it is produced by a command
run during the relevant task and the command/result is documented. Existing
generated artifacts must not be manually edited to imply a fresh pass.

## Hosted CI Vs Local Evidence Policy

Local evidence can prove local scripts, typechecks, Testcontainers, Docker
Compose, Playwright, and synthetic runtime behavior. It cannot prove hosted
GitHub Actions green.

Hosted CI evidence must include:

- commit SHA;
- GitHub Actions run URL;
- job names and conclusions;
- whether jobs started on a runner;
- failure or blocked reason;
- local fallback commands, listed separately.

If GitHub billing/spending limits or runner allocation issues block jobs before
startup, the status is `blocked`, not `pass`.

## Historical Phase 0 Command Evidence

Commands already used for the original Phase 0 inventory:

- `git status --short --branch`
- `rg --files`
- `sed -n '1,260p' PLAN.md`
- `sed -n '1,260p' BANKING_LAB_CODEX_PROMPT.md`
- `sed -n '1,260p' AGENTS.md`
- `rg -n "^(test|describe|it)\(|node:test|assert|suite|\btest\(" tests/*.test.mjs`
- `sed -n '1,260p' README.md`
- `sed -n '1,320p' docs/implementation-coverage-matrix.md`
- `sed -n '1,260p' docs/codex/remaining-hardening-status.md`
- `sed -n '1,260p' docs/test-evidence/evidence-gap-report.md`
- `sed -n '1,320p' package.json`
- `sed -n '1,340p' .github/workflows/ci.yml`
- `rg -n "^#|^##" docs/adr/*.md docs/architecture/*.md docs/migration/*.md`
- `find services/core-banking/src/main/kotlin services/payment-service/src/main/kotlin services/notification-service/src/main/kotlin services/reporting-service/src/main/kotlin -maxdepth 5 -type f`
- `find apps/customer-web/src apps/staff-terminal/src packages/api-client/src contracts/openapi contracts/events contracts/asyncapi screen-manifests -maxdepth 4 -type f`

Verification commands run for this Phase 0 baseline:

- `npm test`: pass, 178 tests.
- `npm run validate:manifests`: pass, 67 screen manifests.
- `npm run packages:typecheck`: pass for screen-engine, form-engine,
  api-client, and auth-client.
- `npm run scripts:typecheck`: pass.

## Current Baseline Refresh Evidence

This refresh is documentation and structural-test only. It does not regenerate
runtime evidence, alter generated JSON artifacts, or claim fresh hosted CI.

Commands run for this refresh:

- `node --test tests/finalHardeningBaseline.test.mjs`: pass, 1 test.
- `npm test`: pass, 191 tests.
- `npm run validate:manifests`: pass, 73 screen manifests.
- `npm run packages:typecheck`: pass for screen-engine, form-engine,
  api-client, and auth-client.
- `npm run scripts:typecheck`: pass.

## Domain Invariants Affected

Phase 0 is documentation-only and does not alter banking domain behavior.
Ledger double-entry, projection, idempotency, closed-day, reversal, adjustment,
maker-checker, reason-required audit, masking, outbox, Temporal, and Keycloak
controls are not changed by this baseline.

## Next Smallest Safe Task

After Phase 0 verification, the next smallest safe task is Phase 1:

- normalize `docs/implementation-coverage-matrix.md` status values;
- add or update a status-consistency test;
- clean README/evidence phrasing that conflicts with the normalized status
  model;
- rerun `npm test`, `npm run validate:manifests`, and
  `npm run evidence:refresh-check`.
