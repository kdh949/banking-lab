# AGENTS.md — Bank-grade Core Banking Lab Full Rewrite

## Mission

Build a bank-grade simulated banking platform using the target stack, not the existing Node.js MVP stack.

The current Node.js `.mjs` implementation is a legacy reference/oracle only. The final implementation must be rebuilt around Kotlin/Java + Spring Boot, PostgreSQL, Kafka/Redpanda, Temporal, TypeScript + Next.js/React, Keycloak, OpenTelemetry, Kubernetes, Terraform, Helm, Argo CD, and security verification tooling.

This project must never handle real customer money, real personal data, real KYC, real payment networks, or real financial institution APIs. Use synthetic data and simulators only. The implementation should still model bank-grade reliability, auditability, internal control, and operational resilience.

## Primary Directive

Do not continue expanding the Node.js MVP as the target product.

Use the Node implementation only for:

- behavior reference;
- parity scenario extraction;
- legacy demo comparison;
- domain rule clarification;
- temporary migration oracle.

New production-like code must be written in the target architecture:

- Kotlin/Java + Spring Boot for core banking and backend services;
- PostgreSQL for the ledger database;
- Kafka or Redpanda with the Outbox Pattern for events;
- Temporal for long-running workflows;
- TypeScript + Next.js/React for customer, staff, complaint, admin, audit, and FDS/AML UIs;
- Keycloak with OAuth2/OIDC, RBAC/ABAC, and WebAuthn/MFA for identity;
- Python, DuckDB/Spark, and scikit-learn for AML/FDS analytics;
- Docker Compose first, then Kubernetes, Terraform, Helm, and Argo CD;
- OpenTelemetry, Prometheus, Grafana, Loki, and Tempo for observability;
- OWASP ASVS 5.0, SAST, DAST, SCA, SBOM, Trivy, and Semgrep for security verification.

## Required Reading Order

Before planning or coding, read these files:

1. `PLAN.md`
2. `BANKING_LAB_CODEX_PROMPT.md`
3. Existing Node reference tests under `tests/*.test.mjs`
4. Existing architecture, ADR, evidence, and migration documents

If the files conflict, priority is:

```text
PLAN.md
  > AGENTS.md
  > BANKING_LAB_CODEX_PROMPT.md
  > migration/parity docs
  > old README claims
  > old Node implementation details
```

## Non-negotiable Banking Invariants

1. Ledger integrity comes first.
2. Every financial movement must be represented as balanced double-entry ledger postings.
3. Balances are projections from postings, not mutable source-of-truth fields.
4. Finalized transactions are never updated or deleted; use reversal or adjustment transactions.
5. Every externally retried command must be idempotent.
6. PostgreSQL transaction isolation must be explicitly tested, at least REPEATABLE READ and SERIALIZABLE for critical ledger paths.
7. Staff access to customer/account/transaction data must require a business reason and produce audit events.
8. PII must be masked by default.
9. High-risk staff operations require maker-checker approval and separation of duties.
10. Long-running workflows must be modeled through Temporal or a clearly justified state machine before Temporal is introduced.
11. Events must be emitted through a durable Outbox Pattern, not by directly publishing inside the same business method without persistence.
12. Reconciliation corrections must use balanced adjustment transactions, not direct balance edits.
13. Closed business dates must reject direct posting.
14. Every milestone must include tests and evidence.

## Target Architecture

```text
[Customer Web / Staff Terminal / Complaint Portal / Admin / Audit / FDS-AML]
        |
[Next.js / React UI + shared design system + screen manifest renderer]
        |
[BFF/API Layer: Kotlin or TypeScript]
        |
[Keycloak OAuth2/OIDC + RBAC/ABAC + WebAuthn/MFA]
        |
[Domain Services: Kotlin/Spring Boot]
  - core-banking-service
  - customer-service
  - account-service
  - transfer-service
  - ledger-service or core module
  - complaint-service
  - fds-service
  - aml-service
  - reconciliation-service
  - notification-service
  - reporting-service
        |
[PostgreSQL + Redis + Kafka/Redpanda + Temporal]
        |
[Python AML/FDS analytics: DuckDB/Spark/scikit-learn]
        |
[OpenTelemetry + Prometheus + Grafana + Loki/Tempo]
        |
[Docker Compose -> Kubernetes + Terraform + Helm + Argo CD]
```

## Implementation Order

### Phase 0 — Rewrite Foundation

- Freeze the Node MVP as a reference.
- Create or repair the target root structure.
- Commit Gradle wrapper.
- Make Spring Boot build runnable.
- Make Next.js build runnable.
- Add Docker Compose services for PostgreSQL, Redis, Redpanda/Kafka, Keycloak, Temporal, and observability stubs.

### Phase 1 — Core Ledger in Kotlin/Spring Boot

- Implement customers, accounts, ledger transactions, postings, idempotency keys, balances as projections, daily closings, and audit events.
- Use PostgreSQL and Flyway migrations.
- Test REPEATABLE READ and SERIALIZABLE behavior on critical ledger operations.
- Implement deposit, withdrawal, internal transfer, reversal, adjustment, account hold, and closing.
- Use JUnit and Testcontainers.

### Phase 2 — Eventing and Outbox

- Add `outbox_events` table.
- Publish domain events through Kafka/Redpanda only after durable transaction commit.
- Add idempotent consumers and retry/dead-letter behavior.

### Phase 3 — Temporal Workflows

- Implement workflows for transfer review, complaint handling, FDS release/block, AML closure, reconciliation adjustment, and account hold/release.
- Persist workflow references in domain tables.
- Make workflow state visible in staff/admin screens.

### Phase 4 — Identity and Access Control

- Add Keycloak realm configuration.
- Implement OAuth2/OIDC login.
- Add RBAC/ABAC policies.
- Add WebAuthn/MFA design or local simulator.
- Enforce screen and API authorization consistently.

### Phase 5 — Next.js Channel and Operations UIs

- Build customer web, staff terminal, complaint portal, admin console, audit console, and FDS/AML console with TypeScript + Next.js/React.
- Use a shared UI system.
- Use screen manifests and templates for inquiry, command, case, and parameter screens.
- Add Playwright E2E tests.

### Phase 6 — AML/FDS Analytics

- Add Python analytics service.
- Use DuckDB first, Spark optional for larger batch simulation.
- Implement rule-based detection first, then scikit-learn scoring as an optional enhancement.
- Keep all data synthetic.

### Phase 7 — Platform, Observability, and Security

- Add OpenTelemetry instrumentation.
- Add Prometheus, Grafana, Loki, and Tempo profiles.
- Add Kubernetes manifests, Helm charts, Terraform modules, and Argo CD app definitions.
- Add Trivy, Semgrep, SBOM, SCA, DAST profile, and OWASP ASVS 5.0 mapping.

## Screen Platform Requirements

Do not hand-code every banking screen. Build reusable screen infrastructure.

Required templates:

1. Inquiry Template: search, result table, detail panel, masking, reason, audit.
2. Command Template: target lookup, before/after snapshot, reason, validation, approval, audit.
3. Case Template: status, owner, SLA, comments, attachments, timeline, approval.
4. Parameter Template: current value, scheduled value, effective date, approval, rollback.

Core staff terminal concepts:

- transaction code input;
- tabbed business screens;
- customer context panel;
- masked PII;
- reason-required lookup;
- audit log panel;
- maker-checker approval inbox;
- role-aware menu;
- workflow timeline;
- exception/retry panel.

## Testing Requirements

Use the right testing tool for each layer:

- Kotlin domain tests: JUnit.
- PostgreSQL and transaction tests: Testcontainers.
- API contract tests: OpenAPI and Spring integration tests.
- Frontend E2E: Playwright.
- Workflow tests: Temporal test environment.
- Event tests: Kafka/Redpanda Testcontainers.
- Security tests: Semgrep, Trivy, dependency scan, SBOM, DAST where possible.
- Reliability tests: failure drills for DB crash, duplicate idempotency key, Kafka delay, workflow retry, and reconciliation mismatch.

## Prohibited

- Expanding Node.js as the final system.
- Claiming Java/Kotlin parity before Spring tests pass.
- Claiming PostgreSQL durability while runtime state is still in-memory.
- Publishing events without Outbox persistence.
- Directly mutating balances.
- Exposing unmasked PII by default.
- Handling real money, real PII, real bank APIs, or real financial network integrations.
- Adding many screens by copy-paste instead of screen templates/manifests.
- Marking evidence as passed without running commands.

## Codex Reporting Format

After every task, report:

```text
Changed files
Commands run
Passing tests
Failing/skipped tests with reason
Domain invariants affected
Security/control impact
Remaining risk
Next smallest safe task
```
