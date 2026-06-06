# Phase 6 Operations And Security Evidence

Date: 2026-06-06

Branch: `codex/remaining-hardening-phase-6-ops-security`

Scope: Phase 6 of `docs/codex/remaining-hardening-goals.md` for the synthetic banking lab. This work does not add real customer money, real personal data, real payment/card-network integration, Open Banking, real KYC providers, or external financial institution APIs.

## Implemented Controls

- Added secrets hygiene controls through `.env.example`, `docs/security/secrets-management.md`, `scripts/check-secret-placeholders.ts`, and `npm run security:secrets-check`.
- Added a production-like default-secret startup guard in core banking and targeted Spring tests for the guard.
- Added Micrometer-backed metrics for ledger command latency/errors, idempotency replays, outbox pending/dead-letter backlog, authorization denials, and audit append failures.
- Added structural observability assets and docs:
  - `infra/observability/prometheus-rules.yaml`
  - `infra/observability/grafana-dashboard-core-banking.json`
  - `infra/observability/grafana-dashboard-outbox.json`
  - `docs/operations/slo.md`
  - `docs/operations/observability.md`
- Added five operational runbooks with required incident sections:
  - `docs/operations/runbooks/ledger-drift.md`
  - `docs/operations/runbooks/outbox-dead-letter.md`
  - `docs/operations/runbooks/authz-denial-spike.md`
  - `docs/operations/runbooks/eod-failure.md`
  - `docs/operations/runbooks/postgres-restore.md`
- Added `V036__audit_export_jobs.sql` for `audit_export_jobs` and `audit_export_files`.
- Added the general audit export workflow:
  - `POST /api/audit/exports`
  - `GET /api/audit/exports/{exportId}`
  - `POST /api/audit/exports/{exportId}/approve`
  - `POST /api/audit/exports/{exportId}/reject`
- Added audit-console API-backed smoke wiring for audit export request, replay, approval, validation failure, authorization failure, and export metadata display.

## Control Verdict

Passed for the implemented Phase 6 slice:

- Audit export is limited to `AUDITOR` and `COMPLIANCE_MANAGER` roles.
- Export request/read/approve/reject paths are reason-required.
- Export request/approve/reject POST paths are step-up protected by the existing policy enforcer.
- Request submission is idempotency-key protected and detects command-hash conflicts.
- Export materialization requires an independent checker through maker-checker approval.
- Export output is local synthetic NDJSON/JSONL content with `syntheticOnly=true`.
- Export metadata records row count, hash-chain start/end, payload SHA-256, storage URI, and `ledgerRowsMutated=false`.
- Export execution appends audit events and does not insert, update, or delete ledger transaction rows.
- The Semgrep raw balance-mutation rule now passes after replacing an AML/FDS dirty-data test fixture update with a temporary-table rebuild.

## Commands Run

| Command | Result | Notes |
| --- | --- | --- |
| `npm run security:secrets-check` | first run failed, final rerun pass | Initial scanner version flagged broad placeholder-like strings. Final rerun passed across 856 files after allowlist/fixture tuning. |
| `scripts/run-core-banking-tests.sh :services:core-banking:test --tests '*SpringSecurityResourceServerTest'` | sandbox failed, escalated pass | Validated the production-like default-secret guard. Sandbox failure was the known Gradle file-lock socket restriction. |
| `npm run observability:validate` | pass | Validated observability docs/assets/runbooks and required metric coverage. |
| `scripts/run-core-banking-tests.sh :services:core-banking:integrationTest --tests '*ObservabilityActuatorIntegrationTest'` | escalated pass | Verified actuator/prometheus exposure after adding Phase 6 metric wiring. |
| `npm run test:core-banking:integration -- --tests '*AuditExport*'` | first run failed, final rerun pass | First run exposed a PostgreSQL `FOR UPDATE` issue on a nullable outer-join side; final rerun passed after locking only the export job alias. |
| `npm run contracts:lint` | pass | OpenAPI lint passed after adding audit export operations. |
| `npm run contracts:check-client` | pass | The shared API client covers the audit export operation IDs. |
| `npm run scripts:typecheck` | pass | Typechecked TypeScript scripts after observability validator updates. |
| `npm run packages:typecheck` | pass | Shared package typechecks passed after audit export API-client DTOs/methods. |
| `npm run next:audit-console:typecheck` | pass | Audit console typecheck passed after adding the API-backed audit export smoke block. |
| `npm run next:audit-console:build` | pass | Audit console production build passed. |
| `npm run analytics:fds-aml:test` | sandbox failed, equivalent escalated `uv` pass | Sandbox run failed on PyPI DNS for `hatchling`. `uv --cache-dir .uv-cache run --directory analytics/aml-fds-python --project . --extra dev pytest` passed 10 Python tests after the dirty-fixture rewrite. |
| `npm run security:evidence` | sandbox failed, first escalated run failed, escalated rerun pass with DAST skipped | Sandbox lacked npm registry/Docker access. First escalated run passed npm audit/SBOM but failed Semgrep and Trivy DB download. Later rerun passed npm audit, Semgrep, Trivy filesystem scan, and SBOM; DAST was skipped because `BANKING_LAB_DAST_URL` was unset. |
| `docker network create banking-lab-dast-live` | escalated pass | Created disposable synthetic DAST network. |
| `docker run -d --rm --name banking-lab-dast-postgres --network banking-lab-dast-live ... postgres:16-alpine` | escalated pass | Started disposable synthetic PostgreSQL dependency. |
| `docker run -d --rm --name banking-lab-dast-core --network banking-lab-dast-live -p 18132:8081 ... banking-lab-dast-smoke-core-banking:latest` | escalated pass | Started disposable synthetic core-banking DAST target. |
| `curl -fsS http://127.0.0.1:18132/health` | pass | Confirmed synthetic health endpoint before DAST. |
| `BANKING_LAB_DAST_URL=http://host.docker.internal:18132/health npm run security:evidence:docker` | escalated pass | Docker-forced npm audit, Semgrep, Trivy filesystem scan, SBOM, and ZAP baseline DAST all passed. |
| `docker stop banking-lab-dast-core` | escalated pass | Cleaned up disposable DAST target. |
| `docker stop banking-lab-dast-postgres` | escalated pass | Cleaned up disposable DAST database. |
| `docker network rm banking-lab-dast-live` | escalated pass | Removed disposable DAST network. |
| `npm test` | pass | Final Phase 6 Node structural/oracle suite passed with 172 tests after the Docker-forced generated security evidence refresh. |
| `gh pr view 50 --json mergeStateStatus,statusCheckRollup,url` | pass | PR #50 is mergeable but unstable because hosted CI jobs failed before runner startup. |
| `gh api /repos/kdh949/banking-lab/check-runs/79873771310/annotations` | pass | GitHub Actions reported that the job was not started because account payments/spending limits blocked runner allocation. |

## Generated Security Evidence

`BANKING_LAB_DAST_URL=http://host.docker.internal:18132/health npm run security:evidence:docker` produced the following final summary:

| Check | Final status |
| --- | --- |
| npm audit high | pass |
| Semgrep SAST | pass |
| Trivy filesystem scan | pass |
| CycloneDX SBOM | pass |
| ZAP baseline DAST | pass |

Generated artifact paths:

- `docs/test-evidence/generated/security-evidence-summary.md`
- `docs/test-evidence/generated/security-evidence-summary.json`
- `docs/test-evidence/generated/npm-audit.txt`
- `docs/test-evidence/generated/semgrep.json`
- `docs/test-evidence/generated/trivy-fs.json`
- `docs/test-evidence/generated/sbom.cdx.json`
- `docs/test-evidence/generated/zap-baseline.json`
- `docs/test-evidence/generated/zap-baseline.log`

## Not Run

- Live Prometheus/Grafana/Loki/Tempo alert-routing evidence was not rerun in this phase. This phase added structural observability assets, application metrics, and runbook validation.
- Full cross-service Gradle suites were not rerun for Phase 6; the changed backend areas were covered by targeted Spring tests and the audit export integration test.
- PR #50 hosted CI jobs did not execute because GitHub account billing/spending limits blocked runner allocation before job steps started. This is recorded as external CI availability evidence, not a passed hosted CI run.

## Residual Risk

- The audit export storage path is a local synthetic artifact store suitable for the lab. It is not an enterprise WORM/object-store implementation.
- The runbooks are structural local runbooks; live alert routing, on-call paging, and recovery drills require a running synthetic platform stack.
- `security:evidence` depends on external scanner/network availability. The final rerun passed, but future reruns can still fail on scanner DB availability rather than project code.
