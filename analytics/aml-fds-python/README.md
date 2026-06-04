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
```

## Controls

- DuckDB loads only synthetic CSV rows into an in-memory mart.
- Features include 24-hour velocity, first beneficiary, high amount, risk grade, and median amount ratio.
- Scoring combines deterministic rules with deterministic anomaly scoring.
- Generated artifacts are evidence only; they are not a real monitoring feed.
