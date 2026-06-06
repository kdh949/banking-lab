# Architecture And Folder Placement

This is the canonical folder-placement guide for the full-platform Goal.

## Core Rule

`services/core-banking` is a modular monolith for ledger-coupled banking domains,
not a catch-all folder for every platform capability. Put a capability in
`services/core-banking` only when it needs the same transactional boundary as
customer/account/ledger state or must synchronously preserve ledger invariants.

## `services/core-banking` Bounded Contexts

Use these package boundaries under:

```text
services/core-banking/src/main/kotlin/lab/banking/core/
```

- `customer`: Customer Service read/write paths that must join account, staff
  access, or audit state.
- `staff`: Back Office staff workflows and reason-required access.
- `security`: Identity & Access authorization enforcement, session, step-up, and
  trusted-device controls.
- `ledger`: Ledger Service, Transaction Posting, Balance Service, posting-time
  limits, reversals, adjustments, and closing guards.
- `product`: Deposit Product, Fee & Charge, and Interest Engine logic that posts
  to the ledger.
- `loan`: Loan Service application, approval, execution, accrual, repayment, and
  delinquency foundations.
- `card`: Card Service issue, authorization, capture, reversal, limits, loss,
  and dispute hooks.
- `complaint`: Complaint and Dispute / Claim case foundations when they need
  staff approval and audit visibility.
- `fds`, `aml`, `reconciliation`, `eod`: operational control domains that may
  trigger ledger holds, releases, adjustments, or closing guards.
- `approval`, `audit`, `eventing`, `temporal`, `workflow`, `parameters`,
  `observability`, and `common`: cross-cutting modules used by the domains.

## Separate Services

Use separate services for capabilities with different lifecycle or scaling needs:

- `services/payment-service`: Payment Service for billing, payment instruction,
  autopay, retry, cancellation, and payment-network simulators. It must call or
  publish commands to the core-banking ledger boundary instead of writing ledger
  tables directly.
- `services/notification-service`: Notification Service for templates, delivery
  preferences, event consumption, delivery logs, retry/dead-letter handling, and
  synthetic SMS/push/email sinks.
- `services/reporting-service`: reporting and document-generation workloads that
  can read projections and evidence without holding ledger command transactions.
- `services/external-simulators`: synthetic-only simulators for payment networks,
  KYC checks, mail/SMS/push providers, and partner systems.
- `services/workflow-workers`: optional Temporal workers when workflows are split
  from the Spring API service.

## Frontend Placement

Use existing channel apps:

- `apps/customer-web`
- `apps/staff-terminal`
- `apps/complaint-portal`
- `apps/admin-console`
- `apps/audit-console`
- `apps/fds-aml-console`
- `apps/ops-console`

Do not copy-paste screens. New or changed screens must go through
`screen-manifests/<channel>`, `packages/screen-engine`, `packages/form-engine`,
and `packages/channel-ui` unless a narrow custom panel is justified.

## Contracts And Infrastructure

- API/event/workflow contracts: `contracts/openapi`, `contracts/events`,
  `contracts/asyncapi`, and `contracts/temporal`.
- PostgreSQL migrations: `db/migrations`.
- Docker Compose, Kubernetes, Helm, Terraform, Argo CD, Keycloak, observability,
  and security assets: `infra/**`.
- Evidence and operating reports: `docs/test-evidence/**`,
  `docs/failure-drills/**`, and feature-specific docs under `docs/**`.

## Required Component Names

Customer Service, KYC / CDD / EDD, Identity & Access, Account Service, Ledger
Service, Transaction Posting, Balance Service, Limit Service, Transfer Service,
Payment Service, Card Service, Loan Service, Deposit Product, Fee & Charge,
Interest Engine, Statement Service, Notification Service, Dispute / Claim, Back
Office, Admin Console.

