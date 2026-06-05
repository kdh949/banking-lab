# Reporting Service Evidence

Review date: 2026-06-05

Scope: supporting Reporting Service first slice from `docs/codex/goal-mode/full-platform-completion/supporting/03-reporting-service.md`. The service is target-stack Kotlin/Spring Boot with PostgreSQL metadata persistence. It does not generate real regulatory filings, real statements, real PII, or real external-bank artifacts.

## Implemented Surface

- `services/reporting-service` is registered in Gradle as a standalone Spring Boot service.
- `GET /api/reports/catalog` returns seeded synthetic report definitions and requires a business reason.
- `POST /api/reports/artifacts` creates metadata-only report artifacts with idempotency by `(requested_by, idempotency_key)`.
- `GET /api/reports/artifacts` lists generated artifacts with a reason-required audit event.
- `report_definitions`, `report_artifacts`, and `reporting_access_audit_events` are created by Flyway.
- Docker Compose platform profile exposes `reporting-service` with a dedicated `reporting_flyway_schema_history` table and Flyway baseline version `0` on the shared synthetic PostgreSQL database.
- Prometheus scrapes `reporting-service:8090` through the platform observability profile.
- Raw Kubernetes and Helm manifests define a reporting-service Deployment/Service with the reporting audience and dedicated Flyway table.
- Reporting routes are role-gated for `AUDITOR`, `COMPLIANCE_MANAGER`, `OPS_MANAGER`, and `REPORTING_ANALYST`.
- Signed JWKS JWTs are the default path; simulator tokens are only accepted when explicitly enabled for local tests.
- Structured errors include the reporting docs pointer and `syntheticOnly=true`.
- Live Keycloak client-credentials smoke proves the confidential
  `reporting-service-api` service account can call catalog, generate, and list
  report artifact routes with simulator tokens disabled.

## Controls

- Report access and generation require a business reason.
- Report artifacts are metadata-only and `masked_by_default=true`.
- The schema seeds only synthetic report types: `AUDIT_SUMMARY`, `OPERATIONS_DAILY`, and `EVIDENCE_COVERAGE`.
- Idempotent report generation prevents duplicate artifacts for an external retry key.
- Reporting access appends `REPORT_CATALOG_VIEW`, `REPORT_GENERATED`, `REPORT_GENERATE_REPLAYED`, and `REPORT_ARTIFACT_LIST_VIEW` audit rows.

## Verification

- `npm run test:reporting-service:integration -- --tests lab.banking.reporting.ReportingServiceIntegrationTest --rerun-tasks`
- `npm run test:reporting-service:keycloak-service-token`
- `docker compose --profile platform config`
- `npm run k8s:validate`
- `npm run helm:template`
- `npm run security:posture-check`
- `npm test`
- `npm run evidence:refresh-check`
- `npm run node:retirement-gate`

## Remaining Risk

- This slice stores artifact metadata only; runnable report rendering, retention lifecycle, export packaging, and channel UI wiring remain pending.
- No Kafka/Outbox dispatch is added for report-generated events yet.
- Live Kubernetes/Helm rollout and browser/channel propagation for reporting
  routes remain future work.
