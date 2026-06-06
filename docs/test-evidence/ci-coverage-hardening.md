# CI Coverage Hardening

Review date: 2026-06-06

Scope: Phase 1 of the remaining-hardening plan. This evidence covers CI wiring only. It does not claim live Docker runtime smoke, production deployment, real payment-network connectivity, real customer data, real PII, real KYC, real card-network, or Open Banking integration.

## PR CI Jobs And Risk Coverage

| Job | Command surface | Risk reduced |
| --- | --- | --- |
| `node-and-manifests` | `npm ci`, `npm test`, `npm run validate:manifests`, package/script typechecks, `npm run ci:check-workflow` | Keeps the Node oracle/reference tests, manifest catalog, shared packages, scripts, and CI wiring guard green. |
| `next-builds` | Typecheck and build for every channel app | Prevents customer, staff, complaint, ops, audit, FDS/AML, and admin channel regressions. |
| `backend-core-banking` | Core-banking unit and integration tests | Protects ledger, audit, maker-checker, structured error, Keycloak/JWKS, Temporal/outbox, and PostgreSQL control paths. |
| `backend-payment-service` | Payment-service unit and integration tests | Prevents payment bounded-context regressions in idempotency, settlement bridge, cancellation approval, outbox, and authorization. |
| `backend-notification-service` | Notification-service unit and integration tests | Prevents notification bounded-context regressions in templates, preferences, masking, provider retry/dead-letter, event consumer, and authorization. |
| `backend-reporting-service` | Reporting-service unit and integration tests | Prevents reporting bounded-context regressions in artifact generation, export metadata, retention, outbox, and authorization. |
| `backend-all-gradle` | Aggregate unit test tasks for all Spring services | Catches Gradle multi-project wiring breakage and cross-module build drift early. |
| `platform-validation` | `npm run platform:validate` | Validates Kubernetes, Helm, and Argo CD structural expectations without claiming a live cluster. |
| `contracts-validation` | Existing scaffold/contract structural tests | Guards checked-in OpenAPI/AsyncAPI/Temporal contract anchors until Phase 4 adds full contract lint/drift scripts. |
| `compose-platform-config` | `docker compose config`, `docker compose --profile platform config` | Ensures default and platform Compose profiles render syntactically and include configured service topology. |
| `playwright-manifest-e2e` | `npm run test:e2e` | Preserves manifest-backed browser shell and conditional API smoke coverage. |
| `security-evidence` | `npm audit --audit-level=high`, `npm run security:evidence` | Keeps SCA/security evidence generation connected to CI with explicit scanner/DAST skip reporting. |
| `formal-model` | `npm run formal:ledger` | Keeps the executable ledger/idempotency formal check in CI. |

## PR CI Versus Manual/Nightly CI

Default PR CI is intentionally limited to deterministic or structurally bounded checks:

- Node oracle/reference and target-stack scaffold tests.
- TypeScript package/script typechecks.
- Next.js channel typechecks and builds.
- Spring service unit/integration tests.
- Playwright manifest E2E.
- Contract structural tests using existing checked-in artifacts.
- Platform structural validation and Compose config rendering.
- Formal model and security evidence scripts.

Manual or nightly jobs should remain separate for higher-cost or environment-dependent evidence:

- Docker live smoke.
- Keycloak live realm/browser smoke.
- Full platform live runtime smoke.
- ZAP/DAST against a live synthetic target.
- Docker-backed Semgrep/Trivy/SBOM reruns when scanner images are unavailable in normal CI.
- PostgreSQL live backup/restore drill.
- Synthetic load tests.
- Large-ledger dataset and query benchmark once Phase 8 adds those scripts.

## Fallback Policy

- Docker Compose config rendering is allowed in PR CI because it does not start services.
- Kubernetes/Helm/Argo CD validation remains structural. If `kubectl` or a live cluster is missing, validation must record an explicit skip or structural-only status rather than a live-cluster pass.
- Helm rendering may use the existing Node structural fallback only when the Helm CLI is unavailable; evidence must identify the fallback renderer.
- Contract validation is structural in Phase 1. It must not be described as full OpenAPI/AsyncAPI drift prevention until Phase 4 adds `contracts:lint`, `contracts:check-client`, and `contracts:check-events`.
- Security evidence must record scanner/DAST skip reasons when Docker, scanner images, or `BANKING_LAB_DAST_URL` are unavailable.

## No Unrun Pass Claims

CI and evidence documents must distinguish:

- `pass`: command actually ran and exited successfully.
- `failed`: command ran and exited unsuccessfully.
- `skipped`: command was intentionally not run, with an explicit reason.
- `not implemented`: script or feature does not exist yet.

Do not mark historical evidence as current Phase 1 pass evidence unless the command was rerun for this phase. Do not reuse generated evidence files as proof of a command that was not executed during the current phase.

## Phase 1 Local Verification

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
