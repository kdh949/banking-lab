# Final Hardening Baseline

Review date: 2026-06-10

Branch: `codex/final-hardening-baseline`

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
  ops console, audit console, FDS/AML console, and admin console.
- `customer-web` includes route pages for signup, login, accounts, transfers,
  complaints, security, cards, loans, payments, and notifications.
- `staff-terminal` is currently an iWorks-style integrated terminal shell with
  `/` and `/api/terminal-status`; Spring staff-control APIs remain backend
  covered but are not exposed as the previous staff manifest route set.
- Shared TypeScript packages include screen engine, form engine, API client,
  and auth client support.
- OpenAPI files exist for core banking, payment, notification, and reporting.
- AsyncAPI and event JSON schemas exist for the current event envelope catalog.
- Screen manifests remain for customer, complaint, ops, audit, FDS/AML, and
  admin channels; staff-terminal manifests are not present in the current
  target UI model.
- Python/DuckDB/scikit-learn AML/FDS analytics and data-quality evidence
  tooling exist under `analytics/aml-fds-python`.
- Docker Compose, Kubernetes, Helm, Terraform, Argo CD, observability, security,
  and evidence scripts are present.

## Current Documentation And Evidence Tensions

The repository is advanced, but documentation is not fully normalized against
the current PLAN.

- `README.md` still mixes broad completed claims, historical Node-oracle
  wording, and current target-stack claims that need sharper separation.
- `docs/implementation-coverage-matrix.md` declares a limited status enum, but
  rows also use values such as `route-backed-live-gated`,
  `integrated-terminal`, and `backend-control-covered`. Phase 1 must normalize
  this before the matrix can be used as a reliable status source.
- Existing evidence documents contain long historical command logs from earlier
  branches. They are useful as prior evidence, but they must not be presented
  as commands rerun for this branch.
- Hosted GitHub Actions are documented in prior evidence as blocked by account
  billing/spending-limit restrictions. That is a blocked hosted-CI condition,
  not a green CI result.
- Several Playwright live API flows are env-gated. A skipped live API test is
  not proof of route-to-live-API execution.
- Current contract gates are structural. PLAN Phase 4 still requires DTO-level
  generated OpenAPI diffing and runtime event-envelope validation.

## Remaining Hardening Gaps

The active PLAN identifies these portfolio-completion gaps:

1. README, coverage matrix, and evidence/status documents need consistency
   cleanup without inflating implementation status.
2. Hosted CI evidence must be separated from local evidence, and blocked hosted
   CI must remain explicitly blocked rather than marked green.
3. `customer-web` and `staff-terminal` need live API route execution evidence
   for the major flows, not only manifest, shell, or fixture coverage.
4. OpenAPI DTO-level generated diffing and runtime event-envelope validation
   are still missing.
5. A call-center agent workflow is missing as a dedicated app or staff-terminal
   API-backed workflow slice.
6. Final scorecard and demo script must be regenerated from actual verified
   capabilities and residual limitations.

## Scope For This Workstream

This workstream should proceed in small, commit-sized branches:

- Phase 0: create this baseline and rerun the local baseline commands.
- Phase 1: normalize README, coverage matrix status values, and related
  evidence/status docs; add a matrix/status consistency test if none exists.
- Phase 2: strengthen CI self-check and add hosted CI status evidence that
  clearly distinguishes green, failed, and externally blocked runs.
- Phase 3: add env-gated live API route execution evidence for customer-web and
  staff-terminal without treating skips as passes.
- Phase 4: add DTO-level OpenAPI diffing and runtime event-envelope validation.
- Phase 5: implement call-center workflow with reason-required audit, masking,
  escalation, after-call tasks, authorization, and synthetic-only controls.
- Phase 6: generate final scorecard and demo script only after the prior gates
  are accurate.

## Explicitly Out Of Scope For Phase 0

This baseline does not:

- change Spring domain behavior;
- change ledger postings, balance projection, or idempotency semantics;
- add new customer, staff, payment, reporting, notification, FDS/AML, or
  call-center functionality;
- rewrite README or the coverage matrix yet;
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

## Current Phase 0 Command Evidence

Commands already used for inventory in this branch:

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
