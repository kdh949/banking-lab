# Demo Video Script

## Opening

This is a synthetic Bank-grade Core Banking Lab. It does not connect to real money, real PII, or real payment networks. The point is to show bank-grade control structure: double-entry ledger, idempotency, audit logs, maker-checker, manifest-driven screens, complaints, FDS/AML, and reconciliation.

## Scene 1: Ledger Integrity

Show:

- `services/core-banking`
- ledger tests
- balance projection from postings

Narration:

Balances are not directly mutated. Every movement is represented as a balanced transaction and postings.

## Scene 2: Staff Integrated Terminal

Open:

- `/staff-terminal`

Show:

- transaction code input
- reason-required customer lookup
- masked PII
- audit panel
- approval inbox
- `APR001` approval inbox tab with API-backed list/detail/approval controls when the Spring API is configured
- `AUD001` audit log tab with API-backed event list and hash-chain status when the Spring API is configured

Narration:

Sensitive staff access requires a reason and leaves an audit event. The approval inbox and audit log are no longer only declared screens; they have conditional Spring API-backed paths. Account hold, limit change, fee waiver, KYC review, and transaction correction remain follow-up command slices.

## Scene 3: Customer Web Transfer

Open:

- `/customer-web`

Show:

- accounts
- transaction history
- idempotent transfer result

Narration:

Customer and staff transaction history read the same ledger source of truth.

## Scene 4: Complaint Workflow

Open:

- `/complaint-portal`
- `/staff-terminal`

Show:

- complaint intake
- staff workflow
- answer draft
- maker-checker approval
- customer confirmation

Narration:

Customer-visible answers are sent only after approval.

## Scene 5: FDS and AML

Open:

- `/fds-aml-console`

Show:

- high amount transfer hold
- FDS release/block
- AML STR simulation closure

Narration:

Held transfers do not hit the ledger until a checker approves release.

## Scene 6: EOD Reconciliation

Open:

- `/ops-console`

Show:

- seed transfer
- run EOD
- unmatched item
- adjustment request
- approval

Narration:

Closed business dates cannot be mutated directly. Corrections are balanced adjustment transactions on an open date.

## Scene 7: Coverage and Remaining Gaps

Open:

- `docs/implementation-coverage-matrix.md`
- `docs/test-evidence/evidence-gap-report.md`

Show:

- API-backed, manifest-only, partial, and missing classifications
- missing staff command slices
- product/fee/interest module gap
- Python/DuckDB analytics gap
- formal TLC gap
- Kubernetes/Helm validation and load or backup/restore drill gap

Narration:

This is a synthetic lab portfolio. The matrix separates what is API-backed from what is only declared, so the demo does not overstate implementation coverage.

## Closing

Show:

- `docs/test-evidence/evidence-pack-summary.md`
- `npm test`
- `npm run validate:manifests`
- `npm run test:e2e`

Narration:

The repository includes runnable controls and evidence documents, not only UI screens.
