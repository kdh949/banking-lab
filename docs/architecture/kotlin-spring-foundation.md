# Kotlin Spring Foundation Architecture

## Runtime Intent

```text
HTTP client
  -> Spring Boot core-banking service
       -> /health
       -> /api/ledger/* commands
       -> structured API error handler
       -> PostgreSQL datasource
       -> Flyway V001-V005 migrations
       -> durable ledger transaction boundary
```

The Node runtime remains the executable reference while this target backend is introduced.

## Current Scaffold

- Root Gradle Kotlin DSL project includes `:services:core-banking`.
- Spring Boot/Kotlin source lives under `services/core-banking/src/main/kotlin`.
- `/health` returns `status`, `syntheticOnly`, `auditHashChainValid`, `nodeReferenceRuntimeRetained`, and `migrationTarget`.
- Structured error DTOs mirror `docs/migration/structured-api-error-contract.md`.
- `application.yml` points to PostgreSQL and canonical `db/migrations` Flyway files, with a module-relative fallback for Gradle execution.
- `LedgerCommandService` implements PostgreSQL-backed deposit, withdrawal, internal transfer, reversal, adjustment, daily closing, idempotency replay, closed-day rejection, row locking, balance projection updates, and outbox row insertion.
- `docker-compose.yml` keeps the Node runtime as default, adds PostgreSQL behind the `migration` profile, and adds Redis, Redpanda, Keycloak, Temporal, Prometheus, Grafana, Loki, and Tempo behind the `platform` profile.

## Verification Gap

Local Java and Gradle are not installed on the host, so direct `./gradlew` execution still fails outside a container. Docker-based JDK 21 execution was used for Kotlin verification.

Passing target-stack commands:

```bash
docker run --rm -v /Users/donghyunkim/Documents/banking-lab:/workspace -w /workspace gradle:8.14.3-jdk21 ./gradlew :services:core-banking:test --no-daemon

docker run --rm -e TESTCONTAINERS_RYUK_DISABLED=true -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -v /var/run/docker.sock:/var/run/docker.sock -v /Users/donghyunkim/Documents/banking-lab:/workspace -w /workspace gradle:8.14.3-jdk21 ./gradlew :services:core-banking:integrationTest --no-daemon
```

The Testcontainers command needs `TESTCONTAINERS_RYUK_DISABLED=true` and `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` when Gradle itself is run inside Docker on Docker Desktop.

Target verification commands:

```bash
docker compose --profile migration up -d postgres
./gradlew :services:core-banking:test
./gradlew :services:core-banking:integrationTest
./gradlew :services:core-banking:bootRun
curl http://127.0.0.1:8081/health
```

Boot-run and live `/health` smoke testing remain pending on a host with JDK 21 or an application runtime container.
