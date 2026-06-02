# Kotlin Spring Foundation Architecture

## Runtime Intent

```text
HTTP client
  -> Spring Boot core-banking service
       -> /health
       -> structured API error handler
       -> PostgreSQL datasource
       -> Flyway foundation migration
       -> future Kotlin domain services
```

The Node runtime remains the executable reference while this target backend is introduced.

## Current Scaffold

- Root Gradle Kotlin DSL project includes `:services:core-banking`.
- Spring Boot/Kotlin source lives under `services/core-banking/src/main/kotlin`.
- `/health` returns `status`, `syntheticOnly`, `auditHashChainValid`, `nodeReferenceRuntimeRetained`, and `migrationTarget`.
- Structured error DTOs mirror `docs/migration/structured-api-error-contract.md`.
- `application.yml` points to PostgreSQL and `db/migrations` for Flyway.
- `docker-compose.yml` keeps the Node runtime as default and adds PostgreSQL behind the `migration` profile.

## Verification Gap

Local Java and Gradle are not installed in this environment, so Gradle execution is pending. The scaffold is guarded by Node structural tests until JDK 21 and Gradle execution are available.

Target verification commands:

```bash
docker compose --profile migration up -d postgres
gradle :services:core-banking:test
gradle :services:core-banking:bootRun
curl http://127.0.0.1:8081/health
```

The first future runnable slice must replace this structural verification with real JUnit/Spring/Testcontainers results.
