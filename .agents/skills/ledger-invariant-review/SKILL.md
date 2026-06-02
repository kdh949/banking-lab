---
name: ledger-invariant-review
description: Review banking-lab ledger changes for double-entry integrity, projected balances, idempotency, append-only finalized transactions, closed-day rules, isolation tests, outbox persistence, and synthetic-only safety. Use before or during changes to Kotlin/Spring ledger code, migrations, ledger tests, reconciliation adjustments, or parity reviews.
---

# Ledger Invariant Review

## Scope

Use this skill for ledger-affecting work in `/Users/donghyunkim/Documents/banking-lab`.

Read first:

1. `PLAN.md`
2. `AGENTS.md`
3. `docs/migration/kotlin-next-playbook.md`
4. `docs/migration/parity-scenarios.json`
5. `docs/architecture/phase-2-ledger-core.md`
6. Relevant Node oracle tests under `tests/ledger*.test.mjs`

## Review Checklist

- Every movement creates balanced postings per transaction and currency.
- Balances are derived from postings or projection rows updated from postings, never mutable source-of-truth fields.
- Finalized transactions are append-only; reversal and adjustment are modeled as new balanced transactions.
- External commands are idempotent and replay returns the original result.
- Closed business dates reject direct posting.
- Reconciliation corrections use balanced `ADJUSTMENT` transactions on open business dates.
- Critical posting paths explicitly test transaction isolation, at least `REPEATABLE READ` and `SERIALIZABLE` when implementation scope reaches PostgreSQL concurrency.
- PostgreSQL writes persist durable state inside the business transaction.
- Events are written to `outbox_events`; do not publish directly from the business method without durable outbox persistence.
- Error responses follow `docs/migration/structured-api-error-contract.md`.
- All data and examples remain synthetic.

## Commands

Prefer the narrowest relevant checks, then the full parity gate when the slice is ready:

```bash
npm run parity
npm test
./gradlew :services:core-banking:test
./gradlew :services:core-banking:integrationTest
```

If Gradle/JDK/Testcontainers are unavailable, report the exact environment blocker and run the closest Node oracle checks.

## Output

Lead with findings ordered by severity. Include file and line references when possible.

Always report:

- Invariant verdict
- Commands run
- Parity scenarios checked
- Missing tests or evidence
- Required coordinator-owned changes
