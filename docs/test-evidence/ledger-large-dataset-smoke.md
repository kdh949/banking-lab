# Large Ledger Dataset Smoke

Date: 2026-06-06

Status: pass

This is deterministic synthetic large-ledger evidence for Phase 8 of `docs/codex/remaining-hardening-goals.md`. It does not use real customer money, real PII, real KYC, real payment/card networks, Open Banking, external financial institution APIs, or production traffic.

## Command

```bash
npm run ledger:large-dataset-smoke
```

## Dataset Shape

| Metric | Value |
| --- | ---: |
| Customers | 240 |
| Customer accounts | 720 |
| System accounts | 1 |
| Ledger transactions | 5000 |
| Ledger postings | 10000 |
| Idempotency keys | 5000 |
| Balance projections | 721 |
| Archive candidate transactions | 2494 |

## Determinism

- Seed: 424242
- Business date range: 2025-01-01 to 2026-12-31
- Dataset hash: `46bc4baf35f8fff447648aa47cfea9778c08903cae9726daa7f271cc97262b4b`
- Summary JSON: `docs/test-evidence/generated/large-ledger-dataset-summary.json`

## Checks

| Check | Status | Details |
| --- | --- | --- |
| deterministic-seed-replay | pass | first hash 46bc4baf35f8fff447648aa47cfea9778c08903cae9726daa7f271cc97262b4b, replay hash 46bc4baf35f8fff447648aa47cfea9778c08903cae9726daa7f271cc97262b4b |
| balanced-postings | pass | 0 transactions have non-zero debit/credit net |
| projection-matches-postings | pass | 0 account/currency projections differ from signed postings |
| idempotency-keys-unique | pass | 5000 unique keys for 5000 transactions |
| business-dates-in-range | pass | 2025-01-01 through 2026-12-31 |
| partition-route-coverage | pass | 5000 transaction routes and 10000 posting routes |
| synthetic-boundary | pass | all identifiers use CUS-SYN, ACCT-SYN, LTX-LARGE, and IDEMP-LARGE fixtures only |

## Evidence Boundary

- This smoke proves generator determinism, balanced postings, idempotency-key uniqueness, projection equality, synthetic identifiers, and partition-route coverage for the generated fixture.
- It is not a production capacity benchmark and does not prove Kubernetes, external IdP, external provider, or live PostgreSQL throughput.
