# Goal Contract

This document defines what "done" means for the full platform completion Goal.
It is intentionally stricter than a normal implementation checklist because a
Codex Goal should continue until evidence proves the outcome or a real blocker
prevents further progress.

## Completion Conditions

The Goal can be marked complete only when all of the following are true:

1. Every required component has target-stack implementation evidence:
   Customer Service, KYC / CDD / EDD, Identity & Access, Account Service, Ledger
   Service, Transaction Posting, Balance Service, Limit Service, Transfer
   Service, Payment Service, Card Service, Loan Service, Deposit Product, Fee &
   Charge, Interest Engine, Statement Service, Notification Service, Dispute /
   Claim, Back Office, and Admin Console.
2. Every supporting area has evidence: FDS/AML analytics, reconciliation,
   reporting, screen platform, platform deployment, observability, security
   verification, backup/restore, and failure drills.
3. `docs/implementation-coverage-matrix.md` reflects actual code state and does
   not count legacy Node behavior as target-stack coverage.
4. Each feature document's acceptance criteria are satisfied or explicitly
   marked out of scope with a documented reason accepted by the user.
5. Every financial movement is represented by balanced double-entry postings.
6. Balance reads are projections from postings and never mutable truth.
7. Finalized transactions are append-only; corrections use reversals or balanced
   adjustments.
8. High-risk operations enforce maker-checker separation and audit events.
9. Staff access to customer, account, transaction, KYC, complaint, FDS/AML, and
   dispute data requires a business reason.
10. PII is masked by default and unmasking is privileged, time-boxed, audited,
    and synthetic-only.
11. Events are emitted through durable Outbox persistence.
12. Temporal workflows or explicitly justified Temporal-compatible state machines
    exist for long-running cases.
13. Verification commands in `05-testing-verification-evidence.md` pass, or any
    skipped command has a precise environment reason and documented residual
    risk.

## Non-Completion Conditions

Do not mark the Goal complete when:

- The feature exists only in a manifest or README.
- The behavior exists only in legacy Node `.mjs` code.
- A screen exists without an API-backed command/read path where the feature
  requires state.
- A Spring API exists without PostgreSQL-backed state for durable business data.
- A command mutates account balances directly.
- A high-risk staff command lacks maker-checker approval.
- An event is published directly without durable Outbox state.
- Evidence files say "pass" without commands being run.
- Integration tests are skipped and the feature is still claimed as complete.

## Boundaries

Allowed target implementation areas:

- `services/core-banking/**`
- `services/payment-service/**`
- `services/notification-service/**`
- `services/reporting-service/**`
- `analytics/**`
- `apps/**`
- `packages/**`
- `screen-manifests/**`
- `contracts/**`
- `infra/**`
- `db/migrations/**`
- `docs/**`
- `tests/**`

Legacy Node areas may be read for oracle behavior and parity extraction, but
must not become the target implementation.

## Iteration Rule

Each iteration must finish with one of these states:

- Verified progress: changed target-stack code or docs, ran a relevant check,
  and recorded evidence.
- No-op evidence update: no implementation change was needed because actual code
  already satisfied the criterion, and evidence was updated from inspection.
- Blocked: no safe next step remains without user input or environment change.

## Blocked Report Required Fields

When blocked, report:

```text
Attempted paths
Evidence gathered
Exact blocker
Files inspected
Commands run
Remaining risk
Smallest input or environment change needed
```

