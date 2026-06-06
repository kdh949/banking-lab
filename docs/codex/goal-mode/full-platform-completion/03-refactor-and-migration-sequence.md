# Refactor And Migration Sequence

Use this order when the Goal discovers that folder placement, package boundaries,
or feature state need cleanup. The sequence minimizes ledger risk and avoids
turning documentation cleanup into unsafe implementation churn.

## Phase 0: Re-Audit Before Moving Anything

1. Read the feature doc.
2. Inspect actual code, manifests, contracts, migrations, and tests.
3. Check `docs/implementation-coverage-matrix.md`.
4. Decide whether the issue is missing behavior, misplaced code, duplicated UI,
   missing evidence, or stale documentation.

Do not move code only to satisfy a preferred tree if current boundaries are
working and no implementation task requires the move.

## Phase 1: Preserve Public Behavior

Before refactoring package or folder placement:

- Identify public APIs, events, workflow names, screen IDs, table names, and
  evidence files that must remain stable.
- Add or confirm tests for the behavior being moved.
- Keep Node `.mjs` reference paths untouched except for oracle/evidence updates.

## Phase 2: Move By Bounded Context

Move or create code by domain boundary:

- Customer Service and KYC / CDD / EDD before Account Service changes.
- Account Service before Ledger Service command expansion.
- Ledger Service, Transaction Posting, Balance Service, and Limit Service before
  Transfer Service, Payment Service, Card Service, Loan Service, Deposit Product,
  Fee & Charge, and Interest Engine.
- Statement Service after ledger projections are stable.
- Dispute / Claim, Back Office, and Admin Console after approval/audit/security
  foundations are stable.
- Notification Service after Outbox event contracts are stable.

## Phase 3: Add Contracts Before Cross-Service Calls

For Payment Service, Notification Service, reporting, and workflow workers:

1. Define API/event/workflow contract.
2. Add synthetic simulator boundary.
3. Add idempotency and retry semantics.
4. Add contract tests or integration tests.
5. Only then wire runtime behavior.

## Phase 4: Update UI Through Manifests

For each channel:

1. Add or update `screen-manifests/<channel>`.
2. Add API client methods in `packages/api-client`.
3. Add renderer or narrow custom panel in `apps/<channel>`.
4. Run manifest validation, typecheck, and E2E smoke where applicable.

## Phase 5: Evidence And Coverage

Update docs only after verification:

- `docs/implementation-coverage-matrix.md`
- relevant `docs/test-evidence/*.md`
- relevant generated evidence files only when commands generate them
- feature docs if actual implementation changed the accepted plan

## Required Component Names

Customer Service, KYC / CDD / EDD, Identity & Access, Account Service, Ledger
Service, Transaction Posting, Balance Service, Limit Service, Transfer Service,
Payment Service, Card Service, Loan Service, Deposit Product, Fee & Charge,
Interest Engine, Statement Service, Notification Service, Dispute / Claim, Back
Office, Admin Console.

