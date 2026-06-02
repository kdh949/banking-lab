# Evidence Gap Report

Review date: 2026-06-02

## Verdict

Evidence is adequate for keeping the migration moving, but not adequate for retiring the Node reference runtime. The current evidence proves the Node oracle and parts of the Kotlin/PostgreSQL ledger slice. It does not yet prove end-to-end target-stack parity for identity, workflow, eventing, frontend channels, observability, security verification, or Node independence.

## Proven Or Structurally Present

| Area | Current evidence | Confidence |
| --- | --- | --- |
| Node oracle | `npm test` and `npm run parity` are documented as passing in `docs/test-evidence/migration-foundation.md`. | High for reference behavior. |
| Parity scenario inventory | `docs/migration/parity-scenarios.json` maps 42 Node scenarios, and `tests/migrationFoundation.test.mjs` checks the count. | High for inventory completeness. |
| Kotlin/Spring scaffold | Source, Gradle wrapper, Spring health/controller/error files, and structural tests exist. | Medium; live service smoke is pending. |
| PostgreSQL ledger slice | Docker/JDK Testcontainers evidence is documented for ledger persistence, idempotency, outbox rows, and isolation behavior. | Medium-high for ledger slice, subject to reproducibility. |
| Kotlin workflow state machines | Unit tests cover maker-checker, complaint answer approval, FDS release/block handoff, AML closure, and reconciliation adjustment handoff. | Medium for state-machine parity, low for durability until repositories/API routes exist. |
| Next manifest channel shells | Customer-web plus staff, complaint, ops, audit, and FDS/AML Next shells typecheck and build from screen manifests. | Medium for scaffold only; Playwright/API parity is pending. |
| Node retirement guard | `docs/migration/node-retirement-gate.json` is blocked, and the retirement-gate command is documented as passing with blocked status. | High. |

## Evidence Gaps

| Gap | Why it matters | Required evidence |
| --- | --- | --- |
| Live Spring Boot smoke | Source and tests do not prove the app starts with the migration PostgreSQL profile and serves `/health`. | `docker compose --profile migration up -d postgres`, `./gradlew :services:core-banking:bootRun`, and `curl http://127.0.0.1:8081/health` logs. |
| Host target workflow | Current docs say host Java/Gradle are unavailable, so target commands only pass through Docker/JDK. | Either install/document JDK 21 workflow or add a committed application container path for repeatable target runs. |
| Full API parity | Runtime route semantics still primarily live in Node. | Spring integration tests for health, staff/customer APIs, ledger commands, reversals, approvals, complaint, FDS/AML, reconciliation, and structured errors. |
| Durable audit/masking/approval | Node tests cover controls in memory; target persistence for approval execution and audit hash chain is not proven end to end. | PostgreSQL-backed tests for audit events, masked reads, privileged unmasking, maker-checker request/approve/execute, and self-approval rejection. |
| Workflow durability | Node state machines are the oracle; Temporal or a justified durable state machine is not proven. | Temporal test environment or restart-safe state-machine tests for complaint, FDS release/block, AML closure, reconciliation adjustment, and holds. |
| Kafka/Outbox delivery | Outbox row insertion is documented, but event publishing, idempotent consumers, retry, and dead-letter behavior are not proven. | Redpanda/Kafka Testcontainers tests for publish-after-commit, duplicate consume replay, retry, and DLQ. |
| Next.js channel parity | All six channel shells now exist as Next/manifest scaffolds, but builds are not Playwright parity and do not exercise APIs. | Playwright tests for customer, staff, complaint, audit, ops, and FDS/AML flows rendered from manifests. |
| Keycloak authorization | Realm config exists, but OAuth2/OIDC resource-server enforcement is not proven. | API tests with valid/invalid tokens, role policies, customer ownership, manager-only approvals, and audit events for denial. |
| Security verification | Dependency audit evidence exists for npm, but the required Semgrep, Trivy, SBOM, SCA, and DAST evidence is absent. | Captured outputs or summaries for Semgrep, Trivy, SBOM generation, dependency scan, and DAST profile. |
| Observability | Compose stubs exist, but traces/metrics/logs are not verified. | OpenTelemetry smoke test with Prometheus/Grafana/Loki/Tempo evidence or screenshots/log excerpts. |

## Evidence Quality Risks

- Generated evidence files under `docs/test-evidence/generated` are useful summaries, but they are not a substitute for command outputs from the target stack.
- The Node evidence pack can remain green while target parity is still incomplete.
- Structural tests can confirm files and route declarations, but they do not prove runtime behavior, transaction boundaries, restart survival, or authorization enforcement.
- Serialization conflict rejection currently prevents overdraw, but a bank-grade command path still needs an explicit retry or operator-visible failure policy.

## Recommended Next Evidence Slice

The next smallest safe evidence slice is live Spring smoke plus structured-error HTTP parity:

1. Start PostgreSQL with the migration profile.
2. Run Spring Boot against the Flyway migrations.
3. Capture `/health`.
4. Exercise one successful deposit and one closed-day or insufficient-balance failure.
5. Assert the failure matches `docs/migration/structured-api-error-contract.md`.

This would not retire Node, but it would turn the Spring API layer from structural evidence into live target-stack evidence.
