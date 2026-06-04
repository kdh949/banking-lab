# Security Docker Rerun Evidence

Date: 2026-06-04

## Purpose

`npm run security:evidence` can use locally installed security tools or Docker scanner fallbacks. For repeatable CI/workstation reruns where Docker is available, use the Docker-forced wrapper:

```bash
npm run security:evidence:docker
```

The wrapper verifies `docker version` first, then runs `scripts/run-security-evidence.ts` with `BANKING_LAB_SECURITY_FORCE_DOCKER=true`. That forces Semgrep, Trivy filesystem scanning, CycloneDX SBOM generation, and ZAP DAST to prefer Docker scanner paths instead of accidentally using locally installed tools.

## DAST Target

DAST is not run unless a live target URL is supplied:

```bash
BANKING_LAB_DAST_URL=http://127.0.0.1:8080 npm run security:evidence:docker
```

Without `BANKING_LAB_DAST_URL`, the generated evidence must record `dast-zap-baseline` as `skipped` with reason `BANKING_LAB_DAST_URL is not set; no live target was supplied for DAST.`

## Current Generated Evidence

Current generated summary: `docs/test-evidence/generated/security-evidence-summary.json`.

- `npm-audit-high`: pass.
- `semgrep-sast`: pass through Docker fallback in the committed generated evidence.
- `trivy-fs`: pass through Docker fallback in the committed generated evidence.
- `sbom-cyclonedx`: pass through Docker fallback in the committed generated evidence.
- `dast-zap-baseline`: pass through Docker ZAP baseline against the local synthetic target `http://host.docker.internal:18132/health`.

The DAST evidence was produced after starting disposable local synthetic containers:

```bash
docker network create banking-lab-dast-live
docker run -d --rm --name banking-lab-dast-postgres --network banking-lab-dast-live -e POSTGRES_DB=banking_lab -e POSTGRES_USER=banking_lab -e POSTGRES_PASSWORD=banking_lab postgres:16-alpine
docker run -d --rm --name banking-lab-dast-core --network banking-lab-dast-live -p 18132:8081 -e BANKING_LAB_DATABASE_URL=jdbc:postgresql://banking-lab-dast-postgres:5432/banking_lab -e BANKING_LAB_DATABASE_USER=banking_lab -e BANKING_LAB_DATABASE_PASSWORD=banking_lab -e BANKING_LAB_SYNTHETIC_ONLY=true -e BANKING_LAB_SECURITY_ENABLED=false -e BANKING_LAB_OTLP_TRACING_EXPORT_ENABLED=false -e BANKING_LAB_TRACING_ENABLED=false banking-lab-dast-smoke-core-banking:latest
curl -sf http://127.0.0.1:18132/health
env BANKING_LAB_DAST_URL=http://host.docker.internal:18132/health npm run security:evidence:docker
docker stop banking-lab-dast-core
docker stop banking-lab-dast-postgres
docker network rm banking-lab-dast-live
```

Generated ZAP artifacts:

- `docs/test-evidence/generated/zap-baseline.json`
- `docs/test-evidence/generated/zap-baseline.log`

## Synthetic Boundary

The security evidence is for the synthetic Banking Lab repository and local/lab URLs only. It must not scan real customer data, real payment services, real bank APIs, or production financial institution systems.
