# Goal Prompt

Paste this prompt into Codex Goal mode from the repository root.

```text
/goal Complete the synthetic bank-grade Banking Lab platform according to
docs/codex/goal-mode/full-platform-completion/README.md and every feature
document under docs/codex/goal-mode/full-platform-completion/features/ and
docs/codex/goal-mode/full-platform-completion/supporting/. Verify completion
with the listed Spring, Next.js, screen manifest, E2E, formal, security,
platform, evidence, and goal-completion commands. Preserve ledger invariants,
synthetic-only boundaries, Node oracle-only status, PII masking, reason-required
staff access, maker-checker separation, durable Outbox eventing, Temporal or
Temporal-compatible workflow visibility, Keycloak/RBAC/ABAC authorization,
structured errors, and evidence honesty. After each iteration, inspect actual
code and tests, update the relevant feature/evidence docs, run the smallest
meaningful verification, and choose the next highest-risk incomplete feature. If
blocked, report attempted paths, evidence gathered, exact blocker, and the
smallest user input or environment change needed.
```

## Outcome

The desired end state is a target-stack, synthetic-only, bank-grade lab that
covers all 20 required components:

Customer Service, KYC / CDD / EDD, Identity & Access, Account Service, Ledger
Service, Transaction Posting, Balance Service, Limit Service, Transfer Service,
Payment Service, Card Service, Loan Service, Deposit Product, Fee & Charge,
Interest Engine, Statement Service, Notification Service, Dispute / Claim, Back
Office, and Admin Console.

The platform must also cover FDS/AML analytics, reconciliation, reporting, screen
platform infrastructure, observability, security verification, deployment
manifests, backup/restore, and failure-drill evidence.

## Verification Surface

Use concrete evidence only:

- Source inspection in `services/core-banking`, `apps/*`, `packages/*`,
  `screen-manifests/*`, `contracts/*`, `infra/*`, `analytics/*`, `db/migrations`,
  and `docs/test-evidence/*`.
- Target-stack tests and checks listed in `05-testing-verification-evidence.md`.
- Updated rows in `docs/implementation-coverage-matrix.md`.
- Evidence files under `docs/test-evidence/*` and generated evidence under
  `docs/test-evidence/generated/*`.

## Constraints

- Do not expand the Node `.mjs` implementation as the target product.
- Do not handle real money, real PII, real KYC, real payment networks, or real
  financial institution APIs.
- Do not directly mutate balances; balances are projections from postings.
- Do not publish events without durable Outbox persistence.
- Do not expose unmasked PII by default.
- Do not approve high-risk staff operations without maker-checker separation.
- Do not claim completion when tests are skipped unless the skip reason and
  remaining risk are documented.

## Iteration Policy

At the end of every Goal iteration:

1. Re-read the relevant feature doc and current code.
2. Identify the highest-risk incomplete acceptance criterion.
3. Make the smallest target-stack change that advances that criterion.
4. Run the narrowest meaningful verification command.
5. Update evidence and the coverage matrix.
6. Continue only if the objective remains incomplete and a defensible next step
   exists.

## Blocked Stop Condition

Stop and report blocked only when no defensible next action remains under the
current permissions or environment. The report must include attempted paths,
evidence gathered, exact blocker, and the smallest input or environment change
that would unlock progress.

