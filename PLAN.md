# PLAN.md — Full Rewrite Target Stack Plan

> This plan supersedes incremental Node.js MVP expansion. The existing Node.js `.mjs` implementation is a legacy reference/oracle only. The target system must be rebuilt on the originally intended bank-grade stack: Kotlin/Java + Spring Boot, PostgreSQL, Kafka/Redpanda, Temporal, TypeScript + Next.js/React, Keycloak, OpenTelemetry, Kubernetes, Terraform, Helm, Argo CD, and security verification tooling.

---

## 1. Product Goal

Build a synthetic, bank-grade core banking lab that demonstrates how real banking systems preserve correctness, auditability, operational control, and resilience.

This is not a real bank and must never handle:

- real customer money;
- real customer PII;
- real KYC or identity verification;
- real payment networks;
- real financial institution APIs;
- public complaint intake that could be mistaken for a real financial service.

Everything must use synthetic data and simulators. The engineering controls should still model bank-grade patterns.

---

## 2. Rewrite Decision

The current Node.js implementation proved the concept quickly, but it is not the final architecture.

### Decision

Rebuild the project on the target stack instead of extending the Node.js MVP.

### Node.js role

The existing Node code may be used as:

```text
reference behavior
oracle for tests
scenario source
legacy demo
migration comparison
```

It must not be treated as:

```text
final backend
final BFF
final persistence layer
final security layer
final workflow engine
final eventing system
```

---

## 3. Target Technology Stack

### 3.1 Core stack

| Area | Target |
|---|---|
| Core banking | Kotlin/Java + Spring Boot |
| Ledger DB | PostgreSQL, with SERIALIZABLE/REPEATABLE READ verification |
| Events | Kafka/Redpanda + Outbox Pattern |
| Long-running workflow | Temporal |
| Channels | TypeScript + Next.js / React |
| BFF/API | Kotlin or TypeScript |
| Operations Admin | React + RBAC/ABAC |
| AML/FDS analytics | Python, Spark/DuckDB, scikit-learn |
| Infrastructure | Docker, Kubernetes, Terraform, Helm, Argo CD |
| Identity | Keycloak, OAuth2/OIDC, WebAuthn/MFA |
| Observability | OpenTelemetry + Prometheus + Grafana + Loki/Tempo |
| Security verification | OWASP ASVS 5.0, SAST/DAST/SCA, SBOM, Trivy, Semgrep |

### 3.2 App stack

| Area | Target |
|---|---|
| Customer web / staff terminal | TypeScript + Next.js |
| Shared UI | React + shadcn/ui or custom design system |
| Backend | Kotlin/Spring Boot or Java/Spring Boot |
| DB | PostgreSQL |
| Cache | Redis |
| Events | Kafka or Redpanda |
| Workflow | Temporal first, explicit state machine only if justified |
| Identity | Keycloak |
| Observability | OpenTelemetry + Prometheus + Grafana + Loki |
| Testing | Playwright, JUnit, Testcontainers |
| Deployment | Docker Compose → Kubernetes expansion |

---

## 4. Target Architecture

```text
[Customer Web / Staff Terminal / Complaint Portal / Admin / Audit / FDS-AML]
        |
[Next.js / React + shared UI + manifest-driven screen renderer]
        |
[BFF/API Layer: Kotlin or TypeScript]
        |
[Keycloak OAuth2/OIDC + RBAC/ABAC + WebAuthn/MFA]
        |
[Kotlin/Spring Boot Domain Services]
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
[Temporal Workers]
  - complaint workflow
  - FDS release/block workflow
  - AML closure workflow
  - reconciliation adjustment workflow
  - account hold/release workflow
        |
[Data and Messaging]
  - PostgreSQL
  - Redis
  - Kafka/Redpanda
  - Object storage simulator
        |
[Analytics]
  - Python
  - DuckDB
  - Spark optional
  - scikit-learn
        |
[Observability]
  - OpenTelemetry
  - Prometheus
  - Grafana
  - Loki
  - Tempo
        |
[Platform]
  - Docker Compose
  - Kubernetes
  - Terraform
  - Helm
  - Argo CD
```

---

## 5. Banking Domain Invariants

These must be enforced in code, tests, and evidence.

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

## 6. Target Repository Structure

```text
banking-lab/
├─ apps/
│  ├─ customer-web/
│  ├─ staff-terminal/
│  ├─ complaint-portal/
│  ├─ admin-console/
│  ├─ audit-console/
│  └─ fds-aml-console/
│
├─ services/
│  ├─ core-banking/
│  ├─ customer-service/
│  ├─ account-service/
│  ├─ transfer-service/
│  ├─ complaint-service/
│  ├─ workflow-workers/
│  ├─ fds-service/
│  ├─ aml-service/
│  ├─ reconciliation-service/
│  ├─ notification-service/
│  ├─ reporting-service/
│  └─ external-simulators/
│
├─ analytics/
│  ├─ aml-fds-python/
│  ├─ duckdb/
│  └─ notebooks/
│
├─ packages/
│  ├─ ui/
│  ├─ api-client/
│  ├─ screen-engine/
│  ├─ form-engine/
│  ├─ auth-client/
│  └─ banking-contracts/
│
├─ contracts/
│  ├─ openapi/
│  ├─ asyncapi/
│  ├─ events/
│  └─ temporal/
│
├─ infra/
│  ├─ docker-compose/
│  ├─ k8s/
│  ├─ helm/
│  ├─ terraform/
│  ├─ argocd/
│  ├─ keycloak/
│  ├─ observability/
│  └─ security/
│
├─ db/
│  ├─ migrations/
│  ├─ seed/
│  └─ testdata/
│
├─ tests/
│  ├─ e2e/
│  ├─ contract/
│  ├─ load/
│  └─ security/
│
├─ docs/
│  ├─ architecture/
│  ├─ adr/
│  ├─ regulatory-mapping/
│  ├─ threat-model/
│  ├─ test-evidence/
│  ├─ failure-drills/
│  ├─ demo-scenarios/
│  └─ migration/
│
└─ legacy-node-reference/
```

---

## 7. Implementation Phases

### Phase 0 — Foundation Reset

Goal: prepare the repo for full rewrite.

Tasks:

- Add/update Gradle wrapper.
- Make Spring Boot build runnable.
- Make Next.js build runnable.
- Create Docker Compose stack for PostgreSQL, Redis, Redpanda/Kafka, Temporal, Keycloak, Prometheus, Grafana, Loki, and Tempo.
- Move Node-specific language in docs to legacy/reference wording.
- Add ADR explaining the full rewrite decision.

Acceptance:

- Spring Boot `/health` works.
- Next.js customer-web shell builds.
- Docker Compose infrastructure config validates.
- Node code is clearly marked as reference, not target.

### Phase 1 — PostgreSQL Ledger Core

Goal: implement the bank ledger in Kotlin/Spring Boot.

Tasks:

- Define customers, accounts, ledger_transactions, ledger_postings, account_balances projection, idempotency_keys, account_holds, daily_closings, reconciliation_items, audit_events, operator_approvals.
- Implement Flyway migrations.
- Implement deposit, withdrawal, internal transfer, reversal, adjustment, account hold/release.
- Use PostgreSQL transaction boundaries.
- Test SERIALIZABLE and REPEATABLE READ behavior.
- Use JUnit and Testcontainers.

Acceptance:

- Balanced posting invariant is enforced.
- Duplicate idempotency key does not duplicate postings.
- Concurrent withdrawal cannot overdraw.
- Closed day direct posting fails.
- Reversal and adjustment are balanced.

### Phase 2 — Outbox and Event Backbone

Goal: make events durable and replayable.

Tasks:

- Add outbox_events table.
- Add event contracts under `contracts/events` and `contracts/asyncapi`.
- Publish to Kafka/Redpanda after DB commit.
- Implement idempotent consumers.
- Add retry and dead-letter behavior.

Acceptance:

- Ledger posted event emits once.
- Duplicate consumer replay is safe.
- Failed event can be retried.
- Outbox and domain write are atomic.

### Phase 3 — Temporal Workflows

Goal: model long-running bank operations.

Workflows:

- complaint intake and answer approval;
- account hold/release;
- FDS transfer hold/release/block;
- AML case investigation and closure;
- reconciliation adjustment;
- customer information change;
- transfer limit change.

Acceptance:

- Workflow state survives restart.
- Approval tasks are assigned to different checker roles.
- Timeout/SLA behavior is visible.
- Workflow history is linked to audit events.

### Phase 4 — Identity and Access Control

Goal: replace mock users with Keycloak-based identity.

Tasks:

- Add Keycloak realm config.
- Define roles: CUSTOMER, BRANCH_STAFF, BRANCH_MANAGER, CALL_CENTER, COMPLAINT_HANDLER, FDS_REVIEWER, AML_REVIEWER, OPS_OPERATOR, AUDITOR, COMPLIANCE_MANAGER.
- Implement OAuth2/OIDC resource server.
- Add RBAC/ABAC policy checks.
- Add WebAuthn/MFA simulator or documented local profile.

Acceptance:

- Staff APIs require valid token.
- Customer can access only own accounts/cases.
- Manager-only approval is enforced.
- Unauthorized access returns structured errors and audit events.

### Phase 5 — Next.js Web and Staff Terminal

Goal: build the banking surfaces.

Apps:

- customer-web;
- staff-terminal;
- complaint-portal;
- admin-console;
- audit-console;
- fds-aml-console.

Screen strategy:

- Build shared UI components.
- Use screen manifests.
- Implement Inquiry, Command, Case, and Parameter templates.
- Add Playwright E2E.

Priority screens:

```text
CWB-101 account overview
CWB-102 account detail
CWB-103 transaction history
CWB-201 transfer
CWB-202 transfer result
CWB-301 complaint entry
CST-001 customer search
CST-002 customer detail
ACC-101 account search
LED-101 transaction history
CST-103 customer info change
APR-001 approval inbox
AUD-001 audit log
CMP-201 complaint workflow
FDS-201 transaction review
AML-201 STR case review
OPS-101 daily closing
OPS-201 reconciliation items
```

### Phase 6 — AML/FDS Analytics

Goal: make risk workflows credible.

Tasks:

- Implement rule-based FDS first.
- Add Python analytics service.
- Use DuckDB for local mart.
- Add scikit-learn scoring as optional enhancement.
- Keep explainable outputs for reviewer screens.

Acceptance:

- High-risk transfer creates FDS case.
- Release/block requires approval.
- AML STR simulation case can be created and closed.
- Analytics outputs are synthetic and explainable.

### Phase 7 — Platform, Observability, Security

Goal: make the project demonstrably bank-grade.

Tasks:

- Add OpenTelemetry traces, metrics, logs.
- Add Prometheus, Grafana, Loki, Tempo.
- Add Kubernetes manifests.
- Add Helm charts.
- Add Terraform modules.
- Add Argo CD app definitions.
- Add Semgrep, Trivy, SBOM, SCA, and DAST profile.
- Add OWASP ASVS 5.0 mapping.

Acceptance:

- Local observability profile runs.
- Security scans run in CI or documented local commands.
- Evidence pack includes test results, scan summaries, threat model, and failure drills.

---

## 8. Screen Platform Plan

The staff terminal must feel like an integrated bank terminal, not a generic admin page.

Common screen templates:

```text
Inquiry Template
Command Template
Case Template
Parameter Template
```

Common capabilities:

```text
transaction code input
role-aware menu
customer context panel
masked PII
reason-required lookup
unmask workflow
audit log panel
approval inbox
workflow timeline
SLA timer
attachments
comments
exception/retry panel
```

The goal is to support 30 screens first, then 70 screens, then 120+ screens through manifest generation and reusable templates.

---

## 9. Required Evidence

Every phase must update evidence.

Required docs:

```text
docs/adr/*
docs/architecture/*
docs/regulatory-mapping/*
docs/threat-model/*
docs/test-evidence/*
docs/failure-drills/*
docs/demo-scenarios/*
```

Required command evidence:

```text
./gradlew test
./gradlew integrationTest
npm run typecheck
npm run build
npx playwright test
docker compose config
trivy scan output
semgrep scan output
SBOM generation output
```

If a command cannot be run, document why. Do not claim it passed.

---

## 10. First Codex Task

Use this prompt first:

```text
Read AGENTS.md, PLAN.md, and BANKING_LAB_CODEX_PROMPT.md.

The project direction has changed: do not extend the Node.js MVP as the final system. Treat it as a legacy reference/oracle only. Rebuild the project using the target stack: Kotlin/Java + Spring Boot, PostgreSQL, Kafka/Redpanda + Outbox, Temporal, TypeScript + Next.js/React, Keycloak, OpenTelemetry, Docker/Kubernetes/Terraform/Helm/Argo CD, and security scanning.

Start with Phase 0 and Phase 1:

1. Ensure Gradle wrapper exists and Spring Boot builds.
2. Create/repair Flyway migrations under db/migrations or the configured migration path.
3. Implement Kotlin/Spring Boot ledger domain types and pure invariant tests.
4. Implement PostgreSQL-backed ledger persistence for deposit, withdrawal, internal transfer, reversal, adjustment, idempotency, and closing.
5. Add JUnit + Testcontainers tests.
6. Add Docker Compose dependencies for PostgreSQL and supporting infrastructure.
7. Update ADR and test evidence.

Do not delete Node code yet. Do not add more Node business features. Report commands run, tests passed, tests skipped, risks, and the next smallest safe task.
```

---

## 11. Definition of Done for the Rewrite Stage

This rewrite stage is successful when the project can truthfully say:

```text
The core ledger and control plane run on Kotlin/Spring Boot and PostgreSQL.
Financial events are emitted through Kafka/Redpanda with Outbox.
Long-running workflows run through Temporal.
Customer and staff channels run on TypeScript + Next.js.
Identity is handled through Keycloak/OAuth2/OIDC with RBAC/ABAC.
Observability, security scans, and evidence are part of the workflow.
The old Node.js implementation is no longer the target system.
```
