# Hardening H7 Data Platform Evidence

Date: 2026-06-05

## Scope

H7 adds a synthetic analytical data-platform slice:

- synthetic OLTP extracts for ledger postings and account balance projections;
- DuckDB ELT into finance and risk exposure marts;
- Parquet mart output for finance and risk reporting;
- field-level lineage from source extracts to marts to report fields;
- DQ checks for nulls, duplicate projection keys, positive posting amounts, balance ranges, and ledger projection reconciliation;
- deterministic synthetic liquidity/exposure risk report.

This slice uses only fixed synthetic extracts. It does not use real money, real PII, real KYC, real payment networks, real external providers, real customer data, or production warehouse/lakehouse infrastructure.

## Changed Control Surface

- New package entrypoint: `banking-lab-data-platform`.
- New root command: `npm run data:dq-check`.
- New synthetic source files:
  - `analytics/aml-fds-python/sample-data/data-platform/ledger_postings.csv`;
  - `analytics/aml-fds-python/sample-data/data-platform/account_balance_projections.csv`.
- New generated evidence under `docs/test-evidence/generated/data-platform/`.

## Commands Run

| Command | Result |
| --- | --- |
| `npm run analytics:fds-aml:test` | first sandbox run failed because `uv` attempted to use `~/.cache/uv`; after switching to repo-local `.uv-cache`, the next sandbox run failed on PyPI DNS for `hatchling`; escalated rerun passed with 10 Python tests |
| `npm run data:dq-check` | pass; generated DQ evidence, lineage JSON, risk report JSON, and two Parquet marts |
| `node --test tests/dataPlatform.test.mjs` | pass |
| `uv --cache-dir .uv-cache run --directory analytics/aml-fds-python --project . banking-lab-data-platform --source-dir sample-data/data-platform --output-dir /private/tmp/banking-lab-h7-dirty --inject-dirty-data` | expected fail; DQ gate returned `fail` after injected dirty data |

## Generated Evidence

| Artifact | Evidence |
| --- | --- |
| Data-platform evidence | `docs/test-evidence/generated/data-platform/data-platform-evidence.json` |
| Field lineage | `docs/test-evidence/generated/data-platform/data-lineage.json` |
| Risk report | `docs/test-evidence/generated/data-platform/synthetic-risk-report.json` |
| Finance mart | `docs/test-evidence/generated/data-platform/finance_balance_mart.parquet` |
| Risk exposure mart | `docs/test-evidence/generated/data-platform/risk_exposure_mart.parquet` |

## Evidence Summary

| Control | Evidence |
| --- | --- |
| Balance mart reconciliation | DQ check `ledger_projection_reconciliation` passed with 0 failed rows; Python tests assert the mart equals account balance projections |
| Dirty data gate | injected dirty CLI run returned non-zero and Python tests assert duplicate projection and reconciliation failures |
| Field lineage | `riskReport.totalAvailableBalanceMinor` resolves from `account_balance_projections.available_balance_minor` to `risk_exposure_mart.available_balance_minor` to the report field |
| Reproducible report | Python tests run the same fixed inputs twice and assert identical risk report plus identical `reportHash` |
| Synthetic boundary | generated evidence asserts no real money, PII, KYC, payment-network, or external-provider data |

## Remaining Limitations

- This is a local DuckDB/Parquet analytical simulation, not a live warehouse, lakehouse, CDC, catalog, or production data-governance platform.
- The source extracts are fixed CSV fixtures rather than live PostgreSQL logical replication or batch export jobs.
- The risk report is deterministic synthetic evidence only, not a regulatory or management report for real balances.
