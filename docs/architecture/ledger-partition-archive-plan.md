# Ledger Partition And Archive Plan

Date: 2026-06-06

Scope: synthetic banking lab Phase 8 large-ledger operational evidence. This plan does not introduce real customer money, real PII, real KYC, card/payment networks, Open Banking, or external financial institution APIs.

## Current Decision

`ledger_transactions` and `ledger_postings` remain the append-only source-of-truth tables. They are referenced by many existing foreign keys using `ledger_transaction_id`, so moving them directly to native PostgreSQL range partitioning would require a broader composite-key migration and a larger blast radius.

The current low-risk operational model is:

- keep source ledger tables FK-compatible and immutable;
- index `ledger_transactions(business_date, status)` for date-range access;
- use `ledger_transaction_partition_routes` and `ledger_posting_partition_routes` as range-partitioned route/evidence tables keyed by `business_date`;
- maintain route rows through insert triggers from `V027__ledger_db_integrity_and_accounting_structure.sql`;
- treat native source-table partitioning as a future migration only after all direct foreign-key dependencies have a composite-key plan.

## Archive Candidate Rule

Archive candidates are selected by closed historical business date, not by mutable balance state:

```sql
SELECT ledger_transaction_id, business_date
FROM ledger_transactions
WHERE status IN ('POSTED', 'REVERSED')
  AND business_date < :archive_cutoff_date
ORDER BY business_date, ledger_transaction_id;
```

Before export or cold movement, the archive set must prove:

- every included transaction remains balanced by currency;
- every included posting has a matching transaction;
- route metadata exists for transaction and posting rows;
- projection rebuild can still recompute `account_balance_projections` from retained source rows or an approved immutable archive snapshot;
- audit/hash-chain and reversal references remain queryable.

## Query Evidence Required

Phase 8 records synthetic evidence for:

- business-date index coverage;
- partition route consistency;
- old-date archive candidate query;
- account statement date-range query shape;
- reconciliation date-range query shape.

The executable evidence lives in:

- `scripts/generate-large-ledger-dataset.ts`
- `scripts/check-ledger-partition-readiness.ts`
- `docs/test-evidence/generated/large-ledger-dataset-summary.json`
- `docs/test-evidence/generated/ledger-query-benchmark.json`
- `docs/test-evidence/ledger-large-dataset-smoke.md`

## Future Native Partition Migration Gate

A future native partition migration should not start until these conditions are true:

- all foreign keys referencing `ledger_transactions(ledger_transaction_id)` have a composite-key-compatible migration plan;
- ledger reversal, adjustment, customer transfer, payment settlement, reconciliation, EOD, statements, certificates, and audit export queries are tested against partitioned source tables;
- rollback can restore source-table FK behavior without direct balance mutation;
- migration dry-run evidence exists on disposable synthetic data at least as large as the Phase 8 generator default;
- no evidence claims production capacity from the synthetic benchmark alone.
