# Bank-grade Core Banking Lab

Synthetic core banking lab focused on ledger integrity, auditability, maker-checker control, manifest-driven operations, complaint workflow, AML/FDS simulation, reconciliation, and evidence.

This project does not handle real customer money, real personal data, or real payment networks. All data is synthetic and all external providers are simulators.

## 1. Project Overview

The lab models a simulated digital bank with:

- customer web banking
- staff integrated terminal
- electronic complaint portal
- FDS/AML review console
- operations and reconciliation console
- audit console
- core banking ledger service
- workflow and maker-checker control
- evidence documents and generated test reports

## 2. Why This Is Not a Simple Bank Clone

The implementation prioritizes bank-grade controls over UI breadth:

- every financial movement is represented as balanced double-entry postings
- balances are projections, not source-of-truth fields
- externally retried commands are idempotent
- finalized ledger transactions are reversed or adjusted, not mutated
- staff sensitive access is reason-required and audited
- high-risk operations require maker-checker approval
- screens scale through manifests and reusable templates
- every phase has tests and evidence

## 3. Overall Architecture

```text
apps/*                  static app shells
packages/banking-domain ledger, audit, masking, auth, maker-checker
packages/screen-engine  screen manifest loader and validator
packages/form-engine    reusable validation helpers
services/core-banking   ledger command service
services/complaint-*    complaint workflow
services/fds-service    FDS rule and case lifecycle
services/aml-service    AML case simulation
services/reconciliation EOD and unmatched item workflow
runtime                 local Node HTTP runtime
docs                    ADRs, evidence, mappings, drills, demo scripts
```

## 4. Core Ledger Design

Implemented controls:

- `DEPOSIT`, `WITHDRAWAL`, `INTERNAL_TRANSFER`, `REVERSAL`, and `ADJUSTMENT` commands
- balanced postings enforced by `assertTransactionBalanced`
- projected balances from `projectBalances`
- serialized command execution for concurrent withdrawals
- idempotency store for retried commands
- reversal references to original transaction
- closed business date guard
- reconciliation adjustments as balanced ledger transactions

## 5. Staff Integrated Terminal

The staff terminal includes:

- transaction code input
- tabbed manifest screens
- customer context panel
- masked PII by default
- reason-required customer/account/transaction lookup
- approval inbox
- audit log panel
- customer information change through maker-checker approval

## 6. Customer Web Banking

The customer web includes:

- mock customer login
- account list and detail from projected ledger balances
- transaction history from the same ledger source used by staff inquiry
- idempotent transfer submission
- transfer results for `POSTED`, `HELD`, `FAILED`, and `BLOCKED`
- complaint entry navigation

## 7. Electronic Complaint Workflow

The complaint workflow includes:

- customer complaint intake
- shared customer/staff complaint case source
- SLA due date and timeline
- staff classify, assign, review, and answer draft
- answer approval before customer-visible response
- customer confirmation and case closure

## 8. AML/FDS Simulation

FDS controls:

- high amount rule
- new device and high amount rule
- first-time beneficiary rule
- velocity rule
- transaction hold without ledger posting
- release/block review through maker-checker approval

AML controls:

- customer risk grade evaluation
- suspicious transfer candidate generation
- STR simulation case
- reviewer assignment
- comments
- approval-controlled closure

## 9. Reconciliation

Operations controls:

- EOD daily closing
- ledger invariant validation
- synthetic external institution file
- unmatched reconciliation item creation
- owner-required mismatch cases
- closed-day direct posting rejection
- maker-checker adjustment request
- balanced `ADJUSTMENT` transaction on an open business date

## 10. Security, Audit, and Internal Control

Implemented controls:

- append-only audit hash chain
- role-shaped mock users
- PII masking and privileged unmask path
- reason-required sensitive staff access
- high-risk approval business types
- manifest-declared roles, audit, masking, workflow, and approval metadata
- synthetic-only external simulator boundary

## 11. Test Strategy

Run:

```bash
npm run parity
npm test
npm run validate:manifests
npm run evidence:phase1
npm run evidence:phase2
npm run evidence:phase3
npm run evidence:phase4
npm run evidence:phase5
npm run evidence:phase6
npm run evidence:pack
docker compose config
```

Current automated coverage includes ledger invariants, runtime APIs, customer web, staff terminal, complaint workflow, FDS/AML, reconciliation, manifests, masking, audit, idempotency, reversal, and maker-checker.

## 11.1 Kotlin + Next.js Migration

The current Node.js `.mjs` runtime is the executable reference for the intended Kotlin/Spring Boot backend and TypeScript/Next.js frontend migration. Do not delete the Node reference until the retirement gate is ready.

Migration entrypoints:

```bash
npm run parity
npm run node:retirement-gate
```

Read `docs/migration/kotlin-next-playbook.md` before adding target-stack code. The migration must preserve the 42 mapped Node reference scenarios in `docs/migration/parity-scenarios.json`, use the structured error contract in `docs/migration/structured-api-error-contract.md`, and keep `docs/migration/node-retirement-gate.json` blocked until Spring Boot, Next.js, evidence, and review gates pass.

Initial Spring Boot scaffold files live under `services/core-banking/src/main/kotlin`. Once JDK 21 and Gradle are available, verify the target backend with:

```bash
docker compose --profile migration up -d postgres
gradle :services:core-banking:test
gradle :services:core-banking:bootRun
```

Initial Next.js scaffold files live under `apps/customer-web/src`. The static `apps/customer-web/public/index.html` shell remains for the Node reference runtime. Verify the target frontend with:

```bash
npm run next:customer-web:typecheck
npm run next:customer-web:build
npm audit --omit=dev
```

## 12. Failure Drills

Failure drill documents live under `docs/failure-drills`.

Covered drills include:

- duplicate idempotent transfer
- concurrent withdrawal
- staff lookup without reason
- maker self-approval
- complaint answer before approval
- FDS release/block
- closed-day posting attempt
- reconciliation adjustment

## 13. Run Locally

```bash
npm start
```

Open:

- `http://127.0.0.1:8080/staff-terminal`
- `http://127.0.0.1:8080/customer-web`
- `http://127.0.0.1:8080/complaint-portal`
- `http://127.0.0.1:8080/ops-console`
- `http://127.0.0.1:8080/audit-console`
- `http://127.0.0.1:8080/fds-aml-console`

Docker Compose:

```bash
docker compose up --build
```

## 14. Demo Scenario

Primary walkthrough:

1. Run `npm test` and `npm run evidence:pack`.
2. Show ledger code and invariant tests.
3. Open staff terminal and perform reason-required lookup.
4. Open customer web and submit idempotent transfer.
5. Open complaint portal and staff workflow.
6. Open FDS/AML console and release a held transfer.
7. Open ops console and run EOD reconciliation.
8. Show `docs/test-evidence/evidence-pack-summary.md`.

Detailed script: `docs/demo-scenarios/demo-video-script.md`.

## 15. Limits and Legal Boundary

This is a local simulation:

- no real deposits
- no real transfers
- no real payment networks
- no real KYC provider
- no real customer PII
- no public complaint service

Runtime persistence is in-memory. The SQL migration captures the intended relational contract, but durable storage is not yet wired to the runtime.

## 16. Future Improvements

Next engineering slices:

- PostgreSQL persistence for ledger, approvals, audit, and cases
- real OAuth2/OIDC simulator such as Keycloak
- crash recovery for approval execution
- outbox/inbox event processing
- observability with OpenTelemetry, Prometheus, Grafana, and Loki
- formal ledger model with TLA+ or Alloy
- Playwright browser E2E once a browser target is available
- SAST/SCA/SBOM automation
