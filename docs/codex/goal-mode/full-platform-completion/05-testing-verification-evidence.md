# Testing, Verification, And Evidence

This document defines the verification surface for the full-platform Goal.
Commands should be run from the repository root unless a feature document says
otherwise.

## Documentation Coverage Checks

Use these checks after editing this Goal reference package:

```bash
find docs/codex/goal-mode/full-platform-completion -type f | sort
rg "Customer Service|KYC / CDD / EDD|Identity & Access|Account Service|Ledger Service|Transaction Posting|Balance Service|Limit Service|Transfer Service|Payment Service|Card Service|Loan Service|Deposit Product|Fee & Charge|Interest Engine|Statement Service|Notification Service|Dispute / Claim|Back Office|Admin Console" docs/codex/goal-mode/full-platform-completion
```

## Baseline Repo Checks

Run these after broad documentation or contract work:

```bash
npm test
npm run validate:manifests
npm run test:screen-engine
npm run packages:typecheck
npm run scripts:typecheck
npm run evidence:refresh-check
```

## Backend Checks

Run these after Spring/Kotlin or migration changes:

```bash
npm run test:core-banking:unit
npm run test:core-banking:integration
```

For narrow changes, run the relevant integration test class first, such as:

```bash
npm run test:core-banking:integration -- --tests lab.banking.core.ledger.application.LedgerCommandServiceIntegrationTest
npm run test:core-banking:integration -- --tests lab.banking.core.staff.StaffAccessApiParityIntegrationTest
npm run test:core-banking:integration -- --tests lab.banking.core.product.DepositProductApiIntegrationTest
npm run test:core-banking:integration -- --tests lab.banking.core.product.FeePolicyApiIntegrationTest
npm run test:core-banking:integration -- --tests lab.banking.core.loan.LoanDomainIntegrationTest
npm run test:core-banking:integration -- --tests lab.banking.core.card.CardDomainIntegrationTest
npm run test:core-banking:integration -- --tests lab.banking.core.statement.StatementReadModelIntegrationTest
npm run test:core-banking:integration -- --tests lab.banking.core.parameters.ParameterAdminIntegrationTest
```

## Frontend And Screen Checks

Run the channel-specific checks for changed apps:

```bash
npm run next:customer-web:typecheck
npm run next:staff-terminal:typecheck
npm run next:complaint-portal:typecheck
npm run next:admin-console:typecheck
npm run next:audit-console:typecheck
npm run next:fds-aml-console:typecheck
npm run next:ops-console:typecheck
npm run test:e2e
```

## Formal, Security, Platform, And Operations Checks

Run these before claiming the full Goal complete:

```bash
npm run formal:ledger
npm run security:posture-check
npm run security:evidence
npm run k8s:validate
npm run helm:template
npm run load:synthetic
npm run postgres:backup-drill
npm run node:retirement-gate
npm run goal:completion-audit -- --require-complete
```

Use Docker-backed variants when the implementation or evidence requires live
containers:

```bash
npm run security:evidence:docker
npm run postgres:backup-drill:docker-live
```

## Evidence Update Rule

Update evidence only after commands run. Each task report must include:

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

## Required Component Names

Customer Service, KYC / CDD / EDD, Identity & Access, Account Service, Ledger
Service, Transaction Posting, Balance Service, Limit Service, Transfer Service,
Payment Service, Card Service, Loan Service, Deposit Product, Fee & Charge,
Interest Engine, Statement Service, Notification Service, Dispute / Claim, Back
Office, Admin Console.

