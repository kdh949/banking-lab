# Synthetic AML/FDS Analytics

This package builds a synthetic-only AML/FDS analytics artifact for Banking Lab. It does not use real money, real PII, real KYC data, real payment networks, or external financial institution APIs.

## Local Commands

```bash
uv run --directory analytics/aml-fds-python --project . --extra dev pytest
uv run --directory analytics/aml-fds-python --project . banking-lab-analytics \
  --input sample-data/transactions.csv \
  --output ../../docs/test-evidence/generated/fds-aml-analytics.json \
  --csv-output ../../docs/test-evidence/generated/fds-aml-analytics.csv
```

The repository root also exposes:

```bash
npm run analytics:fds-aml:test
npm run analytics:fds-aml
npm run data:dq-check
```

## Controls

- DuckDB loads only synthetic CSV rows into an in-memory mart.
- Features include 24-hour velocity, first beneficiary, high amount, risk grade, and median amount ratio.
- Scoring combines deterministic rules with deterministic anomaly scoring.
- Generated artifacts are evidence only; they are not a real monitoring feed.
- H7 data-platform evidence loads synthetic OLTP extracts into DuckDB, writes Parquet marts, records field lineage, runs null/duplicate/range/reconciliation DQ checks, and generates a deterministic liquidity/exposure report.
- The data-platform DQ gate fails intentionally when dirty data is injected in tests; no real customer data, real PII, real money, KYC, payment-network, or external provider data is used.
