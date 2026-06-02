# Evidence Gap Report

Review date: 2026-06-02

## Verdict

Evidence is adequate for keeping the migration moving, but not adequate for retiring the Node reference runtime. The current evidence proves the Node oracle and parts of the Kotlin/PostgreSQL ledger slice. It does not yet prove end-to-end target-stack parity for identity, workflow, eventing, frontend channels, observability, security verification, or Node independence.

## Proven Or Structurally Present

| Area | Current evidence | Confidence |
| --- | --- | --- |
| Node oracle | `npm test` and `npm run parity` are documented as passing in `docs/test-evidence/migration-foundation.md`. | High for reference behavior. |
| Parity scenario inventory | `docs/migration/parity-scenarios.json` maps 42 Node scenarios, and `tests/migrationFoundation.test.mjs` checks the count. | High for inventory completeness. |
| Kotlin/Spring scaffold | Source, Gradle wrapper, live `/health` smoke, structured errors, and Testcontainers tests exist. | High for scaffold and current health gate. |
| PostgreSQL ledger slice | Docker/JDK Testcontainers evidence is documented for ledger persistence, idempotency, outbox rows, approval-gated adjustments, and isolation behavior. | Medium-high for ledger slice, subject to retry policy and broader API parity. |
| Kotlin workflow state machines | Unit tests cover maker-checker, complaint answer approval, FDS release/block handoff, AML closure, and reconciliation adjustment handoff; durable PostgreSQL workflow repository tests now cover restart-safe state/events. | Medium for durable state-machine parity; low for case-specific API routes until wired. |
| Next manifest channel shells | Customer-web plus staff, complaint, ops, audit, and FDS/AML Next shells typecheck and have Playwright shell parity from manifests. | Medium for shell parity; API-backed parity is pending. |
| Node retirement guard | `docs/migration/node-retirement-gate.json` is blocked, and the retirement-gate command is documented as passing with blocked status. | High. |

## Evidence Gaps

| Gap | Why it matters | Required evidence |
| --- | --- | --- |
| Live Spring Boot smoke | Completed through Docker/JDK against isolated PostgreSQL on port 15432; `/health` returned `status=ok`, `syntheticOnly=true`, and Flyway applied v007. | Keep repeatable logs in future evidence pack output. |
| Host target workflow | Current docs say host Java/Gradle are unavailable, so target commands only pass through Docker/JDK. | Either install/document JDK 21 workflow or add a committed application container path for repeatable target runs. |
| Full API parity | Runtime route semantics still primarily live in Node. | Spring integration tests for health, staff/customer APIs, ledger commands, reversals, approvals, complaint, FDS/AML, reconciliation, and structured errors. |
| Durable audit/masking/approval | Node tests cover controls in memory; target persistence for approval execution and audit hash chain is not proven end to end. | PostgreSQL-backed tests for audit events, masked reads, privileged unmasking, maker-checker request/approve/execute, and self-approval rejection. |
| Workflow durability | Durable generic workflow repository tests are now present, but case-specific API state persistence is not complete. | Repository-backed tests for complaint, FDS release/block, AML closure, reconciliation adjustment, and holds through real routes. |
| Kafka/Outbox delivery | Outbox row insertion plus PENDING/PUBLISHED/FAILED/DEAD_LETTER and inbox idempotency are proven, but no real Kafka/Redpanda publisher exists. | Redpanda/Kafka Testcontainers tests for actual publish-after-commit, duplicate consume replay, retry, and DLQ. |
| Next.js channel parity | Six channel shells have Playwright manifest parity, but tests do not exercise APIs. | API-backed Playwright tests for customer, staff, complaint, audit, ops, and FDS/AML flows. |
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

1. Add Spring Security resource-server tests for Keycloak role and customer-ownership policies.
2. Replace structured-error probe routes with real staff/customer/workflow route failures.
3. Add Kafka/Redpanda Testcontainers publish and idempotent consumer replay around the durable outbox.
4. Add API-backed Playwright flows for staff reason-required lookup and maker-checker approval.

This would not retire Node by itself, but it would close the largest remaining control gaps after the health, structured-error, outbox-state, workflow-state, and Next shell parity slices.
