# Current State Audit

Use this audit before implementing or claiming any feature. Documentation is not
evidence by itself; the audit must inspect actual code, migrations, contracts,
tests, and generated evidence.

## Required Inspection Targets

Inspect these areas for every feature:

- Backend: `services/core-banking/src/main/kotlin/lab/banking/core/**`
- Backend tests: `services/core-banking/src/test/**` and
  `services/core-banking/src/integrationTest/**`
- Database: `db/migrations/**`
- API client: `packages/api-client/src/**`
- Auth client: `packages/auth-client/src/**`
- Screen engine: `packages/screen-engine/src/**`
- Form engine: `packages/form-engine/src/**`
- Channel UI: `packages/channel-ui/src/**`
- Apps: `apps/customer-web`, `apps/staff-terminal`, `apps/complaint-portal`,
  `apps/admin-console`, `apps/audit-console`, `apps/fds-aml-console`, and
  `apps/ops-console`
- Screen manifests: `screen-manifests/**`
- Contracts: `contracts/events`, `contracts/asyncapi`, and `contracts/temporal`
- Platform: `infra/docker-compose`, `infra/k8s`, `infra/helm`, `infra/terraform`,
  `infra/argocd`, `infra/keycloak`, `infra/observability`, and `infra/security`
- Evidence: `docs/implementation-coverage-matrix.md` and `docs/test-evidence/**`

## Status Classification

Use the same status vocabulary as `docs/implementation-coverage-matrix.md`:

```text
complete
api-backed-read
api-backed-command
browser-e2e-backed
live-keycloak-backed
manifest-only
partial
missing
not-applicable
```

Do not downgrade or upgrade status from text claims. Upgrade only when actual
target-stack code and evidence support it.

## Audit Procedure

1. Start from the feature document's `Current Code To Inspect` list.
2. Search with `rg` for the screen IDs, route names, table names, event names,
   workflow names, and package names.
3. Confirm whether state is PostgreSQL/Flyway-backed or only in-memory/demo.
4. Confirm whether the API client calls a Spring-backed endpoint.
5. Confirm whether the UI is API-backed, manifest-only, or static.
6. Confirm Keycloak/RBAC/ABAC enforcement for protected reads and commands.
7. Confirm audit events, maker-checker, structured errors, idempotency, and
   synthetic-only controls where applicable.
8. Run the smallest relevant verification command.
9. Update `docs/implementation-coverage-matrix.md` and evidence only after the
   command result is known.

## Required Component Names

Every full-platform audit must account for these names exactly:

Customer Service, KYC / CDD / EDD, Identity & Access, Account Service, Ledger
Service, Transaction Posting, Balance Service, Limit Service, Transfer Service,
Payment Service, Card Service, Loan Service, Deposit Product, Fee & Charge,
Interest Engine, Statement Service, Notification Service, Dispute / Claim, Back
Office, Admin Console.
