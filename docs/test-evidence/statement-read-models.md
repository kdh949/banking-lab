# Statement And Certificate Read Model Evidence

Date: 2026-06-06

This evidence covers the Phase 1C read-only CQRS slice for monthly statements, transaction confirmations, balance certificates, and customer access history. It uses synthetic ledger/audit rows only and does not write new ledger source rows.

## Scope

- `StatementService` builds customer period statements from `ledger_transactions` and `ledger_postings`.
- `GET /api/customers/{customerId}/statements?from&to` returns debit totals, credit totals, net movement, opening balance, closing balance, and posting lines.
- `GET /api/transactions/{transactionId}/confirmation` returns a deterministic transaction confirmation ID, postings, totals, and balanced flag.
- `GET /api/accounts/{accountId}/balance-certificate?date` returns a deterministic balance certificate ID and balance as of the requested date.
- `V034__statement_balance_certificate_snapshots.sql` persists deterministic balance certificate snapshots, source posting counts, source last business date, source ledger hash, first/last audit event references, and synthetic-only snapshot timestamps.
- Re-reading the same balance certificate returns the persisted snapshot rather than recomputing mutable current available-balance projection fields.
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
npm test
npm run evidence:refresh-check
npm run node:retirement-gate
```

## Result

- `StatementReadModelIntegrationTest`: pass after sandbox escalation because Gradle/Testcontainers need Docker and local file-lock socket access.
- `npm run packages:typecheck`: pass.
- `npm run next:customer-web:typecheck`: pass.
- `npm run validate:manifests`: pass, 106 manifests.
- `npm run test:screen-engine`: pass, 10 tests.
- `npm test`: pass, 165 tests.
- `npm run evidence:refresh-check`: pass.
- `npm run node:retirement-gate`: pass.

## Invariants Verified

- Statement debit and credit totals reconcile to ledger postings for the period.
- Statement opening and closing balances are derived from signed postings.
- Transaction confirmation reports `balanced=true` only when debit and credit totals match.
- Balance certificate ID is deterministic for fixed account/date/balance/source-ledger input.
- Balance certificate snapshots persist exactly once per deterministic certificate ID and keep first-view current projection values reproducible even if later hold/projection state changes.
- Snapshot metadata records source posting count, source last business date, and a source ledger hash derived from ordered ledger postings.
- Customer token cannot read another customer's transaction confirmation.
- Staff read without reason returns `POLICY_REASON_REQUIRED`.
- Access history excludes another customer's audit event.
- No statement/certificate route inserts ledger transactions.
- Existing ledger postings remain balanced.

## Synthetic Boundary

The integration test uses synthetic customers (`SYN-CUS-STMT-*`), accounts (`ACC-STMT-*`), transactions, and audit events. No real customer data, raw PII, real funds, real KYC, payment network, or external financial institution API is used.

## Remaining Risk

The balance certificate now persists first-view snapshot metadata for reproducibility. A future production-like slice should add scheduled EOD balance snapshots and hold lifecycle snapshots if certificates must reproduce historical available balances for dates before first certificate generation.
