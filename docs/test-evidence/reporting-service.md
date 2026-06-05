# Reporting Service Evidence

Review date: 2026-06-06

Scope: supporting Reporting Service from `docs/codex/goal-mode/full-platform-completion/supporting/03-reporting-service.md`. The service is target-stack Kotlin/Spring Boot with PostgreSQL metadata and rendered synthetic JSON artifact persistence. It does not generate real regulatory filings, real statements, real PII, or real external-bank artifacts.

## Implemented Surface

- `services/reporting-service` is registered in Gradle as a standalone Spring Boot service.
- `GET /api/reports/catalog` returns seeded synthetic report definitions and requires a business reason.
- `POST /api/reports/artifacts` creates rendered synthetic JSON report artifacts with idempotency by `(requested_by, idempotency_key)`.
- `GET /api/reports/artifacts` lists generated artifacts with a reason-required audit event.
- `GET /api/reports/artifacts/{artifactId}/export` returns a synthetic JSON package simulation for a rendered artifact and requires a business reason.
- `POST /api/reports/retention/sweeps` expires artifacts past `retention_until`, records `SYNTHETIC_RETENTION_EXPIRED`, and requires an authorized ops/compliance/reporting actor.
- Report generation, export, and retention sweep completion append durable `reporting_outbox_events` rows in the same transaction as reporting metadata changes.
- `ReportingKafkaOutboxPublisher` reads allow-listed `PENDING reporting_outbox_events`, publishes reporting domain events to Redpanda/Kafka, and marks rows `PUBLISHED` only after broker acknowledgement.
- `ReportingKafkaOutboxPublisher` records broker failures as retryable `FAILED` rows until the configured dead-letter threshold moves the row to `DEAD_LETTER`.
- `ReportingDomainEventPublisherWorker` provides a disabled-by-default scheduled worker controlled by `banking-lab.reporting-service.domain-event-publisher.*` settings, including retry delay and dead-letter threshold.
- `report_definitions`, `report_artifacts`, and `reporting_access_audit_events` are created by Flyway; `V002__report_artifact_rendering.sql` adds `artifact_content`, `content_sha256`, `retention_policy`, `retention_until`, and `export_format`.
- `V004__reporting_outbox_events.sql` adds a durable `PENDING` outbox table with idempotency-key uniqueness for report-generated events.
- `V005__reporting_outbox_retry_dead_letter.sql` adds `retry_count`, `next_retry_at`, `error_message`, and `DEAD_LETTER` status support.
- Docker Compose platform profile exposes `reporting-service` with a dedicated `reporting_flyway_schema_history` table and Flyway baseline version `0` on the shared synthetic PostgreSQL database.
- Docker Compose platform profile also exposes `reporting-domain-event-publisher`, which uses the same Spring image with the reporting publisher enabled and the API container publisher mode disabled.
- Live Docker Compose smoke coverage starts PostgreSQL, Redpanda, and `reporting-domain-event-publisher`, inserts a synthetic pending report event, restarts the publisher, and verifies the row reaches `PUBLISHED` with a consumed Redpanda envelope and batch log.
- Prometheus scrapes `reporting-service:8090` through the platform observability profile.
- Prometheus also scrapes `reporting-domain-event-publisher:8090` for worker observability.
- Raw Kubernetes and Helm manifests define a reporting-service Deployment/Service plus a dedicated reporting-domain-event-publisher Deployment with the reporting audience, Redpanda bootstrap settings, and dedicated Flyway table.
- Reporting routes are role-gated for `AUDITOR`, `COMPLIANCE_MANAGER`, `OPS_MANAGER`, and `REPORTING_ANALYST`.
- Signed JWKS JWTs are the default path; simulator tokens are only accepted when explicitly enabled for local tests.
- Structured errors include the reporting docs pointer and `syntheticOnly=true`.
- Live Keycloak client-credentials smoke proves the confidential
  `reporting-service-api` service account can call catalog, generate, list, and
  export report artifact routes plus run the retention sweep route with
  simulator tokens disabled.
- TypeScript API client methods expose reporting catalog, rendered artifact
  generation, checksum/retention metadata, and artifact list contracts.
- Admin-console screen manifest `ADM-701` and API-backed panel wiring expose
  reporting catalog, artifact generation, artifact list, and artifact export
  package controls when `NEXT_PUBLIC_BANKING_REPORTING_API_BASE_URL` is
  configured.
- Audit-console screen manifest `AUD-401` and API-backed panel wiring expose
  reason-required reporting artifact history when
  `NEXT_PUBLIC_BANKING_REPORTING_API_BASE_URL` is configured.

## Controls

- Report access and generation require a business reason.
- Report artifacts persist rendered JSON payloads with `syntheticOnly=true`, `maskedByDefault=true`, `realPiiUsed=false`, `realMoneyUsed=false`, `externalFilingSubmitted=false`, and `ledgerRowsMutated=false`.
- Artifact responses expose `contentSha256`, `retentionPolicy=SYNTHETIC_7Y`, `retentionUntil`, and `exportFormat=JSON`.
- Export package responses include the rendered artifact content, content checksum, synthetic-only controls, `downloadSimulationOnly=true`, and `ledgerRowsMutated=false`.
- Retention sweeps change only report artifact metadata from `GENERATED` to `EXPIRED`, reject expired artifact export, record `REPORT_RETENTION_SWEEP_RUN`, and prove `ledgerRowsMutated=false`.
- Durable reporting outbox rows are created for `ReportArtifactGenerated`, `ReportArtifactExported`, and `ReportRetentionSweepCompleted`; idempotent generation replay does not create a duplicate generated event.
- Broker-published reporting envelopes carry `sourceService=reporting-service`, `syntheticOnly=true`, aggregate metadata, and payload controls proving `ledgerRowsMutated=false`.
- The reporting publisher uses an allow-list for reporting event types and Redpanda integration coverage proves `ReportArtifactGenerated` and `ReportArtifactExported` transition from `PENDING` to `PUBLISHED`.
- Broker failure coverage proves retryable `FAILED` rows keep `published_at` empty, record bounded `error_message`, increment `retry_count`, honor `next_retry_at`, and move to `DEAD_LETTER` at threshold.
- Live Compose publisher smoke proves the worker can recover a pending row after process restart, publish only the allow-listed `ReportArtifactGenerated` event, and log attempted/published/failed/dead-letter counters.
- The schema seeds only synthetic report types: `AUDIT_SUMMARY`, `OPERATIONS_DAILY`, and `EVIDENCE_COVERAGE`.
- Idempotent report generation prevents duplicate artifacts for an external retry key.
- Reporting access appends `REPORT_CATALOG_VIEW`, `REPORT_GENERATED`, `REPORT_GENERATE_REPLAYED`, `REPORT_ARTIFACT_LIST_VIEW`, `REPORT_ARTIFACT_EXPORTED`, and `REPORT_RETENTION_SWEEP_RUN` audit rows.
- Admin/audit channel panels use simulator tokens only for local API-backed
  smoke paths; live service-token coverage remains in the Keycloak smoke script
  with simulator fallback disabled.

## Verification

- `npm --workspace @banking-lab/api-client run typecheck`
- `npm run packages:typecheck`
- `npm run next:admin-console:typecheck`
- `npm run next:audit-console:typecheck`
- `npm run validate:manifests`
- `node --test tests/reportingServiceScaffold.test.mjs tests/nextScaffold.test.mjs`
- `npm run test:e2e -- apps/admin-console/e2e/admin-console-parity.spec.ts apps/audit-console/e2e/audit-console-parity.spec.ts`
- `npm run test:reporting-service:integration -- --tests lab.banking.reporting.ReportingServiceIntegrationTest --rerun-tasks`
- `npm run test:reporting-service:integration -- --tests lab.banking.reporting.ReportingKafkaOutboxPublisherIntegrationTest --rerun-tasks`
- `npm run test:reporting-service:domain-publisher-compose`
- `npm run test:reporting-service:keycloak-service-token`
- `docker compose --profile platform config`
- `npm run k8s:validate`
- `npm run helm:template`
- `npm run security:posture-check`
- `npm test`
- `npm run evidence:refresh-check`
- `npm run node:retirement-gate`

## Remaining Risk

- Synthetic JSON report rendering, checksum persistence, retention/export metadata, reason-required package export simulation, retention lifecycle expiration, and Redpanda-backed domain event publication are implemented.
- Broker publication and retry/dead-letter state transitions are proven with Redpanda/Testcontainers, and Compose/Kubernetes/Helm worker runtime wiring is structurally validated.
- Live Compose worker smoke is implemented; live Kubernetes worker rollout, operator dead-letter remediation screens, and richer backoff policy controls remain future work.
- Live Kubernetes/Helm rollout and reporting browser propagation against a
  configured live reporting-service URL remain future work; the Playwright
  reporting smokes are present but skipped locally when the reporting E2E URL is
  not configured.
