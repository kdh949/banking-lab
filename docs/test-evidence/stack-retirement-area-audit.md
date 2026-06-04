# Stack Retirement Area Audit

Date: 2026-06-05

Status: pass

## Scope

This evidence checks that current target implementation areas do not carry the legacy Node MVP stack as target source. It does not scan the approved Node oracle/support areas: `runtime/`, `scripts/`, `tests/`, and `legacy-node-reference/`.

This document does not mark Node retirement ready.

## Command

```bash
npm run retirement:stack-audit
```

## Current Result

Passed on 2026-06-05.

The audit checked these target areas:

- `core-banking-backend`: Kotlin/Java + Spring Boot, PostgreSQL/Flyway, Outbox, Temporal, Keycloak/OIDC.
- `frontend-channels`: TypeScript + Next.js/React manifest-rendered channel apps.
- `shared-packages`: TypeScript shared API/auth/screen/form packages.
- `analytics`: Python + DuckDB/scikit-learn synthetic AML/FDS analytics.
- `platform-infra`: Docker Compose, Kubernetes, Terraform, Helm, Argo CD, Keycloak, OpenTelemetry stack, security tooling.
- `contracts-and-data`: OpenAPI/AsyncAPI/Temporal contracts and PostgreSQL/Flyway migrations.

## Controls

- Target backend service source must not depend on legacy `.mjs`, `runtime/server.mjs`, or `runtime/labApp.mjs`.
- Target channel apps must remain Next.js/React source, not legacy static `public/index.html` shells.
- Shared packages must remain TypeScript packages, not restored Node oracle `.mjs` domain packages.
- Analytics remains Python-based and synthetic-only.
- Platform, contracts, and database artifacts remain target-stack configuration, contract, and migration assets.

## Retirement Impact

The stack area audit is green for the current target source tree. `npm run node:retirement-gate` now invokes this audit before accepting a ready result, and `tests/stackRetirementAreaAudit.test.mjs` includes an in-memory canary proving that a target-path import of `legacy-node-reference/runtime/server.mjs` is rejected.

Node retirement is ready for the current synthetic lab scope, while the archived Node oracle/reference boundary remains available for parity comparison.
