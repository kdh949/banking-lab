# Phase 2 Ledger Core Test Evidence

## Acceptance Checks

- Deposit and withdrawal create balanced ledger transactions.
- Withdrawal cannot exceed available balance.
- Internal transfer changes both accounts from the same ledger source of truth.
- Idempotency retry returns the original transaction and does not duplicate postings.
- Reversal references the original transaction and cannot be duplicated.
- Concurrent withdrawals serialize and cannot overdraw.
- Closed business day rejects direct posting commands.
- Runtime withdrawal and reversal endpoints preserve invariants.
- Kotlin/Spring `LedgerCommandService` persists deposits, withdrawals, transfers, reversals, adjustments, daily closings, idempotency replay, and outbox rows through PostgreSQL/Testcontainers.
- Redpanda Testcontainers verifies publish-after-durable-outbox-insert, inbox idempotency on replay, broker failure retry, and DLQ transition.
- REPEATABLE READ and SERIALIZABLE tests verify concurrent withdrawals do not overdraw; serialization conflicts may reject extra attempts until retry policy is added.

## Commands

```bash
npm test
npm run evidence:phase2
docker run --rm -v /Users/donghyunkim/Documents/banking-lab:/workspace -w /workspace gradle:8.14.3-jdk21 ./gradlew :services:core-banking:test --no-daemon
docker run --rm -e TESTCONTAINERS_RYUK_DISABLED=true -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -v /var/run/docker.sock:/var/run/docker.sock -v /Users/donghyunkim/Documents/banking-lab:/workspace -w /workspace gradle:8.14.3-jdk21 ./gradlew :services:core-banking:integrationTest --no-daemon
```

Generated machine-readable evidence is written to `docs/test-evidence/generated/phase-2-ledger-core.json`.
