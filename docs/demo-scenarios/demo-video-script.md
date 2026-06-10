# Demo Video Script

Target length: 5-10 minutes.

Boundary statement: this is a synthetic banking lab. It does not connect to real money, real PII, real KYC/AML providers, card networks, payment networks, regulator filing systems, or external financial-institution APIs.

## Scene 1: Synthetic Boundary

Open `README.md`.

Show:

- target-stack scope and archived Node oracle boundary
- explicit local-simulation limits
- `docs/test-evidence/final-hardening-scorecard.md`

Narration:

This portfolio demonstrates bank-grade control patterns in a synthetic environment. The Node runtime is retained only as an archived oracle; target behavior belongs in Spring, PostgreSQL, Next.js, Redpanda/Kafka, Temporal, Python analytics, and platform evidence.

## Scene 2: Customer Signup And Login

Open `/customer-web/signup` and `/customer-web/login`.

Show:

- synthetic signup/login forms
- session state from Spring auth responses
- masked customer/account context after login

Narration:

Customer self-service uses synthetic identity data only. The secure-default path rejects simulator tokens unless explicit local/test opt-ins are enabled.

## Scene 3: Customer Account And Transfer

Open `/customer-web/accounts`, `/customer-web/accounts/[accountId]`, `/customer-web/transfers/new`, and `/customer-web/transfers/[resultId]`.

Show:

- owned account list/detail
- masked account numbers
- internal recipient lookup
- idempotent transfer replay
- transaction history/status reading the ledger source of truth

Narration:

Balances are projections from postings, not mutable truth fields. A retried transfer returns the original result instead of creating duplicate postings.

## Scene 4: Staff Reason-Required Lookup

Open `/staff-terminal` and the Spring staff API evidence.

Show:

- iWorks integrated terminal shell
- `/api/terminal-status`
- `docs/test-evidence/api-backed-channel-smoke.md`
- `docs/test-evidence/live-route-api-execution.md`

Narration:

The current staff frontend is the integrated terminal shell. Staff Spring control APIs still enforce reason-required access, masking, privileged unmasking, denial audit, and maker-checker controls, but staff-terminal route-to-API execution is not claimed until a new operator route is implemented.

## Scene 5: Fee Waiver Or Transaction Correction Request

Open evidence for `FEE-102`, `FEE-103`, or `LED-103`.

Show:

- reason-required request
- maker-checker approval row
- reversal/refund path instead of source-row mutation
- `docs/test-evidence/api-backed-channel-smoke.md`

Narration:

High-risk operational corrections create approval records and balanced ledger reversal or adjustment effects. Finalized transactions are not edited in place.

## Scene 6: Checker Approval

Open approval evidence and one approval-backed flow.

Show:

- maker request
- independent checker approval
- self-approval rejection
- structured error on invalid workflow transition

Narration:

Maker-checker separation is enforced at the service layer, and invalid approvals fail before business mutation.

## Scene 7: Resulting Ledger Reversal Or Refund

Open ledger evidence.

Show:

- `docs/test-evidence/phase-2-ledger-core.md`
- `docs/test-evidence/hardening-h3-ledger-db-integrity.md`
- balanced postings for reversal/refund/adjustment

Narration:

The accounting effect is append-only. Corrections use new balanced transactions, and PostgreSQL triggers reject invalid posted ledger state.

## Scene 8: Complaint Intake And Escalation

Open `/complaint-portal` and complaint evidence.

Show:

- complaint intake/list/detail
- additional material/reopen/type guide
- answer draft
- checker approval before customer-visible answer
- workflow failure-state rendering

Narration:

Customer-visible complaint answers are sent only after approval. Timeline and audit rows preserve the case lifecycle.

## Scene 9: FDS Held Transfer Release Or Block

Open `/fds-aml-console`.

Show:

- held high-amount transfer
- release request and checker approval
- block request and no ledger posting
- duplicate workflow failure state

Narration:

A held transfer does not hit the ledger until release is approved. Blocking closes the held result without unsafe postings.

## Scene 10: Ops EOD And Reconciliation

Open `/ops-console`.

Show:

- EOD/reconciliation monitor
- mismatch item
- adjustment request
- checker approval
- closed-date posting rejection

Narration:

Closed business dates reject direct posting. Reconciliation corrections are balanced adjustments on an open business date.

## Scene 11: Audit Hash Chain And Access History

Open `/audit-console` and customer access history.

Show:

- hash-chain validity
- reason-required access log
- masked delivery/reporting artifacts where configured
- `docs/test-evidence/hardening-h5-operational-security.md`

Narration:

Audit is not a UI-only claim. The hash chain, access history, WORM export simulation, break-glass review, and SIEM drill are backed by tests and generated evidence.

## Scene 12: Call-Center Workflow Boundary

Open:

- `docs/test-evidence/final-hardening-scorecard.md`
- `docs/test-evidence/live-route-api-execution.md`
- `docs/test-evidence/evidence-gap-report.md`

Show:

- scorecard row `Call-center 상담 전산`
- current missing status
- required future workflow: 상담 이력, 상담 메모, 후처리, escalation, authorization, audit

Narration:

The lab has adjacent staff, complaint, workflow, approval, and audit APIs, but no coherent call-center console or staff-terminal call-center workflow yet. This remains a PLAN gap and is not demonstrated as complete.

## Scene 13: Evidence Pack, CI, Contract, And Formal Gates

Open:

- `docs/test-evidence/evidence-pack-summary.md`
- `docs/test-evidence/ci-hosted-run-status.md`
- `docs/test-evidence/contract-validation-hardening.md`
- `docs/test-evidence/contract-runtime-evidence-boundary.md`
- `docs/test-evidence/formal-ledger-verification.md`

Show:

- local command evidence
- hosted CI blocked status, not green
- OpenAPI/AsyncAPI gates
- runtime event envelope fixture/source-marker gate
- formal ledger/idempotency evidence

Narration:

The portfolio separates local pass evidence from hosted CI. GitHub Actions is currently blocked before runner startup by an external billing/spending-limit issue, so it is documented as blocked, not green.

## Closing

Show:

- `docs/implementation-coverage-matrix.md`
- `docs/test-evidence/final-hardening-scorecard.md`
- `docs/test-evidence/evidence-gap-report.md`

Narration:

The repository is a synthetic, evidence-backed banking lab. It is useful because the documentation names what is API-backed, what is environment-gated, what is structural, and what is still missing.
