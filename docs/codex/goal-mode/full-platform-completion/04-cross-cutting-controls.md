# Cross-Cutting Controls

These controls apply to every feature. A feature document can add stricter rules,
but it cannot weaken these controls.

## Ledger And Money Movement

- Every financial movement must create balanced double-entry postings.
- `sum(postings by transaction, currency) == 0` is non-negotiable.
- Balances are projections from postings and not mutable source-of-truth fields.
- Available balance must never exceed ledger/current balance.
- Finalized transactions are append-only.
- Corrections use reversal or balanced adjustment transactions.
- Closed business dates reject direct posting.
- Reconciliation corrections must use balanced adjustment transactions.

## Idempotency And Retry

- Every externally retried command must have an idempotency key.
- Replaying the same key must return the same business result or a structured
  conflict if the payload changed.
- Failed commands must not leave partial ledger, approval, workflow, or outbox
  state unless the failure state is explicit and tested.

## Eventing And Workflow

- Domain events must be persisted in `outbox_events` or an equivalent durable
  Outbox table before publication.
- Consumers must be idempotent.
- Retry and dead-letter behavior must be documented and tested for event-driven
  services.
- Long-running work must use Temporal or a documented Temporal-compatible state
  machine before Temporal is introduced.

## Security And Staff Controls

- Staff access to customer, account, transaction, KYC, complaint, FDS/AML,
  dispute, and audit data requires a business reason.
- PII is masked by default.
- Privileged unmasking requires role policy, step-up or equivalent control where
  configured, time-boxed exposure, and audit evidence.
- High-risk operations require maker-checker approval and separation of duties.
- Authorization failures must use structured errors.
- Keycloak/OIDC/RBAC/ABAC enforcement must be consistent across API and screen
  access.

## Synthetic-Only Boundary

- No real customer money.
- No real PII.
- No real KYC.
- No real payment network.
- No real financial institution API.
- All external integrations must use synthetic data, simulators, or explicit
  local fixtures.

## Evidence Honesty

- Do not mark evidence as passed without running the command.
- If a command is skipped, document the reason, environment prerequisite, and
  remaining risk.
- Do not count legacy Node behavior as target-stack completion.

## Required Component Names

Customer Service, KYC / CDD / EDD, Identity & Access, Account Service, Ledger
Service, Transaction Posting, Balance Service, Limit Service, Transfer Service,
Payment Service, Card Service, Loan Service, Deposit Product, Fee & Charge,
Interest Engine, Statement Service, Notification Service, Dispute / Claim, Back
Office, Admin Console.
