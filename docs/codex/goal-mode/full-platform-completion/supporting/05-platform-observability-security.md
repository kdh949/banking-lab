# Platform, Observability, And Security

## Goal

Provide Docker Compose, Kubernetes, Helm, Terraform, Argo CD, Keycloak,
OpenTelemetry, Prometheus, Grafana, Loki, Tempo, SAST, DAST, SCA, SBOM, Trivy,
Semgrep, backup/restore, load, and failure-drill evidence for the synthetic lab.

## Current Code To Inspect

- `docker-compose.yml`
- `infra/docker-compose/**`
- `infra/k8s/**`
- `infra/helm/**`
- `infra/terraform/**`
- `infra/argocd/**`
- `infra/keycloak/**`
- `infra/observability/**`
- `infra/security/**`
- `docs/test-evidence/platform-deployment-validation.md`
- `docs/test-evidence/security-docker-rerun.md`
- `docs/test-evidence/postgres-backup-restore-drill.md`

## Target Folder Placement

Keep all platform assets under `infra/**`. Keep generated evidence under
`docs/test-evidence/generated/**` only when commands generate it. Keep narrative
evidence under `docs/test-evidence/**` and failure drills under
`docs/failure-drills/**`.

## Implementation Plan

- Maintain Docker Compose for local PostgreSQL, Redis, Redpanda/Kafka, Keycloak,
  Temporal, and observability stubs.
- Maintain Kubernetes and Helm manifests for deployable services.
- Add Terraform and Argo CD definitions as structural deployment assets.
- Instrument APIs and workers with OpenTelemetry.
- Preserve Prometheus/Grafana/Loki/Tempo profiles.
- Run security evidence using npm audit, Semgrep, Trivy, SBOM, and DAST where
  a target URL is available.
- Keep backup/restore and load smoke as operational evidence.

## Tests And Evidence

Run `docker compose config`, `docker compose --profile platform config`,
`npm run k8s:validate`, `npm run helm:template`, `npm run security:posture-check`,
`npm run security:evidence`, Docker-backed security evidence where required,
`npm run postgres:backup-drill`, Docker-backed live backup drill where required,
and `npm run load:synthetic`.

## Acceptance Criteria

- Platform manifests validate structurally.
- Security evidence is current and honest.
- Observability stack has smoke evidence.
- Backup/restore proves ledger and audit continuity.
- No production credentials or real external integrations are present.

## Explicit Non-Goals

No production cluster deployment guarantee, no real secrets, no real bank
infrastructure, and no production monitoring claim.

