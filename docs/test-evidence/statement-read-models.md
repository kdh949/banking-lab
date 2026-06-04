# Statement And Certificate Read Model Evidence

Date: 2026-06-04

This evidence covers the Phase 1C read-only CQRS slice for monthly statements, transaction confirmations, balance certificates, and customer access history. It uses synthetic ledger/audit rows only and does not write new ledger source rows.

## Scope

- `StatementService` builds customer period statements from `ledger_transactions` and `ledger_postings`.
- `GET /api/customers/{customerId}/statements?from&to` returns debit totals, credit totals, net movement, opening balance, closing balance, and posting lines.
- `GET /api/transactions/{transactionId}/confirmation` returns a deterministic transaction confirmation ID, postings, totals, and balanced flag.
- `GET /api/accounts/{accountId}/balance-certificate?date` returns a deterministic balance certificate ID and balance as of the requested date.
- `GET /api/customers/{customerId}/access-history` returns only the requested customer's audit events.
- Customer tokens are ownership-scoped; staff tokens require an allowed staff role and a business reason.
- Customer-web CWB-103 now has statement/confirmation/certificate actions, and CWB-401 points at the access-history read model.

## Commands Run

```bash
npm run test:core-banking:integration -- --tests lab.banking.core.statement.StatementReadModelIntegrationTest
npm run packages:typecheck
npm run next:customer-web:typecheck
npm run validate:manifests
npm run test:screen-engine
```

## Result

- `StatementReadModelIntegrationTest`: pass after rerun outside the sandbox because Gradle file-lock socket creation was blocked.
- `npm run packages:typecheck`: pass.
- `npm run next:customer-web:typecheck`: pass.
- `npm run validate:manifests`: pass, 74 manifests.
- `npm run test:screen-engine`: pass, 10 tests.

## Invariants Verified

- Statement debit and credit totals reconcile to ledger postings for the period.
- Statement opening and closing balances are derived from signed postings.
- Transaction confirmation reports `balanced=true` only when debit and credit totals match.
- Balance certificate ID is deterministic for fixed account/date/balance input.
- Customer token cannot read another customer's transaction confirmation.
- Staff read without reason returns `POLICY_REASON_REQUIRED`.
- Access history excludes another customer's audit event.
- No statement/certificate route inserts ledger transactions.
- Existing ledger postings remain balanced.

## Synthetic Boundary

The integration test uses synthetic customers (`SYN-CUS-STMT-*`), accounts (`ACC-STMT-*`), transactions, and audit events. No real customer data, raw PII, real funds, real KYC, payment network, or external financial institution API is used.

## Remaining Risk

The balance certificate uses signed postings as the as-of source and current projection fields for current balances. A future production-like slice should add explicit balance snapshot tables if certificates must reproduce historical available balances including holds.
