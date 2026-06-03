# Phase 4 Customer Web Test Evidence

## Acceptance Checks

- Customer login propagation uses the Keycloak/OIDC browser flow in target evidence.
- Account detail uses projected ledger balances and appends a customer self-service `ACCOUNT_VIEW` audit event with masked account data.
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
scripts/run-core-banking-tests.sh --rerun-tasks :services:core-banking:integrationTest --tests 'lab.banking.core.customer.CustomerAccountApiParityIntegrationTest'
```

Generated machine-readable evidence is written to `docs/test-evidence/generated/phase-4-customer-web.json`.
