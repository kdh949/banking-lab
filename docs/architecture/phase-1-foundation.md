# Phase 1 Foundation Architecture

## Runtime View

```text
Browser
  -> Node runtime
       -> app shell HTML/CSS/JS
       -> mock APIs
       -> banking-domain package
       -> screen-engine package
       -> in-memory synthetic state
```

## App Shells

- `customer-web`: account overview and internal transfer simulation.
- `staff-terminal`: transaction-code input, tabbed manifest screens, reason-required customer search, customer context, audit log, approval inbox.
- `complaint-portal`: complaint intake and case status list.
- `ops-console`: daily closing manifest shell.
- `audit-console`: audit hash-chain review manifest shell.
- `fds-aml-console`: FDS case manifest shell.

## Persistence Direction

`infra/db/migrations/001_foundation.sql` defines the foundation database contract:

- Ledger source of truth: `ledger_transactions`, `ledger_postings`.
- Projection/cache: `account_balances`.
- Idempotency: `idempotency_keys`.
- Operational controls: `audit_events`, `operator_approvals`, `screen_access_logs`, `masking_access_logs`.
- Reliability controls: `daily_closings`, `reconciliation_items`.

The migration includes triggers that reject update/delete against finalized ledger source rows.

## Synthetic Data

`scripts/generate-synthetic-data.mjs` creates deterministic synthetic customers, accounts, opening postings, and projected balances. The generated data is not real PII.
