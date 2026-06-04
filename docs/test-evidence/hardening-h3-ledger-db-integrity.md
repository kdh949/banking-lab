# Hardening H3 Ledger DB Integrity Evidence

Date: 2026-06-05

## Scope

H3 strengthens the synthetic ledger at the PostgreSQL boundary:

- commit-time deferred DB trigger rejects posted ledger transactions whose postings are unbalanced per currency;
- the same trigger rejects posted ledger transactions with fewer than two postings;
- accounts now carry chart-of-accounts metadata: `account_class`, `system_account_kind`, and `synthetic_system_account`;
- synthetic system accounts are seeded for suspense, clearing, settlement, loan asset, interest expense, fee income, and loan interest income;
- interest, fee, and loan-interest postings route through typed system accounts instead of overloading suspense;
- partitioned route tables record business-date routing for ledger transactions and postings, with a 2026 child partition plus default partition.

This remains a synthetic lab control. It does not use real money, real customer data, real clearing/settlement networks, or external financial institution APIs.

## Changed Control Surface

- New Flyway migration: `db/migrations/V027__ledger_db_integrity_and_accounting_structure.sql`.
- New package script: `npm run ledger:integrity-check`.
- New integration suite: `LedgerDatabaseIntegrityIntegrationTest`.
- Updated ledger system account constants and `LedgerCommandService` routing.
- Updated synthetic data seeding so the correction smoke ledger transaction and postings are inserted within one database transaction.
- Existing direct SQL integration fixtures now seed balanced transaction/posting/projection rows inside an explicit transaction so DB deferred triggers can enforce the same invariant as production writes.

## Commands Run

| Command | Result |
| --- | --- |
| `npm run test:core-banking:integration -- --tests lab.banking.core.ledger.application.LedgerDatabaseIntegrityIntegrationTest --rerun-tasks` | pass after sandbox escalation |
| `npm run test:core-banking:integration -- --tests lab.banking.core.ledger.application.LedgerCommandServiceIntegrationTest --rerun-tasks` | pass after sandbox escalation |
| `npm run test:core-banking:integration -- --tests lab.banking.core.ledger.application.LedgerDatabaseIntegrityIntegrationTest --tests lab.banking.core.product.DepositProductApiIntegrationTest --tests lab.banking.core.product.FeePolicyApiIntegrationTest --tests lab.banking.core.loan.LoanDomainIntegrationTest --tests lab.banking.core.card.CardDomainIntegrationTest --rerun-tasks` | pass after fixture transaction-boundary fix |
| `npm run test:core-banking:integration -- --tests lab.banking.core.statement.StatementReadModelIntegrationTest --tests lab.banking.core.fds.FdsCaseApiParityIntegrationTest --tests lab.banking.core.reconciliation.ReconciliationOpsApiParityIntegrationTest --tests lab.banking.core.eod.EodClosingPipelineIntegrationTest --tests lab.banking.core.parameters.ParameterAdminIntegrationTest --tests lab.banking.core.staff.StaffAccessApiParityIntegrationTest --rerun-tasks` | pass after fixture transaction-boundary fix |
| `npm run ledger:integrity-check` | pass after sandbox escalation |
| `npm run formal:ledger` | pass; 3335 states, 8241 transitions, 15 invariants |
| `scripts/run-core-banking-tests.sh :services:core-banking:bootJar` | pass; rebuilt the Spring Boot jar for DAST smoke |
| `COMPOSE_PROJECT_NAME=banking-lab-dast-smoke BANKING_LAB_POSTGRES_PORT=15480 BANKING_LAB_CORE_BANKING_PORT=18132 BANKING_LAB_SYNTHETIC_SEED_ENABLED=true docker compose --profile migration up -d --build postgres core-banking` | pass after Docker socket escalation |
| `curl -fsS http://127.0.0.1:18132/health` | pass; synthetic-only health returned `status=ok` |
| `env BANKING_LAB_DAST_URL=http://host.docker.internal:18132/health npm run security:evidence:docker` | pass; 5 passed, 0 failed, 0 skipped |
| `COMPOSE_PROJECT_NAME=banking-lab-dast-smoke docker compose --profile migration down --remove-orphans` | pass; disposable DAST containers removed |
| `npm test` | pass; 142 Node/manifests/evidence tests |
| `npm run test:core-banking:integration -- --rerun-tasks` | pass after sandbox escalation; full Spring integration suite |
| `npm run node:retirement-gate` | pass; Node reference retirement gate remains ready |

## Failures And Fixes

- Initial sandboxed Gradle run failed with `java.net.SocketException: Operation not permitted` while creating Gradle file-lock sockets. Re-ran the same command with approved escalation.
- The first product/loan/card regression run failed because legacy integration fixtures inserted `POSTED` `ledger_transactions` in autocommit mode before inserting postings. The new deferred trigger correctly rejected the temporarily incomplete transaction. Fixed by wrapping direct fixture seeding in explicit transactions.
- The first Docker DAST smoke boot after H3 failed in `SyntheticDataSeeder.seedLedgerCorrectionTransactions` for the same reason: it inserted the synthetic correction ledger transaction before postings in autocommit mode. Fixed by injecting `PlatformTransactionManager` and wrapping that seed pair in a `TransactionTemplate`.
- A short-lived trigger-scope relaxation was rejected because it would not catch direct zero-posting posted transactions. The final migration keeps both transaction-row and posting-row deferred triggers.

## Invariants And Controls

- `SUM(postings) == 0` is now enforced by both service code and a deferred PostgreSQL trigger for posted/reversed ledger transactions.
- Posted ledger transactions must have at least two postings at commit.
- Existing append-only triggers from `V002__ledger_constraints.sql` still block update/delete of finalized ledger source rows.
- Balance projection remains service-maintained from postings; `npm run formal:ledger` still passes.
- Interest and fee economics now flow through typed synthetic income/expense accounts.
- Partition routing is implemented as FK-compatible partitioned route tables because the current source ledger tables are referenced by many single-column `ledger_transaction_id` foreign keys. Replacing the source tables with native range partitions would require a wider composite-key migration.
