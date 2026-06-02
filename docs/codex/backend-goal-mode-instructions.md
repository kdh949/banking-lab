# Backend-focused Codex Goal Instructions

Use this document when running Codex Goal Mode for backend-only implementation work in `kdh949/banking-lab`.

## 1. Mission

Implement the backend migration slice for the synthetic bank-grade core banking lab.

The backend target is Kotlin/Spring Boot + PostgreSQL first, then Outbox/Kafka, Temporal-compatible workflow contracts, identity hooks, observability, and evidence.

This is not a real bank. Do not handle real money, real PII, real KYC, real payment networks, or real financial institution APIs. Use synthetic data and simulators only.

## 2. Required Reading Order

Before planning or coding, read:

1. `PLAN.md`
2. `AGENTS.md`
3. `BANKING_LAB_CODEX_PROMPT.md`
4. `docs/migration/kotlin-next-playbook.md`
5. `docs/migration/structured-api-error-contract.md`
6. `docs/migration/parity-scenarios.json`
7. `docs/migration/node-retirement-gate.json`
8. Existing Node oracle tests under `tests/*.test.mjs`
9. `docs/architecture/kotlin-spring-foundation.md`

If documents conflict, use this priority:

```text
PLAN.md > AGENTS.md > BANKING_LAB_CODEX_PROMPT.md > docs/migration/* > architecture docs > README claims > Node implementation details
```

## 3. Non-negotiable Constraints

- Do not expand the Node.js `.mjs` MVP as the final system.
- Do not delete or rewrite Node reference assets.
- Treat `runtime/server.mjs`, `runtime/labApp.mjs`, current `.mjs` domain packages, static app shells under `apps/*/public`, `tests/*.test.mjs`, and evidence scripts as legacy oracle assets.
- Do not claim Node retirement readiness until the retirement gate says it is ready with evidence.
- Do not directly mutate balances.
- Do not publish domain events without durable Outbox persistence.
- Do not expose unmasked PII by default.
- Do not mark evidence as passed unless commands actually ran.

## 4. Backend Invariants

Preserve these in code and tests:

```text
sum(postings by transaction, currency) == 0
balance == sum(postings by account)
available_balance <= ledger_balance
idempotent request creates at most one business result
finalized transactions are append-only
closed business day cannot be mutated directly
reversal references original transaction
adjustment is balanced and approved
sensitive staff access requires audit reason
high-risk operation requires maker-checker approval
```

## 5. Backend Scope

Focus on these backend areas only:

```text
services/core-banking/**
infra/db/migrations/**
contracts/openapi/**
contracts/events/**
contracts/asyncapi/**
contracts/temporal/**
docs/architecture/backend-*.md
docs/architecture/api-*.md
docs/architecture/workflow-*.md
docs/test-evidence/backend-*.md
docs/failure-drills/backend-*.md
```

Avoid frontend work unless needed to keep contracts consistent. Do not edit customer/staff React screens in this backend run.

## 6. Single-writer Shared Files

Only one backend subagent may edit each of these files:

```text
settings.gradle.kts
build.gradle.kts
services/core-banking/build.gradle.kts
docker-compose.yml
package.json
package-lock.json
PLAN.md
AGENTS.md
BANKING_LAB_CODEX_PROMPT.md
docs/migration/node-retirement-gate.json
```

Prefer adding new backend files over editing shared root files. If a shared file must change, assign it to one subagent only and document the reason.

## 7. Recommended Backend Subagents

Use subagents explicitly.

### 7.1 `backend-repo-explorer`

Read-only. Map current backend files, tests, migrations, and Node oracle tests. Report safe edit paths, locked files, and smallest backend slice.

### 7.2 `ledger-domain-worker`

Allowed paths:

```text
services/core-banking/src/main/kotlin/lab/banking/core/domain/**
services/core-banking/src/main/kotlin/lab/banking/core/ledger/**
services/core-banking/src/test/kotlin/lab/banking/core/ledger/**
docs/test-evidence/backend-ledger-*.md
```

Implement or improve Kotlin ledger domain logic:

- customers and accounts as synthetic domain records;
- ledger transactions and postings;
- balanced double-entry invariant;
- balance projection from postings;
- deposit, withdrawal, internal transfer;
- reversal and adjustment;
- idempotency behavior;
- closed business date guard;
- tests mirroring Node ledger oracle behavior.

### 7.3 `postgres-migration-worker`

Allowed paths:

```text
infra/db/migrations/**
services/core-banking/src/main/resources/**
services/core-banking/src/test/kotlin/lab/banking/core/persistence/**
docs/test-evidence/backend-postgres-*.md
```

Implement or improve PostgreSQL/Flyway persistence:

- customers;
- accounts;
- ledger_transactions;
- ledger_postings;
- account_balances projection;
- idempotency_keys;
- account_holds;
- daily_closings;
- reconciliation_items;
- audit_events;
- operator_approvals;
- Testcontainers tests if available.

### 7.4 `api-contract-worker`

Allowed paths:

```text
services/core-banking/src/main/kotlin/lab/banking/core/api/**
services/core-banking/src/test/kotlin/lab/banking/core/api/**
contracts/openapi/**
docs/architecture/api-*.md
```

Implement or improve backend API contract:

- `/health` remains synthetic-only and migration-aware;
- structured errors match `docs/migration/structured-api-error-contract.md`;
- ledger command endpoints are contract-safe;
- API responses do not expose real PII;
- OpenAPI draft is additive and stable.

### 7.5 `outbox-event-worker`

Allowed paths:

```text
services/core-banking/src/main/kotlin/lab/banking/core/outbox/**
services/core-banking/src/test/kotlin/lab/banking/core/outbox/**
contracts/events/**
contracts/asyncapi/**
docs/architecture/backend-outbox-*.md
```

Design or implement durable Outbox behavior:

- outbox_events table usage;
- event contract for ledger posted, transfer held, adjustment approved, audit event created;
- idempotent consumer contract;
- retry/dead-letter behavior documentation or tests.

Do not directly publish Kafka events inside business methods without persisted Outbox.

### 7.6 `workflow-control-worker`

Allowed paths:

```text
services/core-banking/src/main/kotlin/lab/banking/core/workflow/**
services/core-banking/src/test/kotlin/lab/banking/core/workflow/**
contracts/temporal/**
docs/architecture/workflow-*.md
```

Implement Temporal-compatible contracts or state-machine foundations for:

- account hold/release;
- transfer review;
- complaint answer approval;
- FDS release/block;
- AML closure;
- reconciliation adjustment;
- customer information change;
- transfer limit change.

Enforce maker-checker separation of duties in tests or contract examples.

### 7.7 `backend-reviewer`

Read-only. Review all backend changes together. Check:

- overlapping file edits;
- direct balance mutation;
- missing idempotency;
- unbalanced postings;
- non-append-only finalized transactions;
- closed-day mutation;
- missing audit reason;
- maker self-approval;
- event publication without Outbox;
- fake evidence claims;
- Node reference deletion or final-system expansion.

## 8. Execution Policy

1. Start with read-only exploration.
2. Produce a file ownership table before implementation.
3. Run only file-disjoint implementation subagents in parallel.
4. Keep shared files single-writer.
5. Prefer the smallest working vertical slice over broad incomplete scaffolding.
6. Add tests and evidence for every backend change.
7. If the environment lacks JDK, Gradle, Docker, or Node dependencies, report that clearly and provide exact local commands.

## 9. Commands to Run

Run existing reference checks when possible:

```bash
npm test
npm run parity
npm run validate:manifests
npm run node:retirement-gate
```

Run backend target checks when possible:

```bash
./gradlew :services:core-banking:test
docker compose --profile migration config
docker compose --profile migration up -d postgres
curl http://127.0.0.1:8081/health
```

If `./gradlew` is not available yet, either add the Gradle wrapper as a single-writer task or report the exact blocker.

## 10. Required Final Report

After the backend task, report:

```text
Subagents spawned
Changed files by subagent
Commands run
Passing tests
Failing/skipped tests with reason
Backend invariants affected
Security/control impact
Conflict risks
Remaining risk
Next smallest safe backend task
```
