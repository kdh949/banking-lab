# Synthetic Load Smoke

This directory contains local synthetic load scenarios for Banking Lab.

The default runner invokes the in-process lab request handler and uses synthetic seed data only. It is a smoke test for scenario coverage, idempotency retry handling, FDS held-case visibility, approval inbox visibility, ledger invariant preservation, and audit hash-chain continuity. It is not a production capacity benchmark.

```bash
npm run load:synthetic
```

The command writes:

- `docs/test-evidence/generated/load-test-summary.json`
- `docs/test-evidence/load-test-summary.md`

No real money, real PII, real KYC, payment network, or external financial institution API is used.
