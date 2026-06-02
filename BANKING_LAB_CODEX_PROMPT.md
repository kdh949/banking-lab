# Bank-grade Core Banking Lab — Codex Master Prompt

> This document now points Codex toward a full rewrite on the original target stack. Do not treat the existing Node.js `.mjs` MVP as the final implementation. Treat it as a legacy reference/oracle only.

---

## 0. Required Reading Order

Codex must read these files before making implementation decisions:

1. `PLAN.md` — full rewrite target stack and phased plan.
2. `AGENTS.md` — repository-level rules and non-negotiable invariants.
3. Existing Node tests under `tests/*.test.mjs` — behavior oracle only.
4. `docs/migration/*`, if still present — migration evidence and parity notes.
5. Existing architecture/ADR/test-evidence documents.

If documents conflict, `PLAN.md` has priority.

---

## 1. Mission

Build a synthetic bank-grade banking platform that demonstrates real banking engineering controls:

- double-entry ledger;
- PostgreSQL-backed durability;
- idempotent financial commands;
- maker-checker approval;
- audit hash chain;
- PII masking and reason-required staff access;
- Temporal workflows;
- Kafka/Redpanda Outbox events;
- Next.js customer and staff channels;
- Keycloak OAuth2/OIDC identity;
- AML/FDS analytics;
- reconciliation and closing;
- observability, security verification, and evidence.

This is not a real bank. Do not integrate real money, real PII, real KYC, real payment networks, or real financial institution APIs.

---

## 2. Full Rewrite Directive

The current Node.js implementation is not the target architecture.

Use Node only as:

```text
reference behavior
legacy oracle
scenario source
temporary demo
```

Do not keep expanding Node as the main system. New target implementation must use:

```text
Kotlin/Java + Spring Boot
PostgreSQL
Kafka/Redpanda + Outbox Pattern
Temporal
TypeScript + Next.js / React
Keycloak + OAuth2/OIDC + WebAuthn/MFA
Python + DuckDB/Spark + scikit-learn for AML/FDS analytics
Docker + Kubernetes + Terraform + Helm + Argo CD
OpenTelemetry + Prometheus + Grafana + Loki/Tempo
OWASP ASVS 5.0 + SAST/DAST/SCA + SBOM + Trivy + Semgrep
```

---

## 3. Core Banking Invariants

Codex must preserve these invariants in code and tests:

```text
sum(postings by transaction, currency) == 0
balance == sum(postings by account)
available_balance <= ledger_balance
idempotent request creates at most one business result
finalized transactions are append-only
closed business day cannot be mutated directly
reversal references original transaction
adjustment is balanced and approved
sensitive access requires audit reason
high-risk operation requires maker-checker approval
```

---

## 4. Target Stack Summary

| Area | Target |
|---|---|
| Core banking | Kotlin/Java + Spring Boot |
| Ledger DB | PostgreSQL, SERIALIZABLE/REPEATABLE READ verification |
| Events | Kafka/Redpanda + Outbox Pattern |
| Workflow | Temporal |
| Customer/staff channels | TypeScript + Next.js / React |
| BFF/API | Kotlin or TypeScript |
| Operations admin | React + RBAC/ABAC |
| AML/FDS analytics | Python, Spark/DuckDB, scikit-learn |
| Infra | Docker, Kubernetes, Terraform, Helm, Argo CD |
| Identity | Keycloak, OAuth2/OIDC, WebAuthn/MFA |
| Observability | OpenTelemetry + Prometheus + Grafana + Loki/Tempo |
| Security | OWASP ASVS 5.0, SAST/DAST/SCA, SBOM, Trivy, Semgrep |

---

## 5. First Implementation Priority

Start with the backend foundation. Do not add more Node features.

First vertical slice:

```text
Kotlin/Spring Boot core-banking
PostgreSQL/Flyway schema
Ledger domain model
Ledger command service
Idempotency persistence
Transaction isolation tests
Structured errors
JUnit + Testcontainers
```

Then add:

```text
Kafka/Redpanda Outbox
Temporal workflows
Keycloak RBAC/ABAC
Next.js customer web
Next.js staff terminal
Python AML/FDS analytics
Observability and security scans
Kubernetes/Terraform/Helm/Argo CD
```

---

## 6. Screen Platform Direction

Do not build banking screens by copy-paste. Build a screen platform.

Required templates:

1. Inquiry Template: search, table, detail, masking, reason, audit.
2. Command Template: target, before/after, reason, validation, approval, audit.
3. Case Template: status, owner, SLA, comments, attachments, timeline.
4. Parameter Template: current value, scheduled value, effective date, approval, rollback.

Staff terminal must include:

```text
transaction code input
customer context
masked PII
reason-required lookup
audit log panel
approval inbox
workflow timeline
RBAC/ABAC-aware menus
```

---

## 7. Testing and Evidence

Use:

```text
JUnit
Testcontainers
Playwright
Temporal test environment
Kafka/Redpanda Testcontainers
Semgrep
Trivy
SBOM generation
OWASP ASVS mapping
OpenTelemetry evidence
failure drills
```

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

---

## 8. Immediate Codex Prompt

```text
Read PLAN.md, AGENTS.md, and BANKING_LAB_CODEX_PROMPT.md.

Do not extend the Node.js MVP as the final system. Treat it as legacy reference only.

Implement the first full rewrite slice:

1. Ensure Gradle wrapper and Spring Boot project are runnable.
2. Create/repair PostgreSQL Flyway migrations.
3. Implement Kotlin ledger domain types and invariant tests.
4. Implement PostgreSQL-backed deposit, withdrawal, internal transfer, reversal, adjustment, idempotency, and closing.
5. Add JUnit + Testcontainers tests for ledger invariants and transaction isolation.
6. Add Docker Compose infrastructure for PostgreSQL, Redis, Redpanda/Kafka, Temporal, Keycloak, and observability profiles.
7. Update ADR and evidence docs.

Do not delete Node code yet, but do not add new Node business features.
```
