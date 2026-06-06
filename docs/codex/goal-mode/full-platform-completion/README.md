# Full Platform Completion Goal Reference

Use this folder as the entry point for a long-running Codex Goal that completes
the synthetic Banking Lab platform. It does not replace `PLAN.md`, `AGENTS.md`,
or `BANKING_LAB_CODEX_PROMPT.md`; it turns those higher-priority instructions
into a Goal-mode work package with auditable finish lines.

The Goal design follows the official OpenAI Cookbook guidance for Codex Goals:
a Goal should define the outcome, verification surface, constraints, boundaries,
iteration policy, and blocked stop condition. See:
https://developers.openai.com/cookbook/examples/codex/using_goals_in_codex

## Reading Order

Before starting or resuming the Goal, read these files in order:

1. `PLAN.md`
2. `AGENTS.md`
3. `BANKING_LAB_CODEX_PROMPT.md`
4. Existing Node oracle tests under `tests/*.test.mjs`
5. `docs/implementation-coverage-matrix.md`
6. `docs/codex/backend-goal-mode-instructions.md`
7. Every file in this folder
8. Relevant architecture, ADR, migration, and evidence docs

If documents conflict, use this priority:

```text
PLAN.md
  > AGENTS.md
  > BANKING_LAB_CODEX_PROMPT.md
  > this Goal reference folder
  > migration/parity docs
  > architecture/evidence docs
  > README claims
  > legacy Node implementation details
```

## Required Component Coverage

The Goal is not complete until the target-stack implementation and evidence cover
all required platform components:

1. Customer Service
2. KYC / CDD / EDD
3. Identity & Access
4. Account Service
5. Ledger Service
6. Transaction Posting
7. Balance Service
8. Limit Service
9. Transfer Service
10. Payment Service
11. Card Service
12. Loan Service
13. Deposit Product
14. Fee & Charge
15. Interest Engine
16. Statement Service
17. Notification Service
18. Dispute / Claim
19. Back Office
20. Admin Console

Supporting areas are also mandatory for a bank-grade lab: FDS/AML analytics,
reconciliation, reporting, screen platform, observability, security verification,
platform deployment, backup/restore, and failure-drill evidence.

## Document Map

- `GOAL_PROMPT.md` contains the ready-to-use `/goal` prompt.
- `00-goal-contract.md` defines completion, continuation, and blocked rules.
- `01-current-state-audit.md` defines how to inspect actual code before claims.
- `02-architecture-folder-placement.md` defines target folder placement.
- `03-refactor-and-migration-sequence.md` defines safe implementation order.
- `04-cross-cutting-controls.md` defines invariants and security controls.
- `05-testing-verification-evidence.md` defines verification commands.
- `features/*.md` defines per-component implementation guidance.
- `supporting/*.md` defines required cross-domain platform capabilities.

## Core Rule

Do not mark any feature complete from documentation alone. For every feature,
inspect the actual target-stack code, check the database migrations and contracts,
run the smallest meaningful verification command, and update evidence before
claiming completion.

## Folder Placement Rule

Do not place every banking capability flatly under one `core-banking` folder.
Use `services/core-banking` as a modular monolith only for ledger-coupled domains
that need one PostgreSQL transaction boundary. Use separate services or folders
for capabilities with different deployment, scaling, or integration lifecycles:
payment, notification, analytics, reporting, simulators, platform, and security
verification.
