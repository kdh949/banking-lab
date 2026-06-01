# Phase 4 Customer Web Test Evidence

## Acceptance Checks

- Customer mock login succeeds.
- Account list and account detail use projected ledger balances.
- Customer transfer posts through double-entry ledger postings.
- Customer transaction history and staff transaction history expose the same ledger transaction.
- Transfer retry returns the original result without duplicating ledger transactions.
- Held and failed transfer states are represented without unsafe ledger postings.
- Customer web exposes complaint entry into the complaint portal.

## Commands

```bash
npm test
npm run validate:manifests
npm run evidence:phase4
```

Generated machine-readable evidence is written to `docs/test-evidence/generated/phase-4-customer-web.json`.
