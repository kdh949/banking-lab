# CI Evidence

Date: 2026-06-04

## Workflow

`.github/workflows/ci.yml` now defines:

- `node-and-manifests`
- `next-builds`
- `backend-core-banking`
- `playwright-manifest-e2e`
- `security-evidence`
- `formal-model`

Triggers:

- `pull_request`
- `push` to `main`
- `workflow_dispatch`

Permissions default to `contents: read`.

## Notes

The security job runs `npm audit --audit-level=high` and `npm run security:evidence`. Tool-specific skips from Semgrep, Trivy, SBOM, or DAST are recorded by the wrapper and uploaded as artifacts; they are not described as completed security scans unless the tools actually ran.

The formal job runs `npm run formal:ledger`. If a TLC runner is available, generated evidence must record the actual TLC result for both root models. If TLC is unavailable, generated evidence must still show the executable bounded state-search checker passing with `staticOnly: false`; static-only mode is not accepted in CI.

## 2026-06-07 Local CI-Equivalent Validation

Branch: `codex/customer-onboarding-self-service`

Matrix baseline commit: `aed3b563a0d5caf07e099f24e8d4fabcb12fd7b3`

Post-fix verification commit: `480873add2d956ea9f1ff70554b5f17367f08abb`

PR: `#54`

Hosted GitHub Actions CI is blocked by billing/spending-limit and has not passed. Inspected PR #54 check-run annotations consistently say: `The job was not started because recent account payments have failed or your spending limit needs to be increased. Please check the 'Billing & plans' section in your settings`. Head-specific hosted-CI block records are also posted in the PR discussion.

Local validation used the existing local dependency and Playwright browser installation. CI setup commands such as `npm ci`, `actions/setup-node`, `actions/setup-java`, `gradle/actions/setup-gradle`, and `npx playwright install --with-deps chromium` were not re-run locally; the commands below are the runnable validation equivalents of the workflow steps.

| Hosted job | Local command(s) | Result |
| --- | --- | --- |
| `node-and-manifests` | `npm test`; `npm run validate:manifests`; `npm run packages:typecheck`; `npm run scripts:typecheck`; `npm run ci:check-workflow` | Pass. `npm test` passed 182 tests. Manifest validation passed 115 screen manifests. Package and script typechecks passed. CI workflow hardening check passed with 13 required jobs wired. |
| `next-builds` matrix | `npm run next:<app>:typecheck` and `npm run next:<app>:build` for `customer-web`, `staff-terminal`, `complaint-portal`, `ops-console`, `audit-console`, `fds-aml-console`, and `admin-console` | Pass. All seven app typechecks and production builds completed successfully. |
| `backend-core-banking` | `scripts/run-core-banking-tests.sh :services:core-banking:test`; `scripts/run-core-banking-tests.sh :services:core-banking:integrationTest`; `npm run test:core-banking:integration -- --tests lab.banking.core.resilience.MultiInstanceLedgerHaDrIntegrationTest`; `npm run test:core-banking:integration -- --tests lab.banking.core.complaint.ComplaintCaseApiParityIntegrationTest`; `npm run test:core-banking:integration` | Pass after follow-up fix. Unit tests passed in the original matrix. Full integration initially completed 176 tests with 2 failed and 35 skipped because `MultiInstanceLedgerHaDrIntegrationTest` used independent security-disabled non-web Spring contexts that could not create `CustomerAuthService` without a `PasswordEncoder` bean. Commit `480873add2d956ea9f1ff70554b5f17367f08abb` moved the password encoder into a web-independent Spring configuration. The exact HA test then passed, an intervening full-suite rerun hit a Flyway/PostgreSQL read timeout in `ComplaintCaseApiParityIntegrationTest`, the complaint class passed on isolated rerun, and the final full core-banking integration rerun completed successfully. |
| `backend-payment-service` | `scripts/run-core-banking-tests.sh :services:payment-service:test`; `scripts/run-core-banking-tests.sh :services:payment-service:integrationTest` | Pass. Both tasks completed successfully. |
| `backend-notification-service` | `scripts/run-core-banking-tests.sh :services:notification-service:test`; `scripts/run-core-banking-tests.sh :services:notification-service:integrationTest` | Pass. Both tasks completed successfully. |
| `backend-reporting-service` | `scripts/run-core-banking-tests.sh :services:reporting-service:test`; `scripts/run-core-banking-tests.sh :services:reporting-service:integrationTest` | Pass. Unit task was `NO-SOURCE`; integration task completed successfully. |
| `backend-all-gradle` | `scripts/run-core-banking-tests.sh :services:core-banking:test :services:payment-service:test :services:notification-service:test :services:reporting-service:test` | Pass. Aggregate unit task completed successfully. |
| `platform-validation` | `npm run platform:validate` | Pass with local cluster caveat. Kubernetes structural validation passed 27 resources with `kubectl skipped_no_cluster`; Helm template validation passed 27 rendered resources; Argo CD validation passed 2 applications. |
| `contracts-validation` | `npm run contracts:lint`; `npm run contracts:check-client`; `npm run contracts:check-events`; `node --test tests/springScaffold.test.mjs tests/nextScaffold.test.mjs tests/stackRetirementAreaAudit.test.mjs` | Pass. Contract lint validated 4 OpenAPI files and 1 AsyncAPI file. API client check matched 153 operation IDs against 146 shared client methods/exemptions. Event contract check validated 17 AsyncAPI schema references. Structural Node tests passed 26 tests. |
| `compose-platform-config` | `docker compose config`; `docker compose --profile platform config` | Pass. Both Compose configs rendered successfully. |
| `playwright-manifest-e2e` | `npm run test:e2e` | Pass with environment skips. Playwright passed 19 tests and skipped 59 live API/Keycloak/payment/notification-backed smokes because the corresponding local runtime and token environment variables were not supplied. |
| `security-evidence` | `npm audit --audit-level=high`; `npm run security:evidence`; `env BANKING_LAB_DAST_URL=http://host.docker.internal:18132/health npm run security:evidence:docker` | Pass. Direct `npm audit` found 0 vulnerabilities. The first sandboxed wrapper run failed on registry DNS and Docker socket access; the approved rerun without DAST URL passed npm audit, Semgrep SAST, Trivy FS, and CycloneDX SBOM with DAST skipped. A disposable local synthetic core-banking target was then started and the Docker-forced rerun passed all 5 checks, including ZAP DAST. |
| `formal-model` | `npm run formal:ledger` | Pass. Formal ledger executable model check passed 3335 states, 8241 transitions, and 15 invariants. |

Overall local CI-equivalent status: passed locally after the follow-up `backend-core-banking` fix and rerun. This local run is not a substitute for a passed hosted CI run; it is evidence collected while hosted CI is blocked before runner startup.
