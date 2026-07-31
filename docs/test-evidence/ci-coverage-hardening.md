# CI Coverage Hardening

Review date: 2026-07-31

Scope: Phase 1 of the remaining-hardening plan. This evidence covers CI wiring only. It does not claim live Docker runtime smoke, production deployment, real payment-network connectivity, real customer data, real PII, real KYC, real card-network, or Open Banking integration.

## Pull Request Portfolio Gate

The pull-request workflow is `.github/workflows/portfolio-gate.yml`. It is deliberately scoped to the payment, ledger, settlement, and reconciliation portfolio story so reviewers receive a fast, relevant signal without removing any broad validation from the repository.

| Job | Command surface | Risk reduced |
| --- | --- | --- |
| `node-contracts` | `npm ci`, `npm run portfolio:verify:node` | Protects customer ownership, `LEDGER_POSTED` semantics, settlement provenance, 3-way reconciliation, manifests, shared packages, contracts, Helm fallback, and CI boundary guards. |
| `core-payment` | `npm run portfolio:verify:backend` | Runs Core Banking and Payment Service units plus focused PostgreSQL integration tests for ledger integrity, ledger evidence, settlement import/batches, and 3-way reconciliation. |
| `ops-console` | ops-console typecheck and build | Proves the representative settlement operations surface compiles as a target Next.js application. |
| `settlement-playwright` | `npm run test:e2e:portfolio` | Drives independent CSV → batch → 3-way → exception queue and asserts the Payment Service contract paths and request bodies. |

The gate pins Node 24, cancels superseded runs for the same PR, and keeps all data and dependencies synthetic.

## Full Validation Jobs And Risk Coverage

The broad workflow remains `.github/workflows/ci.yml`. It runs after pushes to `main`, on a nightly schedule, or through `workflow_dispatch`; it no longer duplicates every pull-request run.

| Job | Command surface | Risk reduced |
| --- | --- | --- |
| `node-and-manifests` | `npm ci`, `npm test`, `npm run validate:manifests`, package/script typechecks, `npm run ci:check-workflow` | Keeps the Node oracle/reference tests, manifest catalog, shared packages, scripts, and CI wiring guard green. |
| `next-builds` | Typecheck and build for every channel app | Prevents customer, staff, complaint, ops, audit, FDS/AML, admin, and call-center channel regressions. |
| `backend-core-banking` | Core-banking unit and integration tests | Protects ledger, audit, maker-checker, structured error, Keycloak/JWKS, Temporal/outbox, and PostgreSQL control paths. |
| `backend-payment-service` | Payment-service unit and integration tests | Prevents payment bounded-context regressions in idempotency, ledger posting, settlement import, reconciliation, cancellation approval, outbox, and authorization. |
| `backend-notification-service` | Notification-service unit and integration tests | Prevents notification bounded-context regressions in templates, preferences, masking, provider retry/dead-letter, event consumer, and authorization. |
| `backend-reporting-service` | Reporting-service unit and integration tests | Prevents reporting bounded-context regressions in artifact generation, export metadata, retention, outbox, and authorization. |
| `backend-all-gradle` | Aggregate unit test tasks for all Spring services | Catches Gradle multi-project wiring breakage and cross-module build drift. |
| `platform-validation` | `npm run platform:validate` | Validates Kubernetes, Helm, and Argo CD structural expectations without claiming a live cluster. |
| `contracts-validation` | OpenAPI/AsyncAPI/client/runtime-envelope structural gates | Guards checked-in contracts, generated DTO/path diff, shared client drift, and event schema references. |
| `compose-platform-config` | `docker compose config`, `docker compose --profile platform config` | Ensures default and platform Compose profiles render syntactically and include the configured topology. |
| `playwright-manifest-e2e` | `npm run test:e2e` | Preserves the complete manifest-backed browser shell and conditional API smoke coverage. |
| `security-evidence` | `npm audit --audit-level=high`, `npm run security:evidence` | Keeps SCA/security evidence generation connected to CI with explicit scanner/DAST skip reporting. |
| `formal-model` | `npm run formal:ledger` | Keeps the executable ledger/idempotency formal check in full validation. |

## PR CI Versus Manual/Nightly CI

Default PR CI is now the focused `Portfolio gate` above. It covers the representative financial vertical slice, including actual Spring/PostgreSQL integration tests and the dedicated browser path, while avoiding duplicate builds of unrelated channels and services.

Main, nightly, or manual `Full validation` retains the broader checks:

- all Node oracle/reference and target-stack scaffold tests;
- every TypeScript package and script typecheck;
- all eight Next.js channel typechecks and builds;
- all four Spring service unit/integration suites and the aggregate Gradle unit task;
- broad Playwright manifest E2E;
- contract, Kubernetes, Helm, Argo CD, and Compose structural validation;
- security evidence and the formal ledger model.

Higher-cost live evidence remains outside both deterministic workflows when it requires dedicated credentials or infrastructure:

- Docker live platform smoke and multi-node storage drills;
- Keycloak live realm/browser smoke;
- ZAP/DAST against a live synthetic target;
- PostgreSQL live backup/restore and disaster-recovery drills;
- synthetic load and large-ledger benchmarks.

## Fallback Policy

- Docker Compose config rendering stays in full validation because it does not start services but is outside the focused PR story.
- Kubernetes/Helm/Argo CD validation remains structural. If `kubectl` or a live cluster is missing, validation must record an explicit skip or structural-only status rather than a live-cluster pass.
- Helm rendering may use the existing Node structural fallback only when the Helm CLI is unavailable; evidence must identify the fallback renderer.
- Phase 4 adds `contracts:lint`, `contracts:check-client`, and `contracts:check-events`. If these scripts are unavailable or skipped in a future branch, CI/evidence must explicitly record the skipped gate rather than claiming contract drift prevention.
- Security evidence must record scanner/DAST skip reasons when Docker, scanner images, or `BANKING_LAB_DAST_URL` are unavailable.

## No Unrun Pass Claims

CI and evidence documents must distinguish:

- `pass`: command actually ran and exited successfully.
- `failed`: command ran and exited unsuccessfully.
- `skipped`: command was intentionally not run, with an explicit reason.
- `not implemented`: script or feature does not exist yet.

Do not mark historical evidence as current Phase 1 pass evidence unless the command was rerun for this phase. Do not reuse generated evidence files as proof of a command that was not executed during the current phase.

## Historical Phase 1 Local Verification

Local verification for this phase ran on 2026-06-06:

| Command | Result | Notes |
| --- | --- | --- |
| `npm run ci:check-workflow` | pass | Confirmed 13 required jobs are wired. |
| `node --test tests/springScaffold.test.mjs tests/nextScaffold.test.mjs tests/stackRetirementAreaAudit.test.mjs tests/ciHardening.test.mjs` | pass | 27 tests passed. |
| `npm run scripts:typecheck` | pass | Validated the new TypeScript CI checker. |
| `npm run platform:validate` | pass | K8s validation passed with `kubectl skipped_no_cluster`; Helm and Argo CD structural validation passed. |
| `docker compose --profile platform config` | pass | Rendered full platform profile. |
| `npm run test:payment-service:unit` | sandbox failed, escalated pass | Sandbox failed before task execution on Gradle file-lock socket; approved rerun passed. |
| `npm run test:payment-service:integration` | sandbox failed, escalated pass | Sandbox failed before task execution on Gradle file-lock socket; approved rerun passed. |
| `npm run test:notification-service:unit` | sandbox failed, escalated pass | Sandbox failed before task execution on Gradle file-lock socket; approved rerun passed. |
| `npm run test:notification-service:integration` | sandbox failed, escalated pass | Sandbox failed before task execution on Gradle file-lock socket; approved rerun passed. |
| `npm run test:reporting-service:unit` | sandbox failed, escalated pass | Sandbox failed before task execution on Gradle file-lock socket; approved rerun passed with no unit test sources. |
| `npm run test:reporting-service:integration` | sandbox failed, escalated pass | Sandbox failed before task execution on Gradle file-lock socket; approved rerun passed. |
| `scripts/run-core-banking-tests.sh :services:core-banking:test :services:payment-service:test :services:notification-service:test :services:reporting-service:test` | sandbox failed, escalated pass | Validated the aggregate Gradle unit command used by `backend-all-gradle`. |
| `npm test` | pass | 169 tests passed after adding `tests/ciHardening.test.mjs`. |

Useful rerun set:

```bash
npm run ci:check-workflow
node --test tests/springScaffold.test.mjs tests/nextScaffold.test.mjs tests/stackRetirementAreaAudit.test.mjs
npm run test:payment-service:unit
npm run test:payment-service:integration
npm run test:notification-service:unit
npm run test:notification-service:integration
npm run test:reporting-service:unit
npm run test:reporting-service:integration
npm run platform:validate
docker compose --profile platform config
```

Commands not run for a given branch must be listed in the final task report with the reason.
