# Bank-grade Core Banking Lab

Synthetic core banking lab focused on ledger integrity, auditability, maker-checker control, and screen-manifest based operations.

This project does not handle real customer money, real personal data, or real payment networks. All data is synthetic and all external providers are simulators.

## Phase 1 Foundation

Implemented foundation scope:

- Monorepo structure for apps, services, packages, screen manifests, infra, and evidence docs.
- Mock auth users for customer, branch staff, manager, complaint, audit, and FDS/AML roles.
- Static app shells for customer web, staff terminal, complaint portal, ops console, audit console, and FDS/AML console.
- Staff terminal shell with transaction-code input, tabbed manifest screens, customer context, masked PII, reason-required lookup, audit panel, and approval inbox.
- Common UI assets under `packages/ui`.
- Screen manifest loader and validator under `packages/screen-engine`.
- Form validation helpers under `packages/form-engine`.
- Core banking domain primitives for double-entry postings, projection balances, idempotency, reversal, audit hash chain, masking, maker-checker, workflow, and synthetic data.
- PostgreSQL foundation migration under `infra/db/migrations/001_foundation.sql`.
- Node runtime serving the app shells and mock APIs.
- Test and evidence generation scripts.

## Phase 2 Ledger Core

Implemented ledger core scope:

- Customer/account/ledger/posting/balance command service in `services/core-banking`.
- Deposit, withdrawal, internal transfer, and reversal commands.
- Idempotency replay for ledger commands.
- Serialized command execution to prevent concurrent withdrawal overdraw in the in-memory runtime.
- Closed business day guard for direct posting commands.
- Ledger invariant validation for balanced postings, duplicate idempotency keys, reversal references, partial finalization, and balance projection.
- Runtime APIs under `/api/ledger/deposits`, `/api/ledger/withdrawals`, `/api/ledger/transfers`, `/api/ledger/reversals`, `/api/ledger/transactions`, and `/api/ledger/balances`.

## Phase 3 Staff Terminal MVP

Implemented staff terminal scope:

- Transaction-code work area for `CST-001`, `CST-002`, `ACC-101`, `LED-101`, `CST-103`, `APR-001`, and `AUD-001`.
- Customer context panel, approval inbox, and audit log panel.
- Reason-required customer detail, account inquiry, and transaction inquiry APIs.
- Masked PII by default and privileged, reasoned unmask request with audit trail.
- Customer information change request with maker-checker approval before mutation.
- Staff terminal manifests for customer detail, customer info change, and transaction history.

## Run Locally

```bash
npm test
npm run validate:manifests
npm run generate:synthetic-data
npm run evidence:phase1
npm run evidence:phase2
npm run evidence:phase3
npm start
```

Then open:

- `http://127.0.0.1:8080/staff-terminal`
- `http://127.0.0.1:8080/customer-web`
- `http://127.0.0.1:8080/complaint-portal`
- `http://127.0.0.1:8080/ops-console`
- `http://127.0.0.1:8080/audit-console`
- `http://127.0.0.1:8080/fds-aml-console`

Docker Compose entrypoint:

```bash
docker compose up --build
```

## Key Invariants

- Every ledger transaction has two or more postings and must sum to zero by currency.
- Balances are derived through `projectBalances`; source-of-truth balances are not directly mutated.
- External commands require idempotency keys.
- Reversal transactions reference the original transaction.
- Closed business days reject direct posting commands.
- Concurrent withdrawals serialize through the ledger command service and cannot overdraw available balance.
- Staff customer/account-sensitive access requires a business reason and audit event.
- Audit events are append-only and hash chained.
- PII unmasking is timeboxed, role-gated, reason-required, and audited.
- High-risk staff commands require maker-checker approval, and maker and checker must differ.
- Screen manifests must declare roles, template, audit, masking, and approval metadata.

## Repository Map

- `apps/*`: app shells.
- `runtime`: local Node runtime and mock API.
- `packages/banking-domain`: ledger, audit, auth, masking, maker-checker, workflow, synthetic data.
- `packages/screen-engine`: manifest loading and validation.
- `packages/form-engine`: reusable form validation.
- `screen-manifests/*`: manifest-driven business screens.
- `services/*`: initial service boundary exports.
- `infra/db/migrations`: PostgreSQL schema foundation.
- `docs/*`: ADRs, mappings, evidence, drills, reports, demo scenarios.
- `tests/*`: foundation invariant and runtime tests.

## Current Boundary

Phase 1 is a local runnable scaffold, not a production banking system. Persistence is in-memory at runtime while the database migration captures the intended PostgreSQL schema. Keycloak is represented by mock auth until a later infrastructure phase.
